package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BiPredicate;

/**
 * サーバー専用の仮想Ethernet/IPv4エンジン。外部通信APIは使わない。
 * ケーブル列はLinkへ圧縮され、探索・ログ・同時転送を上限付きで処理する。
 */
public final class NetworkSimulator {
    public static final int MAX_NODES=2048, MAX_LINKS=8192, MAX_STEPS=8192, MAX_FLOWS=128;
    public static final int MAX_PATH_NODES=64, MAX_VLANS=128, MAX_FOREST_WORK=262144;

    /** 1要求全体で共有する予算。途中までの探索結果を成功扱いしない。 */
    private static final class SearchBudget {
        private int remaining=MAX_STEPS;
        void spend() { if (remaining--<=0) throw new SearchLimit("通信探索の処理上限です"); }
        void path(int size) { if (size>MAX_PATH_NODES) throw new SearchLimit("通信経路の長さ上限です"); }
    }
    private static final class SearchLimit extends RuntimeException {
        SearchLimit(String message) { super(message,null,false,false); }
    }
    public record Link(String id,String a,int ap,String b,int bp,CableGrade grade,int length,boolean wan){
        public Link {Objects.requireNonNull(id);Objects.requireNonNull(a);Objects.requireNonNull(b);Objects.requireNonNull(grade);
            if(ap<0||ap>=6||bp<0||bp>=6||length<0||a.equals(b))throw new IllegalArgumentException("不正なリンク");}
        String peer(String id){return a.equals(id)?b:a;}
        int peerPort(String id){return a.equals(id)?bp:ap;}
        int port(String id){return a.equals(id)?ap:bp;}
    }
    public record Result(boolean success,String message,long bps,double milliseconds,List<String> nodes,List<String> links,String destination,
                         int servicePort,List<Long> natBindings){
        public Result(boolean success,String message,long bps,double milliseconds,List<String> nodes,List<String> links,String destination){
            this(success,message,bps,milliseconds,nodes,links,destination,0,List.of());
        }
        public Result{nodes=List.copyOf(nodes);links=List.copyOf(links);natBindings=List.copyOf(natBindings);}
        public static Result fail(String reason){return new Result(false,reason,0,0,List.of(),List.of(),"");}
    }
    public static final class Flow {
        public final long id,totalBytes;
        public final String source,destination;
        public final List<String> links,nodes;
        public final long capacity;
        public final List<Long> natBindings;
        public double remaining,delayTicks;
        Flow(long id,long bytes,String source,Result path){this.id=id;this.totalBytes=bytes;this.source=source;this.destination=path.destination;
            this.links=path.links;this.nodes=path.nodes;this.capacity=path.bps;this.natBindings=path.natBindings;remaining=bytes;delayTicks=path.milliseconds/50;}
        public double progress(){return Math.clamp(1-remaining/totalBytes,0.0,1.0);}
    }
    private final Map<String,NetworkProfile> nodes=new LinkedHashMap<>();
    private final Map<String,Link> links=new LinkedHashMap<>();
    private final Map<String,Link[]> ports=new HashMap<>();
    private final Map<String,String> macAddresses=new HashMap<>();
    private final Map<String,Map<String,Integer>> mac=new HashMap<>();
    private final Map<String,Map<String,String>> arp=new HashMap<>();
    private final Map<String,ArrayDeque<String>> logs=new HashMap<>();
    private final List<Flow> flows=new ArrayList<>();
    private Set<String> blocked=Set.of();
    private boolean forestDirty=true;
    private String forestLimit="";
    private long nextFlow=1;
    private final OspfSimulation ospfRuntime=new OspfSimulation(this);
    private final BgpSimulation bgpRuntime=new BgpSimulation(this);
    public BgpEngine bgp(){return bgpRuntime.engine();}
    private final NatTable natTable=new NatTable();
    private final DnsResolver dnsResolver=new DnsResolver(this);
    private final SyslogRuntime syslogRuntime=new SyslogRuntime(this);
    private int suppressSyslog,suppressProbeLogs;
    private final MonitorRuntime monitorRuntime=new MonitorRuntime(this);
    public MonitorRuntime monitor(){return monitorRuntime;}
    public SyslogRuntime syslog(){return syslogRuntime;}
    public DnsResolver dnsResolver(){return dnsResolver;}
    public NatTable nat(){return natTable;}
    public OspfEngine ospf(){return ospfRuntime.engine();}
    private final StpSimulation stpRuntime=new StpSimulation(this);
    public StpEngine stp(){return stpRuntime.engine();}
    public List<StpEngine.Key> stpKeys(String id){return stpRuntime.keys(id);}
    public String stpIssue(String id,int port){return stpRuntime.issue(id,port);}
    public void clearStpGuard(String id,int port){stpRuntime.clearGuard(id,port);}
    Link physicalLink(String id,int port){var row=ports.get(id);return row==null?null:row[port];}
    private final VrrpSimulation vrrpRuntime=new VrrpSimulation(this);
    public VrrpEngine vrrp(){return vrrpRuntime.engine();}
    public void shutdownVrrp(String id){vrrpRuntime.shutdown(id);}
    public boolean vrrpMaster(String id){return vrrpRuntime.master(id);}
    private final Set<String> changedProfiles=new LinkedHashSet<>();
    public static final int WEB_PORT=80, TRANSFER_PORT=5000, CONTROL_PORT=25570;
    /** DHCPサーバー側の予約変更もBlockEntityの保存対象にする。 */
    public Set<String> drainChangedProfiles(){var changed=Set.copyOf(changedProfiles);changedProfiles.clear();return changed;}
    public void put(String id,NetworkProfile profile){
        Objects.requireNonNull(id);Objects.requireNonNull(profile);
        if(!nodes.containsKey(id)&&nodes.size()>=MAX_NODES)throw new IllegalStateException("機器数上限");
        nodes.put(id,profile);dnsResolver.synchronizeClient(id,profile);macAddresses.computeIfAbsent(id,NetworkSimulator::macAddress);
        ports.computeIfAbsent(id,k->new Link[6]);monitorRuntime.synchronize(id);invalidate(id);
    }
    /** 権威DNSの編集は経路変更ではない。既存接続と遠隔キャッシュを維持する。 */
    void applyDnsSettings(String id,NetworkProfile validated){
        var current=nodes.get(id);if(current==null)throw new IllegalArgumentException("機器が未登録です");
        current.dnsRecords.clear();current.dnsRecords.putAll(validated.dnsRecords);
        current.dnsSettings.copyFrom(validated.dnsSettings);changedProfiles.add(id);
    }
    void applySyslogSettings(String id,NetworkProfile validated){
        var current=nodes.get(id);if(current==null)throw new IllegalArgumentException("機器が未登録です");
        current.syslog.copyFrom(validated.syslog);syslogRuntime.configurationChanged(id);changedProfiles.add(id);
    }
    void applyMonitorSettings(String id,NetworkProfile validated){
        var current=nodes.get(id);if(current==null)throw new IllegalArgumentException("機器が未登録です");
        current.monitor.copyFrom(validated.monitor);monitorRuntime.synchronize(id);changedProfiles.add(id);
    }
    /** 安全方針の変更はルートや無関係な転送を失効させない。 */
    void applyControlSafety(String id,NetworkProfile validated){
        var current=nodes.get(id);if(current==null)throw new IllegalArgumentException("機器が未登録です");
        current.controlSafety.copyFrom(validated.controlSafety);changedProfiles.add(id);
    }
    /** 定期プローブは平常/失敗の操作ログを出さず、状態変化だけ監視側で通知する。 */
    MonitorRuntime.Probe probeMonitor(String source,MonitorTarget target){
        suppressProbeLogs++;
        try{
            var type=target.kind();
            return new MonitorRuntime.Probe(exchange(source,replyAddress(source,target.address()),target.address(),type.protocol(),
                    type==MonitorTarget.Kind.ICMP?0:49152,target.port(),new SearchBudget(),type==MonitorTarget.Kind.ICMP?-1:type.servicePort(),type::unavailable),false);
        }
        catch(SearchLimit limit){return new MonitorRuntime.Probe(Result.fail(limit.getMessage()),true);}
        catch(IllegalArgumentException invalid){return new MonitorRuntime.Probe(Result.fail(invalid.getMessage()),false);}
        finally{suppressProbeLogs--;}
    }
    public void remove(String id){
        if(!nodes.containsKey(id))return;
        // 分割前の成分を確保。切断の反対側も再確立させるが、別物理網は保護する。
        Set<String> affected=component(Set.of(id));
        for(Link link:new ArrayList<>(links.values()))if(link.a.equals(id)||link.b.equals(id))detach(link);
        nodes.remove(id);ports.remove(id);macAddresses.remove(id);mac.remove(id);arp.remove(id);logs.remove(id);changedProfiles.remove(id);dnsResolver.removeDevice(id);syslogRuntime.remove(id);monitorRuntime.remove(id);
        invalidateComponents(affected,"機器の削除で再確立");
    }
    public void link(Link link){
        if(links.size()>=MAX_LINKS)throw new IllegalStateException("リンク数上限");
        if(!nodes.containsKey(link.a)||!nodes.containsKey(link.b))throw new IllegalArgumentException("端点なし");
        if(ports.get(link.a)[link.ap]!=null||ports.get(link.b)[link.bp]!=null)throw new IllegalArgumentException("同じポートへの多重接続");
        if(links.containsKey(link.id))throw new IllegalArgumentException("リンクID重複");
        links.put(link.id,link);ports.get(link.a)[link.ap]=link;ports.get(link.b)[link.bp]=link;
        invalidateComponents(component(Set.of(link.a,link.b)),"配線の接続で再確立");
    }
    private void detach(Link link){links.remove(link.id);ports.get(link.a)[link.ap]=null;ports.get(link.b)[link.bp]=null;}
    public void unlink(String id){
        Link link=links.get(id);if(link==null)return;
        Set<String> affected=component(Set.of(link.a,link.b));detach(link);
        invalidateComponents(affected,"配線の切断で再確立");
    }
    /** 最大2048機器/6ポートの物理成分。VLAN、故障、管理Downも保守的に同じ成分とする。 */
    private Set<String> component(Set<String> seeds){
        var reached=new LinkedHashSet<String>();var todo=new ArrayDeque<String>();
        for(String seed:seeds)if(reached.add(seed))todo.add(seed);
        while(!todo.isEmpty()){String id=todo.removeFirst();var row=ports.get(id);if(row==null)continue;
            for(Link link:row)if(link!=null){String peer=link.peer(id);if(reached.add(peer))todo.addLast(peer);}
        }
        return reached;
    }
    public void invalidate(String id){invalidateComponents(component(Set.of(id)),"機器設定の変更で再確立");}
    /** 全体操作/低レベル試験用。通常のGUI・配線操作にはID付きの局所失効を使う。 */
    public void invalidate(){invalidateComponents(Set.copyOf(nodes.keySet()),"全体設定の変更で再確立");}
    private void invalidateComponents(Set<String> affected,String reason){
        if(affected.isEmpty())return;
        forestDirty=true;stpRuntime.changed(affected);ospfRuntime.changed(affected);vrrpRuntime.changed(affected);bgpRuntime.changed(affected);
        invalidateData(affected,reason);
    }
    private void invalidateData(Set<String> affected,String reason){
        for(String id:affected){mac.remove(id);arp.remove(id);}natTable.clearRouters(affected);
        flows.removeIf(flow->{boolean hit=flow.nodes.stream().anyMatch(affected::contains);
            if(hit)log(flow.source,"転送 #"+flow.id+": "+reason);return hit;});
    }
    public Map<String,NetworkProfile> nodes(){return Collections.unmodifiableMap(nodes);}
    public Collection<Link> links(){return Collections.unmodifiableCollection(links.values());}
    /** ゴーグル表示など読み取り専用で現在の物理ポート接続を取得する。 */
    public Link linkAt(String deviceId,int port){
        if(port<0||port>=NetworkProfile.PORT_COUNT)return null;
        Link[] row=ports.get(deviceId);
        return row==null?null:row[port];
    }
    public List<Flow> flows(){return List.copyOf(flows);}
    public Map<String,Integer> macTable(String id){return Map.copyOf(mac.getOrDefault(id,Map.of()));}
    public Map<String,String> arpTable(String id){return Map.copyOf(arp.getOrDefault(id,Map.of()));}
    public List<String> logs(String id){return List.copyOf(logs.getOrDefault(id,new ArrayDeque<>()));}
    public void log(String id,String text){log(id,6,text);}
    public void log(String id,int severity,String text){
        if(suppressProbeLogs>0)return;
        if(severity<0||severity>7)throw new IllegalArgumentException("severityは0〜7です");
        String safe=SyslogRuntime.text(text,200);var q=logs.computeIfAbsent(id,k->new ArrayDeque<>());while(q.size()>=64)q.removeFirst();q.addLast(safe);
        if(suppressSyslog==0)syslogRuntime.offer(id,severity,safe);
    }
    /** UDP片方向配送。失敗ログを自分の配送キューへ戻さず、返信も要求しない。 */
    SyslogRuntime.Delivery deliverSyslog(String source,String target,int port){
        suppressSyslog++;
        try(var transaction=natTable.begin()){
            String local=replyAddress(source,target);if(local.isBlank()||!owns(source,local))return SyslogRuntime.Delivery.fail("送信元IPなし/停止");
            var packet=NetworkPolicy.Packet.of("udp",local,target,49154,port);transaction.localOrigin(source,packet);
            var result=walk(source,new SearchBudget(),packet,transaction);
            if(!result.result.success())return SyslogRuntime.Delivery.fail(result.result.message());
            if(result.packet.destinationPort()!=SyslogSettings.PORT)return SyslogRuntime.Delivery.fail("Syslog転送先ポートは514である必要があります");
            var receiver=nodes.get(result.result.destination());
            if(receiver==null||receiver.failed||receiver.kind!=DeviceKind.SERVER||!receiver.syslog.server)return SyslogRuntime.Delivery.fail("Syslog受信サービス停止/非対応");
            if(!NetworkPolicy.Prefix.parse(receiver.syslog.allow).contains(result.packet.source()))return SyslogRuntime.Delivery.fail("Syslog受信元フィルターで拒否");
            transaction.commit();return new SyslogRuntime.Delivery(true,result.result.destination(),Ipv4.format(result.packet.source()),"");
        }catch(SearchLimit|IllegalArgumentException failure){return SyslogRuntime.Delivery.fail(failure.getMessage());}
        finally{suppressSyslog--;}
    }
    public long linkCapacity(Link l){
        NetworkProfile a=nodes.get(l.a),b=nodes.get(l.b);
        if(a==null||b==null||a.failed||b.failed||l.length>l.grade.maxLength()||!a.accepts(l.ap,l.grade)||!b.accepts(l.bp,l.grade))return 0;
        var ap=a.ports[l.ap];var bp=b.ports[l.bp];
        long physical=Math.min(l.grade.bps(),Math.min(
                NetworkProfile.SPEEDS[Math.clamp(ap.moduleTier,0,4)],
                NetworkProfile.SPEEDS[Math.clamp(bp.moduleTier,0,4)]));
        // 固定速度は帯域シェーパーではない。両端が一致し、媒体/装着品が対応する必要がある。
        if(ap.speedLimitBps>physical || bp.speedLimitBps>physical
                || ap.speedLimitBps>0&&bp.speedLimitBps>0&&ap.speedLimitBps!=bp.speedLimitBps)return 0;
        long limit=ap.speedLimitBps>0?ap.speedLimitBps:bp.speedLimitBps>0?bp.speedLimitBps:physical;
        if(l.wan)limit=Math.min(limit,Math.min(NetworkProfile.WAN_SPEEDS[Math.clamp(a.wanTier,0,5)],NetworkProfile.WAN_SPEEDS[Math.clamp(b.wanTier,0,5)]));
        return limit;
    }
    public String portStatus(String node,int port){Link link=ports.getOrDefault(node,new Link[6])[port];if(link==null)return "未接続";long bps=linkCapacity(link);return bps==0?"Link Down (媒体/距離/固定速度/故障/無効)":CableGrade.speed(bps)+" → "+nodes.get(link.peer(node)).name;}
    static int receivedVlan(NetworkProfile.Port out,int vlan,NetworkProfile.Port in){
        int wire=out.trunk&&vlan!=out.vlan?vlan:0;
        if(wire!=0&&!in.trunk)return -1;
        return wire==0?in.vlan:wire;
    }
    private static String domain(String id,int vlan){return id+"@"+vlan;}
    private static String treeKey(Link link,int av,int bv){return link.id+"@"+av+":"+bv;}
    private static String root(Map<String,String> parent,String a){String p=parent.computeIfAbsent(a,k->k);while(!p.equals(parent.get(p)))p=parent.get(p);String r=p;p=a;while(!p.equals(r)){String next=parent.get(p);parent.put(p,r);p=next;}return r;}
    /** 簡略STP: 各VLANの決定的な木を構成。BPDU/タイマーの完全再現ではない。 */
    private void rebuildForest(){
        if(!forestDirty)return;
        forestLimit="";
        Set<Integer> vlans=new TreeSet<>();vlans.add(1);for(var profile:nodes.values())for(var p:profile.ports)vlans.add(p.vlan);
        if(vlans.size()>MAX_VLANS){forestLimit="VLAN種類数の上限です (128)";blocked=Set.of();forestDirty=false;return;}
        Map<String,String> parent=new HashMap<>();Set<String> result=new HashSet<>();
        List<Link> sorted=new ArrayList<>(links.values());sorted.sort(Comparator.comparing(Link::id));
        int work=0;
        for(Link link:sorted){NetworkProfile a=nodes.get(link.a),b=nodes.get(link.b);
            if(!a.kind.bridge()||!b.kind.bridge()||a.spanning.classic||b.spanning.classic||linkCapacity(link)==0)continue;
            var ap=a.ports[link.ap];var bp=b.ports[link.bp];Set<Integer> candidates=new LinkedHashSet<>();candidates.add(ap.vlan);if(ap.trunk&&bp.trunk)candidates.addAll(vlans);
            for(int av:candidates){
                if(++work>MAX_FOREST_WORK){forestLimit="ループ保護の計算量上限です";blocked=Set.of();forestDirty=false;return;}
                int bv=receivedVlan(ap,av,bp);if(bv<0||receivedVlan(bp,bv,ap)!=av)continue;
                String ar=root(parent,domain(link.a,av)),br=root(parent,domain(link.b,bv));
                if(ar.equals(br)){if(a.stp||b.stp)result.add(treeKey(link,av,bv));}
                else parent.put(br,ar);
            }
        }
        blocked=Set.copyOf(result);forestDirty=false;
    }
    public Set<String> blockedLinks(){rebuildForest();return blocked;}
    private boolean blocked(Link link,String from,int vlan,int peerVlan){rebuildForest();return blocked.contains(treeKey(link,link.a.equals(from)?vlan:peerVlan,link.a.equals(from)?peerVlan:vlan));}
    private record Step(String id,int ingress,int vlan,long bps,double delay,List<String> path,List<String> wires){}
    private void requireForest(){rebuildForest();if(!forestLimit.isEmpty())throw new SearchLimit(forestLimit);}
    private List<Step> segment(String source,int port,BiPredicate<String,Integer> predicate,SearchBudget budget){
        requireForest();
        var start=nodes.get(source);if(start==null||start.failed||!start.ports[port].enabled)return List.of();
        var queue=new ArrayDeque<Step>();queue.add(new Step(source,-1,start.ports[port].vlan,Long.MAX_VALUE,0,List.of(source),List.of()));
        Set<String> visited=new HashSet<>();List<Step> found=new ArrayList<>();
        while(!queue.isEmpty()){budget.spend();Step current=queue.removeFirst();NetworkProfile node=nodes.get(current.id);
            if(current.ingress>=0){if(!visited.add(domain(current.id,current.vlan)+"/"+current.ingress))continue;
                learn(mac,current.id,current.vlan+" "+macAddresses.get(source),current.ingress);
                if(predicate.test(current.id,current.ingress))found.add(current);
                if(!node.kind.bridge())continue;
            }
            for(int p=0;p<6;p++){
                budget.spend();
                if(current.ingress<0&&p!=port||p==current.ingress)continue;
                var output=node.ports[p];if(!output.enabled||!output.trunk&&output.vlan!=current.vlan)continue;
                Link link=ports.get(current.id)[p];if(link==null)continue;long speed=linkCapacity(link);if(speed==0)continue;
                String peerId=link.peer(current.id);int peerPort=link.peerPort(current.id);var peer=nodes.get(peerId);
                int nextVlan=receivedVlan(output,current.vlan,peer.ports[peerPort]);if(nextVlan<0||blocked(link,current.id,current.vlan,nextVlan))continue;
                if(!stpRuntime.forwarding(current.id,p,current.vlan))continue;
                if(!stpRuntime.forwarding(peerId,peerPort,nextVlan)){
                    if(stpRuntime.learning(peerId,peerPort,nextVlan))learn(mac,peerId,nextVlan+" "+macAddresses.get(source),peerPort);
                    continue;
                }
                if(current.path.contains(peerId))continue;
                budget.path(current.path.size()+1);
                if(queue.size()>=MAX_STEPS)throw new SearchLimit("通信待ち行列の上限です");
                var path=new ArrayList<>(current.path);path.add(peerId);var wires=new ArrayList<>(current.wires);wires.add(link.id);
                long cap=Math.min(speed,Math.min(node.capacity(),peer.capacity()));
                double delay=0.05+link.length*0.002+(link.wan?Math.max(0.1,25.0/(1+Math.min(node.wanTier,peer.wanTier))):0);
                queue.addLast(new Step(peerId,peerPort,nextVlan,Math.min(current.bps,cap),current.delay+delay,List.copyOf(path),List.copyOf(wires)));
            }
        }
        return found;
    }
    private static <T> void learn(Map<String,Map<String,T>> table,String device,String key,T value){var map=table.computeIfAbsent(device,k->new LinkedHashMap<>());if(map.size()>=256&&!map.containsKey(key))map.remove(map.keySet().iterator().next());map.put(key,value);}
    public static String macAddress(String id){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            return String.format(Locale.ROOT,"02:%02x:%02x:%02x:%02x:%02x",digest[0]&255,digest[1]&255,digest[2]&255,digest[3]&255,digest[4]&255);
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}
    }
    /** Proxy ARPを暗黙に有効にせず、受信側のインターフェース/VLAN内だけで応答する。 */
    private boolean answersArp(String id,int ingress,String ip){
        var p=nodes.get(id);if(p==null||p.failed||!p.ports[ingress].enabled)return false;
        if(!p.kind.routing())return ip.equals(p.address);
        for(int n=0;n<6;n++){
            var port=p.ports[n];
            if(n!=ingress&&!(p.kind==DeviceKind.L3_SWITCH&&port.vlan==p.ports[ingress].vlan))continue;
            if(!port.enabled||port.address.isBlank())continue;
            if(ip.equals(port.address))return true;
            if(ip.equals(p.vip)&&vrrpRuntime.master(id)&&ingress==p.vrrp.resolvePort(p))return true;
        }
        return false;
    }
    private boolean owns(String node,String ip){var p=nodes.get(node);if(p==null||p.failed)return false;
        if(!p.address.isBlank()&&ip.equals(p.address))return true;for(var port:p.ports)if(ip.equals(port.address))return true;return ip.equals(p.vip)&&vrrpRuntime.master(node);}
    private record Decision(int port,String nextHop,String sourceIp){}
    private List<Decision> decisions(String node,String target){
        var p=nodes.get(node);List<Decision> choices=new ArrayList<>();if(p==null||p.failed)return choices;
        if(!p.kind.routing()){
            if(p.address.isBlank())return choices;
            String hop=Ipv4.sameSubnet(p.address,target,p.prefix)?target:p.gateway;if(hop.isBlank()||!Ipv4.sameSubnet(p.address,hop,p.prefix))return choices;
            for(int i=0;i<6;i++)if(ports.get(node)[i]!=null)choices.add(new Decision(i,hop,p.address));return choices;
        }
        int best=-1;boolean ospfChoice=false;NetworkProfile.Route staticRoute=null;
        for(var route:p.routes)if(route.matches(target)&&route.prefix()>best){best=route.prefix();staticRoute=route;}
        for(int i=0;i<6;i++){var port=p.ports[i];if(!port.address.isBlank()&&Ipv4.sameSubnet(port.address,target,port.prefix)&&port.prefix>=best){
            if(port.prefix>best)choices.clear();best=port.prefix;staticRoute=null;choices.add(new Decision(i,target,port.address));}}
        if(staticRoute!=null){var port=p.ports[staticRoute.port()];if(!port.address.isBlank()&&Ipv4.sameSubnet(port.address,staticRoute.gateway(),port.prefix))choices.add(new Decision(staticRoute.port(),staticRoute.gateway(),port.address));}
        for(var route:ospfRuntime.routes(node))if(route.prefix()>best&&Ipv4.sameSubnet(route.network(),target,route.prefix())
                &&p.ports[route.port()].enabled&&portSpeed(node,route.port())>0){
            best=route.prefix();ospfChoice=true;choices.clear();choices.add(new Decision(route.port(),route.gateway(),p.ports[route.port()].address));
        }
        int distance=ospfChoice?110:staticRoute!=null?1:0;
        for(var r:bgp().routes(node))if(p.bgp.enabled&&!r.local()&&r.prefix().contains(Ipv4.parse(target))){
            int candidateDistance=r.external()?20:200;
            var hop=bgpResolve(node,r.nextHop());if(hop==null)continue;
            if(r.prefix().length()>best||r.prefix().length()==best&&candidateDistance<distance){
                best=r.prefix().length();distance=candidateDistance;choices.clear();
                choices.add(new Decision(hop.port(),hop.gateway(),p.ports[hop.port()].address));
            }
        }
        return choices;
    }
    /** BGP以外のローカルRIBだけでNEXT_HOPを解決し、自己依存したBGP再帰を避ける。 */
    BgpEngine.Hop bgpResolve(String node,String target){
        var p=nodes.get(node);if(p==null||p.failed||!p.kind.routing())return null;
        long ip;try{ip=Ipv4.parse(target);}catch(IllegalArgumentException invalid){return null;}
        BgpEngine.Hop best=null;
        for(int n=0;n<6;n++){
            var port=p.ports[n];if(!port.enabled||port.address.isBlank()||portSpeed(node,n)==0)continue;
            // 自身の実IPをNEXT_HOPにした受信経路は使用しない。
            if(port.address.equals(target))return null;
            if(Ipv4.sameSubnet(port.address,target,port.prefix))best=prefer(best,new BgpEngine.Hop(n,target,0,port.prefix,0));
        }
        for(var r:p.routes){var port=p.ports[r.port()];
            if(port.enabled&&!port.address.isBlank()&&portSpeed(node,r.port())>0&&r.matches(target)&&Ipv4.sameSubnet(port.address,r.gateway(),port.prefix))
                best=prefer(best,new BgpEngine.Hop(r.port(),r.gateway(),1,r.prefix(),1));
        }
        for(var r:ospfRuntime.routes(node))if(Ipv4.sameSubnet(r.network(),target,r.prefix())&&p.ports[r.port()].enabled&&portSpeed(node,r.port())>0)
            best=prefer(best,new BgpEngine.Hop(r.port(),r.gateway(),r.cost(),r.prefix(),110));
        return best;
    }
    private static BgpEngine.Hop prefer(BgpEngine.Hop a,BgpEngine.Hop b){
        return a==null||b.prefix()>a.prefix()||b.prefix()==a.prefix()&&b.distance()<a.distance()?b:a;
    }
    boolean bgpOriginates(String id,NetworkPolicy.Prefix prefix){
        var p=nodes.get(id);if(p==null||p.failed)return false;
        for(int i=0;i<6;i++){var port=p.ports[i];if(port.enabled&&!port.address.isBlank()&&portSpeed(id,i)>0
                &&new NetworkPolicy.Prefix(Ipv4.parse(port.address),port.prefix).equals(prefix))return true;}
        for(var r:p.routes)if(new NetworkPolicy.Prefix(Ipv4.parse(r.network()),r.prefix()).equals(prefix)){
            var port=p.ports[r.port()];if(port.enabled&&!port.address.isBlank()&&portSpeed(id,r.port())>0&&Ipv4.sameSubnet(port.address,r.gateway(),port.prefix))return true;
        }
        return ospfRuntime.routes(id).stream().anyMatch(r->new NetworkPolicy.Prefix(Ipv4.parse(r.network()),r.prefix()).equals(prefix)&&portSpeed(id,r.port())>0);
    }
    boolean bgpUsable(String id,BgpEngine.Route route){
        if(route.local())return bgpOriginates(id,route.prefix());
        if(bgpResolve(id,route.nextHop())==null)return false;
        var base=bgpResolve(id,Ipv4.format(route.prefix().network()));
        return base==null||route.prefix().length()>base.prefix()||route.prefix().length()==base.prefix()&&(route.external()?20:200)<base.distance();
    }
    /** TCP179のメタデータの往復経路を検証。NATを通したBGPピアはこの版では許可しない。 */
    BgpEngine.Channel bgpChannel(String id,BgpSettings.Peer peer){
        var local=nodes.get(id);if(local==null||local.failed||!local.bgp.enabled)return null;
        try{
            var hop=bgpResolve(id,peer.address());if(hop==null)return null;
            int sourcePort=peer.sourcePort()<0?hop.port():peer.sourcePort();var port=local.ports[sourcePort];
            if(!port.enabled||port.address.isBlank()||portSpeed(id,sourcePort)==0)return null;
            boolean external=peer.remoteAs()!=local.bgp.asn;
            if(external&&(sourcePort!=hop.port()||!Ipv4.sameSubnet(port.address,peer.address(),port.prefix)))return null;
            var budget=new SearchBudget();var packet=NetworkPolicy.Packet.of("tcp",port.address,peer.address(),49179,179);
            String remote=bgpWalk(id,packet,budget,external);if(remote==null||remote.equals(id))return null;
            String back=bgpWalk(remote,packet.reverse(),budget,external);if(!id.equals(back))return null;
            return new BgpEngine.Channel(remote,port.address,peer.address());
        }catch(SearchLimit|IllegalArgumentException failure){return null;}
    }
    private String bgpWalk(String source,NetworkPolicy.Packet packet,SearchBudget budget,boolean direct){
        requireForest();String current=source,target=Ipv4.format(packet.destination());var visited=new HashSet<String>();int ingress=-1;
        for(int ttl=0;ttl<32;ttl++){
            var profile=nodes.get(current);if(profile==null||profile.failed||!profile.kind.routing())return null;
            if(!visited.add(current))return null;
            for(var port:profile.ports)if(port.enabled&&port.address.equals(target))return current;
            if(direct&&ttl>0)return null;
            var choice=bgpResolve(current,target);if(choice==null)return null;
            // BGPは実インターフェース宛てのunderlay通信。仮想NATセッションを作らない。
            if(profile.nat.enabled&&ingress>=0&&profile.nat.role(ingress)!=profile.nat.role(choice.port())
                    &&profile.nat.role(ingress)!=NatSettings.Role.NONE&&profile.nat.role(choice.port())!=NatSettings.Role.NONE)return null;
            var hits=segment(current,choice.port(),(id,in)->answersArp(id,in,choice.gateway()),budget);
            var unique=new LinkedHashMap<String,Step>();for(Step hit:hits)unique.putIfAbsent(hit.id,hit);
            if(unique.size()!=1)return null;Step path=unique.values().iterator().next();
            if(filterPath(path.path,path.wires,packet,budget)!=null)return null;
            current=path.id;ingress=path.ingress;
        }
        return null;
    }
    public Result route(String source,String address){
        try(var transaction=natTable.begin()){
            var packet=NetworkPolicy.Packet.of("ip",replyAddress(source,address),address,0,0);
            // 一方向の診断だけではNATを確定しない。
            return walk(source,new SearchBudget(),packet,transaction).result;
        }catch(SearchLimit|IllegalArgumentException limit){return Result.fail(limit.getMessage());}
    }
    private record Delivery(Result result,NetworkPolicy.Packet packet){}
    /** 各ホップの受信ACLは変換前、送信ACLは変換後のパケットを検査する。 */
    private Delivery walk(String source,SearchBudget budget,NetworkPolicy.Packet packet,NatTable.Transaction transaction){
        requireForest();var original=nodes.get(source);
        if(original==null||original.failed)return new Delivery(Result.fail("送信元が停止しています"),packet);
        String current=source;int ingress=-1;
        List<String> path=new ArrayList<>();path.add(source);List<String> wires=new ArrayList<>();
        long bps=original.capacity();double delay=0;Set<String> routed=new HashSet<>();
        for(int ttl=0;ttl<32;ttl++){
            if(!routed.add(current))return new Delivery(Result.fail("TTL超過: ルーティングループ"),packet);
            var profile=nodes.get(current);
            var translated=transaction.ingress(current,profile,ingress,packet);packet=translated.packet();
            String target=Ipv4.format(packet.destination());
            if(owns(current,target)){
                if(translated.forward()!=null||translated.expectedInside()>=0)
                    return new Delivery(Result.fail("NAT内部宛先がルーター自身と競合しています"),packet);
                return new Delivery(new Result(true,"到達",bps,delay,List.copyOf(path),List.copyOf(wires),current),packet);
            }
            if(ingress>=0&&!profile.kind.routing())return new Delivery(Result.fail("次ホップがルーターではありません"),packet);
            Step reached=null;Decision selected=null;
            for(Decision choice:decisions(current,target)){
                final String sought=choice.nextHop;
                var hits=segment(current,choice.port,(id,port)->answersArp(id,port,sought),budget);
                if(hits.isEmpty())continue;
                Map<String,Step> unique=new LinkedHashMap<>();for(Step hit:hits)unique.putIfAbsent(hit.id,hit);
                boolean virtual=unique.values().stream().allMatch(step->sought.equals(nodes.get(step.id).vip));
                if(unique.size()>1)return new Delivery(Result.fail(virtual?"VRRP競合: 同じLANに複数のMasterが応答しました。広告/VLAN/VRIDを確認":"ARP応答競合: IPアドレスが重複しています"),packet);
                reached=unique.values().iterator().next();
                selected=choice;var responder=nodes.get(reached.id);learn(arp,current,sought,sought.equals(responder.vip)?VrrpSettings.virtualMac(responder.vrrp.group):macAddresses.get(reached.id));break;
            }
            if(reached==null)return new Delivery(Result.fail("到達不能: IP/経路/VLAN/媒体/断線を確認してください"),packet);
            packet=transaction.egress(current,profile,ingress,selected.port,translated);
            String denied=filterPath(reached.path,reached.wires,packet,budget);
            if(denied!=null){log(source,denied);return new Delivery(Result.fail(denied),packet);}
            budget.path(path.size()+reached.path.size()-1);
            path.addAll(reached.path.subList(1,reached.path.size()));wires.addAll(reached.wires);
            bps=Math.min(bps,reached.bps);delay+=reached.delay;current=reached.id;ingress=reached.ingress;
        }
        return new Delivery(Result.fail("TTL超過"),packet);
    }
    private String replyAddress(String source,String target){var p=nodes.get(source);if(p==null)return "";if(!p.address.isBlank())return p.address;var ds=decisions(source,target);return ds.isEmpty()?"":ds.getFirst().sourceIp;}
    /** ステートレスACLは実際のリンク上で見えるIP/ポートを出側/入側で評価する。 */
    private String filterPath(List<String> path,List<String> wires,NetworkPolicy.Packet packet,SearchBudget budget){
        for(int i=0;i<wires.size();i++){
            Link link=links.get(wires.get(i));String from=path.get(i),to=path.get(i+1);
            String denied=filterPort(from,link.port(from),NetworkPolicy.Direction.OUT,packet,budget);
            if(denied!=null)return denied;
            denied=filterPort(to,link.port(to),NetworkPolicy.Direction.IN,packet,budget);
            if(denied!=null)return denied;
        }
        return null;
    }
    private String filterPort(String id,int port,NetworkPolicy.Direction direction,NetworkPolicy.Packet packet,SearchBudget budget){
        var profile=nodes.get(id);var verdict=profile.services.policy.decide(port,direction,packet);
        for(int i=0;i<verdict.checked();i++)budget.spend();
        return verdict.allowed()?null:"ACL拒否: "+profile.name+" P"+port+" "+direction+" "+(verdict.sequence()==0?"暗黙のdeny":"#"+verdict.sequence());
    }
    public Result ping(String source,String target){
        try{return request(source,resolveAddress(source,target),"icmp",0);}catch(IllegalArgumentException e){return Result.fail(e.getMessage());}
    }
    public String resolveAddress(String source,String target){
        String normalized=DnsNames.target(target);if(DnsNames.numeric(normalized))return normalized;
        var answer=dnsResolver.lookup(source,normalized);if(!answer.success())throw new IllegalArgumentException(answer.message());return answer.selected();
    }
    public ServiceAddress resolveEndpoint(String source,String text,int defaultPort){
        var endpoint=DnsNames.endpoint(text,defaultPort);return new ServiceAddress(resolveAddress(source,endpoint.host()),endpoint.port());
    }
    /** メタデータによる往復検査。実TCPハンドシェイクや外部ソケットは生成しない。 */
    public Result request(String source,String target,String protocol,int destinationPort){
        return requestService(source,target,protocol,destinationPort,-1);
    }
    /** 転送後のポートが実サービスと違う場合、変換表を確定せず拒否する。 */
    public Result requestService(String source,String target,String protocol,int destinationPort,int expectedServicePort){
        try {
            if(expectedServicePort< -1||expectedServicePort>65535)throw new IllegalArgumentException("サービスのポート番号が不正です");
            var type=NetworkPolicy.Protocol.valueOf(protocol.toUpperCase(Locale.ROOT));
            if(type==NetworkPolicy.Protocol.IP||type==NetworkPolicy.Protocol.OSPF||type==NetworkPolicy.Protocol.VRRP||type==NetworkPolicy.Protocol.ICMP&&destinationPort!=0)throw new IllegalArgumentException("requestはicmp(0), tcp, udpです");
            return exchange(source,replyAddress(source,target),target,protocol,type==NetworkPolicy.Protocol.ICMP?0:49152,destinationPort,new SearchBudget(),expectedServicePort);
        }catch(SearchLimit|IllegalArgumentException e){return Result.fail(e.getMessage());}
    }
    /** 制御命令・更新の認可もNAT取引の中で検査。定期更新は操作ログを増殖させない。 */
    public Result controlRequest(String source,ServiceAddress endpoint,java.util.function.Function<String,String> authorize){
        Objects.requireNonNull(endpoint);Objects.requireNonNull(authorize);suppressProbeLogs++;
        try{
            return exchange(source,replyAddress(source,endpoint.address()),endpoint.address(),"tcp",49152,endpoint.port(),new SearchBudget(),CONTROL_PORT,
                    (id,p)->p!=null&&p.kind==DeviceKind.REDSTONE_TERMINAL?Objects.requireNonNull(authorize.apply(id)):"受信先はレッドストーン通信端末ではありません");
        }catch(SearchLimit|IllegalArgumentException invalid){return Result.fail(invalid.getMessage());}
        finally{suppressProbeLogs--;}
    }
    /** 設定保管庫はTCP5001。機種・認可・容量の検査をNAT取引の確定前に行う。 */
    public Result backupRequest(String source,ServiceAddress endpoint,java.util.function.Function<String,String> authorize){
        Objects.requireNonNull(endpoint);Objects.requireNonNull(authorize);suppressProbeLogs++;
        try{
            return exchange(source,replyAddress(source,endpoint.address()),endpoint.address(),"tcp",49152,endpoint.port(),new SearchBudget(),5001,
                    (id,p)->p!=null&&p.kind==DeviceKind.SERVER?Objects.requireNonNull(authorize.apply(id)):"通信先はバックアップサーバーではありません");
        }catch(SearchLimit|IllegalArgumentException invalid){return Result.fail(invalid.getMessage());}
        finally{suppressProbeLogs--;}
    }
    private Result exchange(String source,String local,String target,String protocol,int sourcePort,int destinationPort,SearchBudget budget){
        return exchange(source,local,target,protocol,sourcePort,destinationPort,budget,-1);
    }
    private Result exchange(String source,String local,String target,String protocol,int sourcePort,int destinationPort,SearchBudget budget,int expectedServicePort){
        return exchange(source,local,target,protocol,sourcePort,destinationPort,budget,expectedServicePort,p->"");
    }
    /** 稼働判定もNAT取引内。監視で停止を観測しても変換エントリーを延命しない。 */
    private Result exchange(String source,String local,String target,String protocol,int sourcePort,int destinationPort,SearchBudget budget,int expectedServicePort,
                            java.util.function.Function<NetworkProfile,String> availability){
        return exchange(source,local,target,protocol,sourcePort,destinationPort,budget,expectedServicePort,(id,p)->availability.apply(p));
    }
    private Result exchange(String source,String local,String target,String protocol,int sourcePort,int destinationPort,SearchBudget budget,int expectedServicePort,
                            java.util.function.BiFunction<String,NetworkProfile,String> availability){
        if(local.isBlank()||!owns(source,local))return Result.fail("送信元IPが未設定または機器に属していません");
        var packet=NetworkPolicy.Packet.of(protocol,local,target,sourcePort,destinationPort);
        try(var transaction=natTable.begin()){
            transaction.localOrigin(source,packet);
            var forward=walk(source,budget,packet,transaction);if(!forward.result.success)return forward.result;
            if(expectedServicePort>=0&&forward.packet.destinationPort()!=expectedServicePort)
                return Result.fail("NAT転送先ポートが実サービスと一致しません (必要: "+expectedServicePort+")");
            if(expectedServicePort==53&&!DnsSettings.available(nodes.get(forward.result.destination)))
                return Result.fail("DNSサービスが停止または非対応です");
            String unavailable=availability.apply(forward.result.destination,nodes.get(forward.result.destination));
            if(!unavailable.isEmpty())return Result.fail(unavailable);
            var reverse=walk(forward.result.destination,budget,forward.packet.reverse(),transaction);
            if(!reverse.result.success)return Result.fail("応答経路なし: "+reverse.result.message);
            if(!reverse.result.destination.equals(source)||!reverse.packet.equals(packet.reverse()))
                return Result.fail("応答先IP/ポートが競合またはNAT状態と一致しません");
            var bindings=transaction.commit();
            var a=forward.result;var b=reverse.result;
            var result=new Result(true,"Reply from "+target+" ["+protocol.toUpperCase(Locale.ROOT)+"] RTT="+String.format(Locale.ROOT,"%.2f ms",a.milliseconds+b.milliseconds),
                    Math.min(a.bps,b.bps),a.milliseconds+b.milliseconds,a.nodes,a.links,a.destination,forward.packet.destinationPort(),bindings);
            log(source,result.message+" / "+CableGrade.speed(result.bps)+(bindings.isEmpty()?"":" / NAT "+bindings.size()+"段"));return result;
        }
    }
    public Result dhcp(String source){
        try{return dhcp(source,new SearchBudget());}catch(SearchLimit|IllegalArgumentException limit){return Result.fail(limit.getMessage());}
    }
    private Result dhcp(String source,SearchBudget budget){
        requireForest();var client=nodes.get(source);
        if(client==null||client.failed||client.kind.routing())return Result.fail("稼働中の端末で実行してください");
        String problem="DHCP応答なし、またはプールが満杯です";
        for(int port=0;port<6;port++){
            final int clientPort=port;
            List<Step> members=segment(source,port,(id,in)->true,budget);
            for(Step offer:members){
                var responder=nodes.get(offer.id);
                String relayTarget=responder.kind.routing()?responder.services.relay(offer.ingress):"";
                if(!responder.dhcp&&relayTarget.isBlank())continue;
                String denied=filterPath(offer.path,offer.wires,NetworkPolicy.Packet.of("udp","0.0.0.0","255.255.255.255",68,67),budget);
                if(denied!=null){problem=denied;continue;}
                var backPaths=segment(offer.id,offer.ingress,(id,in)->id.equals(source)&&in==clientPort,budget);
                if(backPaths.isEmpty()){problem="DHCP応答のLAN側経路がありません";continue;}
                var back=backPaths.getFirst();
                var localInterface=interfaceFor(responder,offer.ingress);
                String serverId=offer.id,serverAddress=responder.address,giaddr="";
                NetworkServices.Pool pool;
                List<String> path=new ArrayList<>(offer.path),wires=new ArrayList<>(offer.wires);
                long capacity=Math.min(offer.bps,back.bps);double delay=offer.delay+back.delay;
                if(!relayTarget.isBlank()){
                    if(localInterface==null){problem="DHCPリレー元のLAN側IPが未設定です";continue;}
                    giaddr=localInterface.address;
                    Result upstream=exchange(offer.id,giaddr,relayTarget,"udp",67,67,budget);
                    if(!upstream.success){problem="DHCP relay: "+upstream.message;continue;}
                    serverId=upstream.destination;serverAddress=relayTarget;
                    var server=nodes.get(serverId);
                    if(!server.dhcp){problem="DHCP relay: 宛先DHCPサービスは停止しています";continue;}
                    pool=server.services.poolFor(giaddr);
                    if(pool==null){problem="DHCP relay: giaddr="+giaddr+" に合うプールがありません";continue;}
                    budget.path(path.size()+upstream.nodes.size()-1);
                    path.addAll(upstream.nodes.subList(1,upstream.nodes.size()));wires.addAll(upstream.links);
                    capacity=Math.min(capacity,upstream.bps);delay+=upstream.milliseconds;
                }else{
                    String local=responder.kind.routing()?(localInterface==null?"":localInterface.address):responder.address;
                    int prefix=responder.kind.routing()?(localInterface==null?24:localInterface.prefix):responder.prefix;
                    if(local.isBlank()||prefix>30)continue;
                    serverAddress=local;pool=responder.services.poolFor(local);
                    if(pool==null){
                        if(!responder.services.pools().isEmpty()){problem="受信LANに合うDHCPプールがありません";continue;}
                        long base=Ipv4.parse(local)&Ipv4.mask(prefix),hosts=(~Ipv4.mask(prefix))&0xffffffffL;
                        pool=new NetworkServices.Pool(new NetworkPolicy.Prefix(base,prefix),Ipv4.format(base+Math.min(100,Math.max(1,hosts/2))),Ipv4.format(base+Math.min(hosts-1,253)),responder.kind.routing()?local:responder.gateway,local);
                    }
                }
                denied=filterPath(back.path,back.wires,NetworkPolicy.Packet.of("udp",serverAddress,"255.255.255.255",67,68),budget);
                if(denied!=null){problem=denied;continue;}
                var server=nodes.get(serverId);Set<String> used=new HashSet<>(server.services.reservedByOthers(source));
                used.add(pool.gateway());used.add(pool.dns());used.add(serverAddress);
                for(Step member:members){var p=nodes.get(member.id);used.add(p.address);used.add(p.vip);for(var mp:p.ports)used.add(mp.address);}
                String prior=server.services.reserved(source,pool),candidate=null;
                if(prior!=null&&pool.contains(prior)&&!used.contains(prior))candidate=prior;
                if(candidate==null)for(long n=Ipv4.parse(pool.first());n<=Ipv4.parse(pool.last());n++){budget.spend();String address=Ipv4.format(n);if(!used.contains(address)){candidate=address;break;}}
                if(candidate==null){problem="DHCPプール枯渇: "+pool.subnet();continue;}
                // ACK確定直前に保存容量も検査。失敗時は配布予約・端末設定とも戻す。
                String oldAddress=client.address,oldGateway=client.gateway,oldDns=client.dns;int oldPrefix=client.prefix;
                try {
                    server.services.reserve(source,pool,candidate);server.encode();
                    client.address=candidate;client.prefix=pool.subnet().length();client.gateway=pool.gateway();client.dns=pool.dns();client.encode();
                }catch(IllegalArgumentException e){server.services.undoReserve(source,pool,prior);client.address=oldAddress;client.prefix=oldPrefix;client.gateway=oldGateway;client.dns=oldDns;problem=e.getMessage();continue;}
                changedProfiles.add(source);changedProfiles.add(serverId);
                String message="DHCP ACK: "+candidate+"/"+pool.subnet().length()+(giaddr.isBlank()?"":" / relay giaddr="+giaddr);
                log(source,message);log(serverId,"DHCP予約: "+source+" → "+candidate);
                return new Result(true,message,capacity,delay,List.copyOf(path),List.copyOf(wires),serverId);
            }
        }
        return Result.fail(problem);
    }
    private static NetworkProfile.Port interfaceFor(NetworkProfile p,int ingress){
        var port=p.ports[ingress];if(port.enabled&&!port.address.isBlank())return port;
        if(p.kind==DeviceKind.L3_SWITCH)for(var other:p.ports)if(other.enabled&&other.vlan==port.vlan&&!other.address.isBlank())return other;
        return null;
    }
    /** nslookup互換の直接問い合わせ。アプリケーション用のキャッシュとは別。 */
    public Result resolve(String source,String name){
        var answer=dnsResolver.query(source,name,null);if(!answer.success())return Result.fail(answer.message());
        var path=answer.transport();return new Result(true,answer.message(),path.bps(),path.milliseconds(),path.nodes(),path.links(),answer.selected());
    }

    public Result startTransfer(String source,String destination,long bytes){
        if(bytes<=0||bytes>1_000_000_000_000L)return Result.fail("転送量は1〜1TBです");if(flows.size()>=MAX_FLOWS)return Result.fail("同時転送の上限です");
        var sender=nodes.get(source);if(sender==null)return Result.fail("送信元なし");
        if(flows.stream().filter(f->f.source.equals(source)).count()>=4+Math.clamp(sender.buffer,0,6)*4)return Result.fail("送信バッファ満杯: バッファモジュールで同時転送枠を増設できます");
        final ServiceAddress endpoint;try{endpoint=resolveEndpoint(source,destination,TRANSFER_PORT);}catch(IllegalArgumentException e){return Result.fail(e.getMessage());}
        Result path=requestService(source,endpoint.address(),"tcp",endpoint.port(),TRANSFER_PORT);if(!path.success)return path;var flow=new Flow(nextFlow++,bytes,source,path);flows.add(flow);log(source,"仮想転送 #"+flow.id+": "+bytes+" bytes");return new Result(true,"転送 #"+flow.id+" 開始 (実ファイルは生成しません)",path.bps,path.milliseconds,path.nodes,path.links,path.destination);
    }
    /** OSPFのローカルリンク帯域。経路探索やチャンク読み込みはしない。 */
    long portSpeed(String id,int port){var row=ports.get(id);if(row==null||row[port]==null)return 0;return linkCapacity(row[port]);}
    /** 設定/配線変更後に予算を分割して呼ばれるL2マルチキャスト到達判定。 */
    List<OspfEngine.Peer> findOspfPeers(String id,int port){
        try{
            var local=nodes.get(id);if(local==null||local.failed||local.ports[port].address.isBlank())return List.of();
            var budget=new SearchBudget();var packet=NetworkPolicy.Packet.of("ospf",local.ports[port].address,"224.0.0.5",0,0);
            Set<OspfEngine.Peer> peers=new LinkedHashSet<>();
            for(Step path:segment(id,port,(peer,in)->nodes.get(peer).kind.routing(),budget)){
                var remote=nodes.get(path.id);var remotePort=remote.ports[path.ingress];
                if(!remote.ospf.enabled||remote.ospf.modes[path.ingress]!=OspfSettings.Mode.ACTIVE||remotePort.address.isBlank())continue;
                if(remotePort.prefix!=local.ports[port].prefix||!Ipv4.sameSubnet(remotePort.address,local.ports[port].address,remotePort.prefix))continue;
                if(filterPath(path.path,path.wires,packet,budget)==null)peers.add(new OspfEngine.Peer(path.id,path.ingress));
                if(peers.size()>OspfEngine.MAX_NEIGHBORS)throw new IllegalStateException("OSPF隣接候補上限");
            }
            return List.copyOf(peers);
        }catch(SearchLimit limit){throw new IllegalStateException(limit.getMessage());}
    }
    /** VRRPは224.0.0.18宛てのリンクローカル広告。L3経路/NATを使って中継しない。 */
    List<String> findVrrpPeers(String id,int port){
        try{
            var local=nodes.get(id);if(local==null||local.failed||local.ports[port].address.isBlank())return List.of();
            var budget=new SearchBudget();var packet=NetworkPolicy.Packet.of("vrrp",local.ports[port].address,"224.0.0.18",0,0);
            Set<String> peers=new LinkedHashSet<>();
            for(Step path:segment(id,port,(peer,in)->nodes.get(peer).kind.routing(),budget)){
                var remote=nodes.get(path.id);
                if(!remote.vrrp.enabled||remote.vip.isBlank()||remote.vrrp.resolvePort(remote)!=path.ingress)continue;
                if(filterPath(path.path,path.wires,packet,budget)==null)peers.add(path.id);
                if(peers.size()>VrrpEngine.MAX_PEERS)throw new IllegalStateException("VRRP受信候補上限");
            }
            return List.copyOf(peers);
        }catch(SearchLimit limit){throw new IllegalStateException(limit.getMessage());}
    }
    public void advance(long ticks){
        if(ticks<=0)return;
        syslogRuntime.elapse(ticks);
        dnsResolver.advance(ticks);
        stpRuntime.advance(ticks);
        Set<String> stpChanged=stpRuntime.drainChanges();
        if(!stpChanged.isEmpty()){
            var affected=component(stpChanged);invalidateData(affected,"STP状態変更で中断。再送できます");
            // STP自身のListening/Forward Delayを再起動せず、当該網の広告経路だけ再探索。
            ospfRuntime.changed(affected);vrrpRuntime.changed(affected);bgpRuntime.changed(affected);
        }
        vrrpRuntime.advance(ticks);
        Set<String> owners=vrrpRuntime.drainOwners();
        if(!owners.isEmpty())invalidateData(component(owners),"VRRP切替で中断。再送できます");
        ospfRuntime.advance(ticks);
        Set<String> changedRoutes=ospf().drainRouteChanges();
        if(!changedRoutes.isEmpty())flows.removeIf(flow->{
            boolean affected=flow.nodes.stream().anyMatch(changedRoutes::contains);
            if(affected)log(flow.source,"転送 #"+flow.id+": OSPF経路変更で中断。再送できます");return affected;
        });
        if(!changedRoutes.isEmpty())bgpRuntime.changed(component(changedRoutes));
        bgpRuntime.advance(ticks);
        Set<String> bgpChanges=bgp().drainChanges();
        if(!bgpChanges.isEmpty())invalidateData(bgpChanges,"BGP経路変更で中断。再送できます");
        Set<Long> activeNat=new HashSet<>();for(var flow:flows)activeNat.addAll(flow.natBindings);
        natTable.advance(ticks,activeNat);
        Map<String,Integer> use=new HashMap<>();for(var f:flows){for(String link:new HashSet<>(f.links))use.merge("l"+link,1,Integer::sum);for(String node:new HashSet<>(f.nodes))use.merge("n"+node,1,Integer::sum);}
        for(var it=flows.iterator();it.hasNext();){var f=it.next();double time=Math.max(0,ticks-f.delayTicks);f.delayTicks=Math.max(0,f.delayTicks-ticks);double rate=f.capacity;boolean live=f.natBindings.stream().allMatch(natTable::alive);
            for(String id:f.links){Link link=links.get(id);if(link==null||linkCapacity(link)==0){live=false;break;}rate=Math.min(rate,(double)linkCapacity(link)/use.get("l"+id));}
            for(String id:f.nodes){var p=nodes.get(id);if(p==null||p.failed){live=false;break;}rate=Math.min(rate,(double)p.capacity()/use.get("n"+id));}
            if(!live){log(f.source,"転送 #"+f.id+": リンク断で中断");it.remove();continue;}
            f.remaining-=rate/8.0*time/20.0;if(f.remaining<=0){log(f.source,"転送 #"+f.id+": 完了");it.remove();}
        }
        monitorRuntime.advance(ticks);
        syslogRuntime.dispatch();
    }
}
