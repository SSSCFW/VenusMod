package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** STP設定はコピーを検証してから反映。status/guideはタイマーを進めない。 */
public final class StpCommands {
    private StpCommands(){}
    public static boolean handles(String[] tokens){return tokens.length>0&&(tokens[0].equals("stp")||tokens.length>1&&tokens[0].equals("show")&&tokens[1].equals("spanning-tree"));}
    public static NetworkServiceCommands.Reply run(NetworkSimulator sim,String id,String[] input){
        var old=sim.nodes().get(id);if(old==null)return reply(false,"機器未登録");
        String[] t=input[0].equals("show")?(input.length==3?new String[]{"stp","status",input[2]}:input.length==2?new String[]{"stp","status"}:new String[]{"stp","invalid"}):input;
        try {
            if(!old.kind.bridge())throw new IllegalArgumentException("STPはL2中継機器のみです");
            if(t.length==1||t[1].equals("status")){
                if(t.length>3)throw new IllegalArgumentException("stp status [VLAN]");int selected=t.length==3?Integer.parseInt(t[2]):-1;
                if(t.length==3&&(selected<1||selected>4094))throw new IllegalArgumentException("VLANは1〜4094です");
                List<String> lines=new ArrayList<>();lines.add("STP "+(old.stp?"ON":"OFF")+" / "+(old.spanning.classic?"classic: BPDU・タイマー":"simple: 即時の簡略木方式"));
                if(!old.spanning.classic){lines.add("stp mode classic でBPDU方式。接続する全L2機器を同じ方式にしてください。");return new NetworkServiceCommands.Reply(true,false,lines);}
                lines.add("BID priority="+old.spanning.priority+" / "+NetworkSimulator.macAddress(id));
                lines.add("設定タイマー Hello/MaxAge/ForwardDelay="+old.spanning.helloTicks/20+"/"+old.spanning.maxAgeTicks/20+"/"+old.spanning.forwardDelayTicks/20+"秒 (運用値はrootから受信)");
                if(!sim.stp().error().isBlank())lines.add(sim.stp().error());
                var keys=sim.stpKeys(id);if(keys.isEmpty())lines.add("稼働tick待ち / classic無効・故障・未登録");
                int displayed=0;for(var key:keys){if(selected!=-1&&key.vlan()!=selected)continue;if(displayed++>=4){lines.add("以降は stp status VLAN番号 で確認");break;}
                    var s=sim.stp().status(key);lines.add("VLAN "+key.vlan()+" Root "+s.root()+" cost="+s.rootCost()+" RP="+s.rootPort());
                    for(int n=0;n<s.ports().size();n++){var p=s.ports().get(n);lines.add(" P"+n+" "+p.role()+"/"+p.state()+" cost="+p.cost()+" BPDU tx/rx="+p.sent()+"/"+p.received()+" wait="+p.expiresInTicks()+"tick");String issue=sim.stpIssue(id,n);if(!issue.isEmpty())lines.add("  "+issue);}
                }
                return new NetworkServiceCommands.Reply(true,false,lines);
            }
            if(t[1].equals("help")){count(t,2);return new NetworkServiceCommands.Reply(true,false,List.of("stp mode simple|classic / stp on|off / show spanning-tree [VLAN]","stp priority 4096 / stp vlan 10 priority 8192","stp port 0 cost 20000|auto / stp port 0 priority 128","stp port 0 edge on|off / stp port 0 guard on|off / stp port 0 reset","stp timers 2 20 15 (Hello / MaxAge / ForwardDelay 秒)","classicはListening→Learning→Forwarding。BlockingでもBPDUを受信します。","edgeは端末専用。BPDU受信で解除、guard併用ならErr-disabled。","RSTP/PVST/MSTワイヤ互換、TCN、simpleとの混在は未対応。"));}
            if(t.length==4&&t[1].equals("port")&&t[3].equals("reset")){sim.clearStpGuard(id,port(t[2]));return reply(false,"BPDU guardの停止を解除しました。誤配線が残れば再停止します。");}
            var p=NetworkProfile.decode(old.kind,old.encode());p.stability=old.stability;p.processor=old.processor;p.buffer=old.buffer;p.wanTier=old.wanTier;for(int n=0;n<6;n++)p.ports[n].moduleTier=old.ports[n].moduleTier;
            switch(t[1]){
                case "on","off"->{count(t,2);p.stp=t[1].equals("on");}
                case "mode"->{count(t,3);if(!Set.of("simple","classic").contains(t[2]))throw new IllegalArgumentException("simple または classic");p.spanning.classic=t[2].equals("classic");p.stp=true;}
                case "priority"->{count(t,3);p.spanning.priority=Integer.parseInt(t[2]);}
                case "vlan"->{count(t,5);int vlan=Integer.parseInt(t[2]);if(vlan<1||vlan>4094)throw new IllegalArgumentException("VLANは1〜4094です");if(!t[3].equals("priority"))throw new IllegalArgumentException("stp vlan 10 priority 4096|auto");if(t[4].equals("auto"))p.spanning.vlanPriorities.remove(vlan);else p.spanning.vlanPriorities.put(vlan,Integer.parseInt(t[4]));}
                case "timers"->{count(t,5);p.spanning.helloTicks=Math.multiplyExact(Integer.parseInt(t[2]),20);p.spanning.maxAgeTicks=Math.multiplyExact(Integer.parseInt(t[3]),20);p.spanning.forwardDelayTicks=Math.multiplyExact(Integer.parseInt(t[4]),20);}
                case "port"->{count(t,5);int n=port(t[2]);switch(t[3]){
                    case "cost"->{p.spanning.costs[n]=t[4].equals("auto")?0:Integer.parseInt(t[4]);if(!t[4].equals("auto")&&p.spanning.costs[n]<1)throw new IllegalArgumentException("costはautoまたは1〜200000000です");}
                    case "priority"->p.spanning.portPriorities[n]=Integer.parseInt(t[4]);
                    case "edge"->p.spanning.edge[n]=on(t[4]);
                    case "guard"->p.spanning.guard[n]=on(t[4]);
                    default->throw new IllegalArgumentException("STP portはcost/priority/edge/guardを指定");}}
                default->throw new IllegalArgumentException("stp help で構文を確認してください");
            }
            p.validate();p.encode();sim.put(id,p);sim.invalidate(id);return reply(true,"STP設定を保存しました。classicの状態はBPDUと稼働tickで決まります。");
        }catch(IllegalArgumentException|ArithmeticException e){return reply(false,"設定を変更しませんでした: "+e.getMessage());}
    }
    private static int port(String text){int n=Integer.parseInt(text);if(n<0||n>=6)throw new IllegalArgumentException("ポートは0〜5です");return n;}
    private static boolean on(String text){if(!Set.of("on","off").contains(text))throw new IllegalArgumentException("on または off");return text.equals("on");}
    private static void count(String[] t,int size){if(t.length!=size)throw new IllegalArgumentException("引数の数が違います。stp help を確認");}
    private static NetworkServiceCommands.Reply reply(boolean changed,String line){return new NetworkServiceCommands.Reply(true,changed,List.of(line));}
}
