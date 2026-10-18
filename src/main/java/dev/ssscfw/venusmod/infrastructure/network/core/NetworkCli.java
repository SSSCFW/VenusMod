package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 入力は固定コマンドのみ。OSシェル・URL・スクリプトとして評価しない。 */
public final class NetworkCli {
    public record Reply(boolean changed,List<String> lines){}
    private NetworkCli(){}
    public static Reply run(NetworkSimulator sim,String id,String input){
        if(input==null||input.length()>512||input.codePoints().anyMatch(Character::isISOControl))return reply(false,"コマンドは改行なし512文字以内です");
        var original=sim.nodes().get(id);if(original==null)return reply(false,"機器が読み込まれていません");
        String[] t=input.trim().split("\\s+");
        if(SyslogCommands.handles(t)){var reply=SyslogCommands.run(sim,id,t);return new Reply(reply.changed(),reply.lines());}
        if(DnsCommands.handles(t)){var reply=DnsCommands.run(sim,id,t);return new Reply(reply.changed(),reply.lines());}
        if(BgpCommands.handles(t)){var reply=BgpCommands.run(sim,id,t);return new Reply(reply.changed(),reply.lines());}
        if(StpCommands.handles(t)){var reply=StpCommands.run(sim,id,t);return new Reply(reply.changed(),reply.lines());}
        if(VrrpCommands.handles(t)){var reply=VrrpCommands.run(sim,id,t);return new Reply(reply.changed(),reply.lines());}
        boolean editing=Set.of("name","ip","gateway","dns","port","dhcp-server","web","public","route","fault").contains(t[0].toLowerCase(Locale.ROOT));
        // 誤入力時は既存オブジェクトにも通信表にも触れない。
        var p=editing?NetworkProfile.decode(original.kind,original.encode()):original;
        if(editing){p.stability=original.stability;p.processor=original.processor;p.buffer=original.buffer;p.wanTier=original.wanTier;for(int i=0;i<6;i++)p.ports[i].moduleTier=original.ports[i].moduleTier;}
        try{
            switch(t[0].toLowerCase(Locale.ROOT)){
                case "help" -> {return new Reply(false,guide());}
                case "show","status" -> {
                    List<String> text=new ArrayList<>();text.add(p.name+" / "+p.kind+" / "+(p.failed?"故障":"正常"));
                    text.add("IP "+(p.address.isBlank()?"未設定":p.address+"/"+p.prefix)+" / GW "+p.gateway);
                    text.add("本体容量 "+CableGrade.speed(p.capacity())+" / WAN "+CableGrade.speed(NetworkProfile.WAN_SPEEDS[Math.clamp(p.wanTier,0,5)]));
                    text.add("安定化Lv"+p.stability+" / "+(p.stability==3?"内部故障率 0%":"修理・冗長化で保護"));
                    for(int i=0;i<6;i++)text.add("P"+i+" "+portName(i)+": "+sim.portStatus(id,i));
                    text.add("経路 "+p.routes.size()+"件 / 仮想転送 "+sim.flows().size()+"件");
                    text.add("STP: "+(p.spanning.classic?"classic (BPDU/タイマー)":"simple (即時の簡略木方式)")+" / stp status で確認");return new Reply(false,text);
                }
                case "ports" -> {List<String> text=new ArrayList<>();for(int i=0;i<6;i++){var port=p.ports[i];text.add("P"+i+" "+portName(i)+" "+(port.enabled?"ON":"OFF")+" "+(port.trunk?"Trunk":"Access")+" VLAN "+port.vlan);text.add("  "+CableGrade.speed(port.capacity())+" "+(port.optical?"光":"銅線")+" "+port.address+"/"+port.prefix);}return new Reply(false,text);}
                case "mac" -> {return new Reply(false,sim.macTable(id).entrySet().stream().map(e->e.getKey()+" → P"+e.getValue()).toList());}
                case "arp" -> {return new Reply(false,sim.arpTable(id).entrySet().stream().map(e->e.getKey()+" → "+e.getValue()).toList());}
                case "log" -> {return new Reply(false,sim.logs(id));}
                case "routes" -> {return new Reply(false,p.routes.stream().map(r->r.network()+"/"+r.prefix()+" via "+r.gateway()+" P"+r.port()).toList());}
                case "flows" -> {return new Reply(false,sim.flows().stream().filter(f->f.source.equals(id)).map(f->"#"+f.id+" "+String.format(Locale.ROOT,"%.1f%%",f.progress()*100)+" / "+f.totalBytes+" bytes").toList());}
                case "ping","trace" -> {need(t,2);var result=sim.ping(id,t[1]);List<String> lines=new ArrayList<>();lines.add(result.message());if(result.success()){lines.add("上限 "+CableGrade.speed(result.bps()));if(t[0].equals("trace"))for(String node:result.nodes())lines.add("→ "+sim.nodes().get(node).name);}return new Reply(false,lines);}
                case "dhcp" -> {need(t,1);var r=sim.dhcp(id);return reply(r.success(),r.message());}
                case "transfer" -> {need(t,3);long bytes=Math.multiplyExact(Long.parseLong(t[2]),1_000_000L);return reply(false,sim.startTransfer(id,t[1],bytes).message());}
                case "name" -> {if(t.length<2)throw new IllegalArgumentException("name 機器名");p.name=input.substring(input.indexOf(' ')+1).trim();}
                case "ip" -> {need(t,2);var cidr=cidr(t[1]);if(p.kind.routing()){p.ports[0].address=cidr[0];p.ports[0].prefix=Integer.parseInt(cidr[1]);}else{p.address=cidr[0];p.prefix=Integer.parseInt(cidr[1]);}}
                case "gateway" -> {need(t,2);p.gateway=t[1].equals("none")?"":Ipv4.normalize(t[1]);}
                case "dns" -> {need(t,2);p.dns=t[1].equals("none")?"":Ipv4.normalize(t[1]);}
                case "port" -> {
                    if(t.length<3||t.length>4)throw new IllegalArgumentException("port 番号 vlan|ip|speed|media|mode 値 / enable|disable");
                    int n=Integer.parseInt(t[1]);if(n<0||n>=6)throw new IllegalArgumentException("ポート番号は0〜5です");var port=p.ports[n];
                    switch(t[2]){
                        case "enable" -> {need(t,3);port.enabled=true;}
                        case "disable" -> {need(t,3);port.enabled=false;}
                        case "vlan" -> {need(t,4);port.vlan=Integer.parseInt(t[3]);}
                        case "mode" -> {need(t,4);if(!Set.of("access","trunk").contains(t[3]))throw new IllegalArgumentException("access または trunk");port.trunk=t[3].equals("trunk");}
                        case "media" -> {need(t,4);if(!Set.of("copper","fiber").contains(t[3]))throw new IllegalArgumentException("copper または fiber");if(t[3].equals("fiber")&&port.moduleTier==0&&!(p.kind.optical()&&n==0))throw new IllegalArgumentException("光接続には10G以上の対応モジュールが必要です");port.optical=t[3].equals("fiber");}
                        case "speed" -> {need(t,4);long rate=speed(t[3]);if(rate>NetworkProfile.SPEEDS[Math.clamp(port.moduleTier,0,4)])throw new IllegalArgumentException("ポートモジュールの速度上限です");port.speedLimitBps=rate;}
                        case "ip" -> {need(t,4);if(!p.kind.routing())throw new IllegalArgumentException("端末は ip コマンドを使用してください");var c=cidr(t[3]);port.address=c[0];port.prefix=Integer.parseInt(c[1]);}
                        default -> throw new IllegalArgumentException("不明なポート操作");
                    }
                }
                case "dhcp-server" -> {need(t,2);if(!p.kind.routing()&&p.kind!=DeviceKind.SERVER)throw new IllegalArgumentException("DHCPはルーター/サーバーのみです");p.dhcp=on(t[1]);}
                case "web" -> {need(t,2);if(p.kind!=DeviceKind.SERVER)throw new IllegalArgumentException("Webはサーバーのみです");p.web=on(t[1]);}
                case "public" -> {need(t,2);p.publicService=on(t[1]);}
                case "route" -> {if(!p.kind.routing())throw new IllegalArgumentException("経路追加はL3機器で行います");if(t.length==2&&t[1].equals("clear"))p.routes.clear();else{need(t,7);if(!t[1].equals("add")||!t[3].equals("via")||!t[5].equals("port"))throw new IllegalArgumentException("route add 10.0.0.0/24 via 192.168.1.1 port 0");var c=cidr(t[2]);p.routes.add(new NetworkProfile.Route(c[0],Integer.parseInt(c[1]),t[4],Integer.parseInt(t[6])));}}
                case "fault" -> {need(t,1);p.failed=true;}
                case "ospf","ipv6","nat","ipsec","lacp" -> {return reply(false,"この版では未実装です。成功した通信として扱いません。");}
                default -> {return reply(false,"不明なコマンドです。help で一覧を表示します");}
            }
            p.validate();p.encode();sim.put(id,p);return reply(true,"設定を保存しました: "+input);
        }catch(IllegalArgumentException|ArithmeticException e){
            return reply(false,"設定を変更しませんでした: "+e.getMessage());
        }
    }
    private static Reply reply(boolean changed,String text){return new Reply(changed,List.of(text));}
    private static void need(String[] args,int count){if(args.length!=count)throw new IllegalArgumentException("引数の数が違います。help を確認してください");}
    private static String[] cidr(String text){String[] p=text.split("/",-1);if(p.length!=2)throw new IllegalArgumentException("192.168.1.10/24 の形式です");p[0]=Ipv4.normalize(p[0]);Ipv4.mask(Integer.parseInt(p[1]));return p;}
    private static boolean on(String s){if(!s.equals("on")&&!s.equals("off"))throw new IllegalArgumentException("on または off");return s.equals("on");}
    public static long speed(String s){return switch(s.toLowerCase(Locale.ROOT)){case "auto"->0;case "10m"->10_000_000L;case "100m"->100_000_000L;case "1g"->1_000_000_000L;case "10g"->10_000_000_000L;case "40g"->40_000_000_000L;case "100g"->100_000_000_000L;case "400g"->400_000_000_000L;default->throw new IllegalArgumentException("auto/10m/100m/1g/10g/40g/100g/400g を指定してください");};}
    public static String portName(int p){return switch(p){case 0->"北";case 1->"東";case 2->"南";case 3->"西";case 4->"上";case 5->"下";default->"?";};}
    public static List<String> guide(){return NetworkHelp.lines();}
}
