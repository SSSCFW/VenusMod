import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class StpBoundaryTest {
    static int checks;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        // v5の変更前クラスから生成した固定fixtureを挿入。現在形式を切って旧版に見せない。
        String legacy="AAAABQAKbGVnYWN5LXN0cAAAAAAAGAAAAAABAAAAAAANMTkyLjE2OC4xLjI1NAAAAGQBAAAAAAARAAsxOTIuMTY4LjEuMQAAABgAAAAAAAAAAAEAAAAAAAEACzE5Mi4xNjguMi4xAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAQAAAAAAAQAAAAAAGAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABQAAABQAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAANPRkYAAAAAAAEABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUABE5PTkUAAAAAAQH/////AAAAGwAAABQ=";
        var old=NetworkProfile.decode(DeviceKind.L3_SWITCH,legacy);
        check(old.name.equals("legacy-stp")&&!old.stp&&old.failed&&old.ports[0].vlan==17,"v5 data survives migration");
        check(!old.spanning.classic&&old.spanning.priority==32768&&old.vrrp.group==27,"migration preserves simple and unrelated vrrp");
        var s=new NetworkSimulator();s.put("a",new NetworkProfile(DeviceKind.L2_SWITCH));
        for(String text:List.of("stp mode classic","stp priority 4096","stp vlan 20 priority 8192","stp port 0 cost 123","stp port 2 priority 32","stp port 1 edge on","stp port 1 guard on","stp timers 1 6 4")){
            var r=NetworkServiceCommands.run(s,"a",text);check(r.changed(),"setting "+text);
        }
        String packed=s.nodes().get("a").encode();var restored=NetworkProfile.decode(DeviceKind.L2_SWITCH,packed);
        check(packed.equals(restored.encode()),"v6 exact round-trip");check(restored.spanning.classic&&restored.spanning.costs[0]==123&&restored.spanning.portPriorities[2]==32&&restored.spanning.edge[1]&&restored.spanning.guard[1],"port settings persisted");
        check(restored.spanning.vlanPriorities.get(20)==8192&&restored.spanning.helloTicks==20&&restored.spanning.forwardDelayTicks==80,"VLAN priority and timers persisted");
        for(String text:List.of("stp priority 3","stp priority 65536","stp port 6 cost 10","stp port 0 cost -1","stp port 0 priority 15","stp timers 1 4 4","stp timers 1 40 4","stp timers 2147483647 20 15","stp mode rstp","stp port 0 guard yes","stp vlan 5000 priority 4096","stp vlan 10 priority 1","show spanning-tree 10 extra","stp status 0","stp port 0 cost 0","stp vlan -1 priority auto")){
            var r=NetworkServiceCommands.run(s,"a",text);check(r.handled()&&!r.changed(),"invalid rejected: "+text);check(s.nodes().get("a").encode().equals(packed),"invalid input didn't damage config");
        }
        byte[] bytes=Base64.getDecoder().decode(packed);
        for(int n:List.of(0,3,20,bytes.length-1)){
            boolean reject=false;try{NetworkProfile.decode(DeviceKind.L2_SWITCH,Base64.getEncoder().encodeToString(Arrays.copyOf(bytes,n)));}catch(IllegalArgumentException e){reject=true;}check(reject,"truncated persistent input rejected");
        }
        var root=new StpEngine.BridgeId(0,"02:00:00:00:00:01");var k=new StpEngine.Key("a",1);var config=new StpEngine.Configuration(root,20,120,80,List.of(
            new StpEngine.PortConfig(true,10,128,false,false,"x"),new StpEngine.PortConfig(false,10,128,false,false,""),new StpEngine.PortConfig(false,10,128,false,false,""),new StpEngine.PortConfig(false,10,128,false,false,""),new StpEngine.PortConfig(false,10,128,false,false,""),new StpEngine.PortConfig(false,10,128,false,false,"")));
        var e=new StpEngine(new StpEngine.Transport(){public StpEngine.Peer peer(StpEngine.Key key,int p){return null;}public void changed(StpEngine.Key key){}public void log(StpEngine.Key key,String text){}});
        e.synchronize(Map.of(k,config));
        for(var bad:List.of(new StpEngine.Bpdu(root,-1,root,1,20,120,80,0),new StpEngine.Bpdu(root,1,root,0,20,120,80,0),new StpEngine.Bpdu(root,1,root,1,0,120,80,0),new StpEngine.Bpdu(root,1,root,1,20,120,80,120),new StpEngine.Bpdu(root,1,root,1,20,120,80,-1))){e.receive(k,0,bad);check(e.status(k).ports().get(0).received()==0,"invalid BPDU is not accepted");}
        e.receive(k,-1,null);e.receive(k,6,null);check(e.scheduledEvents()<=13,"invalid requests don't expand deadlines");
        var other=new StpEngine.BridgeId(0,"00:00:00:00:00:01");var bpdu=new StpEngine.Bpdu(other,0,other,1,20,120,80,0);
        for(int n=0;n<1000;n++)e.receive(k,0,bpdu);
        check(e.scheduledEvents()<=13&&e.queuedDeliveries()==0,"one receive expiry after 1000 refreshes");
        check(e.status(k).ports().get(0).received()==1000,"refresh test really received all BPDUs");
        System.out.println("StpBoundaryTest: "+checks+" checks passed (1000 BPDU refreshes for bounded timer test)");
    }
}
