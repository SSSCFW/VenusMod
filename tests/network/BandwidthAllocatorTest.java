import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class BandwidthAllocatorTest {
    private static int checks;
    private static void ok(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void near(double a,double b,String message){ok(Math.abs(a-b)<1e-6*Math.max(1,b),message+": "+a+" != "+b);}
    private static BandwidthAllocator.Demand d(long id,long cap,int weight,String... keys){return new BandwidthAllocator.Demand(id,cap,weight,List.of(keys));}
    private static void bad(Runnable r){try{r.run();throw new AssertionError("must reject input");}catch(IllegalArgumentException e){checks++;}}
    public static void main(String[] args){
        var pair=List.of(d(1,1000,2,"shared"),d(2,1000,2,"shared"));
        var a=BandwidthAllocator.allocate(pair,Map.of("shared",1000L));near(a.rates().get(1L),500,"equal sharing");near(a.rates().get(2L),500,"equal sharing2");
        a=BandwidthAllocator.allocate(List.of(d(1,100,2,"shared"),d(2,1000,2,"shared")),Map.of("shared",1000L));
        near(a.rates().get(1L),100,"slow demand stops growing");near(a.rates().get(2L),900,"unused demand is reassigned");
        a=BandwidthAllocator.allocate(List.of(d(1,1000,1,"shared"),d(2,1000,4,"shared")),Map.of("shared",1000L));
        near(a.rates().get(1L),200,"low not starved");near(a.rates().get(2L),800,"weighted high");
        a=BandwidthAllocator.allocate(List.of(d(1,1000,2,"left"),d(2,1000,2,"left","right"),d(3,1000,2,"right")),Map.of("left",100L,"right",1000L));
        near(a.rates().get(1L),50,"left saturation");near(a.rates().get(2L),50,"shared slow path");near(a.rates().get(3L),950,"second resource reuses slack");
        a=BandwidthAllocator.allocate(List.of(d(1,1000,2,"link","link")),Map.of("link",1000L));near(a.rates().get(1L),1000,"deduplicate resource");
        a=BandwidthAllocator.allocate(List.of(d(1,1000,2,"dead"),d(2,1000,2,"live")),Map.of("dead",0L,"live",1000L));near(a.rates().get(1L),0,"zero capacity");near(a.rates().get(2L),1000,"unrelated link survives");
        bad(()->BandwidthAllocator.allocate(pair,Map.of()));bad(()->BandwidthAllocator.allocate(List.of(pair.get(0),pair.get(0)),Map.of("shared",1000L)));
        bad(()->d(1,-1,1,"x"));bad(()->d(1,1,0,"x"));bad(()->BandwidthAllocator.allocate(pair,Map.of("shared",-1L)));
        bad(()->BandwidthAllocator.allocate(java.util.stream.LongStream.rangeClosed(1,129).mapToObj(n->d(n,10,1,"x")).toList(),Map.of("x",10L)));
        var random=new Random(71140);for(int sample=0;sample<200;sample++){
            int count=1+random.nextInt(25),resources=1+random.nextInt(8);Map<String,Long> caps=new LinkedHashMap<>();
            for(int j=0;j<resources;j++)caps.put("r"+j,1L+random.nextInt(10000));List<BandwidthAllocator.Demand> demands=new ArrayList<>();
            for(int j=0;j<count;j++){List<String> use=new ArrayList<>();for(int k=0;k<resources;k++)if(random.nextBoolean())use.add("r"+k);demands.add(new BandwidthAllocator.Demand(j+1,1+random.nextInt(5000),1<<random.nextInt(3),use));}
            var answer=BandwidthAllocator.allocate(demands,caps);if(answer.rounds()>count)throw new AssertionError("unbounded iterations");
            for(var demand:demands){double rate=answer.rates().get(demand.id());if(!Double.isFinite(rate)||rate<0||rate>demand.ceilingBps()+1e-5)throw new AssertionError("cap violation");}
            for(var resource:caps.entrySet()){double used=demands.stream().filter(demand->demand.resources().contains(resource.getKey())).mapToDouble(demand->answer.rates().get(demand.id())).sum();if(used>resource.getValue()+1e-5)throw new AssertionError("shared oversubscription");}
            // 残っている転送は自身の上限、または飽和した資源に制限される。
            for(var demand:demands)if(answer.rates().get(demand.id())<demand.ceilingBps()-1e-4){boolean bottleneck=false;for(String key:demand.resources()){double used=demands.stream().filter(v->v.resources().contains(key)).mapToDouble(v->answer.rates().get(v.id())).sum();if(Math.abs(used-caps.get(key))<1e-4)bottleneck=true;}if(!bottleneck)throw new AssertionError("idle usable bandwidth");}
            Collections.reverse(demands);var reversed=BandwidthAllocator.allocate(demands,caps);for(long id:answer.rates().keySet())if(Math.abs(answer.rates().get(id)-reversed.rates().get(id))>1e-5)throw new AssertionError("insertion order changes fairness");
        }
        ok(true,"200 deterministic randomized resource graphs preserve limits, saturation and order invariance");
        a=BandwidthAllocator.allocate(List.of(d(1,400_000_000_000L,2,"fiber"),d(2,400_000_000_000L,2,"fiber")),Map.of("fiber",400_000_000_000L));near(a.rates().get(1L),200_000_000_000.0,"400G no integer overflow");
        System.out.println("BandwidthAllocatorTest: "+checks+" checks passed (including 200 randomized graphs)");
    }
}
