package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;
import java.util.function.Consumer;

/** 受信側の期限と送信側の更新予約。期限は各1件、通信確認は毎tick最大2件。 */
public final class ControlLeaseRuntime {
    public static final int MAX_LEASES=128,MAX_STREAMS=64,MAX_PER_SOURCE=4,HEARTBEAT_TICKS=20,WORK_PER_TICK=2;
    public enum Refresh { RECEIVED, RETRY, CANCEL }
    public record Lease(String target,long deadline,long duration){}
    public record Stream(String source,String target,UUID actor,ServiceAddress endpoint,long due){}
    @FunctionalInterface public interface Transport {Refresh refresh(Stream stream);}
    private final Map<String,Lease> leases=new HashMap<>();
    private final NavigableSet<Lease> expiry=new TreeSet<>(Comparator.comparingLong(Lease::deadline).thenComparing(Lease::target));
    private final Map<String,Stream> streams=new HashMap<>();
    private final NavigableSet<Stream> sendQueue=new TreeSet<>(Comparator.comparingLong(Stream::due).thenComparing(Stream::target));
    private final Map<String,Integer> sourceCounts=new HashMap<>();
    private long clock=-1,lastWorkTick=-1;
    public boolean active(String target,long now){var lease=leases.get(target);return lease!=null&&now<lease.deadline();}
    public long remaining(String target,long now){var lease=leases.get(target);return lease==null?0:Math.max(0,lease.deadline()-now);}
    public int leaseCount(){return leases.size();}
    public int expiryCount(){return expiry.size();}
    public int streamCount(){return streams.size();}
    public int queueCount(){return sendQueue.size();}
    public List<Stream> streams(String source){return streams.values().stream().filter(s->s.source().equals(source)).sorted(Comparator.comparing(Stream::target)).toList();}
    public boolean canAccept(String target,String source,boolean maintain){
        if(!leases.containsKey(target)&&leases.size()>=MAX_LEASES)return false;
        if(!maintain)return true;
        var existing=streams.get(target);
        return (existing!=null||streams.size()<MAX_STREAMS)
                &&(existing!=null&&existing.source().equals(source)||sourceCounts.getOrDefault(source,0)<MAX_PER_SOURCE);
    }
    /** 呼出元は経路・認可・設定を検証済み。失敗時に一部の予約だけを変更しない。 */
    public boolean accept(String target,String source,UUID actor,ServiceAddress endpoint,long duration,long now,boolean maintain){
        Objects.requireNonNull(target);Objects.requireNonNull(source);Objects.requireNonNull(actor);Objects.requireNonNull(endpoint);
        if(target.isBlank()||source.isBlank()||duration<20||duration>72000||now<0||now<clock||now>Long.MAX_VALUE-duration
                ||maintain&&duration<60)throw new IllegalArgumentException("制御の期限または時刻が不正です (定期維持は3秒以上)");
        if(!canAccept(target,source,maintain))return false;
        cancelTarget(target);replace(new Lease(target,now+duration,duration));
        if(maintain)addStream(new Stream(source,target,actor,endpoint,now+HEARTBEAT_TICKS));
        return true;
    }
    private void replace(Lease lease){var old=leases.put(lease.target(),lease);if(old!=null)expiry.remove(old);expiry.add(lease);}
    private void addStream(Stream stream){streams.put(stream.target(),stream);sendQueue.add(stream);sourceCounts.merge(stream.source(),1,Integer::sum);}
    private void removeStream(String target){var old=streams.remove(target);if(old!=null){sendQueue.remove(old);sourceCounts.computeIfPresent(old.source(),(k,n)->n==1?null:n-1);}}
    public void cancelTarget(String target){removeStream(target);var old=leases.remove(target);if(old!=null)expiry.remove(old);}
    /** 送信停止は最後に受信した期限を延長も短縮もしない。 */
    public int stopSource(String source){var current=streams(source);for(var s:current)removeStream(s.target());return current.size();}
    public void removed(String id){cancelTarget(id);stopSource(id);}
    public void advance(long now,Transport transport,Consumer<String> expired){
        Objects.requireNonNull(transport);Objects.requireNonNull(expired);
        if(now<0||now<clock)throw new IllegalArgumentException("制御時計を巻き戻せません");clock=now;
        // 同時失効でも最大MAX_LEASES件。通信探索より先に失効し、期限後の復活を防ぐ。
        while(!expiry.isEmpty()&&expiry.first().deadline()<=now){var due=expiry.pollFirst();leases.remove(due.target());removeStream(due.target());expired.accept(due.target());}
        if(lastWorkTick==now)return;lastWorkTick=now;
        for(int work=0;work<WORK_PER_TICK&&!sendQueue.isEmpty()&&sendQueue.first().due()<=now;work++){
            var stream=sendQueue.pollFirst();var lease=leases.get(stream.target());
            if(lease==null||lease.deadline()<=now){removeStream(stream.target());continue;}
            Refresh result=Objects.requireNonNull(transport.refresh(stream));
            // 検証側で機器が撤去・置換されたときに、古い処理でジョブを復活させない。
            if(streams.get(stream.target())!=stream||leases.get(stream.target())!=lease)continue;
            if(result==Refresh.CANCEL){removeStream(stream.target());continue;}
            if(result==Refresh.RECEIVED&&now<=Long.MAX_VALUE-lease.duration())replace(new Lease(lease.target(),now+lease.duration(),lease.duration()));
            if(now>Long.MAX_VALUE-HEARTBEAT_TICKS){removeStream(stream.target());continue;}
            var next=new Stream(stream.source(),stream.target(),stream.actor(),stream.endpoint(),now+HEARTBEAT_TICKS);
            streams.put(next.target(),next);sendQueue.add(next);
        }
    }
}
