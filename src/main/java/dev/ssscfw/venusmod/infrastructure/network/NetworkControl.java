package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.server.level.*;

/** 実機器の出力と、認可付きの仮想制御通信を仲介する。監視からは呼び出さない。 */
public final class NetworkControl {
    private final ServerLevel level;
    private final NetworkWorld world;
    private final ControlLeaseRuntime runtime=new ControlLeaseRuntime();
    NetworkControl(ServerLevel level,NetworkWorld world){this.level=level;this.world=world;}
    public ControlLeaseRuntime runtime(){return runtime;}
    public String status(NetworkDeviceEntity device){
        if(device.profile().kind!=DeviceKind.REDSTONE_TERMINAL)return "control-statusはレッドストーン通信端末用です";
        var config=device.profile().controlSafety.config();long now=level.getGameTime();
        return "出力="+device.redstone()+" / "+config.mode()+" / "+(device.profile().failed?"機器故障":!config.guarded()?"保持":runtime.active(device.id(),now)?"制御更新有効":"安全出力・新しい制御待ち")
                +"\n残り="+runtime.remaining(device.id(),now)+"tick / 設定期限="+config.seconds()+"秒 / 安全値="+config.fallback();
    }
    public String command(NetworkDeviceEntity source,ServerPlayer player,String[] args){
        if(!source.canUse(player)||source.isRemoved()||source.getLevel()!=level)return "制御元の管理権限がありません";
        if(args.length==2&&args[1].equals("stop"))return "継続更新を"+runtime.stopSource(source.id())+"件停止しました。受信済み期限の満了後に安全出力へ移行します。";
        if(args.length==2&&args[1].equals("links")){
            var list=runtime.streams(source.id());return list.isEmpty()?"継続更新なし":String.join("\n",list.stream().map(s->s.endpoint().address()+":"+s.endpoint().port()+" / 次回tick="+s.due()).toList());
        }
        boolean maintain=args.length>1&&args[1].equals("maintain");
        if(args.length!=(maintain?4:3))throw new IllegalArgumentException("redstone 名前/IP[:PORT] 0〜15 / redstone maintain 名前/IP[:PORT] 0〜15 / redstone stop|links");
        int value=Integer.parseInt(args[maintain?3:2]);if(value<0||value>15)throw new IllegalArgumentException("出力は0〜15です");
        if(maintain&&!source.configuredManager(player.getUUID()))return "継続更新には制御元の所有者または明示共有権限が必要です。OP権限だけでは作成しません。";
        var endpoint=world.simulator().resolveEndpoint(source.id(),args[maintain?2:1],NetworkSimulator.CONTROL_PORT);
        var result=world.simulator().controlRequest(source.id(),endpoint,id->{
            var receiver=world.device(id);
            if(receiver==null||!receiver.canControl(player))return "通信先の制御権限がありません";
            var config=receiver.profile().controlSafety.config();
            if(maintain){
                if(!receiver.configuredControl(player.getUUID()))return "継続更新には宛先の所有者・共有権限またはサービス公開が必要です";
                if(!config.guarded()||config.seconds()<3)return "継続更新には端末のcontrol-safety stop/valueと3秒以上の期限が必要です";
            }
            return !config.guarded()||runtime.canAccept(id,source.id(),maintain)?"":"制御期限または継続更新の上限です";
        });
        if(!result.success())return result.message();
        var receiver=world.device(result.destination());var config=receiver.profile().controlSafety.config();
        if(config.guarded()){
            // 同じサーバースレッド上で、認可・枠の検査から確定まで行う。
            if(!runtime.accept(receiver.id(),source.id(),player.getUUID(),endpoint,config.ticks(),level.getGameTime(),maintain))
                return "制御枠の上限です。出力は変更しませんでした";
        }else runtime.cancelTarget(receiver.id());
        receiver.setRedstone(value);
        world.simulator().log(receiver.id(),5,"CONTROL 出力="+value+(maintain?" / 定期更新を開始":" / 単発命令"));
        return "通信に成功し、出力を"+value+"に設定しました"+(maintain?"。20tickごとに通信と権限を確認します。期限切れ後は明示的な再操作が必要です。":"");
    }
    void tick(){runtime.advance(level.getGameTime(),this::refresh,id->{
        var device=world.device(id);if(device==null)return;
        device.setRedstone(device.profile().controlSafety.config().fallback());
        world.simulator().log(id,3,"CONTROL SAFE: 制御更新期限が切れ、安全出力="+device.redstone()+"。復旧後も新しい操作を待ちます。");
    });}
    private ControlLeaseRuntime.Refresh refresh(ControlLeaseRuntime.Stream stream){
        var source=world.device(stream.source());var receiver=world.device(stream.target());
        if(source==null||receiver==null||source.isRemoved()||receiver.isRemoved())return ControlLeaseRuntime.Refresh.CANCEL;
        if(!source.configuredManager(stream.actor())||!receiver.configuredControl(stream.actor()))return ControlLeaseRuntime.Refresh.CANCEL;
        if(receiver.profile().failed||!receiver.profile().controlSafety.config().guarded())return ControlLeaseRuntime.Refresh.CANCEL;
        if(source.profile().failed)return ControlLeaseRuntime.Refresh.RETRY;
        var result=world.simulator().controlRequest(source.id(),stream.endpoint(),id->{
            if(!id.equals(stream.target()))return "制御先の機器が変わりました";
            return receiver.configuredControl(stream.actor())?"":"制御権限が失効しました";
        });
        // 成功は期限だけ更新する。安全動作後の出力を古いジョブから復活させない。
        return result.success()?ControlLeaseRuntime.Refresh.RECEIVED:ControlLeaseRuntime.Refresh.RETRY;
    }
}
