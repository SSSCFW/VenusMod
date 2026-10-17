package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.util.*;

/** ACLとDHCPの永続設定。予約は期限のない手動配布用で、リース更新タイマーではない。 */
public final class NetworkServices {
    public static final int MAX_POOLS=16, MAX_BINDINGS=128, MAX_POOL_ADDRESSES=4096;
    public final NetworkPolicy policy=new NetworkPolicy();
    private final String[] relays=new String[NetworkProfile.PORT_COUNT];
    private final List<Pool> pools=new ArrayList<>();
    private final Map<BindingKey,String> bindings=new LinkedHashMap<>();
    public NetworkServices(){Arrays.fill(relays,"");}
    public String relay(int port){checkPort(port);return relays[port];}
    public void relay(int port,String address){checkPort(port);relays[port]=address.isBlank()?"":Ipv4.normalize(address);}
    public List<Pool> pools(){return List.copyOf(pools);}
    public Map<BindingKey,String> bindings(){return Collections.unmodifiableMap(bindings);}
    public record Pool(NetworkPolicy.Prefix subnet,String first,String last,String gateway,String dns) {
        public Pool {
            Objects.requireNonNull(subnet);first=Ipv4.normalize(first);last=Ipv4.normalize(last);gateway=gateway.isBlank()?"":Ipv4.normalize(gateway);dns=dns.isBlank()?"":Ipv4.normalize(dns);
            long a=Ipv4.parse(first),b=Ipv4.parse(last),g=gateway.isBlank()?0:Ipv4.parse(gateway),broadcast=subnet.network()|(~Ipv4.mask(subnet.length())&0xffffffffL);
            if(subnet.length()<1||subnet.length()>30||a>b||b-a+1>MAX_POOL_ADDRESSES||!subnet.contains(a)||!subnet.contains(b)||a==subnet.network()||b==broadcast||!gateway.isBlank()&&(!subnet.contains(g)||g==subnet.network()||g==broadcast))
                throw new IllegalArgumentException("DHCPプールは同じサブネットの有効ホスト範囲(最大4096件)、GWも同じLANにしてください");
        }
        public boolean contains(String ip){long n=Ipv4.parse(ip);return n>=Ipv4.parse(first)&&n<=Ipv4.parse(last);}
    }
    public record BindingKey(String client,String subnet) {
        public BindingKey {if(client==null||client.isBlank()||client.length()>64||client.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("DHCP端末IDが不正です");subnet=NetworkPolicy.Prefix.parse(subnet).toString();}
    }
    public void addPool(Pool pool){
        if(pools.size()>=MAX_POOLS)throw new IllegalArgumentException("DHCPプールは16件までです");
        if(pools.stream().anyMatch(p->p.subnet.contains(pool.subnet.network())||pool.subnet.contains(p.subnet.network())))throw new IllegalArgumentException("DHCPプールのサブネットが重複しています");
        pools.add(pool);
    }
    public void removePool(String subnet){String normalized=NetworkPolicy.Prefix.parse(subnet).toString();if(!pools.removeIf(p->p.subnet.toString().equals(normalized)))throw new IllegalArgumentException("指定プールはありません");bindings.keySet().removeIf(k->k.subnet.equals(normalized));}
    public void clearPools(){pools.clear();bindings.clear();}
    public void clearBindings(){bindings.clear();}
    public Pool poolFor(String giaddr){long ip=Ipv4.parse(giaddr);return pools.stream().filter(p->p.subnet.contains(ip)).findFirst().orElse(null);}
    public String reserved(String client,Pool pool){return bindings.get(new BindingKey(client,pool.subnet.toString()));}
    public Set<String> reservedByOthers(String client){Set<String> result=new HashSet<>();bindings.forEach((k,v)->{if(!k.client.equals(client))result.add(v);});return result;}
    public void reserve(String client,Pool pool,String address){
        BindingKey key=new BindingKey(client,pool.subnet.toString());
        if(!bindings.containsKey(key)&&bindings.size()>=MAX_BINDINGS)throw new IllegalArgumentException("DHCP配布予約が128件に達しました。不要な予約を管理者が解除してください");
        if(!pool.contains(address))throw new IllegalArgumentException("DHCP予約アドレスがプール外です");bindings.put(key,Ipv4.normalize(address));
    }
    public void undoReserve(String client,Pool pool,String previous){var key=new BindingKey(client,pool.subnet.toString());if(previous==null)bindings.remove(key);else bindings.put(key,previous);}
    public void validate(NetworkProfile profile){
        if(!profile.kind.routing()&&(!policy.rules().isEmpty()||Arrays.stream(relays).anyMatch(s->!s.isBlank())))throw new IllegalArgumentException("ACL・DHCPリレーはL3機器のみです");
        if(!profile.kind.routing()&&profile.kind!=DeviceKind.SERVER&&(!pools.isEmpty()||!bindings.isEmpty()))throw new IllegalArgumentException("DHCPプールはサーバーまたはルーターで設定します");
    }
    public void write(DataOutput out)throws IOException {
        policy.write(out);for(String relay:relays)out.writeUTF(relay);
        out.writeInt(pools.size());for(Pool p:pools){out.writeUTF(p.subnet.toString());out.writeUTF(p.first);out.writeUTF(p.last);out.writeUTF(p.gateway);out.writeUTF(p.dns);}
        out.writeInt(bindings.size());for(var e:bindings.entrySet()){out.writeUTF(e.getKey().client);out.writeUTF(e.getKey().subnet);out.writeUTF(e.getValue());}
    }
    public void read(DataInput in)throws IOException {
        policy.read(in);for(int i=0;i<relays.length;i++)relay(i,in.readUTF());
        int count=in.readInt();if(count<0||count>MAX_POOLS)throw new IllegalArgumentException("保存プール件数が不正です");
        for(int i=0;i<count;i++)addPool(new Pool(NetworkPolicy.Prefix.parse(in.readUTF()),in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF()));
        count=in.readInt();if(count<0||count>MAX_BINDINGS)throw new IllegalArgumentException("保存配布予約件数が不正です");
        for(int i=0;i<count;i++){var key=new BindingKey(in.readUTF(),in.readUTF());String value=Ipv4.normalize(in.readUTF());if(!NetworkPolicy.Prefix.parse(key.subnet).contains(Ipv4.parse(value))||bindings.putIfAbsent(key,value)!=null)throw new IllegalArgumentException("DHCP予約が不正です");}
    }
    private static void checkPort(int port){if(port<0||port>=NetworkProfile.PORT_COUNT)throw new IllegalArgumentException("機器ポートは0〜5です");}
}
