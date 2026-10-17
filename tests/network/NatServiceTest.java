import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.lang.reflect.*;

/** 公開ポートと実サービスのポートを混同しないことを、ゲーム内通信APIで確認する。 */
public final class NatServiceTest {
    private static int checks;
    private static void ok(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static Object endpoint(String text,int port){try{return Class.forName("dev.ssscfw.venusmod.infrastructure.network.core.ServiceAddress").getMethod("parse",String.class,int.class).invoke(null,text,port);}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError("サービス宛先パーサーが未実装",e);}}
    private static NetworkSimulator.Result service(NetworkSimulator s,String from,String to,int publicPort,int servicePort){try{return (NetworkSimulator.Result)s.getClass().getMethod("requestService",String.class,String.class,String.class,int.class,int.class).invoke(s,from,to,"tcp",publicPort,servicePort);}catch(ReflectiveOperationException e){throw new AssertionError("実サービス宛先ポートの検査が未実装",e);}}
    private static void config(NetworkSimulator s,String id,String command){var r=NetworkServiceCommands.run(s,id,command);ok(r.changed(),command+" -> "+r.lines());}
    public static void main(String[] args)throws Exception{
        var address=endpoint("198.51.100.1:8080",80);
        ok(address.getClass().getMethod("address").invoke(address).equals("198.51.100.1"),"IPv4 parsed");
        ok((int)address.getClass().getMethod("port").invoke(address)==8080,"public port parsed");
        address=endpoint("198.51.100.1",80);ok((int)address.getClass().getMethod("port").invoke(address)==80,"default service port");
        for(String bad:List.of("http://example.com","localhost","198.51.100.1:0","198.51.100.1:65536","198.51.100.1:+80","198.51.100.1:80:1","198.51.100.1:\n80")){
            boolean rejected=false;try{endpoint(bad,80);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"bad endpoint: "+bad);
        }
        var s=new NetworkSimulator();var r=new NetworkProfile(DeviceKind.ROUTER);r.dhcp=false;r.ports[0].address="10.20.0.1";r.ports[1].address="198.51.100.1";
        var inside=new NetworkProfile(DeviceKind.SERVER);inside.address="10.20.0.10";inside.gateway="10.20.0.1";inside.web=true;
        var outside=new NetworkProfile(DeviceKind.PC);outside.address="198.51.100.20";
        s.put("r",r);s.put("inside",inside);s.put("outside",outside);
        s.link(new NetworkSimulator.Link("in","inside",0,"r",0,CableGrade.CAT5E,2,false));s.link(new NetworkSimulator.Link("out","r",1,"outside",0,CableGrade.CAT5E,2,false));
        config(s,"r","nat port 0 inside");config(s,"r","nat port 1 outside");config(s,"r","nat on");
        config(s,"r","nat forward add 1 tcp 8080 10.20.0.10 80");config(s,"r","nat forward add 1 tcp 8081 10.20.0.10 81");
        var result=service(s,"outside","198.51.100.1",8081,80);ok(!result.success(),"wrong private service port must not open Web");ok(s.nat().sessions("r").isEmpty(),"service mismatch creates no mapping");
        result=service(s,"outside","198.51.100.1",8080,80);ok(result.success()&&result.destination().equals("inside")&&result.servicePort()==80,"published Web resolves to private port80");
        config(s,"r","nat forward add 1 tcp 5001 10.20.0.10 5000");
        ok(s.startTransfer("outside","198.51.100.1:5001",1_000_000_000_000L).success(),"virtual transfer accepts public endpoint");
        for(int tick=0;tick<2501;tick++)s.advance(1);
        ok(!s.flows().isEmpty()&&s.nat().sessions("r").size()==1,"ongoing transfer maintains mapping beyond idle timeout");
        ok(s.nat().expiryCount()==1,"active flow has one deadline, not one event per tick");
        config(s,"r","nat clear");ok(s.flows().isEmpty()&&s.nat().size()==0,"clear removes live flow and NAT state");
        config(s,"r","nat overload off");ok(!s.request("inside","198.51.100.20","tcp",80).success(),"forward-only mode blocks new dynamic translation");
        ok(service(s,"outside","198.51.100.1",8080,80).success(),"forward-only mode permits static rule replies");
        System.out.println("NatServiceTest: "+checks+" checks passed");
    }
}
