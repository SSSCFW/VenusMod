package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** GUIが既に使うコマンド経路に載せる。閲覧で設定やタイマーをリセットしない。 */
public final class OspfCommands {
    private OspfCommands(){}
    public static boolean handles(String[] t){
        return t.length>0&&(t[0].equals("ospf")||String.join(" ",t).equals("show ip route")
                ||t.length>=3&&t[0].equals("show")&&t[1].equals("ip")&&t[2].equals("ospf"));
    }
    public static NetworkServiceCommands.Reply run(NetworkSimulator sim,String id,String[] input){
        NetworkProfile old=sim.nodes().get(id);if(old==null)return reply(false,"機器が未登録です");
        String[] t=input;
        if(t[0].equals("show")){
            if(String.join(" ",t).equals("show ip route"))return routes(sim,id,old,true);
            if(t.length==4)t=new String[]{"ospf",t[3]};else t=new String[]{"ospf"};
        }
        if(t.length==1||t.length==2&&t[1].equals("status")){
            List<String> lines=new ArrayList<>();lines.add("OSPF "+(old.ospf.enabled?"ON":"OFF")+" / 単一エリア内サブセット");
            lines.add("Router ID: "+(old.ospf.routerId.isBlank()?"auto (最大の機器IP)":old.ospf.routerId)+" / Area "+old.ospf.area);
            lines.add("Hello "+old.ospf.helloTicks/20+"秒 / Dead "+old.ospf.deadTicks/20+"秒");
            for(int n=0;n<6;n++)lines.add("P"+n+" "+old.ospf.modes[n]+" cost="+(old.ospf.costs[n]==0?"auto":old.ospf.costs[n]));
            lines.add("状態: "+(sim.ospf().error().isBlank()?"隣接"+sim.ospf().neighbors(id).size()+" / 学習経路"+sim.ospf().routes(id).size():sim.ospf().error()));
            lines.add("ospf neighbors / database / routes / help");return new NetworkServiceCommands.Reply(true,false,lines);
        }
        if(t.length==2)switch(t[1]){
            case "neighbors","neighbor"->{List<String> lines=new ArrayList<>();for(var n:sim.ospf().neighbors(id))lines.add(n.routerId()+" "+n.state()+" P"+n.localPort()+" next-hop="+n.address()+" dead="+n.deadInTicks()+"tick");if(lines.isEmpty())lines.add("隣接なし: ポート有効化・IP/VLAN・Area・Hello/Dead・ACLを確認");return new NetworkServiceCommands.Reply(true,false,lines);}
            case "database"->{List<String> lines=new ArrayList<>();for(var l:sim.ospf().database(id))lines.add("Router-LSA "+l.routerId()+" seq="+l.sequence()+" links="+l.links()+" prefixes="+l.prefixes()+" remain="+l.remainingTicks());return new NetworkServiceCommands.Reply(true,false,lines);}
            case "routes"->{return routes(sim,id,old,false);}
            case "help"->{return new NetworkServiceCommands.Reply(true,false,List.of("ospf router-id 1.1.1.1 (または auto)","ospf area 0 / ospf on / ospf off","ospf port 0 on|off|passive","ospf cost 0 10 (または auto)","ospf timers 1 4 (秒単位)","ospf neighbors / database / routes","show ip route / show ip ospf neighbor","同じArea・サブネット・Hello/Deadが必要。全隣接方式。","接続図のガイドで設定例を確認できます。","ABR/DR/BDR/認証/外部経路再配布/ECMPは未対応。"));}
        }
        try{
            if(!old.kind.routing())throw new IllegalArgumentException("OSPFはルーター/L3スイッチのみです");
            var p=NetworkProfile.decode(old.kind,old.encode());p.stability=old.stability;p.processor=old.processor;p.buffer=old.buffer;p.wanTier=old.wanTier;
            for(int i=0;i<6;i++)p.ports[i].moduleTier=old.ports[i].moduleTier;
            switch(t[1]){
                case "on","off"->{count(t,2);p.ospf.enabled=t[1].equals("on");}
                case "router-id"->{count(t,3);p.ospf.routerId=t[2].equals("auto")?"":Ipv4.format(OspfSettings.unicast(t[2]));}
                case "area"->{count(t,3);p.ospf.area=t[2].contains(".")?Ipv4.parse(t[2]):Long.parseLong(t[2]);}
                case "port"->{count(t,4);p.ospf.modes[port(t[2])]=switch(t[3]){case "on"->OspfSettings.Mode.ACTIVE;case "off"->OspfSettings.Mode.OFF;case "passive"->OspfSettings.Mode.PASSIVE;default->throw new IllegalArgumentException("on/off/passive");};}
                case "cost"->{count(t,4);int value=t[3].equals("auto")?0:Integer.parseInt(t[3]);if(value==0&&!t[3].equals("auto"))throw new IllegalArgumentException("コストは1〜65535またはautoです");p.ospf.costs[port(t[2])]=value;}
                case "timers"->{count(t,4);p.ospf.helloTicks=Math.multiplyExact(Integer.parseInt(t[2]),20);p.ospf.deadTicks=Math.multiplyExact(Integer.parseInt(t[3]),20);}
                default->throw new IllegalArgumentException("ospf help を参照してください");
            }
            p.validate();p.encode();sim.put(id,p);sim.invalidate(id);
            return reply(true,"OSPF設定を保存しました。稼働tickでHelloと経路広告を開始します。");
        }catch(IllegalArgumentException|ArithmeticException error){return reply(false,"設定を変更しませんでした: "+error.getMessage());}
    }
    private static NetworkServiceCommands.Reply routes(NetworkSimulator sim,String id,NetworkProfile p,boolean all){
        List<String> lines=new ArrayList<>();
        if(all){for(int i=0;i<6;i++)if(!p.ports[i].address.isBlank())lines.add("C "+NetworkPolicy.Prefix.parse(p.ports[i].address+"/"+p.ports[i].prefix)+" P"+i);for(var r:p.routes)lines.add("S "+r.network()+"/"+r.prefix()+" via "+r.gateway()+" P"+r.port());}
        for(var r:sim.ospf().routes(id))lines.add("O "+r.network()+"/"+r.prefix()+" cost="+r.cost()+" via "+r.gateway()+" P"+r.port());
        if(all)for(var r:sim.bgp().routes(id))if(!r.local()&&sim.bgpUsable(id,r))lines.add("B "+r.prefix()+" via "+r.nextHop()+" AS_PATH="+r.path()+" "+(r.external()?"eBGP":"iBGP"));
        if(lines.isEmpty())lines.add("学習経路なし。ネイバーとLSDB/BGPを確認してください。");
        return new NetworkServiceCommands.Reply(true,false,lines);
    }
    private static int port(String s){int n=Integer.parseInt(s);if(n<0||n>=6)throw new IllegalArgumentException("ポート番号は0〜5です");return n;}
    private static void count(String[] t,int n){if(t.length!=n)throw new IllegalArgumentException("引数の数が違います: ospf help");}
    private static NetworkServiceCommands.Reply reply(boolean changed,String text){return new NetworkServiceCommands.Reply(true,changed,List.of(text));}
}
