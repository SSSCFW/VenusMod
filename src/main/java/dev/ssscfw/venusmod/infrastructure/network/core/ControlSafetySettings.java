package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;

/** 遠隔出力の永続方針。受信済みの期限や継続送信の権限は保存しない。 */
public final class ControlSafetySettings {
    public enum Mode { HOLD, STOP, VALUE }
    public record Config(Mode mode,int seconds,int value) {
        public Config {
            if(mode==null||seconds<1||seconds>3600||value<0||value>15)
                throw new IllegalArgumentException("安全出力は0〜15、期限は1〜3600秒です");
            if(mode!=Mode.VALUE&&value!=0)throw new IllegalArgumentException("指定値はvalueモードだけです");
        }
        public boolean guarded(){return mode!=Mode.HOLD;}
        public int fallback(){return mode==Mode.VALUE?value:0;}
        public long ticks(){return seconds*20L;}
    }
    private Config config=new Config(Mode.HOLD,5,0);
    public Config config(){return config;}
    public void set(Config value){config=java.util.Objects.requireNonNull(value);}
    public void copyFrom(ControlSafetySettings other){config=other.config;}
    public void validate(NetworkProfile profile){
        if(profile.kind!=DeviceKind.REDSTONE_TERMINAL&&!config.equals(new Config(Mode.HOLD,5,0)))
            throw new IllegalArgumentException("安全出力はレッドストーン通信端末のみです");
    }
    void write(DataOutput out)throws IOException {out.writeUTF(config.mode().name());out.writeInt(config.seconds());out.writeInt(config.value());}
    void read(DataInput in)throws IOException {
        try{config=new Config(Mode.valueOf(in.readUTF()),in.readInt(),in.readInt());}
        catch(IllegalArgumentException error){throw new IOException("安全出力設定が不正です",error);}
    }
}
