package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/**
 * 隣接ポートのBPDUだけで選出する、VLAN別のClassic STP状態機械。
 * Ethernetのワイヤ形式・RSTPのproposal/agreement・TCNは扱わない。
 */
public final class StpEngine {
    public static final int MAX_INSTANCES=512, MAX_DELIVERIES=4096, MAX_EVENTS=128;
    private static final long MAX_COST=4_294_967_295L;
    public enum Role { ROOT, DESIGNATED, ALTERNATE, DISABLED }
    public enum State { DISABLED, BLOCKING, LISTENING, LEARNING, FORWARDING, ERR_DISABLED }
    public record Key(String device,int vlan) implements Comparable<Key> {
        public Key {if(device==null||device.isBlank()||device.length()>128||vlan<1||vlan>4094)throw new IllegalArgumentException("STP instance");}
        public int compareTo(Key other){int n=device.compareTo(other.device);return n==0?Integer.compare(vlan,other.vlan):n;}
    }
    public record BridgeId(int priority,String mac) implements Comparable<BridgeId> {
        public BridgeId {if(priority<0||priority>61440||priority%4096!=0||mac==null||!mac.matches("[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}"))throw new IllegalArgumentException("Bridge ID");mac=mac.toLowerCase(Locale.ROOT);}
        public int compareTo(BridgeId other){int n=Integer.compare(priority,other.priority);return n==0?mac.compareTo(other.mac):n;}
        public String toString(){return priority+"/"+mac;}
    }
    public record PortConfig(boolean up,int cost,int priority,boolean edge,boolean guard,String connection) {
        public PortConfig {if(cost<1||cost>200_000_000||priority<0||priority>240||priority%16!=0||connection==null)throw new IllegalArgumentException("STP port");}
    }
    public record Configuration(BridgeId bridge,int hello,int maxAge,int forwardDelay,List<PortConfig> ports) {
        public Configuration {Objects.requireNonNull(bridge);validateTimers(hello,maxAge,forwardDelay);ports=List.copyOf(ports);if(ports.size()!=6)throw new IllegalArgumentException("STP ports");}
    }
    public record Peer(Key key,int port) {public Peer{Objects.requireNonNull(key);checkPort(port);}}
    public record Bpdu(BridgeId root,long cost,BridgeId sender,int portId,int hello,int maxAge,int forwardDelay,long messageAge) {}
    public record PortStatus(Role role,State state,int cost,boolean edge,long sent,long received,long expiresInTicks,String reason) {}
    public record Status(BridgeId root,long rootCost,int rootPort,List<PortStatus> ports,String reason) {}
    public interface Transport {
        Peer peer(Key from,int port);
        void changed(Key key);
        void log(Key key,String message);
    }
    private record Vector(BridgeId root,long cost,BridgeId sender,int portId,int localPortId) implements Comparable<Vector> {
        public int compareTo(Vector o){int n=root.compareTo(o.root);if(n==0)n=Long.compare(cost,o.cost);if(n==0)n=sender.compareTo(o.sender);if(n==0)n=Integer.compare(portId,o.portId);return n==0?Integer.compare(localPortId,o.localPortId):n;}
    }
    private record Info(Bpdu bpdu,long receivedAt) {}
    private record Event(long due,Key key,int kind,int port) implements Comparable<Event> {
        public int compareTo(Event o){int n=Long.compare(due,o.due);if(n==0)n=key.compareTo(o.key);if(n==0)n=Integer.compare(kind,o.kind);return n==0?Integer.compare(port,o.port):n;}
    }
    private record Delivery(long due,long sequence,Key source,int sourcePort,Key target,int targetPort,Bpdu bpdu) {}
    private static final class Port {
        Role role=Role.DISABLED;State state=State.DISABLED;
        Info info;long expiry,stage,sent,received;
        boolean guardTripped,sawBpdu;String reason="未接続";
    }
    private static final class Bridge {
        final Key key;Configuration config;final Port[] ports=new Port[6];
        BridgeId root;long cost,helloDue;int rootPort=-1,hello,maxAge,delay;
        Bridge(Key key,Configuration c){this.key=key;config=c;root=c.bridge;hello=c.hello;maxAge=c.maxAge;delay=c.forwardDelay;for(int n=0;n<6;n++)ports[n]=new Port();}
    }
    private final Transport transport;
    private final Map<Key,Bridge> bridges=new TreeMap<>();
    private final TreeSet<Event> events=new TreeSet<>();
    private final PriorityQueue<Delivery> deliveries=new PriorityQueue<>(Comparator.comparingLong(Delivery::due).thenComparingLong(Delivery::sequence));
    private long now,sequence;
    private String error="";
    public StpEngine(Transport transport){this.transport=Objects.requireNonNull(transport);}
    public static void validateTimers(int hello,int maxAge,int delay){
        if(hello<20||hello>200||maxAge<120||maxAge>800||delay<80||delay>600||hello%20!=0||maxAge%20!=0||delay%20!=0
                ||maxAge<2*(hello+20)||maxAge>2*(delay-20))throw new IllegalArgumentException("STPタイマー: Hello1〜10 / MaxAge6〜40 / ForwardDelay4〜30秒、2*(Hello+1) <= MaxAge <= 2*(ForwardDelay-1)");
    }
    private static void checkPort(int port){if(port<0||port>=6)throw new IllegalArgumentException("STPポートは0〜5です");}
    public String error(){return error;}
    public int scheduledEvents(){return events.size();}
    public int queuedDeliveries(){return deliveries.size();}
    public long time(){return now;}
    public boolean forwarding(Key key,int port){var b=bridges.get(key);return error.isEmpty()&&b!=null&&b.ports[port].state==State.FORWARDING;}
    public boolean learning(Key key,int port){var b=bridges.get(key);return error.isEmpty()&&b!=null&&(b.ports[port].state==State.LEARNING||b.ports[port].state==State.FORWARDING);}
    public Status status(Key key){
        var b=bridges.get(key);if(b==null)return new Status(null,0,-1,List.of(),error.isEmpty()?"起動待ち/未登録":error);
        var ports=new ArrayList<PortStatus>();for(int n=0;n<6;n++){var p=b.ports[n];ports.add(new PortStatus(p.role,p.state,b.config.ports.get(n).cost,b.config.ports.get(n).edge&&!p.sawBpdu,p.sent,p.received,Math.max(0,Math.max(p.expiry,p.stage)-now),p.reason));}
        return new Status(b.root,b.cost,b.rootPort,List.copyOf(ports),error.isEmpty()?"BPDU / Classic (VLAN "+key.vlan+")":error);
    }
    public void stop(String reason){
        error=reason;events.clear();deliveries.clear();
        for(var b:bridges.values())for(int n=0;n<6;n++){var p=b.ports[n];p.role=Role.DISABLED;p.expiry=p.stage=0;state(b,n,State.DISABLED,reason);}
    }
    /** 設定が変わらないinstanceはタイマーを維持する。 */
    public void synchronize(Map<Key,Configuration> configurations){
        if(configurations.size()>MAX_INSTANCES){stop("STP instance上限 (512)");return;}
        if(!error.isEmpty()){bridges.clear();events.clear();deliveries.clear();error="";}
        Set<Key> removed=new HashSet<>(bridges.keySet());removed.removeAll(configurations.keySet());
        for(Key key:removed){bridges.remove(key);events.removeIf(e->e.key.equals(key));transport.changed(key);}
        deliveries.removeIf(d->!configurations.containsKey(d.target)||!configurations.containsKey(d.source));
        for(var entry:configurations.entrySet()){
            Key key=entry.getKey();Configuration c=entry.getValue();Bridge b=bridges.get(key);
            if(b!=null&&b.config.equals(c))continue;
            boolean reset=b==null||!b.config.bridge.equals(c.bridge)||b.config.hello!=c.hello||b.config.maxAge!=c.maxAge||b.config.forwardDelay!=c.forwardDelay;
            if(reset){
                Bridge previous=b;if(previous!=null)transport.changed(key);events.removeIf(e->e.key.equals(key));b=new Bridge(key,c);bridges.put(key,b);
                // 優先度/タイマー変更だけで、誤配線のguardやBPDU受信歴を消さない。
                if(previous!=null)for(int n=0;n<6;n++)if(previous.config.ports.get(n).connection.equals(c.ports.get(n).connection)&&previous.config.ports.get(n).up&&c.ports.get(n).up){
                    b.ports[n].sawBpdu=previous.ports[n].sawBpdu;b.ports[n].guardTripped=previous.ports[n].guardTripped&&c.ports.get(n).guard;
                }
            }
            else {
                for(int n=0;n<6;n++)if(!b.config.ports.get(n).equals(c.ports.get(n))){
                    var old=b.config.ports.get(n);var p=b.ports[n];var next=c.ports.get(n);
                    cancel(b,n,2);
                    if(!old.connection.equals(next.connection)||old.up!=next.up){cancel(b,n,1);p.info=null;p.sawBpdu=false;}
                    if(!next.guard||!old.up&&next.up)p.guardTripped=false;
                    state(b,n,State.DISABLED,"ポート設定変更");
                }
                b.config=c;
            }
            recompute(b,reset);scheduleHello(b,now+1);
        }
    }
    public void clearGuard(Key key,int port){checkPort(port);var b=bridges.get(key);if(b!=null){b.ports[port].guardTripped=false;b.ports[port].sawBpdu=false;b.ports[port].info=null;cancel(b,port,1);recompute(b,false);scheduleHello(b,now+1);}}
    private static int portId(Bridge b,int port){return b.config.ports.get(port).priority*256+port+1;}
    private static Vector received(Bridge b,int n,boolean addCost){var p=b.ports[n];var bpdu=p.info.bpdu;return new Vector(bpdu.root,Math.min(MAX_COST,bpdu.cost+(addCost?b.config.ports.get(n).cost:0)),bpdu.sender,bpdu.portId,portId(b,n));}
    private Vector sentVector(Bridge b,int n){return new Vector(b.root,b.cost,b.config.bridge,portId(b,n),portId(b,n));}
    private void recompute(Bridge b,boolean forceWait){
        BridgeId oldRoot=b.root;long oldCost=b.cost;int oldPort=b.rootPort,oldDelay=b.delay;
        Vector best=new Vector(b.config.bridge,0,b.config.bridge,0,0);int rootPort=-1;
        for(int n=0;n<6;n++){var p=b.ports[n];if(!b.config.ports.get(n).up||p.guardTripped||p.info==null||p.expiry<=now)continue;
            Vector candidate=received(b,n,true);if(candidate.cost>=MAX_COST)continue;if(candidate.compareTo(best)<0){best=candidate;rootPort=n;}}
        b.root=best.root;b.cost=best.cost;b.rootPort=rootPort;
        if(rootPort<0){b.hello=b.config.hello;b.maxAge=b.config.maxAge;b.delay=b.config.forwardDelay;}
        else{var bpdu=b.ports[rootPort].info.bpdu;b.hello=bpdu.hello;b.maxAge=bpdu.maxAge;b.delay=bpdu.forwardDelay;}
        boolean changed=forceWait||!oldRoot.equals(b.root)||oldCost!=b.cost||oldPort!=rootPort||oldDelay!=b.delay;
        if(changed)transport.log(b.key,"STP root="+b.root+" cost="+b.cost+" rootPort="+b.rootPort);
        for(int n=0;n<6;n++){
            var p=b.ports[n];var c=b.config.ports.get(n);Role previous=p.role;
            if(!c.up||p.guardTripped){p.role=Role.DISABLED;cancel(b,n,2);state(b,n,p.guardTripped?State.ERR_DISABLED:State.DISABLED,p.guardTripped?"BPDU guard: 手動resetが必要":"リンク停止/非対応の接続");continue;}
            if(n==b.rootPort)p.role=Role.ROOT;
            else if(p.info==null||p.expiry<=now||sentVector(b,n).compareTo(received(b,n,false))<=0)p.role=Role.DESIGNATED;
            else p.role=Role.ALTERNATE;
            if(p.role==Role.ALTERNATE){cancel(b,n,2);state(b,n,State.BLOCKING,"代替経路 (BPDU受信は継続)");}
            else if(c.edge&&!p.sawBpdu){cancel(b,n,2);state(b,n,State.FORWARDING,"端末向けedge");}
            else if(changed||previous!=p.role||p.state==State.DISABLED||p.state==State.ERR_DISABLED||p.state==State.BLOCKING){
                cancel(b,n,2);state(b,n,State.LISTENING,"Forward Delay待機");scheduleStage(b,n,now+b.delay);
            }
        }
        if(changed)scheduleHello(b,now+1);
    }
    private void state(Bridge b,int port,State next,String reason){var p=b.ports[port];boolean was=p.state==State.FORWARDING;if(p.state!=next)transport.log(b.key,"STP P"+port+" "+p.state+" -> "+next);p.state=next;p.reason=reason;if(was!=(next==State.FORWARDING))transport.changed(b.key);}
    private void scheduleHello(Bridge b,long due){if(b.helloDue>now&&b.helloDue<=due)return;if(b.helloDue!=0)events.remove(new Event(b.helloDue,b.key,0,-1));b.helloDue=due;events.add(new Event(due,b.key,0,-1));}
    private void scheduleStage(Bridge b,int port,long due){cancel(b,port,2);b.ports[port].stage=due;events.add(new Event(due,b.key,2,port));}
    private void cancel(Bridge b,int port,int kind){var p=b.ports[port];long due=kind==1?p.expiry:p.stage;if(due!=0)events.remove(new Event(due,b.key,kind,port));if(kind==1)p.expiry=0;else p.stage=0;}
    private static boolean valid(Bpdu p){
        if(p==null||p.root==null||p.sender==null||p.root.compareTo(p.sender)>0||p.cost<0||p.cost>=MAX_COST||p.portId<1||p.portId>65535||p.messageAge<0||p.messageAge>=p.maxAge)return false;
        try{validateTimers(p.hello,p.maxAge,p.forwardDelay);return true;}catch(IllegalArgumentException invalid){return false;}
    }
    /** BPDUはBlocking/Listening/Learningでも受信。IPv4のデータフレームではない。 */
    public void receive(Key key,int port,Bpdu packet){
        if(port<0||port>=6||!error.isEmpty()||!valid(packet))return;var b=bridges.get(key);if(b==null)return;var p=b.ports[port];var c=b.config.ports.get(port);
        if(!c.up||p.guardTripped)return;p.received++;boolean wasEdge=c.edge&&!p.sawBpdu;p.sawBpdu=true;
        if(c.edge&&c.guard||packet.sender.equals(b.config.bridge)){
            p.guardTripped=true;p.info=null;cancel(b,port,1);recompute(b,false);return;
        }
        p.info=new Info(packet,now);cancel(b,port,1);p.expiry=now+packet.maxAge-packet.messageAge;events.add(new Event(p.expiry,key,1,port));
        recompute(b,wasEdge);
    }
    private void transmit(Bridge b){
        long age=0;
        if(b.rootPort>=0){Info info=b.ports[b.rootPort].info;if(info==null){recompute(b,false);return;}age=info.bpdu.messageAge+(now-info.receivedAt)+20;}
        if(age<b.maxAge)for(int n=0;n<6;n++){
            var p=b.ports[n];if(p.role!=Role.DESIGNATED||!b.config.ports.get(n).up||p.guardTripped)continue;
            Peer peer=transport.peer(b.key,n);if(peer==null||!bridges.containsKey(peer.key))continue;
            if(deliveries.size()>=MAX_DELIVERIES){stop("STP BPDU配送キュー上限");return;}
            var bpdu=new Bpdu(b.root,b.cost,b.config.bridge,portId(b,n),b.hello,b.maxAge,b.delay,age);p.sent++;
            deliveries.add(new Delivery(now+1,++sequence,b.key,n,peer.key,peer.port,bpdu));
        }
        scheduleHello(b,now+b.hello);
    }
    public void advance(long ticks){
        if(ticks<=0||!error.isEmpty())return;long target=Math.addExact(now,ticks);int work=0;
        while(work++<MAX_EVENTS){
            long e=events.isEmpty()?Long.MAX_VALUE:events.first().due,d=deliveries.isEmpty()?Long.MAX_VALUE:deliveries.peek().due;
            if(Math.min(e,d)>target)break;now=Math.max(now,Math.min(e,d));
            if(d<=e){Delivery packet=deliveries.remove();var peer=transport.peer(packet.source,packet.sourcePort);if(peer!=null&&peer.key.equals(packet.target)&&peer.port==packet.targetPort&&bridges.containsKey(packet.source))receive(packet.target,packet.targetPort,packet.bpdu);}
            else {
                Event event=events.pollFirst();var b=bridges.get(event.key);if(b==null)continue;
                if(event.kind==0){b.helloDue=0;transmit(b);}
                else if(event.kind==1){b.ports[event.port].expiry=0;b.ports[event.port].info=null;recompute(b,false);}
                else {var p=b.ports[event.port];p.stage=0;
                    if(p.state==State.LISTENING){state(b,event.port,State.LEARNING,"MAC学習のみ・転送待機");scheduleStage(b,event.port,now+b.delay);}
                    else if(p.state==State.LEARNING)state(b,event.port,State.FORWARDING,"転送可能");
                }
            }
            if(!error.isEmpty())break;
        }
        now=target;
    }
}
