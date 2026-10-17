package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.util.*;

/** 永続化するOSPF設定のみ。隣接・LSDB・学習経路はアイテムへ保存しない。 */
public final class OspfSettings {
    public static final int PORTS=6;
    public enum Mode { OFF, ACTIVE, PASSIVE }
    public boolean enabled;
    public String routerId="";
    public long area;
    public int helloTicks=20, deadTicks=80;
    public final Mode[] modes=new Mode[PORTS];
    public final int[] costs=new int[PORTS];
    public OspfSettings(){Arrays.fill(modes,Mode.OFF);}
    public void validate(){
        if(!routerId.isBlank())unicast(routerId);
        if(area<0||area>0xffffffffL)throw new IllegalArgumentException("OSPFエリアは0〜4294967295です");
        if(helloTicks<20||helloTicks>1200||deadTicks<helloTicks*2||deadTicks>4800)
            throw new IllegalArgumentException("Helloは1〜60秒、DeadはHelloの2倍以上〜240秒です");
        for(int i=0;i<PORTS;i++)if(modes[i]==null||costs[i]<0||costs[i]>65535)
            throw new IllegalArgumentException("OSPFコストはautoまたは1〜65535です");
    }
    public static long ipv4(String ip){
        if(ip==null||!ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new IllegalArgumentException("IPv4形式で指定してください");
        long n=0;for(String part:ip.split("\\.")){int b=Integer.parseInt(part);if(b>255)throw new IllegalArgumentException("IPv4範囲外です");n=n<<8|b;}return n;
    }
    public static long unicast(String ip){long n=ipv4(ip);if(n==0||n>=0xe0000000L)throw new IllegalArgumentException("OSPFにはユニキャストIPv4を指定してください");return n;}
    public static String format(long n){return ((n>>>24)&255)+"."+((n>>>16)&255)+"."+((n>>>8)&255)+"."+(n&255);}
    public static long mask(int prefix){if(prefix<0||prefix>32)throw new IllegalArgumentException("IPv4 prefix");return prefix==0?0:(0xffffffffL<<(32-prefix))&0xffffffffL;}
    public static boolean sameSubnet(String a,String b,int prefix){return (ipv4(a)&mask(prefix))==(ipv4(b)&mask(prefix));}
    public void write(DataOutput out)throws IOException{
        validate();out.writeBoolean(enabled);out.writeUTF(routerId);out.writeLong(area);out.writeInt(helloTicks);out.writeInt(deadTicks);
        for(int i=0;i<PORTS;i++){out.writeUTF(modes[i].name());out.writeInt(costs[i]);}
    }
    public void read(DataInput in)throws IOException{
        enabled=in.readBoolean();routerId=in.readUTF();area=in.readLong();helloTicks=in.readInt();deadTicks=in.readInt();
        for(int i=0;i<PORTS;i++){modes[i]=Mode.valueOf(in.readUTF());costs[i]=in.readInt();}validate();
    }
}
