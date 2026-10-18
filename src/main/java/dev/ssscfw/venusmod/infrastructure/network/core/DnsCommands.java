package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 元設定と稼働フローを変更せず検証し、DNS設定だけのコピーを反映する。 */
public final class DnsCommands {
    private static final Set<String> COMMANDS=Set.of("dns-record","dns-server","dns-negative-ttl","dns-cache","nslookup");
    private DnsCommands(){}
    public static boolean handles(String[] t){return t.length>0&&COMMANDS.contains(t[0].toLowerCase(Locale.ROOT));}
    public static NetworkServiceCommands.Reply run(NetworkSimulator sim,String id,String[] t){
        var original=sim.nodes().get(id);if(original==null)return reply(false,List.of("機器未登録です"));
        try{
            String cmd=t[0].toLowerCase(Locale.ROOT);
            if(cmd.equals("nslookup")){
                if(t.length<2||t.length>3)throw new IllegalArgumentException("nslookup NAME [DNS-IP]");
                return reply(false,List.of(sim.dnsResolver().query(id,t[1],t.length==3?t[2]:null).message()));
            }
            if(cmd.equals("dns-cache")){
                if(t.length==2&&t[1].equals("clear")){sim.dnsResolver().clear(id);return reply(false,List.of("自機のDNSキャッシュを消去しました"));}
                count(t,1);return reply(false,sim.dnsResolver().describe(id));
            }
            if(t.length==2&&t[1].equals("help"))return reply(false,help());
            if(!original.kind.routing()&&original.kind!=DeviceKind.SERVER)throw new IllegalArgumentException("DNS設定はサーバー/ルーター/L3スイッチのみです");
            if(cmd.equals("dns-record")&&(t.length==1||t.length==2&&t[1].equals("list"))){
                var lines=new ArrayList<String>();lines.add("DNS "+(original.dnsSettings.enabled?"ON":"OFF")+" / 否定TTL "+original.dnsSettings.negativeTtl+"秒");
                for(String name:original.dnsRecords.keySet())try{var r=original.dnsSettings.records(original,name);lines.add(r.name()+" TTL="+r.ttl()+" "+String.join(", ",r.addresses()));}
                    catch(IllegalArgumentException e){lines.add("旧レコード "+name+": "+original.dnsRecords.get(name)+" (要修正)");}
                return reply(false,lines);
            }
            var p=NetworkProfile.decode(original.kind,original.encode());p.stability=original.stability;p.processor=original.processor;p.buffer=original.buffer;p.wanTier=original.wanTier;
            for(int i=0;i<6;i++)p.ports[i].moduleTier=original.ports[i].moduleTier;
            switch(cmd){
                case "dns-server"->{count(t,2);if(!Set.of("on","off").contains(t[1]))throw new IllegalArgumentException("dns-server on|off");p.dnsSettings.enabled=t[1].equals("on");}
                case "dns-negative-ttl"->{count(t,2);p.dnsSettings.negativeTtl=Integer.parseInt(t[1]);}
                case "dns-record"->{
                    if(t.length>=5&&t[1].equals("set"))p.dnsSettings.set(p,t[2],Integer.parseInt(t[3]),Arrays.asList(t).subList(4,t.length));
                    else if(t.length==4&&t[1].equals("ttl"))p.dnsSettings.ttl(p,t[2],Integer.parseInt(t[3]));
                    else if(t.length==3&&t[1].equals("delete"))p.dnsSettings.delete(p,t[2]);
                    else if(t.length==3&&!Set.of("set","ttl","delete","list","help").contains(t[1]))p.dnsSettings.set(p,t[1],DnsSettings.DEFAULT_TTL,List.of(t[2]));
                    else throw new IllegalArgumentException("dns-record NAME IP / set NAME TTL IP... / ttl NAME SECONDS / delete NAME");
                }
                default->throw new IllegalArgumentException("DNSコマンドが不正です");
            }
            p.validate();p.encode();sim.applyDnsSettings(id,p);return reply(true,List.of("DNS設定を保存しました。既存クライアントのTTLは満了まで保持します。"));
        }catch(IllegalArgumentException e){return reply(false,List.of("設定を変更しませんでした: "+e.getMessage()));}
    }
    private static void count(String[] t,int n){if(t.length!=n)throw new IllegalArgumentException("引数の数が違います: dns-record help");}
    private static NetworkServiceCommands.Reply reply(boolean changed,List<String> lines){return new NetworkServiceCommands.Reply(true,changed,List.copyOf(lines));}
    public static List<String> help(){return List.of("dns-record base.local 192.168.1.20 (TTL既定60秒)","dns-record set base.local 10 192.168.1.20 192.168.1.21","dns-record ttl base.local 0 / dns-record delete base.local / dns-record list","dns-server on|off / dns-negative-ttl 10","dns-cache / dns-cache clear (自機のみ)","nslookup base.local [DNS-IP] は毎回問い合わせ、キャッシュを読み書きしません。","ping NAME / open NAME[:PORT] / transfer NAME[:PORT] 100 / redstone NAME[:PORT] 15","アプリは回答順をTTL中保持。ラウンドロビンは稼働監視ではありません。","TTL=0は保存なし。TTL満了/clearで再問い合わせ。外部DNS/URIは使いません。");}
}
