package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.util.*;

/** 機器アイテムに保存する設定のみ。変換表と通信履歴は含めない。 */
public final class NatSettings {
    public static final int MAX_FORWARDS=32;
    public enum Role { NONE, INSIDE, OUTSIDE }
    public record Forward(int outside,NetworkPolicy.Protocol protocol,int publicPort,String address,int privatePort) {
        public Forward {
            checkPort(outside);Objects.requireNonNull(protocol);address=Ipv4.normalize(address);
            if(protocol!=NetworkPolicy.Protocol.TCP&&protocol!=NetworkPolicy.Protocol.UDP)
                throw new IllegalArgumentException("ポート転送はTCP/UDPのみです");
            if(publicPort<1||publicPort>65535||privatePort<1||privatePort>65535)
                throw new IllegalArgumentException("転送ポート番号は1〜65535です");
            unicast(address);
        }
        public String description(){return "P"+outside+" "+protocol+" :"+publicPort+" → "+address+":"+privatePort;}
    }
    public boolean enabled=false,overload=true;
    private final Role[] roles=new Role[NetworkProfile.PORT_COUNT];
    private final List<Forward> forwards=new ArrayList<>();
    public NatSettings(){Arrays.fill(roles,Role.NONE);}
    public Role role(int port){checkPort(port);return roles[port];}
    public void role(int port,Role role){checkPort(port);roles[port]=Objects.requireNonNull(role);}
    public List<Forward> forwards(){return List.copyOf(forwards);}
    public void add(Forward forward){
        if(forwards.size()>=MAX_FORWARDS)throw new IllegalArgumentException("ポート転送は32規則までです");
        if(forwards.stream().anyMatch(f->f.outside==forward.outside&&f.protocol==forward.protocol&&f.publicPort==forward.publicPort))
            throw new IllegalArgumentException("同じ外側ポート・プロトコル・公開ポートの転送があります");
        forwards.add(forward);
    }
    public void delete(int port,NetworkPolicy.Protocol protocol,int publicPort){
        checkPort(port);if(!forwards.removeIf(f->f.outside==port&&f.protocol==protocol&&f.publicPort==publicPort))throw new IllegalArgumentException("指定されたポート転送はありません");
    }
    public void validate(NetworkProfile profile){
        if(!profile.kind.routing()&&(enabled||!forwards.isEmpty()||Arrays.stream(roles).anyMatch(r->r!=Role.NONE)))
            throw new IllegalArgumentException("NATはルーター/L3スイッチのみです");
        if(enabled){
            if(Arrays.stream(roles).noneMatch(r->r==Role.INSIDE)||Arrays.stream(roles).noneMatch(r->r==Role.OUTSIDE))
                throw new IllegalArgumentException("NATのinsideとoutsideを先に設定してください");
            for(int i=0;i<roles.length;i++)if(roles[i]!=Role.NONE){
                if(profile.ports[i].address.isBlank())throw new IllegalArgumentException("NAT参加ポートP"+i+"のIPが未設定です");
                unicast(profile.ports[i].address);
            }
        }
        for(Forward f:forwards){
            if(roles[f.outside]!=Role.OUTSIDE)throw new IllegalArgumentException("転送元はoutsideポートを指定してください");
            for(var port:profile.ports)if(f.address.equals(port.address))throw new IllegalArgumentException("転送先は別の内部機器のIPを指定してください");
            if(f.address.equals(profile.vip)||f.address.equals(profile.address))throw new IllegalArgumentException("自分自身への転送はできません");
        }
    }
    public void write(DataOutput out)throws IOException{
        out.writeBoolean(enabled);out.writeBoolean(overload);for(Role role:roles)out.writeUTF(role.name());
        out.writeInt(forwards.size());for(Forward f:forwards){out.writeInt(f.outside);out.writeUTF(f.protocol.name());out.writeInt(f.publicPort);out.writeUTF(f.address);out.writeInt(f.privatePort);}
    }
    public void read(DataInput in)throws IOException{
        enabled=in.readBoolean();overload=in.readBoolean();for(int i=0;i<roles.length;i++)roles[i]=Role.valueOf(in.readUTF());
        int size=in.readInt();if(size<0||size>MAX_FORWARDS)throw new IllegalArgumentException("NAT保存規則数が不正です");
        for(int i=0;i<size;i++)add(new Forward(in.readInt(),NetworkPolicy.Protocol.valueOf(in.readUTF()),in.readInt(),in.readUTF(),in.readInt()));
    }
    private static void checkPort(int port){if(port<0||port>=NetworkProfile.PORT_COUNT)throw new IllegalArgumentException("機器ポートは0〜5です");}
    private static void unicast(String ip){long n=Ipv4.parse(ip);if(n==0||n>=0xe0000000L)throw new IllegalArgumentException("NATにはユニキャストIPv4を指定してください");}
}
