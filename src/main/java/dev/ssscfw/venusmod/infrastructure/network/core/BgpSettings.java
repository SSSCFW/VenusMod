package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.util.*;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkPolicy.Prefix;

/** 永続設定だけを保持する。セッション・受信経路・タイマーを機器アイテムへ保存しない。 */
public final class BgpSettings {
    public static final int MAX_PEERS=8, MAX_NETWORKS=64, MAX_PATH=32;
    public boolean enabled;
    public long asn=65000;
    public String routerId="";
    public int keepaliveTicks=20, holdTicks=80;
    public final Map<String,Peer> peers=new LinkedHashMap<>();
    public final Set<Prefix> networks=new LinkedHashSet<>();

    /** autoはeBGPでは拒否、iBGPでは許可。CIDR指定はその範囲のmore-specificを含む。 */
    public record Policy(String value) {
        public Policy {
            Objects.requireNonNull(value);
            if(!Set.of("auto","none","all").contains(value))value=prefix(value).toString();
        }
        public boolean permits(Prefix p,boolean external){
            return switch(value){case "none"->false;case "auto"->!external;case "all"->true;
                default->{var scope=Prefix.parse(value);yield p.length()>=scope.length()&&scope.contains(p.network());}};
        }
    }
    public record Peer(String address,long remoteAs,int sourcePort,boolean nextHopSelf,int prepend,
                       long localPref,long med,Policy imports,Policy exports) {
        public Peer {
            address=unicast(address);checkAs(remoteAs);
            if(sourcePort< -1||sourcePort>=6)throw new IllegalArgumentException("送信元ポートはautoまたは0〜5です");
            if(prepend<0||prepend>8)throw new IllegalArgumentException("追加ASプリペンドは0〜8回です");
            if(localPref< -1||localPref>0xffffffffL||med< -1||med>0xffffffffL)throw new IllegalArgumentException("属性値はautoまたは0〜4294967295です");
            Objects.requireNonNull(imports);Objects.requireNonNull(exports);
        }
        public static Peer of(String ip,long asn){return new Peer(ip,asn,-1,false,0,-1,-1,new Policy("auto"),new Policy("auto"));}
    }
    public void validate(NetworkProfile profile){
        checkAs(asn);if(!routerId.isBlank())unicast(routerId);
        if(keepaliveTicks<20||keepaliveTicks>600||holdTicks<3*keepaliveTicks||holdTicks>3600)
            throw new IllegalArgumentException("Keepaliveは1〜30秒、Holdはその3倍以上〜180秒です");
        if(peers.size()>MAX_PEERS||networks.size()>MAX_NETWORKS)throw new IllegalArgumentException("BGPは8ピア・64生成ネットワークまでです");
        if((enabled||!peers.isEmpty()||!networks.isEmpty())&&!profile.kind.routing())throw new IllegalArgumentException("BGPはルーター/L3スイッチのみです");
        for(var entry:peers.entrySet())if(!entry.getKey().equals(entry.getValue().address()))throw new IllegalArgumentException("BGPピアキー不一致");
        for(Prefix p:networks)prefix(p.toString());
    }
    public static String unicast(String text){long n=Ipv4.parse(text);if(n==0||n>=0xe0000000L||n>>>24==127)throw new IllegalArgumentException("BGPにはユニキャストIPv4を指定してください");return Ipv4.format(n);}
    public static void checkAs(long asn){if(asn<1||asn>=0xffffffffL)throw new IllegalArgumentException("AS番号は1〜4294967294です");}
    public static Prefix prefix(String text){
        if(!text.matches("[0-9.]+/[0-9]{1,2}"))throw new IllegalArgumentException("BGPネットワークはIPv4/長さで指定してください");
        Prefix p=Prefix.parse(text);if(p.network()>=0xe0000000L)throw new IllegalArgumentException("マルチキャスト経路は広告できません");return p;
    }
    public void write(DataOutput out)throws IOException {
        out.writeBoolean(enabled);out.writeLong(asn);out.writeUTF(routerId);out.writeInt(keepaliveTicks);out.writeInt(holdTicks);
        out.writeInt(peers.size());for(Peer p:peers.values()){
            out.writeUTF(p.address);out.writeLong(p.remoteAs);out.writeInt(p.sourcePort);out.writeBoolean(p.nextHopSelf);
            out.writeInt(p.prepend);out.writeLong(p.localPref);out.writeLong(p.med);out.writeUTF(p.imports.value);out.writeUTF(p.exports.value);
        }
        out.writeInt(networks.size());for(Prefix p:networks)out.writeUTF(p.toString());
    }
    public void read(DataInput in)throws IOException {
        enabled=in.readBoolean();asn=in.readLong();routerId=in.readUTF();keepaliveTicks=in.readInt();holdTicks=in.readInt();
        int count=in.readInt();if(count<0||count>MAX_PEERS)throw new IllegalArgumentException("BGPピア保存件数上限です");
        for(int i=0;i<count;i++){
            Peer p=new Peer(in.readUTF(),in.readLong(),in.readInt(),in.readBoolean(),in.readInt(),in.readLong(),in.readLong(),new Policy(in.readUTF()),new Policy(in.readUTF()));
            if(peers.putIfAbsent(p.address,p)!=null)throw new IllegalArgumentException("BGPピア重複です");
        }
        count=in.readInt();if(count<0||count>MAX_NETWORKS)throw new IllegalArgumentException("BGP生成経路保存件数上限です");
        for(int i=0;i<count;i++)if(!networks.add(prefix(in.readUTF())))throw new IllegalArgumentException("BGP生成経路重複です");
    }
}
