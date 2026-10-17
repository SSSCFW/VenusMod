package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.util.*;

/** STPの永続設定。旧ワールドは従来のsimple方式で移行し、無断で待ち時間を強制しない。 */
public final class StpSettings {
    public boolean classic;
    public int priority=32768,helloTicks=40,maxAgeTicks=400,forwardDelayTicks=300;
    public final int[] costs=new int[6],portPriorities={128,128,128,128,128,128};
    public final boolean[] edge=new boolean[6],guard=new boolean[6];
    public final Map<Integer,Integer> vlanPriorities=new TreeMap<>();
    public void validate(NetworkProfile profile){
        if(classic&&!profile.kind.bridge())throw new IllegalArgumentException("Classic STPはL2中継機器のみです");
        priority(priority);StpEngine.validateTimers(helloTicks,maxAgeTicks,forwardDelayTicks);
        for(int n=0;n<6;n++)if(costs[n]<0||costs[n]>200_000_000||portPriorities[n]<0||portPriorities[n]>240||portPriorities[n]%16!=0)throw new IllegalArgumentException("STP costはauto/1〜200000000、port priorityは0〜240の16刻みです");
        if(vlanPriorities.size()>32)throw new IllegalArgumentException("VLAN別priorityは32件までです");
        for(var entry:vlanPriorities.entrySet()){if(entry.getKey()<1||entry.getKey()>4094)throw new IllegalArgumentException("VLANは1〜4094です");priority(entry.getValue());}
    }
    private static void priority(int p){if(p<0||p>61440||p%4096!=0)throw new IllegalArgumentException("Bridge priorityは0〜61440の4096刻みです");}
    public int priorityFor(int vlan){return vlanPriorities.getOrDefault(vlan,priority);}
    /** 長形式のコストに基づくゲーム値。1Gbps=20000、10Gbps=2000。 */
    public int cost(int port,long bps){return costs[port]>0?costs[port]:(int)Math.clamp(20_000_000_000_000L/Math.max(1,bps),1,200_000_000);}
    public void write(DataOutput out)throws IOException{
        out.writeBoolean(classic);out.writeInt(priority);out.writeInt(helloTicks);out.writeInt(maxAgeTicks);out.writeInt(forwardDelayTicks);
        for(int n=0;n<6;n++){out.writeInt(costs[n]);out.writeInt(portPriorities[n]);out.writeBoolean(edge[n]);out.writeBoolean(guard[n]);}
        out.writeInt(vlanPriorities.size());for(var e:vlanPriorities.entrySet()){out.writeInt(e.getKey());out.writeInt(e.getValue());}
    }
    public void read(DataInput in)throws IOException{
        classic=in.readBoolean();priority=in.readInt();helloTicks=in.readInt();maxAgeTicks=in.readInt();forwardDelayTicks=in.readInt();
        for(int n=0;n<6;n++){costs[n]=in.readInt();portPriorities[n]=in.readInt();edge[n]=in.readBoolean();guard[n]=in.readBoolean();}
        int size=in.readInt();if(size<0||size>32)throw new IllegalArgumentException("STP VLAN設定上限");vlanPriorities.clear();
        for(int n=0;n<size;n++){int vlan=in.readInt(),p=in.readInt();if(vlanPriorities.put(vlan,p)!=null)throw new IllegalArgumentException("重複STP VLAN設定");}
    }
}
