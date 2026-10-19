package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.*;

/** 設定を保存せず、操作中の機器が送信元である転送だけを管理する。 */
public final class TransferCommands {
    private TransferCommands(){}
    public static boolean handles(String[] t){return t.length>0&&(t[0].equalsIgnoreCase("transfer")||t[0].equalsIgnoreCase("flows"));}
    public static NetworkCli.Reply run(NetworkSimulator sim,String source,String[] t){
        try{
            if(t[0].equalsIgnoreCase("flows")){
                if(t.length!=1)throw new IllegalArgumentException("flows の引数は不要です");
                List<String> lines=new ArrayList<>();lines.add("自機の仮想転送 / low:normal:high = 1:2:4");
                for(var f:sim.flows())if(f.source.equals(source))lines.add("#"+f.id+" "+String.format(Locale.ROOT,"%.1f%%",f.progress()*100)+" / "+f.totalBytes+" bytes / "+f.priority().label()+" / 最終割当 "+CableGrade.speed((long)f.allocatedBps())+(f.delayTicks>0?" (開始待ち)":""));
                if(lines.size()==1)lines.add("転送なし");return new NetworkCli.Reply(false,List.copyOf(lines));
            }
            if(t.length==1||t.length==2&&t[1].equalsIgnoreCase("help"))return new NetworkCli.Reply(false,List.of(
                "transfer 名前/IP[:PORT] MB [low|normal|high] (省略時normal)",
                "transfer priority ID low|normal|high / transfer cancel ID / flows",
                "優先度は帯域の相対重み1:2:4。物理上限・WAN・本体容量を越えません。",
                "管理できるのは自機が送信元の転送のみ。転送・優先度は再起動後に復元しません。"));
            if(t.length>=2&&t[1].equalsIgnoreCase("cancel")){
                if(t.length!=3)throw new IllegalArgumentException("transfer cancel ID");return reply(sim.cancelTransfer(source,id(t[2])).message());
            }
            if(t.length>=2&&t[1].equalsIgnoreCase("priority")){
                if(t.length!=4)throw new IllegalArgumentException("transfer priority ID low|normal|high");return reply(sim.setTransferPriority(source,id(t[2]),TransferPriority.parse(t[3])).message());
            }
            if(t.length!=3&&t.length!=4)throw new IllegalArgumentException("transfer 名前/IP[:PORT] MB [low|normal|high]");
            TransferPriority priority=t.length==4?TransferPriority.parse(t[3]):TransferPriority.NORMAL;
            long bytes=Math.multiplyExact(Long.parseLong(t[2]),1_000_000L);
            return reply(sim.startTransfer(source,t[1],bytes,priority).message());
        }catch(IllegalArgumentException|ArithmeticException invalid){return reply("入力エラー: "+invalid.getMessage());}
    }
    private static long id(String text){long value=Long.parseLong(text);if(value<=0)throw new IllegalArgumentException("転送IDは正の整数です");return value;}
    private static NetworkCli.Reply reply(String text){return new NetworkCli.Reply(false,List.of(text));}
}
