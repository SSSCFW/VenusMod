package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;

/** 時計の設定のみ保存。補正・観測時刻・予約は保存せず再同期する。 */
public final class NtpSettings {
    public static final int PORT=123;
    public boolean enabled,server;
    public String target="";
    public int port=PORT,intervalSeconds=30,driftPpm;
    public long offsetMillis;
    public record Config(boolean enabled,boolean server,String target,int port,int intervalSeconds,long offsetMillis,int driftPpm){}
    public Config config(){return new Config(enabled,server,target,port,intervalSeconds,offsetMillis,driftPpm);}
    public void validate(NetworkProfile owner){
        if(target==null||target.length()>15)throw new IllegalArgumentException("NTP宛先は数値IPv4です");
        if(!target.isEmpty()){long ip=Ipv4.parse(target);if(ip==0||ip>=0xe0000000L)throw new IllegalArgumentException("NTP宛先はユニキャストIPv4です");}
        if(enabled&&target.isEmpty())throw new IllegalArgumentException("先にntp target IPv4を設定してください");
        if(server&&owner.kind!=DeviceKind.SERVER)throw new IllegalArgumentException("NTP基準サーバーはネットワークサーバーのみです");
        if(server&&enabled)throw new IllegalArgumentException("この版ではNTP基準サーバーとクライアントを同時に有効化できません");
        if(port<1||port>65535||intervalSeconds<1||intervalSeconds>3600)throw new IllegalArgumentException("NTPポートは1〜65535、間隔は1〜3600秒です");
        if(offsetMillis< -86400000L||offsetMillis>86400000L||driftPpm< -1000||driftPpm>1000)
            throw new IllegalArgumentException("時計offsetは±86400000ms、driftは±1000ppmです");
    }
    public void copyFrom(NtpSettings s){enabled=s.enabled;server=s.server;target=s.target;port=s.port;intervalSeconds=s.intervalSeconds;offsetMillis=s.offsetMillis;driftPpm=s.driftPpm;}
    public void write(DataOutput out)throws IOException{out.writeBoolean(enabled);out.writeBoolean(server);out.writeUTF(target);out.writeInt(port);out.writeInt(intervalSeconds);out.writeLong(offsetMillis);out.writeInt(driftPpm);}
    public void read(DataInput in)throws IOException{enabled=in.readBoolean();server=in.readBoolean();target=in.readUTF();port=in.readInt();intervalSeconds=in.readInt();offsetMillis=in.readLong();driftPpm=in.readInt();}
}
