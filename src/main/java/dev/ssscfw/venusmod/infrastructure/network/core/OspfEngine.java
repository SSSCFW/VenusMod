package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/**
 * ゲーム用OSPFv2のエリア内サブセット。Helloで隣接を確認し、実際に受信した
 * Router-LSAのスナップショットだけからSPFを計算する。実ソケットは使用しない。
 * DR/BDR、ABR、DD/LSR/ACKのワイヤ形式は未対応。全隣接型のDB同期で代替する。
 */
public final class OspfEngine {
    public static final int MAX_ROUTERS=128, MAX_NEIGHBORS=32, MAX_EVENTS=16384;
    public static final int EVENTS_PER_TICK=64, MAX_SPF_WORK=32768;
    public static final int REFRESH_TICKS=600, MAX_AGE_TICKS=2400;
    public record Peer(String device,int port) {
        public Peer{Objects.requireNonNull(device);if(port<0||port>=6)throw new IllegalArgumentException("OSPF peer port");}
    }
    /** 機器の設定とローカルリンク状態。他機器の経路情報は含めない。 */
    public record Port(int number,String address,int prefix,int cost,OspfSettings.Mode mode,boolean up) {
        public Port{if(number<0||number>=6||cost<1||cost>65535)throw new IllegalArgumentException("OSPF port/cost");OspfSettings.unicast(address);OspfSettings.mask(prefix);Objects.requireNonNull(mode);}
        boolean active(){return up&&mode==OspfSettings.Mode.ACTIVE;}
    }
    public record Configuration(String routerId,long area,int helloTicks,int deadTicks,List<Port> ports) {
        public Configuration{
            var check=new OspfSettings();check.routerId=routerId;check.area=area;check.helloTicks=helloTicks;check.deadTicks=deadTicks;check.validate();
            routerId=OspfSettings.format(OspfSettings.unicast(routerId));ports=List.copyOf(ports);
            if(ports.size()>6||ports.stream().map(Port::number).distinct().count()!=ports.size())throw new IllegalArgumentException("OSPF duplicate ports");
        }
        Port port(int number){for(var p:ports)if(p.number==number)return p;return null;}
    }
    public interface Transport {
        /** ローカルL2/VLANとOSPF ACLが通る方向だけを返す。経路表を使って中継しない。 */
        List<Peer> peers(String source,int port);
        void log(String device,String message);
    }
    public record Route(String network,int prefix,String gateway,int port,int cost,String origin,long area) {}
    public record NeighborView(String routerId,String address,int localPort,String state,long deadInTicks) {}
    public record DatabaseView(String routerId,long sequence,int links,int prefixes,long remainingTicks) {}
    private enum State { INIT, EXCHANGE, FULL }
    private record Key(int localPort,String remote,int remotePort) {}
    private static final class Neighbor {
        final Key key;final String routerId,address;long lastSeen;State state=State.INIT;
        Neighbor(Key key,String routerId,String address,long now){this.key=key;this.routerId=routerId;this.address=address;lastSeen=now;}
    }
    private record Edge(String peer,int localPort,int remotePort,int cost,String nextHop) {}
    private record Prefix(String network,int length,int cost) {}
    private record Lsa(String routerId,long area,long sequence,long expires,List<Edge> edges,List<Prefix> prefixes) {}
    private static final class Node {
        final String id;Configuration config;
        final Map<Key,Neighbor> neighbors=new LinkedHashMap<>();
        final Map<String,Lsa> database=new LinkedHashMap<>();
        List<Route> routes=List.of();boolean spfScheduled;long ageDue=Long.MAX_VALUE;
        Node(String id,Configuration config){this.id=id;this.config=config;}
    }
    private record Event(long due,long order,Node owner,boolean spf,Runnable action) {}
    private final Transport transport;
    private final Map<String,Node> nodes=new LinkedHashMap<>();
    private final PriorityQueue<Event> events=new PriorityQueue<>(Comparator.comparingLong(Event::due).thenComparingLong(Event::order));
    private final Set<String> routeChanges=new HashSet<>();
    private long now,order,sequence;
    private String error="";
    public OspfEngine(Transport transport){this.transport=Objects.requireNonNull(transport);}
    public String error(){return error;}
    public void stop(String reason){fail(reason);}
    public int pendingEvents(){return events.size();}
    public Set<String> drainRouteChanges(){var copy=Set.copyOf(routeChanges);routeChanges.clear();return copy;}
    public List<Route> routes(String id){var n=nodes.get(id);return n==null||!error.isEmpty()?List.of():n.routes;}
    public List<NeighborView> neighbors(String id){var n=nodes.get(id);if(n==null)return List.of();return n.neighbors.values().stream().map(p->new NeighborView(p.routerId,p.address,p.key.localPort,p.state.name(),Math.max(0,p.lastSeen+n.config.deadTicks-now))).toList();}
    public List<DatabaseView> database(String id){var n=nodes.get(id);if(n==null)return List.of();return n.database.values().stream().map(l->new DatabaseView(l.routerId,l.sequence,l.edges.size(),l.prefixes.size(),Math.max(0,l.expires-now))).toList();}

    /** 設定・配線変更時のみ呼ぶ。Hello受信停止だけならDeadタイマーで失効させる。 */
    public void synchronize(Map<String,Configuration> desired){
        if(desired.size()>MAX_ROUTERS){fail("OSPF有効機器は1ディメンション128台までです");return;}
        Set<String> ids=new HashSet<>();for(var c:desired.values())if(!ids.add(c.routerId)){fail("OSPF Router IDが重複しています: "+c.routerId);return;}
        if(!error.isEmpty()){error="";nodes.clear();events.clear();}
        for(var it=nodes.entrySet().iterator();it.hasNext();){var entry=it.next();if(!desired.containsKey(entry.getKey())){routeChanges.add(entry.getKey());it.remove();}}
        for(var entry:desired.entrySet()){
            String id=entry.getKey();Configuration cfg=entry.getValue();Node n=nodes.get(id);
            boolean restart=n==null||!n.config.routerId.equals(cfg.routerId)||n.config.area!=cfg.area||n.config.helloTicks!=cfg.helloTicks||n.config.deadTicks!=cfg.deadTicks;
            if(restart){
                routeChanges.add(id);n=new Node(id,cfg);nodes.put(id,n);originate(n,true);Node added=n;
                schedule(n,1,false,()->hello(added));schedule(n,REFRESH_TICKS,false,()->refresh(added));
            }else if(!n.config.equals(cfg)){
                n.config=cfg;
                for(var it=n.neighbors.values().iterator();it.hasNext();){var neighbor=it.next();var port=cfg.port(neighbor.key.localPort);
                    if(port==null||!port.active()||!OspfSettings.sameSubnet(port.address,neighbor.address,port.prefix)){transport.log(id,"OSPF Down: ポート変更 "+neighbor.routerId);it.remove();}}
                originate(n,true);
            }
        }
        // 古い設定世代のタイマーを溜めず、現存Nodeだけ残す。
        events.removeIf(e->nodes.get(e.owner.id)!=e.owner);
    }
    private void fail(String reason){for(String id:nodes.keySet()){routeChanges.add(id);transport.log(id,reason);}error=reason;nodes.clear();events.clear();}
    private void schedule(Node n,long delay,boolean spf,Runnable action){
        if(!error.isEmpty())return;
        if(events.size()>=MAX_EVENTS){fail("OSPFイベント上限。通信を停止しました。設定を見直してください");return;}
        events.add(new Event(Math.addExact(now,Math.max(1,delay)),++order,n,spf,action));
    }
    /** 1tickごとの呼び出しを想定。大きなdeltaでも無制限のタイマー追いつき処理をしない。 */
    public void advance(long ticks){
        if(ticks<=0||!error.isEmpty())return;now=Math.addExact(now,ticks);int work=0,spfs=0;
        while(!events.isEmpty()&&events.peek().due<=now&&work<EVENTS_PER_TICK){
            Event e=events.peek();if(e.spf&&spfs>=2)break;events.remove();if(nodes.get(e.owner.id)!=e.owner)continue;
            work++;if(e.spf)spfs++;
            try{e.action.run();}catch(IllegalStateException limit){fail("OSPF停止: "+limit.getMessage());return;}
        }
    }
    private List<Peer> peers(Node n,int port){List<Peer> result=transport.peers(n.id,port);if(result.size()>MAX_NEIGHBORS)throw new IllegalStateException("同一ポートのOSPF隣接候補上限");return result;}
    private boolean deliverable(Node source,int out,String remote,int in){return peers(source,out).contains(new Peer(remote,in));}
    private void hello(Node n){
        for(Port p:n.config.ports){if(!p.active())continue;
            Set<String> seen=new HashSet<>();for(var neighbor:n.neighbors.values())if(neighbor.key.localPort==p.number&&now-neighbor.lastSeen<n.config.deadTicks)seen.add(neighbor.routerId);
            for(Peer peer:peers(n,p.number)){
                Node receiver=nodes.get(peer.device);if(receiver==null)continue;
                var config=n.config;Set<String> advertised=Set.copyOf(seen);
                schedule(receiver,1,false,()->{if(nodes.get(n.id)==n&&deliverable(n,p.number,receiver.id,peer.port))receiveHello(receiver,n.id,p,config,peer.port,advertised);});
            }
        }
        schedule(n,n.config.helloTicks,false,()->hello(n));
    }
    private void receiveHello(Node n,String source,Port sent,Configuration sender,int ingress,Set<String> seen){
        Port local=n.config.port(ingress);
        if(local==null||!local.active()||sender.area!=n.config.area||sender.helloTicks!=n.config.helloTicks||sender.deadTicks!=n.config.deadTicks
                ||sent.prefix!=local.prefix||!OspfSettings.sameSubnet(sent.address,local.address,local.prefix)||sent.address.equals(local.address)||sender.routerId.equals(n.config.routerId))return;
        Key key=new Key(ingress,source,sent.number);Neighbor neighbor=n.neighbors.get(key);
        if(neighbor!=null&&(!neighbor.routerId.equals(sender.routerId)||!neighbor.address.equals(sent.address))){n.neighbors.remove(key);originate(n,true);neighbor=null;}
        if(neighbor==null){
            if(n.neighbors.size()>=MAX_NEIGHBORS)throw new IllegalStateException("機器のOSPFネイバー上限");
            neighbor=new Neighbor(key,sender.routerId,sent.address,now);n.neighbors.put(key,neighbor);Neighbor created=neighbor;
            schedule(n,n.config.deadTicks,false,()->expire(n,created));transport.log(n.id,"OSPF INIT: "+sender.routerId+" P"+ingress);
        }
        neighbor.lastSeen=now;
        if(!seen.contains(n.config.routerId)){
            if(neighbor.state!=State.INIT){neighbor.state=State.INIT;originate(n,true);}return;
        }
        if(neighbor.state!=State.FULL){neighbor.state=State.EXCHANGE;sendDatabase(n,neighbor);}
    }
    private void expire(Node n,Neighbor neighbor){
        if(n.neighbors.get(neighbor.key)!=neighbor)return;long remaining=neighbor.lastSeen+n.config.deadTicks-now;
        if(remaining>0){schedule(n,remaining,false,()->expire(n,neighbor));return;}
        n.neighbors.remove(neighbor.key);transport.log(n.id,"OSPF Down: Dead timer "+neighbor.routerId);originate(n,true);
    }
    private void sendDatabase(Node n,Neighbor neighbor){
        Node receiver=nodes.get(neighbor.key.remote);if(receiver==null)return;
        List<Lsa> database=List.copyOf(n.database.values());
        schedule(receiver,1,false,()->{
            if(nodes.get(n.id)!=n||!deliverable(n,neighbor.key.localPort,receiver.id,neighbor.key.remotePort))return;
            var reverse=receiver.neighbors.get(new Key(neighbor.key.remotePort,n.id,neighbor.key.localPort));
            if(reverse==null||reverse.state==State.INIT||!reverse.routerId.equals(n.config.routerId)||receiver.config.area!=n.config.area)return;
            boolean full=reverse.state!=State.FULL;reverse.state=State.FULL;
            for(Lsa lsa:database)install(receiver,lsa,n.id);
            if(full){transport.log(receiver.id,"OSPF FULL: "+n.config.routerId+" / DB同期完了");originate(receiver,true);sendDatabase(receiver,reverse);}
        });
    }
    private void originate(Node n,boolean force){
        List<Edge> edges=new ArrayList<>();List<Prefix> prefixes=new ArrayList<>();
        for(var neighbor:n.neighbors.values()){Port p=n.config.port(neighbor.key.localPort);if(neighbor.state==State.FULL&&p!=null&&p.active())edges.add(new Edge(neighbor.routerId,p.number,neighbor.key.remotePort,p.cost,neighbor.address));}
        for(Port p:n.config.ports)if(p.up&&p.mode!=OspfSettings.Mode.OFF)prefixes.add(new Prefix(OspfSettings.format(OspfSettings.ipv4(p.address)&OspfSettings.mask(p.prefix)),p.prefix,p.cost));
        Lsa previous=n.database.get(n.config.routerId);if(!force&&previous!=null&&previous.edges.equals(edges)&&previous.prefixes.equals(prefixes))return;
        Lsa lsa=new Lsa(n.config.routerId,n.config.area,++sequence,now+MAX_AGE_TICKS,List.copyOf(edges),List.copyOf(prefixes));
        n.database.put(lsa.routerId,lsa);scheduleAge(n,lsa.expires);requestSpf(n);flood(n,lsa,null);
    }
    private void flood(Node n,Lsa lsa,String except){
        for(var neighbor:n.neighbors.values())if(neighbor.state==State.FULL&&!neighbor.key.remote.equals(except)){
            Node receiver=nodes.get(neighbor.key.remote);if(receiver==null)continue;
            schedule(receiver,1,false,()->{
                if(nodes.get(n.id)!=n||!deliverable(n,neighbor.key.localPort,receiver.id,neighbor.key.remotePort))return;
                var reverse=receiver.neighbors.get(new Key(neighbor.key.remotePort,n.id,neighbor.key.localPort));
                if(reverse!=null&&reverse.state==State.FULL)install(receiver,lsa,n.id);
            });
        }
    }
    private void install(Node n,Lsa lsa,String sender){
        if(lsa.area!=n.config.area||lsa.expires<=now||lsa.routerId.equals(n.config.routerId))return;
        Lsa old=n.database.get(lsa.routerId);if(old!=null&&old.sequence>=lsa.sequence)return;
        if(n.database.size()>=MAX_ROUTERS&&old==null)throw new IllegalStateException("LSDB件数上限");
        n.database.put(lsa.routerId,lsa);scheduleAge(n,lsa.expires);requestSpf(n);flood(n,lsa,sender);
    }
    // 最短の有効期限だけを予約。全LSAを毎tick走査せず、期限当日に経路を破棄する。
    private void scheduleAge(Node n,long expires){
        if(expires>=n.ageDue)return;n.ageDue=expires;
        schedule(n,expires-now,false,()->{
            if(n.ageDue!=expires)return;n.ageDue=Long.MAX_VALUE;
            if(n.database.values().removeIf(lsa->lsa.expires<=now))requestSpf(n);
            long next=n.database.values().stream().mapToLong(Lsa::expires).min().orElse(Long.MAX_VALUE);
            if(next!=Long.MAX_VALUE)scheduleAge(n,next);
        });
    }
    private void refresh(Node n){
        if(n.database.values().removeIf(lsa->lsa.expires<=now))requestSpf(n);
        originate(n,true);schedule(n,REFRESH_TICKS,false,()->refresh(n));
    }
    private void requestSpf(Node n){if(!n.spfScheduled){n.spfScheduled=true;schedule(n,1,true,()->spf(n));}}
    private record Backlink(String from,String to,int fromPort,int toPort) {}
    private record Distance(String router,int cost,int firstPort,String gateway) {}
    private void spf(Node n){
        n.spfScheduled=false;String root=n.config.routerId;
        Map<String,Lsa> db=new HashMap<>();for(var lsa:n.database.values())if(lsa.expires>now&&lsa.area==n.config.area)db.put(lsa.routerId,lsa);
        Set<Backlink> backlinks=new HashSet<>();for(var lsa:db.values())for(var e:lsa.edges)backlinks.add(new Backlink(lsa.routerId,e.peer,e.localPort,e.remotePort));
        Comparator<Distance> order=Comparator.comparingInt(Distance::cost).thenComparingInt(Distance::firstPort).thenComparing(Distance::gateway).thenComparing(Distance::router);
        PriorityQueue<Distance> todo=new PriorityQueue<>(order);Map<String,Distance> distances=new HashMap<>();var initial=new Distance(root,0,-1,"");todo.add(initial);distances.put(root,initial);int work=0;
        while(!todo.isEmpty()){
            var d=todo.remove();if(!d.equals(distances.get(d.router)))continue;Lsa lsa=db.get(d.router);if(lsa==null)continue;
            for(var e:lsa.edges){
                if(++work>MAX_SPF_WORK)throw new IllegalStateException("SPF作業量上限");
                if(!db.containsKey(e.peer)||!backlinks.contains(new Backlink(e.peer,d.router,e.remotePort,e.localPort)))continue;
                if(d.router.equals(root)){Port local=n.config.port(e.localPort);if(local==null||!local.active())continue;}
                var next=new Distance(e.peer,d.cost+e.cost,d.router.equals(root)?e.localPort:d.firstPort,d.router.equals(root)?e.nextHop:d.gateway);
                var old=distances.get(e.peer);if(old==null||order.compare(next,old)<0){distances.put(e.peer,next);todo.add(next);}
            }
        }
        Map<String,Route> best=new TreeMap<>();Comparator<Route> preference=Comparator.comparingInt(Route::cost).thenComparingInt(Route::port).thenComparing(Route::gateway).thenComparing(Route::origin);
        for(var d:distances.values())if(!d.router.equals(root)&&d.firstPort>=0){var lsa=db.get(d.router);for(var p:lsa.prefixes){
            if(++work>MAX_SPF_WORK)throw new IllegalStateException("OSPF経路数上限");
            var route=new Route(p.network,p.length,d.gateway,d.firstPort,d.cost+p.cost,d.router,n.config.area);String key=p.network+"/"+p.length;
            var old=best.get(key);if(old==null||preference.compare(route,old)<0)best.put(key,route);
        }}
        List<Route> result=List.copyOf(best.values());if(!n.routes.equals(result)){n.routes=result;routeChanges.add(n.id);transport.log(n.id,"OSPF SPF: "+result.size()+"経路に更新");}
    }
}
