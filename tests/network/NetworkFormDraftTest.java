import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

/** 周期同期・ページ移動・応答遅延が未保存入力を消さないことをGUI非依存で検査。 */
public final class NetworkFormDraftTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static NetworkForm.Values rename(NetworkForm.Values v,String name){return new NetworkForm.Values(v.scope(),name,v.address(),v.prefix(),v.gateway(),v.dns(),v.enabled(),v.trunk(),v.optical(),v.vlan(),v.speed());}
    public static void main(String[] args)throws Exception{
        var p=new NetworkProfile(DeviceKind.PC);var draft=new NetworkFormDraft();
        draft.receive(NetworkForm.captureAll(p),0,false,"");check(draft.view(-1).values().name().equals(p.name),"initial snapshot");
        draft.edit(rename(draft.values(-1),"未保存名"));check(draft.dirty(-1),"draft marked dirty");
        p.name="他人の編集";draft.receive(NetworkForm.captureAll(p),0,false,"");check(draft.values(-1).name().equals("未保存名")&&draft.stale(-1),"periodic sync preserves draft and flags conflict");
        var request=draft.submit(-1);check(request!=null&&draft.pending(),"request pins expected snapshot");
        check(NetworkForm.decodeView(request.expected()).values().name().equals("network_pc"),"does not silently rebase stale edit");
        draft.receive(NetworkForm.captureAll(p),request.id(),false,"競合");check(!draft.pending()&&draft.values(-1).name().equals("未保存名"),"rejection preserves input");
        draft.reload(-1);check(!draft.dirty(-1)&&!draft.stale(-1)&&draft.values(-1).name().equals("他人の編集"),"explicit reload adopts remote");
        draft.edit(rename(draft.values(-1),"保存済み"));request=draft.submit(-1);p.name="保存済み";
        draft.receive(NetworkForm.captureAll(p),0,true,"periodic");check(draft.pending()&&draft.dirty(-1),"periodic state is not write acknowledgement");
        draft.receive(NetworkForm.captureAll(p),request.id(),true,"保存完了");check(!draft.pending()&&!draft.dirty(-1)&&draft.message().equals("保存完了"),"matching acknowledgement commits local baseline");
        draft.edit(rename(draft.values(-1),"次の入力"));request=draft.submit(-1);
        for(int i=0;i<100;i++)draft.tick();check(!draft.pending()&&draft.message().contains("未確認"),"missing acknowledgement times out honestly");
        check(draft.values(-1).name().equals("次の入力"),"timeout preserves draft");
        var fresh=draft.submit(-1);check(fresh.id()!=request.id(),"new request identity");
        draft.receive(NetworkForm.captureAll(p),request.id(),true,"late old reply");check(draft.pending(),"old acknowledgement cannot confirm new request");
        p.ports[2].vlan=44;draft.receive(NetworkForm.captureAll(p),0,false,"");
        check(draft.values(2).vlan().equals("44"),"unedited other page follows latest state while current page is pending");
        var wrong=new ArrayList<>(NetworkForm.captureAll(p));Collections.swap(wrong,0,1);
        boolean rejected=false;try{draft.receive(wrong,0,false,"");}catch(IllegalArgumentException e){rejected=true;}check(rejected,"wrong page order rejected atomically");
        check(draft.values(-1).name().equals("次の入力"),"invalid sync preserves edit");
        var bad=new ArrayList<>(NetworkForm.captureAll(p));bad.set(4,"!");rejected=false;try{draft.receive(bad,0,false,"");}catch(IllegalArgumentException e){rejected=true;}check(rejected,"malformed page refused");
        System.out.println("NetworkFormDraftTest: "+checks+" checks passed");
    }
}
