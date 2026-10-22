import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 削除キーの再利用、誤入力、ACLの変更が実通信へ反映されることを検査する。 */
public final class AclCommandTest {
    private static int checks;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private static boolean run(NetworkSimulator sim,String command){return NetworkServiceCommands.run(sim,"r",command).changed();}
    private static NetworkSimulator fixture(){
        var sim=new NetworkSimulator();
        var a=new NetworkProfile(DeviceKind.PC);a.address="192.168.10.10";a.gateway="192.168.10.1";
        var r=new NetworkProfile(DeviceKind.ROUTER);r.dhcp=false;r.ports[0].address="192.168.10.1";r.ports[1].address="203.0.113.1";
        var s=new NetworkProfile(DeviceKind.SERVER);s.address="203.0.113.20";
        sim.put("a",a);sim.put("r",r);sim.put("s",s);
        sim.link(new NetworkSimulator.Link("ar","a",0,"r",0,CableGrade.CAT5E,2,false));
        sim.link(new NetworkSimulator.Link("rs","r",1,"s",0,CableGrade.CAT5E,2,false));
        check(run(sim,"nat port 0 inside")&&run(sim,"nat port 1 outside")&&run(sim,"nat on"),"NAT fixture ready");
        return sim;
    }
    public static void main(String[] args){
        var sim=fixture();
        check(run(sim,"acl add 0 in 10 deny icmp 192.168.10.42/24 any"),"deny rule added");
        check(run(sim,"acl add 0 in 20 permit ip any any"),"other traffic permitted");
        check(!sim.ping("a","203.0.113.20").success(),"ACL blocks actual ping");
        check(sim.request("a","203.0.113.20","tcp",80).success(),"other protocol still succeeds");
        check(run(sim,"acl delete-match 0 in 10 deny icmp 192.168.10.99/24 0.0.0.0/0 any"),"content-matched deletion supported");
        check(sim.nodes().get("r").services.policy.rules().size()==1,"other sequence preserved");
        check(sim.ping("a","203.0.113.20").success(),"deletion restores ping");

        // 別編集者が同じキーを再利用しても、古い確認内容から削除できない。
        check(run(sim,"acl add 0 in 10 permit tcp 192.168.10.0/24 203.0.113.20 80"),"replacement rule added");
        check(sim.startTransfer("a","203.0.113.20",1_000_000_000L).success(),"live NAT transfer");
        var flow=sim.flows().getFirst();var sessions=sim.nat().sessions("r");
        check(!sessions.isEmpty(),"live NAT state exists");
        String before=sim.nodes().get("r").encode();
        for(String bad:List.of(
                "acl delete-match 0 in 10 deny icmp 192.168.10.0/24 any any",
                "acl delete-match 0 in 10 deny tcp 192.168.10.0/24 203.0.113.20 80",
                "acl delete-match 0 in 10 permit udp 192.168.10.0/24 203.0.113.20 80",
                "acl delete-match 0 in 10 permit tcp 192.168.11.0/24 203.0.113.20 80",
                "acl delete-match 0 in 10 permit tcp 192.168.10.0/24 203.0.113.21 80",
                "acl delete-match 0 in 10 permit tcp 192.168.10.0/24 203.0.113.20 81",
                "acl delete-match 1 in 10 permit tcp 192.168.10.0/24 203.0.113.20 80",
                "acl delete-match 0 out 10 permit tcp 192.168.10.0/24 203.0.113.20 80",
                "acl delete-match 0 in 11 permit tcp 192.168.10.0/24 203.0.113.20 80",
                "acl delete-match 0 in 10", "acl delete-match 6 in 10 permit ip any any",
                "acl delete-match 0 in 10 permit icmp any any 80",
                "acl delete-match 0 in 10 permit tcp any any 65536",
                "acl delete-match 0 in 10 permit tcp any any 80 extra",
                "acl delete-match 0 in 10 permit ip any any\nacl clear 0 in",
                "acl add 0 in 10 permit tcp 192.168.10.0/24 203.0.113.20 80",
                "acl clear 5 out")){
            check(!run(sim,bad),"reject/no-op: "+bad);
            check(sim.nodes().get("r").encode().equals(before),"rejection preserves all config");
            check(sim.flows().contains(flow),"rejection preserves live transfer");
            check(sim.nat().sessions("r").equals(sessions),"rejection preserves NAT mappings");
        }
        check(run(sim,"acl delete-match 0 in 10 permit tcp 192.168.10.5/24 203.0.113.20/32 80"),"matching replacement can be deleted");
        check(sim.flows().isEmpty(),"real rule change reestablishes affected transfers");
        check(run(sim,"acl delete 0 in 20"),"legacy key deletion remains available");
        check(sim.nodes().get("r").services.policy.rules().isEmpty(),"last rule removed");
        check(sim.ping("a","203.0.113.20").success(),"no rules means all traffic permitted");
        check(run(sim,"acl add 0 out 1 deny icmp any any"),"return ACL added");
        check(!sim.ping("a","203.0.113.20").success(),"return direction still checked");
        var restored=NetworkProfile.decode(DeviceKind.ROUTER,sim.nodes().get("r").encode());
        check(restored.services.policy.rules().equals(sim.nodes().get("r").services.policy.rules()),"ACL survives existing codec");
        check(!NetworkServiceCommands.run(sim,"a","acl delete-match 0 in 1 deny ip any any").changed(),"PC cannot edit ACL");
        var full=new NetworkProfile(DeviceKind.L3_SWITCH);sim.put("full",full);
        for(int n=1;n<=64;n++)check(NetworkServiceCommands.run(sim,"full","acl add 0 in "+n+" permit ip any any").changed(),"64-rule capacity");
        check(!NetworkServiceCommands.run(sim,"full","acl add 0 in 65 deny ip any any").changed(),"65th rule rejected");
        check(NetworkServiceCommands.run(sim,"full","acl delete-match 0 in 64 permit ip any any").changed(),"full table can delete exactly");
        check(sim.nodes().get("full").services.policy.rules().size()==63,"full-table removal isolated");
        System.out.println("AclCommandTest: "+checks+" checks passed");
    }
}
