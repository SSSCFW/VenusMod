import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** GUI入力は実CLIと同じ設定モデルへ原子的に反映される。 */
public final class NetworkFormTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static NetworkForm.Values values(NetworkForm.View view,String name,String ip,String prefix,String gw,String dns,String vlan,String speed){
        var v=view.values();return new NetworkForm.Values(v.scope(),name,ip,prefix,gw,dns,v.enabled(),v.trunk(),v.optical(),vlan,speed);
    }
    private static NetworkForm.Outcome apply(NetworkSimulator s,String id,NetworkForm.View view,NetworkForm.Values values){return NetworkForm.apply(s,id,NetworkForm.encode(view),NetworkForm.encode(values));}
    public static void main(String[] args)throws Exception{
        var s=new NetworkSimulator();var a=new NetworkProfile(DeviceKind.PC);a.address="10.0.1.10";s.put("a",a);
        var v=NetworkForm.capture(a,-1);check(NetworkForm.decodeView(NetworkForm.encode(v)).equals(v),"snapshot round trip");
        var updated=values(v,"基地 PC A","10.0.2.10","24","10.0.2.1","10.0.2.53",v.values().vlan(),v.values().speed());
        var r=apply(s,"a",v,updated);check(r.accepted()&&r.changed(),"basic fields apply together");a=s.nodes().get("a");
        check(a.name.equals("基地 PC A")&&a.address.equals("10.0.2.10")&&a.gateway.equals("10.0.2.1")&&a.dns.equals("10.0.2.53"),"all fields written");
        String state=a.encode();r=apply(s,"a",v,updated);check(!r.accepted()&&!r.changed()&&state.equals(a.encode()),"stale editor cannot overwrite");
        v=NetworkForm.capture(a,-1);r=apply(s,"a",v,v.values());check(r.accepted()&&!r.changed(),"no-op is not a mutation");
        for(String bad:List.of("999.1.1.1","localhost","http://10.0.0.1","10.0.0.1\n")){
            r=apply(s,"a",v,values(v,"new",bad,"24","10.0.2.1","10.0.2.53",v.values().vlan(),v.values().speed()));
            check(!r.accepted()&&s.nodes().get("a").encode().equals(state),"reject bad IP without partial name change: "+bad);
        }
        var port=NetworkForm.capture(a,0);var pv=port.values();
        var fast=new NetworkForm.Values(0,"",pv.address(),pv.prefix(),"","",true,false,false,"23","10000000000");
        r=apply(s,"a",port,fast);check(!r.accepted(),"cannot invent 10G hardware");
        a.ports[0].moduleTier=1;r=apply(s,"a",port,fast);check(!r.accepted(),"hardware replacement invalidates snapshot");
        port=NetworkForm.capture(a,0);r=apply(s,"a",port,fast);check(r.accepted()&&r.changed(),"10G module enables per-port speed");
        a=s.nodes().get("a");check(a.ports[0].vlan==23&&a.ports[0].speedLimitBps==10_000_000_000L&&a.ports[1].vlan==1,"port independent");
        var before=NetworkForm.capture(a,1);a.ports[0].vlan=99;r=apply(s,"a",before,new NetworkForm.Values(1,"","","24","","",true,true,false,"24","100000000"));
        check(r.accepted()&&s.nodes().get("a").ports[0].vlan==99,"other port edit preserved");
        var router=new NetworkProfile(DeviceKind.ROUTER);s.put("r",router);var rv=NetworkForm.capture(router,-1);
        r=apply(s,"r",rv,values(rv,"router","10.5.0.1","24","","",rv.values().vlan(),rv.values().speed()));
        check(!r.accepted(),"L3 interface IP must be edited per port");
        var rp=NetworkForm.capture(router,1);var rpv=rp.values();r=apply(s,"r",rp,new NetworkForm.Values(1,"","10.5.0.1","24","","",rpv.enabled(),rpv.trunk(),rpv.optical(),"1","0"));
        check(r.accepted()&&s.nodes().get("r").ports[1].address.equals("10.5.0.1"),"L3 port IP supports multi-field patch");
        r=NetworkForm.apply(s,"r","!","!");check(!r.accepted(),"malformed encoding rejected");
        r=NetworkForm.apply(s,"r","x".repeat(4000),"!");check(!r.accepted(),"oversized encoding rejected");
        r=NetworkForm.apply(s,"absent",NetworkForm.encode(rv),NetworkForm.encode(rv.values()));check(!r.accepted(),"unregistered target rejected");
        System.out.println("NetworkFormTest: "+checks+" checks passed");
    }
}
