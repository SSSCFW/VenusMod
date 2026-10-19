package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 機器メニューで認可済みの仮想NTP設定。ホスト時計・OSソケットは操作しない。 */
public final class NtpCommands {
    private NtpCommands(){}
    public static boolean handles(String[] t){return t.length>0&&t[0].equalsIgnoreCase("ntp");}
    public static NetworkServiceCommands.Reply run(NetworkSimulator simulator,String id,String[] t){
        var current=simulator.nodes().get(id);if(current==null)return reply(false,List.of("機器が未登録です"));
        try{
            if(t.length==1||t.length==2&&t[1].equals("status"))return reply(false,simulator.ntp().describe(id));
            if(t.length==2&&t[1].equals("help"))return reply(false,help());
            if(t.length==2&&t[1].equals("sync")){simulator.ntp().requestSync(id);return reply(false,List.of("NTP同期を予約しました。成功はntp statusで確認してください。"));}
            var copy=NetworkProfile.decode(current.kind,current.encode());var n=copy.ntp;
            switch(t[1]){
                case "on","off"->{count(t,2);n.enabled=t[1].equals("on");}
                case "server"->{count(t,3);n.server=on(t[2]);}
                case "target"->{if(t.length!=3&&t.length!=4)throw new IllegalArgumentException("ntp target IPv4 [PORT] / ntp target none");
                    if(t[2].equals("none")){count(t,3);n.target="";n.enabled=false;n.port=NtpSettings.PORT;}
                    else {n.target=Ipv4.normalize(t[2]);n.port=t.length==4?Integer.parseInt(t[3]):NtpSettings.PORT;}}
                case "interval"->{count(t,3);n.intervalSeconds=Integer.parseInt(t[2]);}
                case "offset"->{count(t,3);n.offsetMillis=Long.parseLong(t[2]);}
                case "drift"->{count(t,3);n.driftPpm=Integer.parseInt(t[2]);}
                default->throw new IllegalArgumentException("ntp help で構文を確認してください");
            }
            copy.validate();copy.encode();simulator.ntp().checkCapacity(id,n);simulator.applyNtpSettings(id,copy);
            return reply(true,List.of("NTP設定を保存しました。表示時計だけを補正し、通信・制御の期限は維持します。"));
        }catch(IllegalArgumentException error){return reply(false,List.of("設定を変更しませんでした: "+error.getMessage()));}
    }
    private static void count(String[] t,int count){if(t.length!=count)throw new IllegalArgumentException("引数の数が違います: ntp help");}
    private static boolean on(String value){if(!Set.of("on","off").contains(value))throw new IllegalArgumentException("on または offです");return value.equals("on");}
    private static NetworkServiceCommands.Reply reply(boolean changed,List<String> lines){return new NetworkServiceCommands.Reply(true,changed,List.copyOf(lines));}
    public static List<String> help(){return List.of("Server: ntp server on (ゲーム内ローカル基準。実UTCではない)","Client: ntp target 192.168.1.20 [PORT] → ntp on","ntp interval 30 / ntp sync (同じ予算で予約) / ntp status / ntp off","ntp offset 5000 (ms) / ntp drift 50 (ppm) は表示時計の試験用設定","UDP123の往復・ACL・NAT・稼働に従います。基準serverとclientは同時使用不可。","Syslogの発生/受信時計は記録時に固定。既存tickと全期限は補正しません。","同期補正は再起動/再設置で消去。設定は保持し再同期します。256台/1tick2件。");}
}
