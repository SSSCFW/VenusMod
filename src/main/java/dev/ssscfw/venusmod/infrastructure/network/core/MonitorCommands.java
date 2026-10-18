package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 管理権限を検証済みの機器メニューから利用する固定コマンド。実pingやOSを起動しない。 */
public final class MonitorCommands {
    private MonitorCommands(){}
    public static boolean handles(String[] args){return args.length>0&&args[0].equalsIgnoreCase("monitor");}
    public static NetworkServiceCommands.Reply run(NetworkSimulator simulator,String id,String[] t){
        var current=simulator.nodes().get(id);if(current==null)return reply(false,List.of("監視元が未登録です"));
        try{
            if(t.length==1||t.length==2&&Set.of("status","list").contains(t[1]))return reply(false,simulator.monitor().describe(id));
            if(t.length==2&&t[1].equals("help"))return reply(false,help());
            if(current.kind!=DeviceKind.SERVER)throw new IllegalArgumentException("定期監視はネットワークサーバーのみです");
            var copy=NetworkProfile.decode(current.kind,current.encode());var m=copy.monitor;
            switch(t[1]){
                case "on","off"->{count(t,2);m.enabled=t[1].equals("on");}
                case "add"->{
                    if(t.length!=4&&t.length!=5)throw new IllegalArgumentException("monitor add NAME [icmp/web/dns/transfer/control] IPv4[:PORT]");
                    MonitorSettings.label(t[2]);if(m.targets.containsKey(t[2]))throw new IllegalArgumentException("監視名は登録済みです。削除してから再登録してください");
                    var target=MonitorTarget.parse(t.length==4?"icmp":t[3],t[t.length-1]);m.targets.put(t[2],target.address());
                    if(target.kind()!=MonitorTarget.Kind.ICMP)m.services.put(t[2],new MonitorTarget.Service(target.kind(),target.port()));
                }
                case "delete"->{count(t,3);if(m.targets.remove(t[2])==null)throw new IllegalArgumentException("監視名がありません");m.services.remove(t[2]);if(m.targets.isEmpty())m.enabled=false;}
                case "clear"->{count(t,2);m.targets.clear();m.services.clear();m.enabled=false;}
                case "interval"->{count(t,3);m.intervalSeconds=Integer.parseInt(t[2]);}
                case "thresholds"->{count(t,4);m.failures=Integer.parseInt(t[2]);m.recoveries=Integer.parseInt(t[3]);}
                default->throw new IllegalArgumentException("monitor help で構文を確認してください");
            }
            copy.validate();copy.encode();simulator.monitor().checkCapacity(id,m);simulator.applyMonitorSettings(id,copy);
            return reply(true,List.of("監視設定を保存しました。既存の転送・NATは維持します。"));
        }catch(IllegalArgumentException invalid){return reply(false,List.of("設定を変更しませんでした: "+invalid.getMessage()));}
    }
    private static void count(String[] t,int n){if(t.length!=n)throw new IllegalArgumentException("引数の数が違います: monitor help");}
    private static NetworkServiceCommands.Reply reply(boolean changed,List<String> lines){return new NetworkServiceCommands.Reply(true,changed,List.copyOf(lines));}
    public static List<String> help(){return List.of("Server: monitor add base 192.168.1.20 → monitor on","monitor interval 5 / monitor thresholds 3 2 (失敗・復旧の連続回数)","monitor status / monitor delete base / monitor clear / monitor off","monitor add site web 192.168.1.20:8080 (既存NATで内部80へ転送)","monitor add names dns 192.168.1.53 / monitor add data transfer 192.168.1.20",
        "monitor add signal control 192.168.1.30 (出力変更なしの応答確認)",
        "種類省略は従来のICMP。サービスは固有TCP/UDPポートと稼働状態を確認します。","状態変化だけ通常logへ記録。syslog target IP → syslog on で集中収集。","監視元が停止中はPAUSED。1台16対象、全体64台/256対象、1tick最大2件。","保存は設定のみ。再起動・回収後はUNKNOWNから再確認。外部IPへ接続しません。");}
}
