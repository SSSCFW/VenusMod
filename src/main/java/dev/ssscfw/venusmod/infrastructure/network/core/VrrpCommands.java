package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 保存済みVIP構文を保ち、閲覧でタイマーやNATを初期化しない。 */
public final class VrrpCommands {
    private VrrpCommands(){}
    public static boolean handles(String[] t){return t.length>0&&(t[0].equals("vrrp")||String.join(" ",t).equals("show vrrp"));}
    public static NetworkServiceCommands.Reply run(NetworkSimulator sim,String id,String[] tokens){
        var old=sim.nodes().get(id);if(old==null)return reply(false,"機器未登録");
        String[] t=tokens[0].equals("show")?new String[]{"vrrp","status"}:tokens;
        if(t.length==1||t.length==2&&t[1].equals("status")){
            var status=sim.vrrp().status(id);int port=old.vrrp.resolvePort(old);
            return new NetworkServiceCommands.Reply(true,false,List.of(
                "VRRP "+(old.vrrp.enabled&&!old.vip.isBlank()?"ON":"OFF")+" / "+status.state()+" / VRID "+old.vrrp.group,
                "VIP "+old.vip+" / priority "+old.vrrpPriority+" / P"+(port<0?"未確定":port),
                "仮想MAC "+VrrpSettings.virtualMac(old.vrrp.group)+" / Master "+status.master(),
                "広告 "+old.vrrp.advertisementTicks/20+"秒 / preempt "+old.vrrp.preempt+" / 期限まで "+status.deadlineInTicks()+"tick",
                "広告送信 "+status.sent()+" / 受信 "+status.received()+" / "+status.reason(),
                port<0?"LAN側の実IP・VIPのサブネットと vrrp port を確認してください":"故障・ポート無効・リンク断の間はVIPを担当しません",
                "IPv4・1機器1グループ。NAT状態同期/owner priority255は未対応。"));
        }
        if(t.length==2&&t[1].equals("help"))return new NetworkServiceCommands.Reply(true,false,List.of(
            "vrrp 10.0.1.254 120 / vrrp port 0 (またはauto)","vrrp group 1 / vrrp interval 1 (1〜10秒)",
            "vrrp preempt on|off / vrrp on|off / show vrrp","MasterだけがVIPに応答。停止広告はpriority0、故障時は広告の途絶を待ちます。",
            "広告間隔の3倍＋優先度に応じた待ち時間。4/16幅の配線で同じLANへ接続。",
            "acl add 0 in 10 permit vrrp any any / acl add 0 in 20 permit ip any any",
            "広告遮断で両者Masterになる場合は競合エラー。予備機の外向き・返信経路も必要です。"));
        try{
            if(!old.kind.routing())throw new IllegalArgumentException("VRRPはルーター/L3スイッチのみです");
            var p=NetworkProfile.decode(old.kind,old.encode());p.stability=old.stability;p.processor=old.processor;p.buffer=old.buffer;p.wanTier=old.wanTier;
            for(int n=0;n<6;n++)p.ports[n].moduleTier=old.ports[n].moduleTier;
            switch(t[1]){
                case "on","off"->{count(t,2);p.vrrp.enabled=t[1].equals("on");if(p.vrrp.enabled&&p.vip.isBlank())throw new IllegalArgumentException("先にVIPを設定してください");}
                case "port"->{count(t,3);p.vrrp.port=t[2].equals("auto")?-1:Integer.parseInt(t[2]);}
                case "group"->{count(t,3);p.vrrp.group=Integer.parseInt(t[2]);}
                case "interval"->{count(t,3);p.vrrp.advertisementTicks=Math.multiplyExact(Integer.parseInt(t[2]),20);}
                case "preempt"->{count(t,3);if(!Set.of("on","off").contains(t[2]))throw new IllegalArgumentException("onまたはoff");p.vrrp.preempt=t[2].equals("on");}
                default->{count(t,3);p.vip=Ipv4.format(VrrpSettings.unicast(t[1]));p.vrrpPriority=Integer.parseInt(t[2]);p.vrrp.enabled=true;for(var port:p.ports)if(p.vip.equals(port.address))throw new IllegalArgumentException("VIPと実IPは分けてください (priority255のownerは未対応)");}
            }
            p.validate();p.encode();
            if(old.vrrp.enabled&&!p.vrrp.enabled)sim.shutdownVrrp(id);
            sim.put(id,p);sim.invalidate();return reply(true,"VRRP設定を保存しました。役割は広告と稼働tickで決まります。");
        }catch(IllegalArgumentException|ArithmeticException e){return reply(false,"設定を変更しませんでした: "+e.getMessage());}
    }
    private static void count(String[] t,int n){if(t.length!=n)throw new IllegalArgumentException("vrrp help の構文を確認してください");}
    private static NetworkServiceCommands.Reply reply(boolean changed,String message){return new NetworkServiceCommands.Reply(true,changed,List.of(message));}
}
