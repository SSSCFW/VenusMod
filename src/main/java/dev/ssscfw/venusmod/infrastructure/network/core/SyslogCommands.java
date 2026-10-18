package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 機器メニューの管理権限内で使用する固定CLI。Profile丸ごとのクライアント入力ではない。 */
public final class SyslogCommands {
    private SyslogCommands(){}
    public static boolean handles(String[] t){return t.length>0&&t[0].equalsIgnoreCase("syslog");}
    public static NetworkServiceCommands.Reply run(NetworkSimulator simulator,String id,String[] t){
        var current=simulator.nodes().get(id);if(current==null)return reply(false,List.of("機器未登録です"));
        try{
            if(t.length==1||t.length==2&&t[1].equals("status"))return reply(false,simulator.syslog().status(id));
            if(t.length==2&&t[1].equals("help"))return reply(false,help());
            if(t[1].equals("logs")){if(t.length>3)throw new IllegalArgumentException("syslog logs [1〜20]");return reply(false,simulator.syslog().describe(id,t.length==3?Integer.parseInt(t[2]):12));}
            if(t[1].equals("clear")){count(t,2);simulator.syslog().clear(id);return reply(false,List.of("自機の受信履歴を消去しました"));}
            if(t[1].equals("test")){
                if(t.length<4)throw new IllegalArgumentException("syslog test SEVERITY TEXT");int severity=Integer.parseInt(t[2]);if(severity<0||severity>7)throw new IllegalArgumentException("severityは0〜7です");
                simulator.log(id,severity,String.join(" ",Arrays.asList(t).subList(3,t.length)));return reply(false,List.of("試験ログをローカルに記録しました。配送は設定と経路に従います。"));
            }
            var p=NetworkProfile.decode(current.kind,current.encode());
            switch(t[1]){
                case "on","off"->{count(t,2);p.syslog.enabled=t[1].equals("on");}
                case "server"->{count(t,3);p.syslog.server=on(t[2]);}
                case "target"->{if(t.length!=3&&t.length!=4)throw new IllegalArgumentException("syslog target IPv4 [PORT] / syslog target none");
                    if(t[2].equals("none")){count(t,3);p.syslog.target="";p.syslog.enabled=false;p.syslog.port=514;}
                    else {p.syslog.target=Ipv4.normalize(t[2]);p.syslog.port=t.length==4?Integer.parseInt(t[3]):514;}}
                case "level"->{count(t,3);p.syslog.level=Integer.parseInt(t[2]);}
                case "allow"->{count(t,3);p.syslog.allow=NetworkPolicy.Prefix.parse(t[2]).toString();}
                default->throw new IllegalArgumentException("syslog help で構文を確認してください");
            }
            p.validate();p.encode();simulator.applySyslogSettings(id,p);
            return reply(true,List.of("Syslog設定を保存しました。通信中フローは維持します。"));
        }catch(IllegalArgumentException e){return reply(false,List.of("設定を変更しませんでした: "+e.getMessage()));}
    }
    private static void count(String[] t,int count){if(t.length!=count)throw new IllegalArgumentException("引数の数が違います: syslog help");}
    private static boolean on(String text){if(!Set.of("on","off").contains(text))throw new IllegalArgumentException("on または off");return text.equals("on");}
    private static NetworkServiceCommands.Reply reply(boolean changed,List<String> lines){return new NetworkServiceCommands.Reply(true,changed,List.copyOf(lines));}
    public static List<String> help(){return List.of("受信Server: syslog server on / syslog allow 192.168.1.0/24","送信機器: syslog target 192.168.1.20 [PORT] → syslog on","syslog level 6 (0〜7。小さいほど重大、指定以下だけ配送)","syslog test 3 test-message / syslog status / syslog logs [1〜20]","syslog off / syslog target none / syslog server off / syslog clear","UDP514片方向。返信不要、ACL/NAT・最終ポート514を検査。送信元停止は送れません。","受信履歴は再転送しません。到達不能時は破棄、再送や停止中の保存はしません。","履歴閲覧は自機の管理権限のみ。public onでログを公開しません。");}
}
