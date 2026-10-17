package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 追加サービスのCLI。検証用コピーを完成してから反映し、誤入力で既存設定を壊さない。 */
public final class NetworkServiceCommands {
    public record Reply(boolean handled,boolean changed,List<String> lines){}
    private static final Set<String> COMMANDS=Set.of("acl","dhcp-relay","dhcp-pool","dhcp-bindings");
    private NetworkServiceCommands(){}
    public static Reply run(NetworkSimulator simulator,String id,String input){
        if(input==null||input.length()>512||input.codePoints().anyMatch(Character::isISOControl))return reply(true,false,"改行なし512文字以内で入力してください");
        String[] t=input.trim().split("\\s+");if(!COMMANDS.contains(t[0]))return new Reply(false,false,List.of());
        NetworkProfile old=simulator.nodes().get(id);if(old==null)return reply(true,false,"機器が未登録です");
        try {
            NetworkProfile p=NetworkProfile.decode(old.kind,old.encode());p.stability=old.stability;p.processor=old.processor;p.buffer=old.buffer;p.wanTier=old.wanTier;
            for(int i=0;i<p.ports.length;i++)p.ports[i].moduleTier=old.ports[i].moduleTier;
            switch(t[0]){
                case "acl" -> {
                    if(t.length==1)return new Reply(true,false,p.services.policy.rules().isEmpty()?List.of("ACL未適用 (すべて許可)"):p.services.policy.rules().stream().map(NetworkPolicy.Rule::description).toList());
                    if(!p.kind.routing())throw new IllegalArgumentException("ACLはルーター・L3スイッチのみです");
                    if(t.length<4)throw new IllegalArgumentException("acl add PORT in|out SEQ permit|deny PROTO SRC DST [DST-PORT]");
                    int port=port(t[2]);var direction=NetworkPolicy.Direction.valueOf(t[3].toUpperCase(Locale.ROOT));
                    switch(t[1]){
                        case "add" -> {
                            if(t.length!=9&&t.length!=10)throw new IllegalArgumentException("acl add 0 in 10 permit tcp 10.0.1.0/24 any 80");
                            if(!Set.of("permit","deny").contains(t[5]))throw new IllegalArgumentException("permit または deny を指定してください");
                            var protocol=NetworkPolicy.Protocol.valueOf(t[6].toUpperCase(Locale.ROOT));
                            p.services.policy.add(new NetworkPolicy.Rule(port,direction,Integer.parseInt(t[4]),t[5].equals("permit"),protocol,NetworkPolicy.Prefix.parse(t[7]),NetworkPolicy.Prefix.parse(t[8]),t.length==9||t[9].equals("any")?-1:Integer.parseInt(t[9])));
                        }
                        case "delete" -> {count(t,5);p.services.policy.remove(port,direction,Integer.parseInt(t[4]));}
                        case "clear" -> {count(t,4);p.services.policy.clear(port,direction);}
                        default -> throw new IllegalArgumentException("ACLはadd/delete/clearを指定します");
                    }
                }
                case "dhcp-relay" -> {
                    if(t.length==1){List<String> lines=new ArrayList<>();for(int i=0;i<6;i++)lines.add("P"+i+" → "+(p.services.relay(i).isBlank()?"リレーなし":p.services.relay(i)));return new Reply(true,false,lines);}
                    count(t,3);if(!p.kind.routing())throw new IllegalArgumentException("DHCPリレーはL3機器のみです");p.services.relay(port(t[1]),t[2].equals("none")?"":t[2]);
                }
                case "dhcp-pool" -> {
                    if(t.length==1)return new Reply(true,false,p.services.pools().isEmpty()?List.of("明示プールなし (直結LANのみ簡易自動プール)"):p.services.pools().stream().map(pool->pool.subnet()+" "+pool.first()+"〜"+pool.last()+" GW "+pool.gateway()+" DNS "+pool.dns()).toList());
                    if(!p.kind.routing()&&p.kind!=DeviceKind.SERVER)throw new IllegalArgumentException("DHCPプールはルーター/サーバーのみです");
                    switch(t[1]){
                        case "add" -> {count(t,7);p.services.addPool(new NetworkServices.Pool(NetworkPolicy.Prefix.parse(t[2]),t[3],t[4],t[5].equals("none")?"":t[5],t[6].equals("none")?"":t[6]));}
                        case "delete" -> {count(t,3);p.services.removePool(t[2]);}
                        case "clear" -> {count(t,2);p.services.clearPools();}
                        default -> throw new IllegalArgumentException("dhcp-pool add CIDR FIRST LAST GW DNS / delete CIDR / clear");
                    }
                }
                case "dhcp-bindings" -> {
                    if(t.length==1){List<String> lines=new ArrayList<>();lines.add("配布予約: "+p.services.bindings().size()+"/128 (期限なし・自動更新なし)");p.services.bindings().entrySet().stream().limit(60).forEach(e->lines.add(e.getKey().client()+" "+e.getKey().subnet()+" → "+e.getValue()));return new Reply(true,false,lines);}
                    count(t,2);if(!t[1].equals("clear"))throw new IllegalArgumentException("dhcp-bindings clear");p.services.clearBindings();
                }
            }
            p.validate();p.encode();simulator.put(id,p);simulator.invalidate();
            return reply(true,true,"設定を保存しました: "+input+(t[0].equals("acl")?" / 規則を設定した方向は未一致通信を拒否します":""));
        }catch(IllegalArgumentException e){return reply(true,false,"設定を変更しませんでした: "+e.getMessage());}
    }
    private static int port(String text){int n=Integer.parseInt(text);if(n<0||n>=6)throw new IllegalArgumentException("機器ポートは0〜5です");return n;}
    private static void count(String[] t,int n){if(t.length!=n)throw new IllegalArgumentException("引数の数が違います。ガイドを確認してください");}
    private static Reply reply(boolean handled,boolean changed,String text){return new Reply(handled,changed,List.of(text));}
}
