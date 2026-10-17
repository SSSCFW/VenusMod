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
    public record Result(boolean success,String message,long bps,double milliseconds,List<String> nodes,List<String> links,String destination){
        public static Result fail(String reason){return new Result(false,reason,0,0,List.of(),List.of(),"");}
    }
    public static final class Flow {
        public final long id,totalBytes;
        public final String source,destination;
        public final List<String> links,nodes;
        public final long capacity;
        public double remaining,delayTicks;
        Flow(long id,long bytes,String source,Result path){this.id=id;this.totalBytes=bytes;this.source=source;this.destination=path.destination;
            this.links=path.links;this.nodes=path.nodes;this.capacity=path.bps;remaining=bytes;delayTicks=path.milliseconds/50;}
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
    public void put(String id,NetworkProfile profile){
        if(!nodes.containsKey(id)&&nodes.size()>=MAX_NODES)throw new IllegalStateException("機器数上限");
        Objects.requireNonNull(id);Objects.requireNonNull(profile);
        nodes.put(id,profile);macAddresses.computeIfAbsent(id,NetworkSimulator::macAddress);
        ports.computeIfAbsent(id,k->new Link[6]);forestDirty=true;
    }
    public void remove(String id){new ArrayList<>(links.values()).stream().filter(l->l.a.equals(id)||l.b.equals(id)).forEach(l->unlink(l.id));nodes.remove(id);ports.remove(id);macAddresses.remove(id);mac.remove(id);arp.remove(id);logs.remove(id);invalidate();}
    public void link(Link link){
        if(links.size()>=MAX_LINKS)throw new IllegalStateException("リンク数上限");
        if(!nodes.containsKey(link.a)||!nodes.containsKey(link.b))throw new IllegalArgumentException("端点なし");
        if(ports.get(link.a)[link.ap]!=null||ports.get(link.b)[link.bp]!=null)throw new IllegalArgumentException("同じポートへの多重接続");
        if(links.containsKey(link.id))throw new IllegalArgumentException("リンクID重複");
        links.put(link.id,link);ports.get(link.a)[link.ap]=link;ports.get(link.b)[link.bp]=link;invalidate();
    }
    public void unlink(String id){Link link=links.remove(id);if(link!=null){ports.get(link.a)[link.ap]=null;ports.get(link.b)[link.bp]=null;invalidate();}}
    public void invalidate(){forestDirty=true;mac.clear();arp.clear();for(var flow:flows)log(flow.source,"転送 #"+flow.id+": 構成変更で中断");flows.clear();}
    public Map<String,NetworkProfile> nodes(){return Collections.unmodifiableMap(nodes);}
    public Collection<Link> links(){return Collections.unmodifiableCollection(links.values());}
    public List<Flow> flows(){return List.copyOf(flows);}
    public Map<String,Integer> macTable(String id){return Map.copyOf(mac.getOrDefault(id,Map.of()));}
    public Map<String,String> arpTable(String id){return Map.copyOf(arp.getOrDefault(id,Map.of()));}
    public List<String> logs(String id){return List.copyOf(logs.getOrDefault(id,new ArrayDeque<>()));}
    public void log(String id,String text){var q=logs.computeIfAbsent(id,k->new ArrayDeque<>());while(q.size()>=64)q.removeFirst();q.addLast(text.length()>200?text.substring(0,200):text);}
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
    private static int receivedVlan(NetworkProfile.Port out,int vlan,NetworkProfile.Port in){
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
            if(!a.kind.bridge()||!b.kind.bridge()||linkCapacity(link)==0)continue;
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
            if(ip.equals(port.address)||ip.equals(p.vip)&&Ipv4.sameSubnet(ip,port.address,port.prefix))return true;
        }
        return false;
    }
    private boolean owns(String node,String ip){var p=nodes.get(node);if(p==null||p.failed)return false;
        if(!p.address.isBlank()&&ip.equals(p.address))return true;for(var port:p.ports)if(ip.equals(port.address))return true;return ip.equals(p.vip)&&p.kind.routing();}
    private record Decision(int port,String nextHop,String sourceIp){}
    private List<Decision> decisions(String node,String target){
        var p=nodes.get(node);List<Decision> choices=new ArrayList<>();if(p==null||p.failed)return choices;
        if(!p.kind.routing()){
            if(p.address.isBlank())return choices;
            String hop=Ipv4.sameSubnet(p.address,target,p.prefix)?target:p.gateway;if(hop.isBlank()||!Ipv4.sameSubnet(p.address,hop,p.prefix))return choices;
            for(int i=0;i<6;i++)if(ports.get(node)[i]!=null)choices.add(new Decision(i,hop,p.address));return choices;
        }
        int best=-1;NetworkProfile.Route staticRoute=null;
        for(var route:p.routes)if(route.matches(target)&&route.prefix()>best){best=route.prefix();staticRoute=route;}
        for(int i=0;i<6;i++){var port=p.ports[i];if(!port.address.isBlank()&&Ipv4.sameSubnet(port.address,target,port.prefix)&&port.prefix>=best){
            if(port.prefix>best)choices.clear();best=port.prefix;staticRoute=null;choices.add(new Decision(i,target,port.address));}}
        if(staticRoute!=null){var port=p.ports[staticRoute.port()];if(!port.address.isBlank()&&Ipv4.sameSubnet(port.address,staticRoute.gateway(),port.prefix))choices.add(new Decision(staticRoute.port(),staticRoute.gateway(),port.address));}
        return choices;
    }
    public Result route(String source,String address){
        try{return route(source,address,new SearchBudget());}catch(SearchLimit limit){return Result.fail(limit.getMessage());}
    }
    private Result route(String source,String address,SearchBudget budget){
        requireForest();
        final String target;try{target=Ipv4.normalize(address);}catch(IllegalArgumentException e){return Result.fail(e.getMessage());}
        var original=nodes.get(source);if(original==null||original.failed)return Result.fail("送信元が停止しています");
        if(owns(source,target))return new Result(true,"ローカルアドレス",original.capacity(),0,List.of(source),List.of(),source);
        String current=source;List<String> path=new ArrayList<>();path.add(source);List<String> wires=new ArrayList<>();long bps=Long.MAX_VALUE;double delay=0;Set<String> routed=new HashSet<>();
        for(int ttl=0;ttl<32;ttl++){
            if(!routed.add(current))return Result.fail("TTL超過: ルーティングループ");Step reached=null;String resolvedIp="";
            for(Decision choice:decisions(current,target)){
                final String sought=choice.nextHop;var hits=segment(current,choice.port,(id,port)->answersArp(id,port,sought),budget);
                if(hits.isEmpty())continue;
                Map<String,Step> unique=new LinkedHashMap<>();for(Step hit:hits)unique.putIfAbsent(hit.id,hit);
                boolean virtual=unique.values().stream().allMatch(s->sought.equals(nodes.get(s.id).vip));
                if(unique.size()>1&&!virtual)return Result.fail("ARP応答競合: IPアドレスが重複しています");
                reached=unique.values().stream().sorted(Comparator.<Step>comparingInt(s->nodes.get(s.id).vrrpPriority).reversed().thenComparing(Step::id)).findFirst().orElseThrow();
                resolvedIp=sought;learn(arp,current,sought,macAddresses.get(reached.id));break;
            }
            if(reached==null)return Result.fail("到達不能: IP/経路/VLAN/媒体/断線を確認してください");
            budget.path(path.size()+reached.path.size()-1);
            for(int i=1;i<reached.path.size();i++)path.add(reached.path.get(i));wires.addAll(reached.wires);bps=Math.min(bps,reached.bps);delay+=reached.delay;
            current=reached.id;if(owns(current,target))return new Result(true,"到達",bps,delay,List.copyOf(path),List.copyOf(wires),current);
            if(!nodes.get(current).kind.routing())return Result.fail("次ホップがルーターではありません: "+resolvedIp);
        }
        return Result.fail("TTL超過");
    }
    private String replyAddress(String source,String target){var p=nodes.get(source);if(p==null)return "";if(!p.address.isBlank())return p.address;var ds=decisions(source,target);return ds.isEmpty()?"":ds.getFirst().sourceIp;}
    public Result ping(String source,String target){
        try{return ping(source,target,new SearchBudget());}catch(SearchLimit limit){return Result.fail(limit.getMessage());}
    }
    private Result ping(String source,String target,SearchBudget budget){
        Result forward=route(source,target,budget);if(!forward.success){log(source,"ping: "+forward.message);return forward;}
        String address=replyAddress(source,target);if(address.isBlank())return Result.fail("送信元IPが未設定です");
        Result reverse=route(forward.destination,address,budget);if(!reverse.success)return Result.fail("応答経路なし: "+reverse.message);
        var result=new Result(true,"Reply from "+target+"  RTT="+String.format(Locale.ROOT,"%.2f ms",forward.milliseconds+reverse.milliseconds),Math.min(forward.bps,reverse.bps),forward.milliseconds+reverse.milliseconds,forward.nodes,forward.links,forward.destination);
        log(source,result.message+" / "+CableGrade.speed(result.bps));return result;
    }
    public Result dhcp(String source){
        try{return dhcp(source,new SearchBudget());}catch(SearchLimit limit){return Result.fail(limit.getMessage());}
    }
    private Result dhcp(String source,SearchBudget budget){
        requireForest();
        var p=nodes.get(source);if(p==null||p.kind.routing())return Result.fail("端末で実行してください");
        for(int port=0;port<6;port++){
            var offers=segment(source,port,(id,in)->nodes.get(id).dhcp,budget);for(Step offer:offers){var server=nodes.get(offer.id);String address=server.kind.routing()?server.ports[offer.ingress].address:server.address;int prefix=server.kind.routing()?server.ports[offer.ingress].prefix:server.prefix;
                if(address.isBlank()||prefix>30)continue;long base=Ipv4.parse(address)&Ipv4.mask(prefix),hosts=(~Ipv4.mask(prefix))&0xffffffffL;
                Set<String> used=new HashSet<>();for(Step member:segment(source,port,(id,in)->true,budget)){var m=nodes.get(member.id);used.add(m.address);used.add(m.vip);for(var mp:m.ports)used.add(mp.address);}
                for(long suffix=Math.min(100,Math.max(1,hosts/2));suffix<Math.min(hosts,254);suffix++){String candidate=Ipv4.format(base+suffix);if(candidate.equals(address)||used.contains(candidate))continue;
                    p.address=candidate;p.prefix=prefix;p.gateway=address;p.dns=address;log(source,"DHCP ACK: "+candidate+"/"+prefix);return new Result(true,"DHCP ACK: "+candidate+"/"+prefix,offer.bps,offer.delay,offer.path,offer.wires,offer.id);}
            }
        }
        return Result.fail("DHCP応答なし、またはプールが満杯です");
    }
    public Result resolve(String source,String name){var p=nodes.get(source);if(p==null||p.dns.isBlank())return Result.fail("DNSサーバー未設定");Result path=ping(source,p.dns);if(!path.success)return path;String ip=nodes.get(path.destination).dnsRecords.get(name.toLowerCase(Locale.ROOT));return ip==null?Result.fail("NXDOMAIN (仮想DNS)"):new Result(true,name+" = "+ip,path.bps,path.milliseconds,path.nodes,path.links,ip);}
    public Result startTransfer(String source,String destination,long bytes){
        if(bytes<=0||bytes>1_000_000_000_000L)return Result.fail("転送量は1〜1TBです");if(flows.size()>=MAX_FLOWS)return Result.fail("同時転送の上限です");
        var sender=nodes.get(source);if(sender==null)return Result.fail("送信元なし");
        if(flows.stream().filter(f->f.source.equals(source)).count()>=4+Math.clamp(sender.buffer,0,6)*4)return Result.fail("送信バッファ満杯: バッファモジュールで同時転送枠を増設できます");
        Result path=ping(source,destination);if(!path.success)return path;var flow=new Flow(nextFlow++,bytes,source,path);flows.add(flow);log(source,"仮想転送 #"+flow.id+": "+bytes+" bytes");return new Result(true,"転送 #"+flow.id+" 開始 (実ファイルは生成しません)",path.bps,path.milliseconds,path.nodes,path.links,path.destination);
    }
    public void advance(long ticks){
        if(ticks<=0)return;Map<String,Integer> use=new HashMap<>();for(var f:flows){for(String link:new HashSet<>(f.links))use.merge("l"+link,1,Integer::sum);for(String node:new HashSet<>(f.nodes))use.merge("n"+node,1,Integer::sum);}
        for(var it=flows.iterator();it.hasNext();){var f=it.next();double time=Math.max(0,ticks-f.delayTicks);f.delayTicks=Math.max(0,f.delayTicks-ticks);double rate=f.capacity;boolean live=true;
            for(String id:f.links){Link link=links.get(id);if(link==null||linkCapacity(link)==0){live=false;break;}rate=Math.min(rate,(double)linkCapacity(link)/use.get("l"+id));}
            for(String id:f.nodes){var p=nodes.get(id);if(p==null||p.failed){live=false;break;}rate=Math.min(rate,(double)p.capacity()/use.get("n"+id));}
            if(!live){log(f.source,"転送 #"+f.id+": リンク断で中断");it.remove();continue;}
            f.remaining-=rate/8.0*time/20.0;if(f.remaining<=0){log(f.source,"転送 #"+f.id+": 完了");it.remove();}
        }
    }
}
