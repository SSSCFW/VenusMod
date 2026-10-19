package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

/** 機器設定や装着品から独立した物理故障。抽選は既存の機器単位の予約でのみ呼ぶ。 */
public final class PortFaults {
    public enum Kind { NONE, DEVICE, PORT }
    public record Event(Kind kind,int port) {}
    private static final Event NONE=new Event(Kind.NONE,-1);
    private PortFaults() {}

    public static boolean hasFault(NetworkProfile p){
        if(p.failed)return true;
        for(var port:p.ports)if(port.failed)return true;
        return false;
    }
    public static String status(NetworkProfile p){
        var ports=new StringJoiner(",");
        for(int i=0;i<p.ports.length;i++)if(p.ports[i].failed)ports.add("P"+i);
        return p.failed?"本体故障"+(ports.length()>0?" / ポート故障 "+ports:""):
                ports.length()>0?"ポート故障 "+ports:"正常";
    }
    /** 未配線の故障も診断できる表示。物理リンクの成否自体は既存simulator.acceptsで判定。 */
    public static String portStatus(NetworkSimulator sim,String id,int port){
        checkPort(port);var p=sim.nodes().get(id);if(p==null)return "未登録ポート";
        if(p.failed)return "Link Down (本体故障)";
        if(p.ports[port].failed)return "Link Down (ポート故障 P"+port+")";
        if(!p.ports[port].enabled)return "Link Down (管理OFF)";
        var link=sim.linkAt(id,port);
        if(link!=null){var remote=sim.nodes().get(link.peer(id));
            if(remote!=null&&(remote.failed||remote.ports[link.peerPort(id)].failed))return "Link Down (対向機器/ポート故障)";}
        return sim.portStatus(id,port);
    }
    public static boolean failPort(NetworkProfile p,int port){
        checkPort(port);if(p.ports[port].failed)return false;
        p.ports[port].failed=true;return true;
    }
    public static boolean repairPort(NetworkProfile p,int port){
        checkPort(port);if(!p.ports[port].failed)return false;
        p.ports[port].failed=false;return true;
    }
    public static boolean repairAll(NetworkProfile p){
        boolean changed=p.failed;p.failed=false;
        for(var port:p.ports){changed|=port.failed;port.failed=false;}
        return changed;
    }
    /** 総故障確率を増やさず、発生後に種類を選ぶ。完全安定化/OFF/本体停止中は乱数不要。 */
    public static Event sample(NetworkProfile p,double daily,long ticks,double portShare,
                               DoubleSupplier unit,IntUnaryOperator bounded){
        Objects.requireNonNull(p);Objects.requireNonNull(unit);Objects.requireNonNull(bounded);
        if(!Double.isFinite(portShare)||portShare<0||portShare>1||!Double.isFinite(daily)||daily<0||daily>=1||ticks<0)
            throw new IllegalArgumentException("故障確率または判定期間が不正です");
        double probability=NetworkProfile.failureProbability(daily,ticks,p.stability);
        if(p.failed||probability<=0||unit.getAsDouble()>=probability)return NONE;
        int[] eligible=new int[NetworkProfile.PORT_COUNT];int count=0;
        for(int i=0;i<p.ports.length;i++)if(p.ports[i].operational())eligible[count++]=i;
        if(count>0&&portShare>0&&unit.getAsDouble()<portShare){
            int selected=bounded.applyAsInt(count);
            if(selected<0||selected>=count)throw new IllegalArgumentException("故障ポートの抽選値が不正です");
            int port=eligible[selected];failPort(p,port);return new Event(Kind.PORT,port);
        }
        p.failed=true;return new Event(Kind.DEVICE,-1);
    }
    private static void checkPort(int port){
        if(port<0||port>=NetworkProfile.PORT_COUNT)throw new IllegalArgumentException("ポート番号は0〜5です");
    }
}
