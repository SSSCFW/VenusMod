package dev.ssscfw.venusmod.infrastructure.network.core;

import java.io.*;
import java.util.*;

/** 名前はパスでなく単純なラベル。容量超過で古い控えを自動削除しない。 */
public final class BackupArchive {
    public static final int MAX_ENTRIES=8,MAX_TOTAL_TEXT=65536,MAX_ENCODED=131072;
    private static final int MAGIC=0x564e4231;
    private final Map<String,ConfigBackup> entries=new LinkedHashMap<>();
    private int textSize;
    private boolean serving;
    public boolean serving(){return serving;}
    public void serving(boolean value){serving=value;}
    public int textSize(){return textSize;}
    public Map<String,ConfigBackup> entries(){return Collections.unmodifiableMap(new LinkedHashMap<>(entries));}
    public ConfigBackup get(String label){label(label);var result=entries.get(label);if(result==null)throw new IllegalArgumentException("保存名が見つかりません");return result;}
    public void canSave(String label,ConfigBackup snapshot){
        label(label);Objects.requireNonNull(snapshot);
        if(entries.containsKey(label))throw new IllegalArgumentException("同じ保存名があります。別名を使用するか明示的に削除してください");
        if(entries.size()>=MAX_ENTRIES||textSize+snapshot.configuration().length()>MAX_TOTAL_TEXT)
            throw new IllegalArgumentException("保管庫の上限(8件/設定64KiB)です。古い控えは削除していません");
    }
    public void save(String label,ConfigBackup snapshot){canSave(label,snapshot);entries.put(label,snapshot);textSize+=snapshot.configuration().length();}
    public void remove(String label){var snapshot=get(label);entries.remove(label);textSize-=snapshot.configuration().length();}
    public static void label(String value){if(value==null||!value.matches("[A-Za-z0-9_-]{1,24}"))throw new IllegalArgumentException("保存名は英数字・_・-の1〜24文字です");}
    public String encode(){
        try{var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
            out.writeInt(MAGIC);out.writeBoolean(serving);out.writeInt(entries.size());
            for(var entry:entries.entrySet()){var s=entry.getValue();out.writeUTF(entry.getKey());out.writeByte(s.kind().ordinal());out.writeUTF(s.sourceId());out.writeLong(s.savedTick());out.writeUTF(s.configuration());out.writeUTF(s.checksum());}
        }String result=Base64.getEncoder().encodeToString(bytes.toByteArray());if(result.length()>MAX_ENCODED)throw new IllegalArgumentException("保管庫データの容量上限です");return result;
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    /** 一部だけを復元しない。失敗時は呼出元で元のデータを保持・隔離する。 */
    public static BackupArchive decode(String encoded){
        if(encoded==null||encoded.length()>MAX_ENCODED)throw new IllegalArgumentException("保管庫データの容量上限です");
        try(var in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))){
            if(in.readInt()!=MAGIC)throw new IllegalArgumentException("保管庫の形式が未対応です");
            var result=new BackupArchive();result.serving=in.readBoolean();int count=in.readInt();
            if(count<0||count>MAX_ENTRIES)throw new IllegalArgumentException("保存件数が不正です");
            for(int i=0;i<count;i++){
                String label=in.readUTF();int kind=in.readUnsignedByte();if(kind>=DeviceKind.values().length)throw new IllegalArgumentException("保存機種が不正です");
                result.save(label,new ConfigBackup(DeviceKind.values()[kind],in.readUTF(),in.readLong(),in.readUTF(),in.readUTF()));
            }
            if(in.available()!=0)throw new IllegalArgumentException("余分な保管庫データです");return result;
        }catch(IOException|IllegalArgumentException e){throw new IllegalArgumentException("保管庫の読込を拒否しました: "+e.getMessage(),e);}
    }
}
