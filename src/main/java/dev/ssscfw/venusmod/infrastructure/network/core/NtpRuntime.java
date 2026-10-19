package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 表示時計と定期NTP。すべての期限は補正されないサーバーtickを使う。 */
public final class NtpRuntime {
    public static final int MAX_CLIENTS=256,WORK_PER_TICK=2;
    public record Exchange(NetworkSimulator.Result result,NtpSample sample,boolean limited){}
    public record Snapshot(String state,double clockMillis,double correctionMillis,long lastSuccess,long samples,
                           NtpSample lastSample,String detail){}
    private static final class Clock {
        final String id;
        NtpSettings.Config config;
        long epoch,due,order,lastSuccess=-1,samples;
        double correction;
        boolean queued,lastFailed;
        NtpSample lastSample;
        String detail="未同期",lastLogged="";
        Clock(String id,NtpSettings.Config config,long now){this.id=id;this.config=config;epoch=now;}
        double read(double tick){return tick*50.0+config.offsetMillis()+(tick-epoch)*50.0*config.driftPpm()/1_000_000.0+correction;}
    }
    private final NetworkSimulator simulator;
    private final Map<String,Clock> clocks=new HashMap<>();
    private final PriorityQueue<Clock> due=new PriorityQueue<>(Comparator.comparingLong((Clock c)->c.due).thenComparingLong(c->c.order));
    private long now,order,totalAttempts;
    private int lastWork;
    NtpRuntime(NetworkSimulator simulator){this.simulator=simulator;}
    public long now(){return now;}
    public int scheduled(){return due.size();}
    public int clockCount(){return clocks.size();}
    public int lastWork(){return lastWork;}
    public long totalAttempts(){return totalAttempts;}
    private static long plus(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
    public void checkCapacity(String id,NtpSettings settings){
        var old=clocks.get(id);if(settings.enabled&&(old==null||!old.queued)&&due.size()>=MAX_CLIENTS)
            throw new IllegalArgumentException("NTP同期クライアントは1ディメンション256台までです");
    }
    /** 別設定・モジュール変更でProfileが差し替わっても表示時計と予約を保持する。 */
    void synchronize(String id){
        var p=simulator.nodes().get(id);if(p==null){remove(id);return;}
        var config=p.ntp.config();var c=clocks.get(id);
        if(c==null){c=new Clock(id,config,now);clocks.put(id,c);}
        else if(c.config.equals(config)){if(config.enabled()&&!c.queued&&due.size()<MAX_CLIENTS){c.detail="再同期待ち";schedule(c,1);}return;}
        else{
            due.remove(c);c.queued=false;
            if(c.config.offsetMillis()!=config.offsetMillis()||c.config.driftPpm()!=config.driftPpm()){
                c.correction=0;c.epoch=now;
            }
            c.config=config;c.lastSuccess=-1;c.lastSample=null;c.lastFailed=false;c.lastLogged="";c.detail="設定変更: 再同期待ち";
        }
        if(config.enabled()){
            if(due.size()>=MAX_CLIENTS)c.detail="LIMIT: NTPクライアント数の上限です";
            else schedule(c,1);
        }
    }
    public void requestSync(String id){
        var c=clocks.get(id);if(c==null||!c.config.enabled())throw new IllegalArgumentException("先にntp onでクライアントを有効にしてください");
        var p=simulator.nodes().get(id);checkCapacity(id,p.ntp);due.remove(c);schedule(c,1);
    }
    public void remove(String id){var c=clocks.remove(id);if(c!=null)due.remove(c);}
    private void schedule(Clock c,long delay){c.due=plus(now,delay);c.order=order++;c.queued=true;due.add(c);}
    void elapse(long ticks){if(ticks>0)now=plus(now,ticks);}
    public double clockMillis(String id){return clockAt(id,now);}
    private double clockAt(String id,double tick){var c=clocks.get(id);return c==null?tick*50.0:c.read(tick);}
    /** 方向ごとの仮想遅延から4時刻を作る。外部から時刻を注入するAPIではない。 */
    NtpSample measure(String source,String server,double forward,double reverse){
        if(forward<0||reverse<0||!Double.isFinite(forward)||!Double.isFinite(reverse))throw new IllegalArgumentException("NTP経路遅延が不正です");
        return NtpSample.calculate(clockAt(source,now),clockAt(server,now+forward/50.0),clockAt(server,now+forward/50.0),clockAt(source,now+(forward+reverse)/50.0));
    }
    void dispatch(){
        lastWork=0;
        while(lastWork<WORK_PER_TICK&&!due.isEmpty()&&due.peek().due<=now){
            var c=due.remove();c.queued=false;lastWork++;var p=simulator.nodes().get(c.id);
            if(p==null||!c.config.enabled())continue;
            if(p.failed){c.detail="送信元故障: 時計は自走し同期のみ休止";schedule(c,c.config.intervalSeconds()*20L);continue;}
            var exchange=simulator.ntpRequest(c.id,new ServiceAddress(c.config.target(),c.config.port()));totalAttempts++;
            if(exchange.result().success()){
                var sample=Objects.requireNonNull(exchange.sample());
                c.correction+=sample.offsetMillis();c.lastSample=sample;c.samples++;c.lastSuccess=now;c.lastFailed=false;
                c.detail="同期成功: "+c.config.target()+":"+c.config.port()+" (仮想基準stratum 1)";
                logState(c,"SYNCED",5);
            }else{
                c.lastFailed=true;c.detail=(exchange.limited()?"判定保留(処理上限): ":"同期失敗: ")+SyslogRuntime.text(exchange.result().message(),160);
                logState(c,state(c),4);
            }
            schedule(c,c.config.intervalSeconds()*20L);
        }
    }
    private void logState(Clock c,String state,int severity){if(!state.equals(c.lastLogged)){c.lastLogged=state;simulator.log(c.id,severity,"NTP "+state+": "+c.detail);}}
    private String state(Clock c){
        var p=simulator.nodes().get(c.id);if(p!=null&&p.failed)return "PAUSED";
        if(c.config.server())return "REFERENCE";
        if(!c.config.enabled())return "OFF";
        if(c.detail.startsWith("LIMIT:"))return "LIMIT";
        if(c.lastSuccess<0)return "WAITING";
        if(now-c.lastSuccess>=3L*c.config.intervalSeconds()*20)return "STALE";
        return c.lastFailed?"HOLDOVER":"SYNCED";
    }
    public Snapshot snapshot(String id){var c=clocks.get(id);if(c==null)throw new IllegalArgumentException("時計が未登録です");return new Snapshot(state(c),c.read(now),c.correction,c.lastSuccess,c.samples,c.lastSample,c.detail);}
    public List<String> describe(String id){
        var c=clocks.get(id);if(c==null)return List.of("時計が未登録です");var s=snapshot(id);var lines=new ArrayList<String>();
        lines.add("NTP "+s.state+" / 表示時計="+String.format(Locale.ROOT,"%.3fms",s.clockMillis)+" / 単調時刻="+now+"tick");
        lines.add("基準server="+c.config.server()+" / 宛先="+c.config.target()+":"+c.config.port()+" / 間隔="+c.config.intervalSeconds()+"秒");
        lines.add("設定offset="+c.config.offsetMillis()+"ms / drift="+c.config.driftPpm()+"ppm / 補正="+String.format(Locale.ROOT,"%.3fms",c.correction));
        lines.add("最終成功="+(c.lastSuccess<0?"未同期":c.lastSuccess+"tick")+" / 成功="+c.samples+"回 / "+c.detail);
        if(c.lastSample!=null){var a=c.lastSample;lines.add(String.format(Locale.ROOT,"T1 %.3f / T2 %.3f / T3 %.3f / T4 %.3f",a.sent(),a.receivedByServer(),a.sentByServer(),a.received()));lines.add(String.format(Locale.ROOT,"推定offset %.3fms / delay %.3fms",a.offsetMillis(),a.delayMillis()));}
        lines.add("実UTCではありません。時計補正で故障/経路/NAT/制御の期限は変わりません。");return List.copyOf(lines);
    }
}
