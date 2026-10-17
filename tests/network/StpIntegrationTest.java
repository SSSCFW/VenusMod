import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class StpIntegrationTest {
    static int checks;
    static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    static void ticks(NetworkSimulator s,int n){for(int i=0;i<n;i++)s.advance(1);}
    static void command(NetworkSimulator s,String id,String text){var reply=NetworkServiceCommands.run(s,id,text);check(reply.handled()&&reply.changed(),"command "+text+" "+reply.lines());}
    static NetworkProfile host(String ip){var p=new NetworkProfile(DeviceKind.PC);p.address=ip;return p;}
    static void wire(NetworkSimulator s,String name,String a,int ap,String b,int bp){s.link(new NetworkSimulator.Link(name,a,ap,b,bp,CableGrade.CAT5E,2,false));}
    static void classic(NetworkSimulator s,String id){command(s,id,"stp mode classic");command(s,id,"stp timers 1 6 4");}
    static StpEngine.Status status(NetworkSimulator s,String id){return s.stp().status(new StpEngine.Key(id,1));}
    public static void main(String[] args){
        var s=new NetworkSimulator();s.put("a",new NetworkProfile(DeviceKind.L2_SWITCH));s.put("b",new NetworkProfile(DeviceKind.L2_SWITCH));s.put("c",new NetworkProfile(DeviceKind.L2_SWITCH));
        s.put("x",host("10.0.0.10"));s.put("y",host("10.0.0.20"));
        wire(s,"ab","a",0,"b",0);wire(s,"ac","a",1,"c",0);wire(s,"bc","b",1,"c",1);wire(s,"ax","a",2,"x",0);wire(s,"cy","c",2,"y",0);
        check(s.ping("x","10.0.0.20").success(),"existing simple mode still immediate");
        for(String id:List.of("a","b","c"))classic(s,id);
        command(s,"a","stp priority 0");command(s,"a","stp port 0 cost 10");command(s,"b","stp port 0 cost 10");command(s,"b","stp port 1 cost 10");command(s,"c","stp port 1 cost 10");command(s,"c","stp port 0 cost 100");
        check(!s.ping("x","10.0.0.20").success(),"classic doesn't instant forward");ticks(s,250);
        check(s.ping("x","10.0.0.20").success(),"BPDU tree permits communication");
        check(status(s,"c").rootPort()==1,"preferred path through b");check(status(s,"c").ports().get(0).state()==StpEngine.State.BLOCKING,"physical alternate blocked");
        check(s.blockedLinks().isEmpty(),"classic isn't secretly chosen by the legacy global forest");
        var before=status(s,"b");for(int n=0;n<10;n++)NetworkServiceCommands.run(s,"b","stp status");check(status(s,"b").equals(before),"GUI status has no timer side effect");
        check(s.startTransfer("x","10.0.0.20",1_000_000_000).success(),"flow starts on converged tree");
        s.nodes().get("b").failed=true;s.invalidate();ticks(s,1);check(!s.ping("x","10.0.0.20").success(),"failed bridge doesn't instant unblock alternate");ticks(s,320);
        check(s.ping("x","10.0.0.20").success()&&status(s,"c").rootPort()==0,"backup after BPDU expiry and delay");check(s.flows().isEmpty(),"old flow not moved invisibly");
        s.nodes().get("b").failed=false;s.invalidate();ticks(s,350);check(status(s,"c").rootPort()==1&&s.ping("x","10.0.0.20").success(),"repair restores cheaper tree");
        command(s,"b","stp mode simple");ticks(s,1);check(!s.stpIssue("a",0).isBlank(),"mixed mode explicit diagnostic");ticks(s,400);check(status(s,"a").ports().get(0).state()==StpEngine.State.DISABLED,"mixed classic/simple link fails closed");
        classic(s,"b");ticks(s,350);check(s.ping("x","10.0.0.20").success(),"correcting mixed mode resumes network");
        // 学習中はMACだけを記録し、宛先に届いたことにはしない。
        var one=new NetworkSimulator();one.put("sw",new NetworkProfile(DeviceKind.L2_SWITCH));one.put("x",host("10.2.0.10"));one.put("y",host("10.2.0.20"));wire(one,"x","sw",0,"x",0);wire(one,"y","sw",1,"y",0);classic(one,"sw");ticks(one,50);
        check(!one.ping("x","10.2.0.20").success()&&one.macTable("sw").isEmpty(),"Listening doesn't learn MAC");ticks(one,40);
        check(!one.ping("x","10.2.0.20").success()&&!one.macTable("sw").isEmpty(),"Learning records source but doesn't forward");ticks(one,100);check(one.ping("x","10.2.0.20").success(),"Forwarding permits traffic");
        // BPDUはIP ACLを通さず、遮断したL3SWポートでも選出情報を受信する。
        var acl=new NetworkSimulator();acl.put("root",new NetworkProfile(DeviceKind.L2_SWITCH));acl.put("sw",new NetworkProfile(DeviceKind.L3_SWITCH));wire(acl,"r","root",0,"sw",0);classic(acl,"root");classic(acl,"sw");command(acl,"root","stp priority 0");command(acl,"sw","acl add 0 in 1 deny ip any any");ticks(acl,250);
        check(acl.stp().status(new StpEngine.Key("sw",1)).root().priority()==0,"BPDU not filtered as IP packet");
        // BPDU guardの誤配線停止はコマンド閲覧では解除しない。
        command(acl,"sw","stp port 0 edge on");command(acl,"sw","stp port 0 guard on");ticks(acl,40);check(status(acl,"sw").ports().get(0).state()==StpEngine.State.ERR_DISABLED,"edge-to-switch guard");
        var read=NetworkServiceCommands.run(acl,"sw","show spanning-tree");check(read.handled()&&!read.changed(),"show spanning-tree dispatch");ticks(acl,200);check(status(acl,"sw").ports().get(0).state()==StpEngine.State.ERR_DISABLED,"guard remains latched");
        command(acl,"sw","stp port 0 guard off");ticks(acl,250);check(acl.stp().status(new StpEngine.Key("sw",1)).ports().get(0).state()==StpEngine.State.FORWARDING,"correct guard setting returns to timed STP");
        System.out.println("StpIntegrationTest: "+checks+" checks passed");
    }
}
