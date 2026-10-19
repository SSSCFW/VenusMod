package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;
import java.util.function.BiConsumer;

/** 待機終了・転送完了で時間を分割。長時間経過をtick単位で再実行しない。 */
final class TransferScheduler {
    private List<BandwidthAllocator.Demand> previousDemands=List.of();
    private Map<String,Long> previousCapacities=Map.of();
    private Map<Long,Double> rates=Map.of();
    private long calculations;
    long calculations(){return calculations;}

    void advance(long ticks,List<NetworkSimulator.Flow> flows,Map<String,Long> capacities,BiConsumer<String,String> log){
        double timeLeft=ticks;int events=0;
        // 1回の呼出中、各転送の待機終了と完了はそれぞれ高々1回。
        while(timeLeft>0&&!flows.isEmpty()){
            if(++events>2*BandwidthAllocator.MAX_DEMANDS+1)throw new IllegalStateException("転送イベント処理上限");
            List<BandwidthAllocator.Demand> demands=new ArrayList<>();double step=timeLeft;
            for(var f:flows){
                f.allocatedBps=0;
                if(f.delayTicks>0)step=Math.min(step,f.delayTicks);else demands.add(f.demand());
            }
            if(!demands.equals(previousDemands)||!capacities.equals(previousCapacities)){
                rates=BandwidthAllocator.allocate(demands,capacities).rates();calculations++;
                previousDemands=List.copyOf(demands);previousCapacities=Map.copyOf(capacities);
            }
            for(var f:flows)if(f.delayTicks<=0){
                f.allocatedBps=rates.getOrDefault(f.id,0.0);
                if(f.allocatedBps>0)step=Math.min(step,Math.max(0,f.remaining)*160.0/f.allocatedBps);
            }
            for(var it=flows.iterator();it.hasNext();){var f=it.next();
                if(f.delayTicks>0){f.delayTicks=Math.max(0,f.delayTicks-step);continue;}
                f.remaining=Math.max(0,f.remaining-f.allocatedBps*step/160.0);
                if(f.remaining<=Math.max(1e-6,f.totalBytes*1e-14)){
                    f.remaining=0;f.allocatedBps=0;it.remove();log.accept(f.source,"転送 #"+f.id+": 完了");
                }
            }
            timeLeft=Math.max(0,timeLeft-step);
        }
        if(flows.isEmpty()){previousDemands=List.of();previousCapacities=Map.of();rates=Map.of();}
    }
}
