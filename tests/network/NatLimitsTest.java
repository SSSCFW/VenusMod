import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import java.io.*;

public final class NatLimitsTest {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static NetworkProfile router(){var p=new NetworkProfile(DeviceKind.ROUTER);p.nat.role(0,NatSettings.Role.INSIDE);p.nat.role(1,NatSettings.Role.OUTSIDE);p.nat.enabled=true;return p;}
    public static void main(String[] args)throws Exception{
        var table=new NatTable();var p=router();
        for(int r=0;r<16;r++)for(int host=10;host<14;host++)for(int port=0;port<32;port++){
            try(var tx=table.begin()){var packet=NetworkPolicy.Packet.of("udp","192.168.1."+host,"192.168.2.20",40000+port,53);tx.egress("r"+r,p,0,1,tx.ingress("r"+r,p,0,packet));tx.commit();}
        }
        ok(table.size()==NatTable.MAX_TOTAL&&table.expiryCount()==NatTable.MAX_TOTAL,"global storage bound");
        boolean denied=false;try(var tx=table.begin()){var packet=NetworkPolicy.Packet.of("tcp","192.168.1.10","192.168.2.20",45000,80);tx.egress("overflow",p,0,1,tx.ingress("overflow",p,0,packet));tx.commit();}catch(IllegalArgumentException e){denied=true;}
        ok(denied&&table.size()==NatTable.MAX_TOTAL,"one more session fails without growing queues");
        table.advance(601,Set.of());ok(table.sessions("r15").isEmpty(),"expiry checked before delayed cleanup");
        for(int i=0;i<32;i++)table.advance(1,Set.of());ok(table.size()==0&&table.expiryCount()==0,"expired entries reclaimed using bounded batches");
        for(int i=0;i<32;i++)p.nat.add(new NatSettings.Forward(1,NetworkPolicy.Protocol.TCP,10000+i,"192.168.1.20",80+i));
        denied=false;try{p.nat.add(new NatSettings.Forward(1,NetworkPolicy.Protocol.TCP,20000,"192.168.1.20",80));}catch(IllegalArgumentException e){denied=true;}
        ok(denied&&p.nat.forwards().size()==32,"static rule bound");
        p.validate();String encoded=p.encode();ok(NetworkProfile.decode(DeviceKind.ROUTER,encoded).encode().equals(encoded),"maximum rule set round-trips");
        byte[] corrupt=Base64.getDecoder().decode(encoded);java.nio.ByteBuffer.wrap(corrupt).putInt(Integer.MAX_VALUE);
        denied=false;try{NetworkProfile.decode(DeviceKind.ROUTER,Base64.getEncoder().encodeToString(corrupt));}catch(IllegalArgumentException e){denied=true;}ok(denied,"unknown version still rejected");
        byte[] raw=Base64.getDecoder().decode(encoded);denied=false;try{NetworkProfile.decode(DeviceKind.ROUTER,Base64.getEncoder().encodeToString(Arrays.copyOf(raw,raw.length-1)));}catch(IllegalArgumentException e){denied=true;}ok(denied,"truncated NAT rules rejected");
        String old="AAAAAwAKbmV0d29ya19wYwAAAAAAGAAAAAAAAAABAAAAAAAAZAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAEAAAAAAAEAAAAAABgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAUAAAAUAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAAADT0ZGAAAAAA==";
        var legacy=NetworkProfile.decode(DeviceKind.PC,old);ok(!legacy.nat.enabled&&legacy.nat.forwards().isEmpty(),"frozen v3 fixture migrates with NAT OFF");
        for(int i=0;i<6;i++)ok(legacy.nat.role(i)==NatSettings.Role.NONE,"no legacy interface enabled accidentally");
        System.out.println("NatLimitsTest: "+checks+" checks passed");
    }
}
