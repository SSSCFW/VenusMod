package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 入力を設定コピーで検証後に反映。閲覧コマンドはセッションや転送をリセットしない。 */
public final class BgpCommands {
    private BgpCommands(){}
    public static boolean handles(String[] t){return t.length>0&&(t[0].equals("bgp")||t.length>=2&&t[0].equals("show")&&t[1].equals("bgp"));}
    public static NetworkServiceCommands.Reply run(NetworkSimulator sim,String id,String[] input){
        var original=sim.nodes().get(id);if(original==null)return reply(false,"機器未登録です");
        String[] t=input;if(t[0].equals("show")){if(t.length==2)t=new String[]{"bgp"};else if(t.length==3)t=new String[]{"bgp",t[2]};else return reply(false,"show bgp [neighbors|routes|received]");}
        try{
            if(t.length==1||t.length==2&&Set.of("status","neighbors").contains(t[1])){
                var lines=new ArrayList<String>();lines.add("BGP "+(original.bgp.enabled?"ON":"OFF")+" AS "+original.bgp.asn+" ID "+(original.bgp.routerId.isBlank()?"auto":original.bgp.routerId));
                for(var peer:original.bgp.peers.values())lines.add(peer.address()+" AS"+peer.remoteAs()+" import="+peer.imports().value()+" export="+peer.exports().value());
                for(var neighbor:sim.bgp().neighbors(id))lines.add(neighbor.address()+" "+neighbor.state()+" hold="+neighbor.holdRemaining()+"t rx="+neighbor.received()+" tx="+neighbor.sent()+" routes="+neighbor.routes()+" / "+neighbor.reason());
                if(!sim.bgp().error().isEmpty())lines.add(sim.bgp().error());lines.add("bgp routes / received / help");return lines(false,lines);
            }
            if(t.length==2&&t[1].equals("routes")){
                var lines=new ArrayList<String>();for(var r:sim.bgp().routes(id))lines.add((r.local()?"L ":r.external()?"e ":"i ")+r.prefix()+" via "+r.nextHop()+" AS_PATH="+r.path()+" LP="+r.localPref()+" MED="+r.med()+(sim.bgpUsable(id,r)?"":" [下位経路が優先]"));
                if(lines.isEmpty())lines.add("BGP選択経路なし。neighbors/received・NEXT_HOP・import/exportを確認");return lines(false,lines);
            }
            if(t.length==2&&t[1].equals("received")){var values=sim.bgp().candidates(id);return lines(false,values.isEmpty()?List.of("受信経路なし"):values);}
            if(t.length==2&&t[1].equals("help"))return lines(false,help());
            if(!original.kind.routing())throw new IllegalArgumentException("BGPはルーター/L3スイッチのみです");
            var p=NetworkProfile.decode(original.kind,original.encode());p.stability=original.stability;p.processor=original.processor;p.buffer=original.buffer;p.wanTier=original.wanTier;
            for(int i=0;i<6;i++)p.ports[i].moduleTier=original.ports[i].moduleTier;
            if(t.length<2)throw new IllegalArgumentException("bgp help を確認してください");
            switch(t[1]){
                case "on","off"->{count(t,2);p.bgp.enabled=t[1].equals("on");}
                case "as"->{count(t,3);p.bgp.asn=Long.parseLong(t[2]);}
                case "router-id"->{count(t,3);p.bgp.routerId=t[2].equals("auto")?"":BgpSettings.unicast(t[2]);}
                case "timers"->{count(t,4);p.bgp.keepaliveTicks=Math.multiplyExact(Integer.parseInt(t[2]),20);p.bgp.holdTicks=Math.multiplyExact(Integer.parseInt(t[3]),20);}
                case "network"->{count(t,4);var prefix=BgpSettings.prefix(t[3]);if(t[2].equals("add"))p.bgp.networks.add(prefix);else if(t[2].equals("delete")){if(!p.bgp.networks.remove(prefix))throw new IllegalArgumentException("未登録の生成経路です");}else throw new IllegalArgumentException("bgp network add|delete CIDR");}
                case "neighbor"->{
                    if(t.length<4)throw new IllegalArgumentException("bgp neighbor IP remote-as ASN / delete");String ip=BgpSettings.unicast(t[2]);
                    if(t[3].equals("delete")){count(t,4);if(p.bgp.peers.remove(ip)==null)throw new IllegalArgumentException("未登録のピアです");break;}
                    count(t,5);var peer=p.bgp.peers.get(ip);
                    if(peer==null){if(!t[3].equals("remote-as"))throw new IllegalArgumentException("先にremote-asを設定してください");peer=BgpSettings.Peer.of(ip,Long.parseLong(t[4]));}
                    long remoteAs=peer.remoteAs(),localPref=peer.localPref(),med=peer.med();int source=peer.sourcePort(),prepend=peer.prepend();boolean self=peer.nextHopSelf();var imports=peer.imports();var exports=peer.exports();
                    switch(t[3]){
                        case "remote-as"->remoteAs=Long.parseLong(t[4]);
                        case "source"->source=t[4].equals("auto")?-1:Integer.parseInt(t[4]);
                        case "next-hop-self"->{if(!Set.of("on","off").contains(t[4]))throw new IllegalArgumentException("on/offを指定してください");self=t[4].equals("on");}
                        case "prepend"->prepend=Integer.parseInt(t[4]);
                        case "local-pref"->localPref=t[4].equals("auto")?-1:Long.parseLong(t[4]);
                        case "med"->med=t[4].equals("auto")?-1:Long.parseLong(t[4]);
                        case "import"->imports=new BgpSettings.Policy(t[4]);
                        case "export"->exports=new BgpSettings.Policy(t[4]);
                        default->throw new IllegalArgumentException("未対応のBGPピア設定です");
                    }
                    p.bgp.peers.put(ip,new BgpSettings.Peer(ip,remoteAs,source,self,prepend,localPref,med,imports,exports));
                }
                default->throw new IllegalArgumentException("bgp help を確認してください");
            }
            p.validate();p.encode();if(original.bgp.enabled&&!p.bgp.enabled)sim.bgp().shutdown(id);
            sim.put(id,p);return reply(true,"BGP設定を保存しました。OPEN/経路広告はサーバーtickで進みます。");
        }catch(IllegalArgumentException|ArithmeticException e){return reply(false,"設定を変更しませんでした: "+e.getMessage());}
    }
    public static List<String> help(){return List.of("bgp as 65001 / bgp router-id 1.1.1.1 / bgp on|off","bgp neighbor 192.0.2.2 remote-as 65002","bgp neighbor 192.0.2.2 import all|none|auto|CIDR","bgp neighbor 192.0.2.2 export all|none|auto|CIDR","eBGPは明示許可まで経路交換なし。iBGPはautoで許可。","bgp network add|delete 10.1.0.0/24 (完全一致する下位経路が必要)","bgp neighbor IP source auto|0〜5 / next-hop-self on|off","bgp neighbor IP prepend 0〜8 / local-pref auto|整数 / med auto|整数","bgp timers 1 4 (Keepalive/Hold秒、Holdは3倍以上)","show bgp / bgp neighbors / bgp routes / bgp received","eBGPは直結。iBGPはstatic/OSPFで下位到達性を用意。","iBGP学習経路を別iBGPへ再広告しません。RR/IPv6/GRは未対応。");}
    private static void count(String[] t,int n){if(t.length!=n)throw new IllegalArgumentException("引数の数が違います: bgp help");}
    private static NetworkServiceCommands.Reply reply(boolean changed,String text){return lines(changed,List.of(text));}
    private static NetworkServiceCommands.Reply lines(boolean changed,List<String> text){return new NetworkServiceCommands.Reply(true,changed,text);}
}
