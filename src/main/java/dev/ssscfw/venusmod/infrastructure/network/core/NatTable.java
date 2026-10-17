package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;
import static dev.ssscfw.venusmod.infrastructure.network.core.NatSettings.Role.*;
import static dev.ssscfw.venusmod.infrastructure.network.core.NetworkPolicy.*;

/**
 * サーバー内だけの期限付きNAPT。相手IP/ポートまで一致する応答のみ復元する。
 * 1要求の往復を仮反映し、経路/ACL検査に失敗した場合は追加も期限延長も行わない。
 */
public final class NatTable {
    public static final int MAX_PER_ROUTER=128,MAX_PER_SOURCE=32,MAX_TOTAL=2048,MAX_PER_REQUEST=16;
    public static final int FIRST_PORT=49153,LAST_PORT=65535;
    public record Entry(long id,String router,int inside,int outside,Packet original,Packet wire,boolean forwarded,long expiresAt) {
        public String description(){return (forwarded?"FORWARD ":"NAPT ")+original.protocol()+" "+Ipv4.format(original.source())+":"+original.sourcePort()+" → "+Ipv4.format(wire.source())+":"+wire.sourcePort()+" / peer "+Ipv4.format(wire.destination())+":"+wire.destinationPort();}
    }
    public record Ingress(Packet packet,int expectedInside,NatSettings.Forward forward,Packet before) {
        static Ingress plain(Packet p){return new Ingress(p,-1,null,p);}
    }
    private record Expiry(long tick,long id){}
    private final Map<Long,Entry> entries=new LinkedHashMap<>();
    private final Map<String,LinkedHashMap<Long,Entry>> routers=new HashMap<>();
    private final TreeSet<Expiry> expiry=new TreeSet<>(Comparator.comparingLong(Expiry::tick).thenComparingLong(Expiry::id));
    private long now,nextId=1,revision;
    public long time(){return now;}
    public int size(){return entries.size();}
    public int expiryCount(){return expiry.size();}
    public List<Entry> sessions(String router){return routers.getOrDefault(router,new LinkedHashMap<>()).values().stream().filter(e->e.expiresAt>now).toList();}
    public boolean alive(long id){var e=entries.get(id);return e!=null&&e.expiresAt>now;}
    public void clear(){entries.clear();routers.clear();expiry.clear();revision++;}
    private void remove(Entry e){entries.remove(e.id);var r=routers.get(e.router);if(r!=null){r.remove(e.id);if(r.isEmpty())routers.remove(e.router);}expiry.remove(new Expiry(e.expiresAt,e.id));}
    private long deadline(Packet packet){long ttl=switch(packet.protocol()){case TCP->2400;case UDP->600;case ICMP->200;default->throw new IllegalArgumentException("NAT未対応プロトコル");};return Math.addExact(now,ttl);}
    private void store(Entry e){var old=entries.get(e.id);if(old!=null)expiry.remove(new Expiry(old.expiresAt,old.id));entries.put(e.id,e);routers.computeIfAbsent(e.router,k->new LinkedHashMap<>()).put(e.id,e);expiry.add(new Expiry(e.expiresAt,e.id));}
    private Entry refreshed(Entry e){return new Entry(e.id,e.router,e.inside,e.outside,e.original,e.wire,e.forwarded,deadline(e.wire));}
    /** 実データ転送中だけ期限を延長。期限キューはエントリー1件につき常に1件。 */
    public void advance(long ticks,Set<Long> active){
        if(ticks<=0)return;
        long previous=now;now=Math.addExact(now,ticks);revision++;
        for(long id:active){var e=entries.get(id);if(e!=null&&e.expiresAt>previous&&e.expiresAt-now<=(deadline(e.wire)-now)/2)store(refreshed(e));}
        for(int work=0;work<64&&!expiry.isEmpty()&&expiry.first().tick<=now;work++){
            var due=expiry.first();var e=entries.get(due.id);if(e!=null)remove(e);else expiry.remove(due);
        }
    }
    public Transaction begin(){return new Transaction();}
    public final class Transaction implements AutoCloseable {
        private final long atRevision=revision;
        private final List<Entry> pending=new ArrayList<>();
        private final Map<Long,Entry> touched=new LinkedHashMap<>();
        private String origin="";
        private Packet originPacket;
        private boolean finished;
        private void open(){if(finished||atRevision!=revision)throw new IllegalArgumentException("NAT処理中に状態が変わりました。再試行してください");}
        public void localOrigin(String id,Packet packet){open();origin=id;originPacket=packet;}
        private List<Entry> candidates(String router){var list=new ArrayList<>(sessions(router));for(var e:pending)if(e.router.equals(router))list.add(e);return list;}
        private void touch(Entry entry){touched.put(entry.id,entry);if(touched.size()>MAX_PER_REQUEST)throw new IllegalArgumentException("1要求のNAT変換段数上限です");}
        private Entry stage(String router,int inside,int outside,Packet original,Packet wire,boolean forwarded){
            open();var candidates=candidates(router);
            for(var e:candidates)if(e.inside==inside&&e.outside==outside&&e.original.equals(original)){
                if(!e.wire.equals(wire))throw new IllegalArgumentException("NAT内部タプルが既存通信と競合しています");touch(e);return e;
            }
            if(entries.size()+pending.size()>=MAX_TOTAL||candidates.size()>=MAX_PER_ROUTER)
                throw new IllegalArgumentException("NAT変換表の上限です");
            if(candidates.stream().filter(e->e.inside==inside&&e.original.source()==original.source()).count()>=MAX_PER_SOURCE)
                throw new IllegalArgumentException("内部IPごとのNAT上限です");
            if(pending.size()>=MAX_PER_REQUEST)throw new IllegalArgumentException("1要求のNAT変換段数上限です");
            var entry=new Entry(Math.addExact(nextId,pending.size()),router,inside,outside,original,wire,forwarded,deadline(wire));
            pending.add(entry);touch(entry);return entry;
        }
        private boolean outsideAddress(NetworkProfile p,long address){for(int i=0;i<p.ports.length;i++)if(p.nat.role(i)==OUTSIDE&&!p.ports[i].address.isBlank()&&Ipv4.parse(p.ports[i].address)==address)return true;return false;}
        public Ingress ingress(String id,NetworkProfile p,int port,Packet packet){
            open();if(!p.nat.enabled||port<0)return Ingress.plain(packet);
            var role=p.nat.role(port);
            if(role==INSIDE&&outsideAddress(p,packet.destination()))throw new IllegalArgumentException("NAT hairpinは未対応です。LAN内では内部IPを指定してください");
            if(role!=OUTSIDE||!outsideAddress(p,packet.destination()))return Ingress.plain(packet);
            if(p.ports[port].address.isBlank()||Ipv4.parse(p.ports[port].address)!=packet.destination())throw new IllegalArgumentException("NATの外側受信ポートが一致しません");
            // 自身が起点の要求に対応する返信だけは、ルーター自身へ返す。
            if(id.equals(origin)&&originPacket!=null&&packet.equals(originPacket.reverse()))return Ingress.plain(packet);
            for(var e:candidates(id))if(e.outside==port&&e.wire.reverse().equals(packet)){
                touch(e);return new Ingress(e.original.reverse(),e.inside,null,packet);
            }
            for(var f:p.nat.forwards())if(f.outside()==port&&f.protocol()==packet.protocol()&&f.publicPort()==packet.destinationPort()){
                Packet translated=new Packet(packet.protocol(),packet.source(),Ipv4.parse(f.address()),packet.sourcePort(),f.privatePort());
                return new Ingress(translated,-1,f,packet);
            }
            // NAT装置自身への通常のpingは許可。未知の照会ID応答は許可しない。
            if(packet.protocol()==Protocol.ICMP&&packet.sourcePort()==0&&packet.destinationPort()==0)return Ingress.plain(packet);
            throw new IllegalArgumentException("NAT受信拒否: 対応する通信または公開ポートがありません");
        }
        public Packet egress(String id,NetworkProfile p,int in,int out,Ingress input){
            open();Packet packet=input.packet;
            if(!p.nat.enabled||in<0)return packet;
            if(input.expectedInside>=0&&(out!=input.expectedInside||p.nat.role(out)!=INSIDE))throw new IllegalArgumentException("NAT応答の内部経路が変わりました");
            if(input.forward!=null){
                if(p.nat.role(out)!=INSIDE)throw new IllegalArgumentException("ポート転送先へinside側の経路がありません");
                stage(id,out,in,packet.reverse(),input.before.reverse(),true);return packet;
            }
            if(p.nat.role(in)!=INSIDE||p.nat.role(out)!=OUTSIDE)return packet;
            for(var e:candidates(id))if(e.inside==in&&e.outside==out&&e.original.equals(packet)){touch(e);return e.wire;}
            if(!p.nat.overload)throw new IllegalArgumentException("動的NAPTはOFFです");
            if(packet.protocol()!=Protocol.TCP&&packet.protocol()!=Protocol.UDP&&packet.protocol()!=Protocol.ICMP)
                throw new IllegalArgumentException("NAPTはTCP/UDP/ICMP照会のみです");
            String external=p.ports[out].address;if(external.isBlank())throw new IllegalArgumentException("NAT外側IPが未設定です");
            long address=Ipv4.parse(external);Set<Integer> used=new HashSet<>();
            for(var e:candidates(id))if(e.wire.protocol()==packet.protocol()&&e.wire.source()==address)used.add(e.wire.sourcePort());
            for(var f:p.nat.forwards())if(f.outside()==out&&f.protocol()==packet.protocol())used.add(f.publicPort());
            int translatedPort=FIRST_PORT;while(translatedPort<=LAST_PORT&&used.contains(translatedPort))translatedPort++;
            if(translatedPort>LAST_PORT)throw new IllegalArgumentException("NAPT変換ポートが枯渇しています");
            Packet wire=new Packet(packet.protocol(),address,packet.destination(),translatedPort,packet.destinationPort());
            return stage(id,in,out,packet,wire,false).wire;
        }
        public List<Long> commit(){
            open();long following=Math.addExact(nextId,pending.size());
            // 期限計算も変更前に完了させる。予期しないオーバーフローでも半端に保存しない。
            var committed=touched.values().stream().map(NatTable.this::refreshed).toList();
            for(var e:committed)store(e);
            nextId=following;revision++;finished=true;return List.copyOf(touched.keySet());
        }
        @Override public void close(){finished=true;pending.clear();touched.clear();}
    }
}
