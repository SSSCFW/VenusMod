package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 開いているメニュー1個に結び付く短期・単発の復元確認。ワールドやアイテムには保存しない。 */
public final class BackupRestore {
    public static final int CONFIRM_TICKS=600;
    private record Pending(String token,String label,String snapshot,String current,long created){}
    private Pending pending;
    public String token(){return pending==null?"":pending.token();}
    public void clear(){pending=null;}
    public String prepare(BackupArchive archive,String label,NetworkProfile current,long now){
        clear();if(now<0)throw new IllegalArgumentException("確認時刻が不正です");
        ConfigBackup snapshot=archive.get(label);NetworkProfile next=snapshot.restored(current);
        pending=new Pending(UUID.randomUUID().toString().substring(0,8),label,snapshot.checksum(),ConfigBackup.fingerprint(current),now);
        String ip=next.kind.routing()?"P0 "+next.ports[0].address+"/"+next.ports[0].prefix:next.address+"/"+next.prefix;
        return "復元確認: "+label+" / "+next.kind+" / "+next.name+"\nIP: "+ip+" / 静的経路 "+next.routes.size()+"件\n"
            +"ネットワーク設定を置換します。故障・装着品・所有権・公開設定・現在のDHCP予約は保持します。\n"
            +"同じIP/Router IDの機器が他にないか確認してください。影響する通信と制御は再確立します。\n"
            +"30秒以内に backup confirm "+pending.token()+" を入力してください";
    }
    public NetworkProfile confirm(BackupArchive archive,String token,NetworkProfile current,long now){
        Pending value=pending;clear();
        if(value==null||!value.token().equals(token))throw new IllegalArgumentException("復元確認がありません。backup restore 保存名からやり直してください");
        if(now<value.created()||now-value.created()>=CONFIRM_TICKS)throw new IllegalArgumentException("復元確認の期限が切れました");
        if(!ConfigBackup.fingerprint(current).equals(value.current()))throw new IllegalArgumentException("設定または装着能力が変更されました。復元内容を再確認してください");
        ConfigBackup snapshot=archive.get(value.label());if(!snapshot.checksum().equals(value.snapshot()))throw new IllegalArgumentException("保存内容が変更されました。復元内容を再確認してください");
        return snapshot.restored(current);
    }
}
