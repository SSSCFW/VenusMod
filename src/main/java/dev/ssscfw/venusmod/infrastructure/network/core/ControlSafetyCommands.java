package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 設定のみの操作。監視や設定表示で制御期限を更新しない。 */
public final class ControlSafetyCommands {
    private ControlSafetyCommands(){}
    public static boolean handles(String[] args){return args.length>0&&args[0].equals("control-safety");}
    public static NetworkServiceCommands.Reply run(NetworkSimulator sim,String id,String[] args){
        var original=sim.nodes().get(id);if(original==null)return reply(false,"機器が未登録です");
        try{
            if(original.kind!=DeviceKind.REDSTONE_TERMINAL)throw new IllegalArgumentException("安全出力はレッドストーン通信端末のみです");
            var settings=original.controlSafety.config();
            if(args.length==1)return reply(false,"通信更新の安全動作: "+settings.mode()+" / "+settings.seconds()+"秒 / 指定値 "+settings.fallback()+"。期限状態はcontrol-statusで確認。");
            var next=switch(args[1]){
                case "hold"->{count(args,2);yield new ControlSafetySettings.Config(ControlSafetySettings.Mode.HOLD,5,0);}
                case "stop"->{count(args,3);yield new ControlSafetySettings.Config(ControlSafetySettings.Mode.STOP,Integer.parseInt(args[2]),0);}
                case "value"->{count(args,4);yield new ControlSafetySettings.Config(ControlSafetySettings.Mode.VALUE,Integer.parseInt(args[3]),Integer.parseInt(args[2]));}
                default->throw new IllegalArgumentException("control-safety hold / stop 秒 / value 0〜15 秒");
            };
            if(settings.equals(next))return reply(false,"安全出力設定は同じです。期限を変更しません。");
            var copy=NetworkProfile.decode(original.kind,original.encode());copy.controlSafety.set(next);copy.validate();copy.encode();
            sim.applyControlSafety(id,copy);return reply(true,"安全出力を保存しました: "+next.mode()+" / "+next.seconds()+"秒。新しい制御命令を受けるまで安全値です。");
        }catch(IllegalArgumentException error){return reply(false,"設定を変更しませんでした: "+error.getMessage());}
    }
    private static void count(String[] args,int count){if(args.length!=count)throw new IllegalArgumentException("control-safety hold / stop 秒 / value 0〜15 秒");}
    private static NetworkServiceCommands.Reply reply(boolean changed,String text){return new NetworkServiceCommands.Reply(true,changed,List.of(text));}
}
