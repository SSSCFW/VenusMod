package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;
import dev.ssscfw.venusmod.infrastructure.network.core.NetworkPolicy.Prefix;

/**
 * IPv4 BGPのゲーム用状態機械。受信した広告だけを選択し、全世界の最短経路からBGP経路を合成しない。
 * UPDATEは最大128経路の置換スナップショットで、差分の欠落分をwithdrawとして扱う。
 */
public final class BgpEngine {
    public static final int MAX_ROUTERS=64, MAX_SESSIONS=256, MAX_ROUTES=128, MAX_QUEUE=1024, MAX_EVENTS=64;
    public enum State { ACTIVE, OPEN_SENT, OPEN_CONFIRM, ESTABLISHED }
    public record Key(String node,String peer) implements Comparable<Key>{
        public int compareTo(Key other){int c=node.compareTo(other.node);return c!=0?c:peer.compareTo(other.peer);}
    }
    public record Hop(int port,String gateway,int cost,int prefix,int distance){}
    public record Channel(String node,String source,String destination){}
    public record Configuration(long asn,String routerId,int keepaliveTicks,int holdTicks,
                                List<BgpSettings.Peer> peers,Set<Prefix> networks){
        public Configuration{peers=List.copyOf(peers);networks=Set.copyOf(networks);BgpSettings.checkAs(asn);routerId=BgpSettings.unicast(routerId);
            if(peers.size()>BgpSettings.MAX_PEERS||networks.size()>BgpSettings.MAX_NETWORKS||keepaliveTicks<20||keepaliveTicks>600||holdTicks<keepaliveTicks*3||holdTicks>3600)
                throw new IllegalArgumentException("BGP設定上限です");}
    }
    public record Advertisement(Prefix prefix,String nextHop,List<Long> path,long localPref,long med){
        public Advertisement{Objects.requireNonNull(prefix);nextHop=BgpSettings.unicast(nextHop);path=List.copyOf(path);
            if(path.size()>BgpSettings.MAX_PATH||localPref<0||localPref>0xffffffffL||med<0||med>0xffffffffL)throw new IllegalArgumentException("BGP属性上限です");
            for(long as:path)BgpSettings.checkAs(as);BgpSettings.prefix(prefix.toString());}
    }
    public record Route(Prefix prefix,String nextHop,List<Long> path,long localPref,long med,boolean local,boolean external,
                        String peer,String routerId,long neighboringAs,Hop hop){public Route{path=List.copyOf(path);}}
    public record Neighbor(String address,long asn,State state,String reason,long holdRemaining,long sent,long received,int routes){}
    public interface Transport {
        Channel channel(String node,String peer);
        Hop resolve(String node,String nextHop);
        boolean originates(String node,Prefix prefix);
        boolean usable(String node,Route route);
        void log(String node,String message);
    }
    private enum Kind { OPEN, KEEPALIVE, UPDATE, CEASE }
    private record Message(Kind kind,long senderEpoch,long receiverEpoch,long asn,String rid,int hold,List<Advertisement> routes){}
    private record Envelope(long due,Key recipient,String targetIp,String senderNode,String senderIp,Message message){}
    private record Due(long tick,Key key) implements Comparable<Due>{public int compareTo(Due d){int c=Long.compare(tick,d.tick);return c!=0?c:key.compareTo(d.key);}}
    private static final class Session {
        final Key key;final BgpSettings.Peer peer;final Configuration config;final long epoch;
        State state=State.ACTIVE;long peerEpoch,deadline,keepAt,updateAt,sent,received,retryAt;
        int hold;String reason="接続待ち",remoteRid="",remoteNode="",source="";
        Map<Prefix,Advertisement> incoming=Map.of();List<Advertisement> exported=List.of();boolean exportDirty=true;
        Due due;
        Session(Key k,BgpSettings.Peer p,Configuration c,long epoch,long now){key=k;peer=p;config=c;this.epoch=epoch;retryAt=now+1;}
    }
    private final Transport transport;
    private final Map<String,Configuration> configs=new LinkedHashMap<>();
    private final Map<Key,Session> sessions=new TreeMap<>();
    private final Map<String,Map<Prefix,Route>> selected=new HashMap<>();
    private final TreeSet<Due> deadlines=new TreeSet<>();
    private final ArrayDeque<Envelope> queue=new ArrayDeque<>();
    private final LinkedHashSet<String> recalculate=new LinkedHashSet<>(),routeChanges=new LinkedHashSet<>();
    private long clock,epoch;
    private String error="";
    public BgpEngine(Transport t){transport=Objects.requireNonNull(t);}
    public String error(){return error;}
    public long clock(){return clock;}
    public int timers(){return deadlines.size();}
    public int queued(){return queue.size();}
    public Set<String> drainChanges(){var result=Set.copyOf(routeChanges);routeChanges.clear();return result;}
    public List<Route> routes(String node){return List.copyOf(selected.getOrDefault(node,Map.of()).values());}
    public List<Neighbor> neighbors(String node){return sessions.values().stream().filter(s->s.key.node.equals(node)).map(s->new Neighbor(s.peer.address(),s.peer.remoteAs(),s.state,s.reason,Math.max(0,s.deadline-clock),s.sent,s.received,s.incoming.size())).toList();}
    public List<String> candidates(String node){
        var lines=new ArrayList<String>();for(Session s:sessions.values())if(s.key.node.equals(node))for(Advertisement a:s.incoming.values()){
            var route=selected.getOrDefault(node,Map.of()).get(a.prefix);boolean best=route!=null&&route.peer.equals(s.peer.address());
            lines.add((best?"*> ":" * ")+a.prefix+" next-hop="+a.nextHop+" AS_PATH="+a.path+" from="+s.peer.address()+(transport.resolve(node,a.nextHop)==null?" [NEXT_HOP到達不能]":""));}
        return List.copyOf(lines);
    }
    public void stop(String reason){
        error=reason;for(String id:selected.keySet())routeChanges.add(id);
        sessions.clear();deadlines.clear();queue.clear();selected.clear();recalculate.clear();
    }
    public void synchronize(Map<String,Configuration> desired){
        int count=desired.values().stream().mapToInt(c->c.peers.size()).sum();
        if(desired.size()>MAX_ROUTERS||count>MAX_SESSIONS){stop("BGP有効機器64台/ピア256接続の上限です");return;}
        error="";
        Map<Key,BgpSettings.Peer> wanted=new TreeMap<>();
        for(var e:desired.entrySet())for(var peer:e.getValue().peers)if(wanted.put(new Key(e.getKey(),peer.address()),peer)!=null)throw new IllegalArgumentException("BGPピア重複");
        for(var it=sessions.entrySet().iterator();it.hasNext();){var e=it.next();var s=e.getValue();var c=desired.get(s.key.node);
            if(c==null||!s.peer.equals(wanted.get(e.getKey()))||s.config.asn!=c.asn||!s.config.routerId.equals(c.routerId)||s.config.holdTicks!=c.holdTicks||s.config.keepaliveTicks!=c.keepaliveTicks){
                if(s.due!=null)deadlines.remove(s.due);recalculate.add(s.key.node);it.remove();}}
        for(String id:new ArrayList<>(selected.keySet()))if(!desired.containsKey(id)){selected.remove(id);routeChanges.add(id);}
        configs.clear();configs.putAll(desired);
        for(var e:wanted.entrySet())if(!sessions.containsKey(e.getKey())){var s=new Session(e.getKey(),e.getValue(),desired.get(e.getKey().node),++epoch,clock);sessions.put(e.getKey(),s);schedule(s);}
        recalculate.addAll(desired.keySet());
        queue.removeIf(e->!sessions.containsKey(e.recipient));
    }
    public void changed(Set<String> nodes){
        for(String id:nodes)if(configs.containsKey(id))recalculate.add(id);
        for(Session s:sessions.values())if(nodes.contains(s.key.node)){s.exportDirty=true;schedule(s);}
    }
    public void shutdown(String id){for(Session s:new ArrayList<>(sessions.values()))if(s.key.node.equals(id)){send(s,Kind.CEASE,List.of());reset(s,"管理者による停止");}}
    public void advance(long ticks){
        if(ticks<=0||!error.isEmpty())return;clock=Math.addExact(clock,ticks);
        for(int i=0;i<MAX_EVENTS&&!queue.isEmpty()&&queue.peekFirst().due<=clock;i++)receive(queue.removeFirst());
        for(int i=0;i<2&&!recalculate.isEmpty();i++){String id=recalculate.iterator().next();recalculate.remove(id);calculate(id);if(!error.isEmpty())return;}
        for(int i=0;i<MAX_EVENTS&&!deadlines.isEmpty()&&deadlines.first().tick<=clock;i++){
            Due due=deadlines.pollFirst();Session s=sessions.get(due.key);if(s==null||!due.equals(s.due))continue;s.due=null;
            if(s.state!=State.ACTIVE&&s.deadline<=clock)reset(s,"Hold Timer満了");
            else if(s.state==State.ACTIVE||s.state==State.OPEN_SENT){
                if(clock>=s.retryAt){if(send(s,Kind.OPEN,List.of())){s.state=State.OPEN_SENT;s.deadline=clock+s.config.holdTicks;s.reason="OPEN送信済み";}s.retryAt=clock+s.config.keepaliveTicks;}
            }else{
                if(clock>=s.keepAt){send(s,Kind.KEEPALIVE,List.of());s.keepAt=clock+keepInterval(s);}
                if(s.state==State.ESTABLISHED&&s.exportDirty&&clock>=s.updateAt)export(s);
            }
            if(!error.isEmpty())return;schedule(s);
        }
    }
    private int keepInterval(Session s){return Math.max(1,Math.min(s.config.keepaliveTicks,s.hold/3));}
    private void schedule(Session s){
        if(s.due!=null)deadlines.remove(s.due);
        long at=s.state==State.ACTIVE?s.retryAt:s.state==State.OPEN_SENT?Math.min(s.deadline,s.retryAt):Math.min(s.deadline,s.keepAt);
        if(s.state==State.ESTABLISHED&&s.exportDirty)at=Math.min(at,s.updateAt);
        s.due=new Due(Math.max(clock+1,at),s.key);deadlines.add(s.due);
    }
    private void reset(Session s,String reason){
        boolean live=!s.incoming.isEmpty()||s.state==State.ESTABLISHED;
        s.state=State.ACTIVE;s.reason=reason;s.peerEpoch=0;s.incoming=Map.of();s.exported=List.of();s.exportDirty=true;s.retryAt=clock+s.config.keepaliveTicks;
        if(live){recalculate.add(s.key.node);transport.log(s.key.node,"BGP "+s.peer.address()+": "+reason);}schedule(s);
    }
    private boolean send(Session s,Kind kind,List<Advertisement> routes){
        Channel channel=transport.channel(s.key.node,s.peer.address());
        if(channel==null){if(s.state==State.ACTIVE)s.reason="下位経路/ARP/VLAN/ACL/対向設定を確認";return false;}
        if(queue.size()>=MAX_QUEUE){stop("BGP配送待ち行列の上限です");return false;}
        s.source=channel.source;
        Message msg=new Message(kind,s.epoch,s.peerEpoch,s.config.asn,s.config.routerId,s.config.holdTicks,List.copyOf(routes));
        queue.addLast(new Envelope(clock+1,new Key(channel.node,channel.source),channel.destination,s.key.node,channel.source,msg));s.sent++;return true;
    }
    private void receive(Envelope envelope){
        Session s=sessions.get(envelope.recipient);if(s==null)return;
        Channel back=transport.channel(s.key.node,s.peer.address());
        if(back==null||!back.node.equals(envelope.senderNode)||!back.source.equals(envelope.targetIp))return;
        Message m=envelope.message;
        if(m.kind==Kind.OPEN){
            if(m.asn!=s.peer.remoteAs()||m.rid.equals(s.config.routerId)||m.hold<60||m.hold>3600){reset(s,"OPEN拒否: AS番号/Router ID/Hold不一致");return;}
            if(s.peerEpoch>m.senderEpoch&&s.remoteNode.equals(envelope.senderNode))return;
            boolean newRemote=s.peerEpoch!=m.senderEpoch||!s.remoteNode.equals(envelope.senderNode);
            if(s.state==State.ESTABLISHED&&!newRemote)return;
            if(s.state==State.ESTABLISHED)reset(s,"対向セッション再起動");
            if(s.state==State.ACTIVE)send(s,Kind.OPEN,List.of());
            s.peerEpoch=m.senderEpoch;s.remoteRid=m.rid;s.remoteNode=envelope.senderNode;s.hold=Math.min(s.config.holdTicks,m.hold);
            s.state=State.OPEN_CONFIRM;s.deadline=clock+s.hold;s.keepAt=clock+keepInterval(s);s.reason="KEEPALIVE待ち";s.received++;
            send(s,Kind.KEEPALIVE,List.of());schedule(s);return;
        }
        if(m.senderEpoch!=s.peerEpoch||m.receiverEpoch!=s.epoch||!envelope.senderNode.equals(s.remoteNode))return;
        if(m.kind==Kind.CEASE){reset(s,"対向の停止通知");return;}
        if(s.state!=State.OPEN_CONFIRM&&s.state!=State.ESTABLISHED)return;
        s.received++;s.deadline=clock+s.hold;
        if(m.kind==Kind.KEEPALIVE&&s.state==State.OPEN_CONFIRM){
            s.state=State.ESTABLISHED;s.reason="Established";s.exportDirty=true;s.updateAt=clock+1;
            transport.log(s.key.node,"BGP "+s.peer.address()+": Established");recalculate.add(s.key.node);
        }
        if(m.kind==Kind.UPDATE&&s.state==State.ESTABLISHED){
            if(m.routes.size()>MAX_ROUTES){reset(s,"受信prefix上限超過");return;}
            Map<Prefix,Advertisement> accepted=new LinkedHashMap<>();boolean external=s.config.asn!=s.peer.remoteAs();
            for(Advertisement a:m.routes){
                if(a.path.contains(s.config.asn)||!s.peer.imports().permits(a.prefix,external))continue;
                if(external&&(a.path.isEmpty()||a.path.getFirst()!=s.peer.remoteAs()))continue;
                if(accepted.putIfAbsent(a.prefix,a)!=null){reset(s,"UPDATEのprefix重複");return;}
            }
            s.incoming=Map.copyOf(accepted);recalculate.add(s.key.node);
        }
        schedule(s);
    }
    private void calculate(String id){
        Configuration config=configs.get(id);if(config==null)return;
        Map<Prefix,List<Route>> candidates=new TreeMap<>(Comparator.comparingLong(Prefix::network).thenComparingInt(Prefix::length));
        for(Prefix prefix:config.networks)if(transport.originates(id,prefix))
            candidates.computeIfAbsent(prefix,p->new ArrayList<>()).add(new Route(prefix,"",List.of(),100,0,true,false,"",config.routerId,config.asn,null));
        for(Session s:sessions.values())if(s.key.node.equals(id)&&s.state==State.ESTABLISHED){boolean external=config.asn!=s.peer.remoteAs();
            for(Advertisement a:s.incoming.values()){
                Hop hop=transport.resolve(id,a.nextHop);if(hop==null)continue;
                long preference=s.peer.localPref()>=0?s.peer.localPref():external?100:a.localPref;
                var route=new Route(a.prefix,a.nextHop,a.path,preference,a.med,false,external,s.peer.address(),s.remoteRid,a.path.isEmpty()?config.asn:a.path.getFirst(),hop);
                candidates.computeIfAbsent(a.prefix,p->new ArrayList<>()).add(route);
            }
        }
        if(candidates.size()>MAX_ROUTES){stop("BGP機器ごとの選択prefix上限です (128)");return;}
        Map<Prefix,Route> best=new LinkedHashMap<>();
        for(var e:candidates.entrySet()){
            var choices=new ArrayList<>(e.getValue());
            long max=choices.stream().mapToLong(Route::localPref).max().orElse(0);choices.removeIf(r->r.localPref!=max);
            if(choices.stream().anyMatch(Route::local))choices.removeIf(r->!r.local);
            int shortest=choices.stream().mapToInt(r->r.path.size()).min().orElse(0);choices.removeIf(r->r.path.size()!=shortest);
            // MEDは同じ隣接ASの候補だけで比較する。比較器の非推移性を避けて先にグループごとに絞る。
            Map<Long,Long> med=new HashMap<>();for(Route r:choices)med.merge(r.neighboringAs,r.med,Math::min);
            choices.removeIf(r->r.med!=med.get(r.neighboringAs));
            choices.sort(Comparator.<Route,Boolean>comparing(r->!r.external).thenComparingInt(r->r.hop==null?0:r.hop.cost)
                    .thenComparingLong(r->Ipv4.parse(r.routerId)).thenComparingLong(r->r.peer.isBlank()?0:Ipv4.parse(r.peer)));
            best.put(e.getKey(),choices.getFirst());
        }
        Map<Prefix,Route> previous=selected.put(id,Map.copyOf(best));
        if(!best.equals(previous)){routeChanges.add(id);for(Session s:sessions.values())if(s.key.node.equals(id)){s.exportDirty=true;schedule(s);}}
    }
    private void export(Session s){
        boolean external=s.config.asn!=s.peer.remoteAs();List<Advertisement> outgoing=new ArrayList<>();
        for(Route route:routes(s.key.node)){
            if(route.peer.equals(s.peer.address())||!s.peer.exports().permits(route.prefix,external))continue;
            if(!route.local&&!route.external&&!external)continue; // iBGP→iBGP再広告禁止 (RR未対応)
            if(!transport.usable(s.key.node,route))continue;
            List<Long> path=new ArrayList<>(route.path);
            if(external){if(path.contains(s.peer.remoteAs()))continue;for(int i=0;i<=s.peer.prepend();i++)path.addFirst(s.config.asn);}
            if(path.size()>BgpSettings.MAX_PATH){s.reason="AS_PATH長上限の経路は広告しません";continue;}
            String nextHop=external||route.local||s.peer.nextHopSelf()?s.source:route.nextHop;
            long med=external?(s.peer.med()>=0?s.peer.med():0):route.med;
            outgoing.add(new Advertisement(route.prefix,nextHop,path,external?100:route.localPref,med));
        }
        outgoing.sort(Comparator.comparingLong((Advertisement a)->a.prefix.network()).thenComparingInt(a->a.prefix.length()));
        if(outgoing.equals(s.exported)){s.exportDirty=false;return;}
        if(send(s,Kind.UPDATE,outgoing)){s.exported=List.copyOf(outgoing);s.exportDirty=false;}
        s.updateAt=clock+20;
    }
}
