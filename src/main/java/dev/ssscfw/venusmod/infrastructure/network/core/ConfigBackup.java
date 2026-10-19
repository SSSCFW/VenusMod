package dev.ssscfw.venusmod.infrastructure.network.core;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** 不変の設定控え。所有権/ItemStack/保管庫自体は含めず、チェックサムは破損検出専用。 */
public record ConfigBackup(DeviceKind kind,String sourceId,long savedTick,String configuration,String checksum) {
    public ConfigBackup {
        Objects.requireNonNull(kind);
        if(sourceId==null||!sourceId.matches("[A-Za-z0-9_-]{1,64}")||savedTick<0)
            throw new IllegalArgumentException("バックアップ元または保存tickが不正です");
        if(configuration==null||configuration.length()>NetworkProfile.MAX_TEXT)
            throw new IllegalArgumentException("バックアップ設定容量の上限です");
        if(!Objects.equals(checksum,digest(kind.name()+"\n"+sourceId+"\n"+savedTick+"\n"+configuration)))
            throw new IllegalArgumentException("バックアップのチェックサムが一致しません");
        NetworkProfile.decode(kind,configuration);
    }
    public static ConfigBackup capture(String id,NetworkProfile current,long tick){
        current.validate();var copy=NetworkProfile.decode(current.kind,current.encode());
        // 機器の物理故障、公開権限と過去のDHCP割当は設定巻戻しの対象外。
        copy.failed=false;for(var port:copy.ports)port.failed=false;copy.publicService=false;copy.services.clearBindings();
        String encoded=copy.encode();return new ConfigBackup(copy.kind,id,tick,encoded,digest(copy.kind.name()+"\n"+id+"\n"+tick+"\n"+encoded));
    }
    /** コピーを作って検証するだけ。呼出元が現在の認可・確認状態を検査してから一括反映する。 */
    public NetworkProfile restored(NetworkProfile current){
        if(current.kind!=kind)throw new IllegalArgumentException("同じ機種の設定だけを復元できます");
        var result=NetworkProfile.decode(kind,configuration);
        result.failed=current.failed;result.publicService=current.publicService;
        result.stability=current.stability;result.processor=current.processor;result.buffer=current.buffer;result.wanTier=current.wanTier;
        for(int i=0;i<NetworkProfile.PORT_COUNT;i++){
            var old=current.ports[i];var next=result.ports[i];next.moduleTier=old.moduleTier;next.failed=old.failed;
            long maximum=NetworkProfile.SPEEDS[Math.clamp(old.moduleTier,0,4)];
            if(next.speedLimitBps!=old.speedLimitBps&&next.speedLimitBps>maximum)
                throw new IllegalArgumentException("P"+i+"の固定速度には対応モジュールが必要です");
            if(next.optical&&!old.optical&&old.moduleTier==0&&!(kind.optical()&&i==0))
                throw new IllegalArgumentException("P"+i+"の光設定には対応モジュールが必要です");
        }
        if(!current.services.bindings().isEmpty()){
            if(!result.services.pools().equals(current.services.pools()))
                throw new IllegalArgumentException("現在のDHCP予約があるため配布プールを巻き戻せません。先に予約とプールを確認してください");
            // 明示プールを使わない簡易DHCPはLAN側の設定も予約範囲を決める。
            if(current.services.pools().isEmpty()){
                boolean changed=!result.address.equals(current.address)||result.prefix!=current.prefix;
                for(int i=0;i<NetworkProfile.PORT_COUNT;i++)changed|=!result.ports[i].address.equals(current.ports[i].address)||result.ports[i].prefix!=current.ports[i].prefix;
                if(changed)throw new IllegalArgumentException("簡易DHCPの現在予約があるためLANアドレスを巻き戻せません");
            }
        }
        result.services.retainBindingsFrom(current.services);result.validate();result.encode();return result;
    }
    /** 設定と実モジュールの能力を合わせた確認用指紋。プレイヤーへ送るデータではない。 */
    public static String fingerprint(NetworkProfile current){
        var text=new StringBuilder(current.encode()).append('|').append(current.stability).append('|').append(current.processor)
            .append('|').append(current.buffer).append('|').append(current.wanTier);
        for(var port:current.ports)text.append('|').append(port.moduleTier);
        return digest(text.toString());
    }
    private static String digest(String text){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}
    }
}
