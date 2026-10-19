package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

/** 機器設定や装着品から独立した物理故障。抽選は既存の機器単位の予約でのみ呼ぶ。 */
public final class PortFaults {
    public enum Kind { NONE, DEVICE, PORT, DEGRADED }
    public record Event(Kind kind,int port) {}
    /** 劣化中は交渉帯域の10%。現実の規格ではなく暫定ゲーム値。 */
    public static final int DEGRADATION_DIVISOR=10;
    private static final Event NONE=new Event(Kind.NONE,-1);
    private PortFaults() {}

    public static boolean hasFault(NetworkProfile p){
        if(p.failed)return true;
        for(var port:p.ports)if(port.failed||port.degraded)return true;
        return false;
    }
    public static String status(NetworkProfile p){
        var down=new StringJoiner(",");var slow=new StringJoiner(",");var parts=new StringJoiner(" / ");
        if(p.failed)parts.add("本体故障");
        for(int i=0;i<p.ports.length;i++){if(p.ports[i].failed)down.add("P"+i);else if(p.ports[i].degraded)slow.add("P"+i);}
        if(down.length()>0)parts.add("ポート故障 "+down);
        if(slow.length()>0)parts.add("帯域劣化 "+slow);
        return parts.length()==0?"正常":parts.toString();
    }
    /** 固定速度の交渉結果は変えず、有効転送量だけを制限。両端劣化でも係数は1回。 */
    public static long degradedBandwidth(NetworkProfile.Port a,NetworkProfile.Port b,long negotiated){
        if(negotiated<0)throw new IllegalArgumentException("負の帯域です");
        if(negotiated==0||!a.degraded&&!b.degraded)return negotiated;
        return Math.max(1,negotiated/DEGRADATION_DIVISOR);
    }
    /** 未配線の劣化も診断可能。劣化の警告で本来のLink Down理由を隠さない。 */
    public static String portStatus(NetworkSimulator sim,String id,int port){
        checkPort(port);var p=sim.nodes().get(id);if(p==null)return "未登録ポート";
        if(p.failed)return "Link Down (本体故障)";
        if(p.ports[port].failed)return "Link Down (ポート故障 P"+port+")";
        String local=p.ports[port].degraded?" / 帯域劣化 P"+port+" (実効10%)":"";
        if(!p.ports[port].enabled)return "Link Down (管理OFF)"+local;
        var link=sim.linkAt(id,port);String remoteWarning="";
        if(link!=null){var remote=sim.nodes().get(link.peer(id));
            if(remote!=null){var peer=remote.ports[link.peerPort(id)];
                if(remote.failed||peer.failed)return "Link Down (対向機器/ポート故障)"+local;
                if(peer.degraded)remoteWarning=" / 対向ポート帯域劣化";}}
        return sim.portStatus(id,port)+local+remoteWarning;
    }
    public static boolean degradePort(NetworkProfile p,int port){
        checkPort(port);var target=p.ports[port];if(target.failed||target.degraded)return false;
        target.degraded=true;return true;
    }
    public static boolean failPort(NetworkProfile p,int port){
        checkPort(port);if(p.ports[port].failed)return false;
        p.ports[port].degraded=false;p.ports[port].failed=true;return true;
    }
    public static boolean repairPort(NetworkProfile p,int port){
        checkPort(port);var target=p.ports[port];if(!target.failed&&!target.degraded)return false;
        target.failed=false;target.degraded=false;return true;
    }
    public static boolean repairAll(NetworkProfile p){
        boolean changed=p.failed;p.failed=false;
        for(var port:p.ports){changed|=port.failed||port.degraded;port.failed=false;port.degraded=false;}
        return changed;
    }
    /** 既存呼出しは従来どおりポート完全停止のみ。 */
    public static Event sample(NetworkProfile p,double daily,long ticks,double portShare,
                               DoubleSupplier unit,IntUnaryOperator bounded){
        return sample(p,daily,ticks,portShare,0,unit,bounded);
    }
    /** 総故障確率を増やさず、発生後に種類を選ぶ。完全安定化/OFF/本体停止中は乱数不要。 */
    public static Event sample(NetworkProfile p,double daily,long ticks,double portShare,double degradationShare,
                               DoubleSupplier unit,IntUnaryOperator bounded){
        Objects.requireNonNull(p);Objects.requireNonNull(unit);Objects.requireNonNull(bounded);
        if(!Double.isFinite(portShare)||portShare<0||portShare>1||!Double.isFinite(daily)||daily<0||daily>=1||ticks<0
                ||!Double.isFinite(degradationShare)||degradationShare<0||degradationShare>1)
            throw new IllegalArgumentException("故障確率または判定期間が不正です");
        double probability=NetworkProfile.failureProbability(daily,ticks,p.stability);
        if(p.failed||probability<=0||unit.getAsDouble()>=probability)return NONE;
        int[] eligible=new int[NetworkProfile.PORT_COUNT];int count=0;
        for(int i=0;i<p.ports.length;i++)if(p.ports[i].operational())eligible[count++]=i;
        if(count>0&&portShare>0&&unit.getAsDouble()<portShare){
            int selected=bounded.applyAsInt(count);
            if(selected<0||selected>=count)throw new IllegalArgumentException("故障ポートの抽選値が不正です");
            int port=eligible[selected];
            // 既に劣化中なら完全故障へ。故障イベントを引いて何も変わらない状態を作らない。
            if(!p.ports[port].degraded&&degradationShare>0&&unit.getAsDouble()<degradationShare){
                degradePort(p,port);return new Event(Kind.DEGRADED,port);
            }
            failPort(p,port);return new Event(Kind.PORT,port);
        }
        p.failed=true;return new Event(Kind.DEVICE,-1);
    }
    private static void checkPort(int port){
        if(port<0||port>=NetworkProfile.PORT_COUNT)throw new IllegalArgumentException("ポート番号は0〜5です");
    }
}
