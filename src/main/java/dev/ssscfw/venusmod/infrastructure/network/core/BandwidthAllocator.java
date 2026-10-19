package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 仮想転送の重み付きmax-min公平配分。パケット配列やOS通信を生成しない。 */
public final class BandwidthAllocator {
    public static final int MAX_DEMANDS=128,MAX_RESOURCES=16384,MAX_PATH_RESOURCES=128;
    public record Demand(long id,long ceilingBps,int weight,List<String> resources){
        public Demand{
            if(id<=0||ceilingBps<0||!Set.of(1,2,4).contains(weight))throw new IllegalArgumentException("転送の帯域/優先度が不正です");
            if(resources==null||resources.size()>MAX_PATH_RESOURCES)throw new IllegalArgumentException("転送経路の上限です");
            for(String key:resources)if(key==null||key.isEmpty())throw new IllegalArgumentException("転送資源が不正です");
            resources=List.copyOf(new LinkedHashSet<>(resources));
        }
    }
    public record Allocation(Map<Long,Double> rates,int rounds){public Allocation{rates=Map.copyOf(rates);}}
    private BandwidthAllocator(){}

    /** 各反復で少なくとも1転送が上限に達するため、反復は最大128回。 */
    public static Allocation allocate(List<Demand> demands,Map<String,Long> capacities){
        if(demands.size()>MAX_DEMANDS||capacities.size()>MAX_RESOURCES)throw new IllegalArgumentException("転送配分の容量上限です");
        Map<String,Integer> indices=new LinkedHashMap<>();int resourceCount=capacities.size();double[] capacity=new double[resourceCount];
        for(var entry:capacities.entrySet()){
            if(entry.getKey()==null||entry.getValue()==null||entry.getValue()<0)throw new IllegalArgumentException("資源容量が不正です");
            int index=indices.size();indices.put(entry.getKey(),index);capacity[index]=entry.getValue();
        }
        int count=demands.size();int[][] paths=new int[count][];boolean[] active=new boolean[count];Arrays.fill(active,true);
        Set<Long> ids=new HashSet<>();double[] rates=new double[count];
        for(int i=0;i<count;i++){
            var demand=demands.get(i);if(!ids.add(demand.id))throw new IllegalArgumentException("転送ID重複");
            paths[i]=new int[demand.resources.size()];for(int j=0;j<paths[i].length;j++){
                Integer index=indices.get(demand.resources.get(j));if(index==null)throw new IllegalArgumentException("転送資源がありません");paths[i][j]=index;
            }
        }
        double[] used=new double[resourceCount],weights=new double[resourceCount];int unfinished=count,rounds=0;
        while(unfinished>0){
            rounds++;Arrays.fill(used,0);Arrays.fill(weights,0);
            double step=Double.POSITIVE_INFINITY;int stopFlow=-1,stopResource=-1;
            for(int i=0;i<count;i++){
                var demand=demands.get(i);
                if(active[i]){double limit=Math.max(0,demand.ceilingBps-rates[i])/demand.weight;if(limit<step){step=limit;stopFlow=i;stopResource=-1;}}
                for(int r:paths[i]){used[r]+=rates[i];if(active[i])weights[r]+=demand.weight;}
            }
            for(int r=0;r<resourceCount;r++)if(weights[r]>0){double limit=Math.max(0,capacity[r]-used[r])/weights[r];if(limit<step){step=limit;stopFlow=-1;stopResource=r;}}
            if(!Double.isFinite(step)||stopFlow<0&&stopResource<0)throw new IllegalStateException("帯域配分の停止条件がありません");
            for(int i=0;i<count;i++)if(active[i])rates[i]=Math.min(demands.get(i).ceilingBps,rates[i]+step*demands.get(i).weight);
            for(int i=0;i<count;i++)if(active[i]){
                boolean stop=i==stopFlow;if(stopResource>=0)for(int r:paths[i])if(r==stopResource){stop=true;break;}
                if(stop){active[i]=false;unfinished--;}
            }
        }
        // 丸め誤差による微小な過剰割当も保守的に抑える。各リンクの容量を越えない。
        Arrays.fill(used,0);for(int i=0;i<count;i++)for(int r:paths[i])used[r]+=rates[i];
        double scale=1;for(int r=0;r<resourceCount;r++)if(used[r]>capacity[r])scale=Math.min(scale,capacity[r]/used[r]);
        Map<Long,Double> result=new LinkedHashMap<>();for(int i=0;i<count;i++)result.put(demands.get(i).id,rates[i]*scale);
        return new Allocation(result,rounds);
    }
}
