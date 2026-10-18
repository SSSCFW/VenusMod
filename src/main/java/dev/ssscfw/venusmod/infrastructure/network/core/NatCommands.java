package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 誤入力は検証コピーで棄却し、機器の元の設定を維持する。 */
public final class NatCommands {
    private NatCommands(){}
    public static NetworkServiceCommands.Reply run(NetworkSimulator sim,String id,String input){
        String[] t=input.trim().split("\\s+");if(!t[0].equals("nat"))return new NetworkServiceCommands.Reply(false,false,List.of());
        var old=sim.nodes().get(id);if(old==null)return reply(false,"機器が未登録です");
        try{
            if(t.length==1||t.length==2&&t[1].equals("show")){
                List<String> lines=new ArrayList<>();lines.add("NAT "+(old.nat.enabled?"ON":"OFF")+" / overload "+old.nat.overload);
                for(int i=0;i<6;i++)lines.add("P"+i+" "+old.nat.role(i)+" "+old.ports[i].address);
                for(var f:old.nat.forwards())lines.add(f.description());return new NetworkServiceCommands.Reply(true,false,lines);
            }
            if(t.length==2&&t[1].equals("sessions")){
                var sessions=sim.nat().sessions(id);List<String> lines=new ArrayList<>();lines.add("NAT変換表 "+sessions.size()+"/128 (再起動で再生成)");
                for(var e:sessions)lines.add(e.description()+" / 残り"+Math.max(0,(e.expiresAt()-sim.nat().time())/20)+"秒");return new NetworkServiceCommands.Reply(true,false,lines);
            }
            if(t.length==2&&t[1].equals("help"))return new NetworkServiceCommands.Reply(true,false,List.of(
                "nat port 0 inside / nat port 1 outside / nat on",
                "nat overload on|off (外向きの動的NAPT)",
                "nat forward add 1 tcp 8080 192.168.10.20 80",
                "nat forward delete 1 tcp 8080",
                "nat sessions / nat clear / nat off",
                "入力ACL→宛先変換→経路→送信元変換→出力ACL。NATだけでACLを代用しません。",
                "TCP/UDP/ICMP照会。hairpin・1対1公開IP・ALG・状態同期は未対応。"));
            if(!old.kind.routing())throw new IllegalArgumentException("NATはルーター/L3スイッチのみです");
            if(t.length==2&&t[1].equals("clear")){sim.invalidate(id);return reply(true,"NAT変換表を消去し、稼働中の仮想転送を中断しました (設定は保持)");}
            var p=NetworkProfile.decode(old.kind,old.encode());p.stability=old.stability;p.processor=old.processor;p.buffer=old.buffer;p.wanTier=old.wanTier;
            for(int i=0;i<6;i++)p.ports[i].moduleTier=old.ports[i].moduleTier;
            switch(t[1]){
                case "on","off"->{count(t,2);p.nat.enabled=t[1].equals("on");}
                case "overload"->{count(t,3);if(!t[2].equals("on")&&!t[2].equals("off"))throw new IllegalArgumentException("on/offを指定してください");p.nat.overload=t[2].equals("on");}
                case "port"->{count(t,4);p.nat.role(Integer.parseInt(t[2]),NatSettings.Role.valueOf(t[3].toUpperCase(Locale.ROOT)));}
                case "forward"->{
                    if(t.length<3)throw new IllegalArgumentException("nat forward add/delete ...");
                    switch(t[2]){
                        case "add"->{count(t,8);p.nat.add(new NatSettings.Forward(Integer.parseInt(t[3]),NetworkPolicy.Protocol.valueOf(t[4].toUpperCase(Locale.ROOT)),Integer.parseInt(t[5]),t[6],Integer.parseInt(t[7])));}
                        case "delete"->{count(t,6);p.nat.delete(Integer.parseInt(t[3]),NetworkPolicy.Protocol.valueOf(t[4].toUpperCase(Locale.ROOT)),Integer.parseInt(t[5]));}
                        default->throw new IllegalArgumentException("転送規則はadd/deleteです");
                    }
                }
                default->throw new IllegalArgumentException("nat helpで構文を確認してください");
            }
            p.validate();p.encode();sim.put(id,p);sim.invalidate(id);return reply(true,"NAT設定を保存しました: "+input);
        }catch(IllegalArgumentException e){return reply(false,"設定を変更しませんでした: "+e.getMessage());}
    }
    private static void count(String[] t,int n){if(t.length!=n)throw new IllegalArgumentException("引数の数が違います。nat helpを参照してください");}
    private static NetworkServiceCommands.Reply reply(boolean changed,String text){return new NetworkServiceCommands.Reply(true,changed,List.of(text));}
}
