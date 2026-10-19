package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** 機器メニューに結び付くバックアップ操作。遠隔のpublic設定は管理権限の代わりにしない。 */
public final class NetworkBackupSession {
    public static final int SERVICE_PORT=5001, MAX_LOADED_TEXT=4*1024*1024;
    private final BackupRestore restore=new BackupRestore();
    public void clear(){restore.clear();}
    public String command(NetworkDeviceEntity source,ServerPlayer player,String[] args){
        if(!source.canUse(player)||source.isRemoved()||source.getLevel()!=player.serverLevel())
            throw new IllegalArgumentException("機器の管理権限がありません");
        if(args.length<2||args[1].equals("help"))return String.join("\n",help());
        var world=NetworkWorld.get(player.serverLevel());
        if(args[1].equals("discard-corrupt")){
            count(args,3);if(!args[2].equals("confirm"))throw new IllegalArgumentException("backup discard-corrupt confirm");
            source.discardCorruptBackups();clear();return "破損した保管庫だけを明示的に破棄しました。機器設定・装着品は変更していません";
        }
        BackupArchive archive=source.backups();long now=player.serverLevel().getGameTime();
        switch(args[1]){
            case "server" -> {
                count(args,3);if(source.profile().kind!=DeviceKind.SERVER)throw new IllegalArgumentException("遠隔保管サービスはServerだけで有効にできます");
                if(!Set.of("on","off").contains(args[2]))throw new IllegalArgumentException("backup server on|off");
                archive.serving(args[2].equals("on"));source.setChanged();return "遠隔設定保管サービス: "+(archive.serving()?"ON / TCP5001 / 管理者・共有者のみ":"OFF");
            }
            case "save" -> {
                count(args,3);ConfigBackup copy=ConfigBackup.capture(source.id(),source.profile(),now);canSave(world,source,args[2],copy);archive.save(args[2],copy);source.setChanged();
                return "ローカル保存: "+args[2]+" / "+copy.configuration().length()+"文字。装着品・所有権・故障・過去のDHCP予約は控えに含みません";
            }
            case "delete" -> {count(args,3);archive.remove(args[2]);source.setChanged();return "保存名 "+args[2]+" を削除しました。稼働中の設定は変更していません";}
            case "list" -> {
                if(args.length==2)return describe(archive);
                count(args,3);var endpoint=world.simulator().resolveEndpoint(source.id(),args[2],SERVICE_PORT);
                var result=world.simulator().backupRequest(source.id(),endpoint,id->remoteProblem(world.device(id),player));
                if(!result.success())return result.message();return describe(world.device(result.destination()).backups());
            }
            case "send" -> {
                count(args,4);BackupArchive.label(args[3]);var copy=ConfigBackup.capture(source.id(),source.profile(),now);
                var endpoint=world.simulator().resolveEndpoint(source.id(),args[2],SERVICE_PORT);
                var result=world.simulator().backupRequest(source.id(),endpoint,id->{
                    var remote=world.device(id);String problem=remoteProblem(remote,player);if(!problem.isEmpty())return problem;
                    try{canSave(world,remote,args[3],copy);return "";}catch(IllegalArgumentException invalid){return invalid.getMessage();}
                });
                if(!result.success())return result.message();var remote=world.device(result.destination());remote.backups().save(args[3],copy);remote.setChanged();
                return "サーバーへ保存: "+args[3]+" / "+copy.configuration().length()+"文字。稼働設定は変更していません";
            }
            case "fetch" -> {
                count(args,5);BackupArchive.label(args[3]);BackupArchive.label(args[4]);
                var endpoint=world.simulator().resolveEndpoint(source.id(),args[2],SERVICE_PORT);ConfigBackup[] fetched={null};
                var result=world.simulator().backupRequest(source.id(),endpoint,id->{
                    var remote=world.device(id);String problem=remoteProblem(remote,player);if(!problem.isEmpty())return problem;
                    try{ConfigBackup copy=remote.backups().get(args[3]);canSave(world,source,args[4],copy);fetched[0]=copy;return "";}
                    catch(IllegalArgumentException invalid){return invalid.getMessage();}
                });
                if(!result.success())return result.message();archive.save(args[4],Objects.requireNonNull(fetched[0]));source.setChanged();
                return "手元に取得: "+args[4]+"。まだ設定を適用していません。backup restore "+args[4]+" で確認します";
            }
            case "restore" -> {count(args,3);return restore.prepare(archive,args[2],source.profile(),now);}
            case "confirm" -> {
                count(args,3);var next=restore.confirm(archive,args[2],source.profile(),now);
                if(next.encode().equals(source.profile().encode()))return "設定に変更はありません。通信・制御期限を維持しました";
                // 以前の操作権を設定の巻戻しによって復活させない。holdでも生の保存出力は復元しない。
                world.controls().runtime().removed(source.id());source.adoptProfile(next);source.reconcileControl(true);source.refreshState();
                world.simulator().log(source.id(),5,"BACKUP RESTORE: 確認済みの設定を復元。影響する通信は再確立");
                return "設定を復元しました。装着品・故障・所有権・公開設定を保持し、影響する通信は再確立します";
            }
            default -> throw new IllegalArgumentException("backup help で操作一覧を確認してください");
        }
    }
    private static void canSave(NetworkWorld world,NetworkDeviceEntity receiver,String label,ConfigBackup copy){
        receiver.backups().canSave(label,copy);long total=copy.configuration().length()+world.backupTextSize();
        // 明示的な保管操作時だけ確認。ケーブルや各機器への毎tick処理は追加しない。
        if(total>MAX_LOADED_TEXT)throw new IllegalArgumentException("このディメンションのロード済み設定保管容量(4MiB)を超えます。既存の控えは削除していません");
    }
    private static String remoteProblem(NetworkDeviceEntity remote,ServerPlayer player){
        if(remote==null||remote.isRemoved()||remote.profile().kind!=DeviceKind.SERVER)return "保存先は稼働中のServerではありません";
        if(!remote.canUse(player))return "保存先の管理・共有権限がありません (public onでは保管庫を公開しません)";
        try{return remote.backups().serving()?"":"バックアップ受信サービスがOFFです";}
        catch(IllegalArgumentException invalid){return invalid.getMessage();}
    }
    private static String describe(BackupArchive archive){
        var lines=new ArrayList<String>();lines.add("設定保管: "+archive.entries().size()+"/8 / "+archive.textSize()+"/65536文字 / 遠隔受信 "+(archive.serving()?"ON":"OFF"));
        archive.entries().forEach((label,s)->lines.add(label+" / "+s.kind()+" / tick="+s.savedTick()+" / "+s.configuration().length()+"文字"));
        return String.join("\n",lines);
    }
    private static void count(String[] args,int n){if(args.length!=n)throw new IllegalArgumentException("引数の数が違います。backup help を確認してください");}
    public static List<String> help(){return List.of(
        "backup save NAME / backup list / backup delete NAME",
        "backup restore NAME → backup confirm TOKEN (同じ画面・30秒以内・一回限り)",
        "Server: backup server on|off (TCP5001、管理権限が必要)",
        "backup send 名前/IP[:PORT] 保存名 (現在設定を送る)",
        "backup fetch 名前/IP[:PORT] 保存名 手元の名前 (取得だけ、適用は別操作)",
        "backup list 名前/IP[:PORT] (許可されたサーバーの保存名一覧)",
        "同機種のみ復元。故障・装着品・所有権・公開状態・現在DHCP予約は戻しません。",
        "保管庫は通常採掘・再起動でも保持。ネットワーク不通でも手元の控えは復元可能。",
        "チェックサム不正の保管庫は隔離して保持。明示破棄: backup discard-corrupt confirm");}
}
