package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;

/** 永続設定のみ。収集ログ・配送待ち・時計は機器アイテムに保存しない。 */
public final class SyslogSettings {
    public static final int PORT=514;
    public boolean enabled,server;
    public String target="",allow="0.0.0.0/0";
    public int port=PORT,level=6;

    public void validate(NetworkProfile owner){
        if(target==null||allow==null||target.length()>15||allow.length()>18)throw new IllegalArgumentException("Syslog設定の文字数上限です");
        if(!target.isEmpty()){
            long ip=Ipv4.parse(target);
            if(ip==0||ip>=0xe0000000L)throw new IllegalArgumentException("Syslog宛先はユニキャストIPv4です");
        }
        if(enabled&&target.isEmpty())throw new IllegalArgumentException("先にsyslog target IPを設定してください");
        if(port<1||port>65535||level<0||level>7)throw new IllegalArgumentException("UDPポートは1〜65535、severityは0〜7です");
        NetworkPolicy.Prefix.parse(allow);
        if(server&&owner.kind!=DeviceKind.SERVER)throw new IllegalArgumentException("Syslog受信はネットワークサーバーのみです");
    }
    public void copyFrom(SyslogSettings other){enabled=other.enabled;server=other.server;target=other.target;allow=other.allow;port=other.port;level=other.level;}
    public void write(DataOutput out)throws IOException{out.writeBoolean(enabled);out.writeBoolean(server);out.writeUTF(target);out.writeUTF(allow);out.writeInt(port);out.writeInt(level);}
    public void read(DataInput in)throws IOException{enabled=in.readBoolean();server=in.readBoolean();target=in.readUTF();allow=in.readUTF();port=in.readInt();level=in.readInt();}
}
