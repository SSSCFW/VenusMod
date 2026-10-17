package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Minecraftに依存しない永続設定。動的MAC/ARP表と処理中フローは含めない。 */
public final class NetworkProfile {
    public static final int PORT_COUNT=6, MAX_TEXT=32768;
    public static final long[] SPEEDS={1_000_000_000L,10_000_000_000L,40_000_000_000L,100_000_000_000L,400_000_000_000L};
    public static final long[] WAN_SPEEDS={20_000_000L,100_000_000L,1_000_000_000L,10_000_000_000L,100_000_000_000L,400_000_000_000L};
    public final DeviceKind kind;
    public final NetworkServices services=new NetworkServices();
    public final OspfSettings ospf=new OspfSettings();
    public final NatSettings nat=new NatSettings();
    public final VrrpSettings vrrp=new VrrpSettings();
    public final Port[] ports=new Port[PORT_COUNT];
    public String name="device",address="",gateway="",dns="";
    public int prefix=24;
    public boolean failed=false,dhcp=false,web=false,stp=true,publicService=false;
    public int stability=0,processor=0,buffer=0,wanTier=0;
    public String vip="";
    public int vrrpPriority=100;
    public final List<Route> routes=new ArrayList<>();
    public final Map<String,String> dnsRecords=new LinkedHashMap<>();
    public NetworkProfile(DeviceKind kind){
        this.kind=Objects.requireNonNull(kind);name=kind.id();
        for(int i=0;i<ports.length;i++)ports[i]=new Port();
        if(kind.routing()){ports[0].address="192.168.1.1";ports[1].address="192.168.2.1";}
        if(kind==DeviceKind.ROUTER)dhcp=true;
        if(kind.optical())ports[0].optical=true;
    }
    public static final class Port {
        public boolean enabled=true,trunk=false,optical=false;
        public int vlan=1,prefix=24,moduleTier=0;
        public long speedLimitBps=0;
        public String address="";
        public long capacity(){long physical=SPEEDS[Math.clamp(moduleTier,0,4)];return speedLimitBps==0?physical:Math.min(physical,speedLimitBps);}
    }
    public record Route(String network,int prefix,String gateway,int port){
        public Route{network=Ipv4.normalize(network);gateway=Ipv4.normalize(gateway);Ipv4.mask(prefix);if(port<0||port>=PORT_COUNT)throw new IllegalArgumentException("ポート範囲外");}
        public boolean matches(String target){return Ipv4.sameSubnet(network,target,prefix);}
    }
    public long capacity(){return 6_000_000_000L << Math.clamp(processor,0,7);}
    public boolean accepts(int port,CableGrade grade){
        Port p=ports[port];
        boolean opticalHardware=p.moduleTier>0 || kind.optical()&&port==0;
        return p.enabled && p.optical==grade.optical() && (!grade.optical()||opticalHardware);
    }
    public static double failureProbability(double daily,long ticks,int stability){
        if(stability>=3||ticks<=0||daily<=0)return 0;
        double factor=stability==2?0.1:stability==1?0.5:1.0;
        return -Math.expm1(Math.log1p(-Math.clamp(daily*factor,0.0,0.999999))*ticks/24000.0);
    }
    public String encode(){
        try{
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            out.writeInt(5);out.writeUTF(name);out.writeUTF(address);out.writeInt(prefix);out.writeUTF(gateway);out.writeUTF(dns);
            out.writeBoolean(failed);out.writeBoolean(dhcp);out.writeBoolean(web);out.writeBoolean(stp);out.writeBoolean(publicService);
            out.writeUTF(vip);out.writeInt(vrrpPriority);
            for(Port p:ports){out.writeBoolean(p.enabled);out.writeBoolean(p.trunk);out.writeBoolean(p.optical);out.writeInt(p.vlan);out.writeUTF(p.address);out.writeInt(p.prefix);out.writeLong(p.speedLimitBps);}
            out.writeInt(routes.size());for(Route r:routes){out.writeUTF(r.network);out.writeInt(r.prefix);out.writeUTF(r.gateway);out.writeInt(r.port);}
            out.writeInt(dnsRecords.size());for(var entry:dnsRecords.entrySet()){out.writeUTF(entry.getKey());out.writeUTF(entry.getValue());}
            services.write(out);ospf.write(out);nat.write(out);vrrp.write(out);
            String result=Base64.getEncoder().encodeToString(bytes.toByteArray());
            if(result.length()>MAX_TEXT)throw new IllegalArgumentException("設定容量の上限です");return result;
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static NetworkProfile decode(DeviceKind kind,String text){
        if(text==null||text.length()>MAX_TEXT)throw new IllegalArgumentException("設定サイズが不正です");
        try{
            var in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text)));
            int version=in.readInt();if(version<1||version>5)throw new IllegalArgumentException("設定バージョンが未対応です");
            var p=new NetworkProfile(kind);p.name=in.readUTF();p.address=in.readUTF();p.prefix=in.readInt();p.gateway=in.readUTF();p.dns=in.readUTF();
            p.failed=in.readBoolean();p.dhcp=in.readBoolean();p.web=in.readBoolean();p.stp=in.readBoolean();p.publicService=in.readBoolean();p.vip=in.readUTF();p.vrrpPriority=in.readInt();
            for(Port port:p.ports){port.enabled=in.readBoolean();port.trunk=in.readBoolean();port.optical=in.readBoolean();port.vlan=in.readInt();port.address=in.readUTF();port.prefix=in.readInt();port.speedLimitBps=in.readLong();}
            int routes=in.readInt();if(routes<0||routes>64)throw new IllegalArgumentException("経路数の上限です");
            for(int i=0;i<routes;i++)p.routes.add(new Route(in.readUTF(),in.readInt(),in.readUTF(),in.readInt()));
            int records=in.readInt();if(records<0||records>64)throw new IllegalArgumentException("DNS件数の上限です");
            for(int i=0;i<records;i++)p.dnsRecords.put(in.readUTF(),in.readUTF());
            if(version>=2)p.services.read(in);
            if(version>=3)p.ospf.read(in);
            if(version>=4)p.nat.read(in);
            if(version>=5)p.vrrp.read(in);
            if(in.available()!=0)throw new IllegalArgumentException("余分な設定データです");p.validate();return p;
        }catch(IOException|IllegalArgumentException e){throw new IllegalArgumentException("機器設定の形式が不正です",e);}
    }
    public void validate(){
        services.validate(this);ospf.validate();nat.validate(this);vrrp.validate(this);
        if(ospf.enabled&&!kind.routing())throw new IllegalArgumentException("OSPFはルーター/L3スイッチのみです");
        if(name.length()>48||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("機器名は制御文字なし48文字までです");
        validateIp(address);validateIp(gateway);validateIp(dns);validateIp(vip);Ipv4.mask(prefix);
        if(vrrpPriority<1||vrrpPriority>254)throw new IllegalArgumentException("優先度は1〜254です");
        for(Port p:ports){validateIp(p.address);Ipv4.mask(p.prefix);if(p.vlan<1||p.vlan>4094)throw new IllegalArgumentException("VLANは1〜4094です");
            if(p.speedLimitBps<0||p.speedLimitBps>SPEEDS[4])throw new IllegalArgumentException("速度範囲外です");}
        if(routes.size()>64||dnsRecords.size()>64)throw new IllegalArgumentException("設定件数上限です");
        for(var e:dnsRecords.entrySet()){if(!e.getKey().matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}"))throw new IllegalArgumentException("仮想ドメイン名が不正です");Ipv4.parse(e.getValue());}
    }
    private static void validateIp(String s){if(!s.isBlank())Ipv4.parse(s);}
}
