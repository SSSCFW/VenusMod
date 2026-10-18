package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 機器ごとの有限キャッシュ。権威データ変更で遠隔のキャッシュを勝手に消さない。 */
public final class DnsResolver {
    public static final int MAX_PER_CLIENT=64,MAX_CACHE=4096,MAX_CURSORS=2048,EXPIRATIONS_PER_TICK=32;
    public record Answer(String name,List<String> addresses,long ttlTicks,boolean cached,boolean negative,String error,NetworkSimulator.Result transport){
        public Answer{addresses=List.copyOf(addresses);}
        public boolean success(){return error.isEmpty()&&!negative&&!addresses.isEmpty();}
        public String selected(){return success()?addresses.getFirst():"";}
        public String message(){return !error.isEmpty()?error:name+" = "+(negative?"NXDOMAIN":String.join(", ",addresses))+" / TTL "+ttlTicks+" tick / "+(cached?"CACHE":"QUERY");}
    }
    private record Key(String client,String name){}
    private record Entry(Key key,List<String> addresses,boolean negative,long due){}
    private static final class Client {
        final String context;
        final Set<Key> keys=new LinkedHashSet<>();
        Client(String context){this.context=context;}
    }
    private record CursorKey(String server,String name){}
    private record Cursor(List<String> addresses,int next){}
    private final NetworkSimulator sim;
    private final Map<String,Client> clients=new HashMap<>();
    private final LinkedHashMap<Key,Entry> cache=new LinkedHashMap<>();
    private final NavigableSet<Entry> deadlines=new TreeSet<>(Comparator.comparingLong(Entry::due).thenComparing(e->e.key.client).thenComparing(e->e.key.name));
    private final LinkedHashMap<CursorKey,Cursor> cursors=new LinkedHashMap<>();
    private long clock;
    public DnsResolver(NetworkSimulator simulator){sim=simulator;}
    public int cacheSize(){return cache.size();}
    public int deadlineCount(){return deadlines.size();}
    public int cursorCount(){return cursors.size();}
    public long clock(){return clock;}
    private static String context(NetworkProfile p){
        StringBuilder text=new StringBuilder().append(p.address).append('/').append(p.prefix).append('|').append(p.gateway).append('|').append(p.dns);
        for(var port:p.ports)text.append('|').append(port.address).append('/').append(port.prefix).append(':').append(port.vlan).append(':').append(port.enabled).append(':').append(port.trunk).append(':').append(port.optical);
        return text.toString();
    }
    void synchronizeClient(String id,NetworkProfile profile){var c=clients.get(id);if(c!=null&&!c.context.equals(context(profile)))clear(id);}
    public Answer lookup(String client,String raw){return lookup(client,raw,null,true);}
    /** 診断は常に直接問い合わせ、アプリケーションキャッシュを読み書きしない。 */
    public Answer query(String client,String raw,String server){return lookup(client,raw,server,false);}
    private Answer lookup(String client,String raw,String explicitServer,boolean useCache){
        try {
            String name=DnsNames.name(raw);NetworkProfile source=sim.nodes().get(client);
            if(source==null||source.failed)return error(name,"DNS: 送信元が停止しています");
            String server=explicitServer==null?source.dns:explicitServer;
            if(server.isBlank())return error(name,"DNSサーバー未設定");server=Ipv4.normalize(server);
            if(useCache){
                synchronizeClient(client,source);Entry e=cache.get(new Key(client,name));
                if(e!=null){if(e.due>clock)return new Answer(name,e.addresses,e.due-clock,true,e.negative,"",null);remove(e);}
            }
            var path=sim.requestService(client,server,"udp",53,53);
            if(!path.success())return error(name,"DNS問い合わせ失敗: "+path.message());
            var authority=sim.nodes().get(path.destination());
            if(!DnsSettings.available(authority))return error(name,"DNSサービスが停止または非対応です");
            var records=authority.dnsSettings.records(authority,name);
            boolean negative=records.addresses().isEmpty();List<String> ordered=records.addresses();
            if(!negative&&ordered.size()>1){
                var key=new CursorKey(path.destination(),name);var current=cursors.get(key);
                int start=current==null||!current.addresses.equals(ordered)?0:current.next;
                List<String> rotated=new ArrayList<>();for(int i=0;i<ordered.size();i++)rotated.add(ordered.get((start+i)%ordered.size()));
                cursors.remove(key);if(cursors.size()>=MAX_CURSORS)cursors.remove(cursors.keySet().iterator().next());
                cursors.put(key,new Cursor(ordered,(start+1)%ordered.size()));ordered=List.copyOf(rotated);
            }
            long ticks=records.ttl()*20L;
            if(useCache&&ticks>0&&clock<Long.MAX_VALUE-ticks)store(client,source,name,ordered,negative,clock+ticks);
            return new Answer(name,ordered,ticks,false,negative,"",path);
        }catch(IllegalArgumentException e){return error(raw==null?"":raw,"DNS: "+e.getMessage());}
    }
    private static Answer error(String name,String message){return new Answer(name,List.of(),0,false,false,message,null);}
    private void store(String client,NetworkProfile profile,String name,List<String> addresses,boolean negative,long due){
        Key key=new Key(client,name);Entry old=cache.get(key);if(old!=null)remove(old);
        Client local=clients.get(client);
        if(local!=null&&local.keys.size()>=MAX_PER_CLIENT)remove(cache.get(local.keys.iterator().next()));
        if(cache.size()>=MAX_CACHE)remove(cache.values().iterator().next());
        local=clients.computeIfAbsent(client,id->new Client(context(profile)));
        Entry value=new Entry(key,addresses,negative,due);local.keys.add(key);cache.put(key,value);deadlines.add(value);
    }
    private void remove(Entry e){
        if(e==null)return;cache.remove(e.key);deadlines.remove(e);Client local=clients.get(e.key.client);
        if(local!=null){local.keys.remove(e.key);if(local.keys.isEmpty())clients.remove(e.key.client);}
    }
    public void clear(String client){Client local=clients.get(client);if(local!=null)for(Key key:List.copyOf(local.keys))remove(cache.get(key));}
    public void removeDevice(String id){clear(id);cursors.keySet().removeIf(k->k.server.equals(id));}
    public void advance(long ticks){
        if(ticks<=0)return;clock=ticks>Long.MAX_VALUE-clock?Long.MAX_VALUE:clock+ticks;
        int count=0;while(!deadlines.isEmpty()&&deadlines.first().due<=clock&&count++<EXPIRATIONS_PER_TICK)remove(deadlines.first());
    }
    public List<String> describe(String client){
        var source=sim.nodes().get(client);if(source==null)return List.of("機器未登録");synchronizeClient(client,source);
        List<String> lines=new ArrayList<>();Client local=clients.get(client);
        if(local!=null)for(Key key:List.copyOf(local.keys)){
            Entry e=cache.get(key);if(e.due<=clock){remove(e);continue;}
            lines.add(key.name+" → "+(e.negative?"NXDOMAIN":String.join(", ",e.addresses))+" / 残り "+(e.due-clock)+" tick");
        }
        if(lines.isEmpty())lines.add("DNSキャッシュは空です");return List.copyOf(lines);
    }
}
