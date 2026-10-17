import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import static dev.ssscfw.venusmod.infrastructure.network.core.NetworkPolicy.*;
import static dev.ssscfw.venusmod.infrastructure.network.core.NatSettings.Role.*;

/** 変換タプル、第三者応答、上限、期限キュー、要求の原子性の検査。 */
public final class NatTableTest {
    private static int checks;
    private static void ok(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static void reject(Runnable action,String reason){boolean failed=false;try{action.run();}catch(IllegalArgumentException e){failed=true;}ok(failed,reason);}
    private static NetworkProfile router(){var p=new NetworkProfile(DeviceKind.ROUTER);p.ports[0].address="192.168.10.1";p.ports[1].address="203.0.113.1";p.nat.role(0,INSIDE);p.nat.role(1,OUTSIDE);p.nat.enabled=true;return p;}
    private static Packet original(int sourcePort){return Packet.of("tcp","192.168.10.10","203.0.113.20",sourcePort,80);}
    private static Packet outgoing(NatTable.Transaction t,String id,NetworkProfile p,Packet packet){return t.egress(id,p,0,1,t.ingress(id,p,0,packet));}
    public static void main(String[] args){
        var table=new NatTable();var p=router();Packet input=original(40000),wire;
        try(var t=table.begin()){wire=outgoing(t,"r",p,input);ok(wire.source()==Ipv4.parse("203.0.113.1")&&wire.sourcePort()>=NatTable.FIRST_PORT,"source translation");}
        ok(table.size()==0&&table.expiryCount()==0,"rollback closes without binding");
        try(var t=table.begin()){
            wire=outgoing(t,"r",p,input);var reply=t.ingress("r",p,1,wire.reverse());
            ok(reply.packet().equals(input.reverse()),"restore whole tuple");ok(t.egress("r",p,1,0,reply).equals(input.reverse()),"reply through correct inside interface");t.commit();
        }
        ok(table.size()==1&&table.expiryCount()==1,"one session one expiry event");
        Packet established=table.sessions("r").getFirst().wire();
        try(var t=table.begin()){
            var forged=new Packet(Protocol.TCP,Ipv4.parse("203.0.113.99"),established.source(),80,established.sourcePort());
            reject(()->t.ingress("r",p,1,forged),"different remote IP cannot use binding");
        }
        try(var t=table.begin()){
            var forged=new Packet(Protocol.TCP,established.destination(),established.source(),81,established.sourcePort());
            reject(()->t.ingress("r",p,1,forged),"different remote port cannot use binding");
        }
        try(var t=table.begin()){
            var reply=t.ingress("r",p,1,established.reverse());reject(()->t.egress("r",p,1,2,reply),"wrong internal interface is not accepted");
        }
        long expiry=table.sessions("r").getFirst().expiresAt();table.advance(30,Set.of());
        try(var t=table.begin()){outgoing(t,"r",p,input);}
        ok(table.sessions("r").getFirst().expiresAt()==expiry,"failed/rejected request never refreshes existing binding");
        for(int i=0;i<200;i++){table.advance(1,Set.of());try(var t=table.begin()){outgoing(t,"r",p,input);t.commit();}}
        ok(table.size()==1&&table.expiryCount()==1,"repeated use does not grow deadline queue");
        try(var stale=table.begin()){outgoing(stale,"r",p,original(40001));table.advance(1,Set.of());reject(stale::commit,"stale transaction cannot commit");}
        ok(table.size()==1,"stale transaction kept old state");
        for(int port=40001;port<40032;port++){try(var t=table.begin()){outgoing(t,"r",p,original(port));t.commit();}}
        ok(table.sessions("r").size()==32,"per internal address limit reached");
        try(var t=table.begin()){reject(()->outgoing(t,"r",p,original(40033)),"per address limit enforced");}
        for(int host=11;host<14;host++)for(int port=0;port<32;port++){try(var t=table.begin()){outgoing(t,"r",p,Packet.of("tcp","192.168.10."+host,"203.0.113.20",40000+port,80));t.commit();}}
        ok(table.sessions("r").size()==128,"router limit reached");
        try(var t=table.begin()){reject(()->outgoing(t,"r",p,Packet.of("udp","192.168.10.14","203.0.113.20",40000,53)),"router limit enforced");}
        ok(table.sessions("r").stream().map(e->e.wire().sourcePort()).distinct().count()==128,"all ports distinct");
        table.advance(2500,Set.of());ok(table.sessions("r").isEmpty(),"expired bindings never accepted while cleanup is budgeted");
        table.advance(1,Set.of());ok(table.size()==0&&table.expiryCount()==0,"bounded cleanup reclaims storage");
        p.nat.add(new NatSettings.Forward(1,Protocol.TCP,NatTable.FIRST_PORT,"192.168.10.20",80));
        try(var t=table.begin()){ok(outgoing(t,"r",p,input).sourcePort()!=NatTable.FIRST_PORT,"reserved published port is not allocated to NAPT");t.commit();}
        try(var t=table.begin()){
            Packet publicRequest=Packet.of("tcp","203.0.113.20","203.0.113.1",42000,NatTable.FIRST_PORT);
            var into=t.ingress("r",p,1,publicRequest);Packet inside=t.egress("r",p,1,0,into);
            ok(inside.destination()==Ipv4.parse("192.168.10.20")&&inside.destinationPort()==80,"forward translates destination");
            ok(outgoing(t,"r",p,inside.reverse()).equals(publicRequest.reverse()),"server reply restores public port");t.commit();
        }
        try(var t=table.begin()){reject(()->t.ingress("r",p,0,Packet.of("tcp","192.168.10.10","203.0.113.1",40000,80)),"unsupported hairpin fails explicitly");}
        var data=table.sessions("r").getFirst();table.advance(3000,Set.of(data.id()));ok(table.alive(data.id()),"active transfer keeps mapping alive");
        table.clear();ok(table.size()==0&&table.expiryCount()==0,"all dynamic state removed");
        System.out.println("NatTableTest: "+checks+" checks passed");
    }
}
