import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 固定seedの編集列を、索引を使わない独立した全走査オラクルと照合する。 */
public final class CableTopologyFuzzTest {
    private static int checks;
    static String endpoint(CableTopology.Node n,int port){return n.id()+":"+port;}
    static Map<String,Integer> oracle(CableTopologyTest.World world){
        var seen=new HashSet<CableTopologyTest.Pos>();var result=new TreeMap<String,Integer>();int[] opposite={2,3,0,1,5,4};
        for(var seed:world.cables.keySet()){
            if(!seen.add(seed))continue;var grade=world.cables.get(seed);var color=world.colors.getOrDefault(seed,-1);var queue=new ArrayDeque<CableTopologyTest.Pos>();queue.add(seed);
            Set<String> endpoints=new TreeSet<>();int count=0;boolean branch=false,unloaded=false;
            while(!queue.isEmpty()){
                var pos=queue.removeFirst();count++;int degree=0;if(!world.loaded(pos)){unloaded=true;continue;}
                for(int d=0;d<6;d++){var next=world.adjacent(pos,d);if(!world.loaded(next)){unloaded=true;continue;}
                    if(world.cables.get(next)==grade&&world.colors.getOrDefault(next,-1)==color){degree++;if(seen.add(next))queue.add(next);}
                    else if(world.devices.containsKey(next)){degree++;endpoints.add(endpoint(world.devices.get(next),opposite[d]));}}
                if(degree>2)branch=true;
            }
            if(endpoints.size()==2&&!branch&&!unloaded&&count<=grade.maxLength()){
                var e=endpoints.iterator();String a=e.next(),b=e.next();if(!a.substring(0,a.lastIndexOf(':')).equals(b.substring(0,b.lastIndexOf(':'))))result.put(a+"-"+b,count);
            }
        }
        return result;
    }
    public static void main(String[] args){
        var world=new CableTopologyTest.World();world.line(0,14,0,"row0");world.line(0,14,8,"row8");world.line(0,14,16,"row16");world.settle();
        var rng=new Random(8219047L);
        for(int round=0;round<300;round++){
            for(int op=0;op<5;op++){int x=1+rng.nextInt(13),z=rng.nextInt(17);CableGrade grade=switch(rng.nextInt(4)){case 0->null;case 1->CableGrade.CAT5E;default->CableGrade.CAT6A;};world.cable(x,z,grade,rng.nextInt(4)==0?-1:rng.nextInt(16));world.topology.step(1+rng.nextInt(3));}
            world.settle();var actual=new TreeMap<String,Integer>();for(var link:world.links.values())actual.put(link.id(),link.length());
            if(!actual.equals(oracle(world)))throw new AssertionError("round "+round+": "+actual+" expected "+oracle(world));checks++;
            if(world.topology.trackedBlocks()!=world.cables.size())throw new AssertionError("stale block ownership in round "+round);checks++;
        }
        System.out.println("CableTopologyFuzzTest: "+checks+" oracle/index checks for 1500 deterministic edits passed");
    }
}
