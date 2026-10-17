package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.util.Locale;

/** IPv4 VRRPの永続設定。役割・受信広告・期限は保存しない。 */
public final class VrrpSettings {
    public boolean enabled=true,preempt=true;
    public int port=-1,group=1,advertisementTicks=20;
    public void validate(NetworkProfile profile){
        if(port< -1||port>=NetworkProfile.PORT_COUNT)throw new IllegalArgumentException("VRRPポートはautoまたは0〜5です");
        if(group<1||group>255)throw new IllegalArgumentException("VRIDは1〜255です");
        if(advertisementTicks<20||advertisementTicks>200||advertisementTicks%20!=0)throw new IllegalArgumentException("広告間隔は1〜10秒の整数です");
        // 旧版で保存できた不適合なVIPも失わない。意味的な不適合はresolvePortで停止する。
    }
    /** autoで複数のL3インターフェースに一致した場合は推測せず停止する。 */
    public int resolvePort(NetworkProfile p){
        if(p.vip.isBlank())return -1;
        final long vip;
        try{vip=unicast(p.vip);}catch(IllegalArgumentException invalid){return -1;}
        for(var physical:p.ports)if(p.vip.equals(physical.address))return -1;
        int selected=-1;
        for(int n=0;n<NetworkProfile.PORT_COUNT;n++){
            if(port>=0&&port!=n)continue;
            var candidate=p.ports[n];
            if(candidate.address.isBlank()||candidate.prefix<1||candidate.prefix>30)continue;
            long ip=Ipv4.parse(candidate.address),mask=Ipv4.mask(candidate.prefix);
            if(ip==vip||ip==0||ip>=0xe0000000L||(ip&mask)!=(vip&mask))continue;
            long base=ip&mask,broadcast=base|(~mask&0xffffffffL);
            if(vip==base||vip==broadcast||ip==base||ip==broadcast)continue;
            if(selected>=0)return -1;selected=n;
        }
        return selected;
    }
    public static long unicast(String address){long ip=Ipv4.parse(address);if(ip==0||ip>=0xe0000000L)throw new IllegalArgumentException("VRRPはユニキャストIPv4を指定してください");return ip;}
    public static boolean hostAddress(String address,int prefix){
        try{long ip=unicast(address),mask=Ipv4.mask(prefix),base=ip&mask;return prefix>=1&&prefix<=30&&ip!=base&&ip!=(base|(~mask&0xffffffffL));}
        catch(IllegalArgumentException e){return false;}
    }
    public static String virtualMac(int group){if(group<1||group>255)throw new IllegalArgumentException("VRID");return String.format(Locale.ROOT,"00:00:5e:00:01:%02x",group);}
    public void write(DataOutput out)throws IOException{out.writeBoolean(enabled);out.writeBoolean(preempt);out.writeInt(port);out.writeInt(group);out.writeInt(advertisementTicks);}
    public void read(DataInput in)throws IOException{enabled=in.readBoolean();preempt=in.readBoolean();port=in.readInt();group=in.readInt();advertisementTicks=in.readInt();}
}
