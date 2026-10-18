package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.util.*;

/** 旧版の一次Aレコードを維持し、TTL・追加Aレコードだけを拡張保存する。 */
public final class DnsSettings {
    public static final int MAX_RECORDS=64,MAX_ADDRESSES=8,DEFAULT_TTL=60,MAX_TTL=86400,MAX_NEGATIVE_TTL=300;
    public boolean enabled=true;
    public int negativeTtl=10;
    public record Options(int ttl,List<String> additional){
        public Options{
            DnsSettings.ttl(ttl);if(additional==null||additional.size()>=MAX_ADDRESSES)throw new IllegalArgumentException("同じ名前のAレコードは8個までです");
            additional=additional.stream().map(Ipv4::normalize).toList();
            if(new HashSet<>(additional).size()!=additional.size())throw new IllegalArgumentException("同じIPを重複登録できません");
        }
    }
    public record Records(String name,List<String> addresses,int ttl){public Records{addresses=List.copyOf(addresses);}}
    private final Map<String,Options> options=new LinkedHashMap<>();
    public static boolean available(NetworkProfile profile){return profile!=null&&!profile.failed&&(profile.kind==DeviceKind.SERVER||profile.kind.routing())&&profile.dnsSettings.enabled;}
    private static void ttl(int value){if(value<0||value>MAX_TTL)throw new IllegalArgumentException("TTLは0〜86400秒です");}
    private static String primaryKey(NetworkProfile p,String name){
        String found=null;for(String key:p.dnsRecords.keySet())if(DnsNames.legacyKey(key).equals(name)){
            if(found!=null)throw new IllegalArgumentException("同名DNSレコードが競合しています: "+name);found=key;
        }return found;
    }
    public Records records(NetworkProfile p,String name){
        name=DnsNames.name(name);String key=primaryKey(p,name);
        if(key==null)return new Records(name,List.of(),negativeTtl);
        Options opt=options.getOrDefault(name,new Options(DEFAULT_TTL,List.of()));
        List<String> addresses=new ArrayList<>();addresses.add(Ipv4.normalize(p.dnsRecords.get(key)));addresses.addAll(opt.additional);
        if(new HashSet<>(addresses).size()!=addresses.size())throw new IllegalArgumentException("DNSレコードのIPが重複しています");
        return new Records(name,addresses,opt.ttl);
    }
    public void set(NetworkProfile p,String name,int ttl,List<String> addresses){
        name=DnsNames.name(name);ttl(ttl);
        if(addresses==null||addresses.isEmpty()||addresses.size()>MAX_ADDRESSES)throw new IllegalArgumentException("Aレコードは1〜8個です");
        var normalized=addresses.stream().map(Ipv4::normalize).toList();
        if(new HashSet<>(normalized).size()!=normalized.size())throw new IllegalArgumentException("同じIPを重複登録できません");
        final String canonical=name;
        long other=p.dnsRecords.keySet().stream().filter(k->!DnsNames.legacyKey(k).equals(canonical)).count();
        if(other>=MAX_RECORDS)throw new IllegalArgumentException("DNS名は64件までです");
        p.dnsRecords.keySet().removeIf(k->DnsNames.legacyKey(k).equals(canonical));p.dnsRecords.put(canonical,normalized.getFirst());
        options.put(canonical,new Options(ttl,normalized.subList(1,normalized.size())));
    }
    public void delete(NetworkProfile p,String raw){
        // 旧版で保存できた不正な区切りの名前も明示的に削除できる。
        if(raw==null||!raw.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}"))throw new IllegalArgumentException("削除するDNS名が不正です");
        String name=DnsNames.legacyKey(raw);p.dnsRecords.keySet().removeIf(k->DnsNames.legacyKey(k).equals(name));options.remove(name);
    }
    public void ttl(NetworkProfile p,String name,int value){var records=records(p,name);if(records.addresses.isEmpty())throw new IllegalArgumentException("レコードが存在しません");set(p,name,value,records.addresses);}
    public void validate(NetworkProfile p){
        if(negativeTtl<0||negativeTtl>MAX_NEGATIVE_TTL)throw new IllegalArgumentException("否定応答TTLは0〜300秒です");
        if(options.size()>MAX_RECORDS)throw new IllegalArgumentException("DNS設定件数の上限です");
        for(var e:options.entrySet()){
            if(!DnsNames.name(e.getKey()).equals(e.getKey())||primaryKey(p,e.getKey())==null)throw new IllegalArgumentException("DNS追加設定の参照が不正です");
            records(p,e.getKey());
        }
    }
    void copyFrom(DnsSettings other){enabled=other.enabled;negativeTtl=other.negativeTtl;options.clear();options.putAll(other.options);}
    public void write(DataOutputStream out)throws IOException{
        out.writeBoolean(enabled);out.writeInt(negativeTtl);out.writeInt(options.size());
        for(var e:options.entrySet()){out.writeUTF(e.getKey());out.writeInt(e.getValue().ttl);out.writeInt(e.getValue().additional.size());for(String address:e.getValue().additional)out.writeUTF(address);}
    }
    public void read(DataInputStream in)throws IOException{
        enabled=in.readBoolean();negativeTtl=in.readInt();int count=in.readInt();
        if(count<0||count>MAX_RECORDS)throw new IllegalArgumentException("DNS設定件数の上限です");options.clear();
        for(int i=0;i<count;i++){
            String key=in.readUTF();int ttl=in.readInt(),size=in.readInt();if(size<0||size>=MAX_ADDRESSES)throw new IllegalArgumentException("DNS回答数の上限です");
            List<String> extra=new ArrayList<>();for(int n=0;n<size;n++)extra.add(in.readUTF());
            if(options.putIfAbsent(key,new Options(ttl,extra))!=null)throw new IllegalArgumentException("DNS追加設定名が重複しています");
        }
    }
}
