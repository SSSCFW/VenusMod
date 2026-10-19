package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/**
 * ログの非同期・片方向配送。1送信元の独占と配送失敗の再帰を防ぐ。
 * OSログ/外部UDPには接続せず、動的履歴はこのディメンションの稼働中だけ保持する。
 */
public final class SyslogRuntime {
    public static final int MAX_PENDING=1024, PER_SENDER=32, PER_SECOND=16, WORK_PER_TICK=2;
    public static final int PER_COLLECTOR=256, MAX_RECEIVED=4096, MAX_MESSAGE=200, MAX_AGE=200;
    public record Entry(long number,long emittedTick,long receivedTick,String origin,String hostname,
                        String sourceAddress,int severity,String message,double emittedClockMillis,double receivedClockMillis){}
    record Delivery(boolean success,String receiver,String sourceAddress,String reason){
        static Delivery fail(String reason){return new Delivery(false,"","",reason);}
    }
    private record Pending(long tick,double clockMillis,String origin,String hostname,String target,int port,int severity,String message){}
    private static final class Sender {
        final ArrayDeque<Pending> queue=new ArrayDeque<>();
        long window=-1,delivered,dropped,filtered;
        int accepted;
        String last="未送信";
    }
    private record Stored(String collector,Entry entry){}
    private final NetworkSimulator simulator;
    private final Map<String,Sender> senders=new HashMap<>();
    private final LinkedHashSet<String> ready=new LinkedHashSet<>();
    private final LinkedHashMap<Long,Stored> received=new LinkedHashMap<>();
    private final Map<String,ArrayDeque<Long>> collectors=new HashMap<>();
    private long now,nextNumber=1;
    private int pending,lastWork;

    SyslogRuntime(NetworkSimulator simulator){this.simulator=simulator;}
    public long now(){return now;}
    public int pending(){return pending;}
    public int retained(){return received.size();}
    public int lastWork(){return lastWork;}
    /** 改行・色指定・制御文字・不正サロゲートをログ本文として画面へ流さない。 */
    static String text(String value,int limit){
        if(value==null)return "";
        var out=new StringBuilder(Math.min(limit,value.length()));
        for(int i=0;i<value.length()&&out.length()<limit;){
            int cp=value.codePointAt(i);i+=Character.charCount(cp);
            if(cp==0x00a7||Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT||cp>=0xd800&&cp<=0xdfff)cp=' ';
            if(out.length()+Character.charCount(cp)>limit)break;out.appendCodePoint(cp);
        }
        return out.toString();
    }
    void offer(String id,int severity,String message){
        if(severity<0||severity>7)throw new IllegalArgumentException("severityは0〜7です");
        var profile=simulator.nodes().get(id);if(profile==null||!profile.syslog.enabled)return;
        var sender=senders.computeIfAbsent(id,k->new Sender());
        if(severity>profile.syslog.level){sender.filtered++;return;}
        if(profile.failed){drop(sender,"送信元停止: 自機からは送れません");return;}
        long window=now/20;if(sender.window!=window){sender.window=window;sender.accepted=0;}
        if(sender.accepted>=PER_SECOND||sender.queue.size()>=PER_SENDER||pending>=MAX_PENDING){drop(sender,"配送待ち/送信レート上限");return;}
        sender.accepted++;sender.queue.addLast(new Pending(now,simulator.ntp().clockMillis(id),id,text(profile.name,48),profile.syslog.target,profile.syslog.port,severity,text(message,MAX_MESSAGE)));
        pending++;ready.add(id);sender.last="配送待ち";
    }
    private static void drop(Sender sender,String reason){sender.dropped++;sender.last=text(reason,160);}
    /** 配送前に時刻だけを進め、プロトコル遷移が生成するログも同じ時刻で記録する。 */
    void elapse(long ticks){if(ticks>0)now=ticks>Long.MAX_VALUE-now?Long.MAX_VALUE:now+ticks;}
    void dispatch(){
        lastWork=0;
        while(!ready.isEmpty()&&lastWork++<WORK_PER_TICK){
            String id=ready.getFirst();ready.remove(id);var sender=senders.get(id);
            if(sender==null||sender.queue.isEmpty())continue;
            var event=sender.queue.removeFirst();pending--;if(!sender.queue.isEmpty())ready.add(id);
            var profile=simulator.nodes().get(id);
            if(profile==null||profile.failed||!profile.syslog.enabled||!event.target.equals(profile.syslog.target)||event.port!=profile.syslog.port){drop(sender,"送信元停止/設定変更で破棄");continue;}
            if(event.severity>profile.syslog.level){sender.filtered++;continue;}
            if(now-event.tick>=MAX_AGE){drop(sender,"配送待ち期限切れ");continue;}
            Delivery delivery=simulator.deliverSyslog(id,event.target,event.port);
            if(!delivery.success){drop(sender,delivery.reason);continue;}
            retain(delivery.receiver,new Entry(nextNumber++,event.tick,now,event.origin,event.hostname,delivery.sourceAddress,event.severity,event.message,event.clockMillis,simulator.ntp().clockMillis(delivery.receiver)));
            sender.delivered++;sender.last="シミュレーション上で到着 (UDP応答ではありません)";
        }
        lastWork=Math.min(lastWork,WORK_PER_TICK);
    }
    private void retain(String collector,Entry entry){
        while(received.size()>=MAX_RECEIVED)evict(received.firstEntry().getKey());
        var ids=collectors.computeIfAbsent(collector,k->new ArrayDeque<>());
        while(ids.size()>=PER_COLLECTOR){long old=ids.removeFirst();received.remove(old);}
        ids.addLast(entry.number);received.put(entry.number,new Stored(collector,entry));
    }
    private void evict(long number){var old=received.remove(number);if(old==null)return;var ids=collectors.get(old.collector);if(ids!=null){ids.remove(number);if(ids.isEmpty())collectors.remove(old.collector);}}
    public List<Entry> entries(String collector){var result=new ArrayList<Entry>();var ids=collectors.get(collector);if(ids!=null)for(long id:ids){var item=received.get(id);if(item!=null)result.add(item.entry);}return List.copyOf(result);}
    public void clear(String collector){var ids=collectors.remove(collector);if(ids!=null)for(long id:ids)received.remove(id);}
    /** 宛先変更・停止時の古い待ちを破棄。届いた履歴は設定変更で消去しない。 */
    void configurationChanged(String id){var sender=senders.get(id);if(sender!=null){sender.dropped+=sender.queue.size();pending-=sender.queue.size();sender.queue.clear();sender.last="設定変更: 待ちを破棄";}ready.remove(id);}
    void remove(String id){configurationChanged(id);senders.remove(id);clear(id);}
    public List<String> status(String id){
        var p=simulator.nodes().get(id);if(p==null)return List.of("機器未登録");var state=senders.get(id);
        return List.of("Syslog送信 "+(p.syslog.enabled?"ON":"OFF")+" → "+(p.syslog.target.isEmpty()?"未設定":p.syslog.target+":"+p.syslog.port)+" / severity<= "+p.syslog.level,
            "受信 "+(p.syslog.server?"ON":"OFF")+" UDP514 / 許可元 "+p.syslog.allow+" / 履歴 "+entries(id).size()+"/"+PER_COLLECTOR,
            "自機待ち "+(state==null?0:state.queue.size())+" / 到着 "+(state==null?0:state.delivered)+" / 破棄 "+(state==null?0:state.dropped)+" / 除外 "+(state==null?0:state.filtered),
            state==null?"未送信":state.last,"履歴・待ち・統計は稼働中のみ。配送保証・再送・TCP/TLSはありません。");
    }
    public List<String> describe(String collector,int limit){
        if(limit<1||limit>20)throw new IllegalArgumentException("表示件数は1〜20です");
        var entries=entries(collector);var lines=new ArrayList<String>();lines.add("Syslog受信履歴 "+entries.size()+"件 (新しい順、表示最大"+limit+")");
        for(int i=entries.size()-1;i>=Math.max(0,entries.size()-limit);i--){var e=entries.get(i);lines.add("#"+e.number+" tick="+e.emittedTick+"→"+e.receivedTick+" clockMs="+String.format(Locale.ROOT,"%.3f→%.3f",e.emittedClockMillis,e.receivedClockMillis)+" sev="+e.severity+" "+e.hostname+" ["+e.sourceAddress+"] "+e.message);}
        return List.copyOf(lines);
    }
}
