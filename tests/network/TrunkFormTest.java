import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class TrunkFormTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static NetworkForm.Values fields(NetworkForm.Values v,String allowed){return new NetworkForm.Values(v.scope(),v.name(),v.address(),v.prefix(),v.gateway(),v.dns(),v.enabled(),v.trunk(),v.optical(),v.vlan(),v.speed(),allowed);}
    private static NetworkForm.Outcome apply(NetworkSimulator s,NetworkForm.View before,String list){return NetworkForm.apply(s,"sw",NetworkForm.encode(before),NetworkForm.encode(fields(before.values(),list)));}
    public static void main(String[] args)throws Exception{
        var p=new NetworkProfile(DeviceKind.L2_SWITCH);p.ports[2].trunk=true;var s=new NetworkSimulator();s.put("sw",p);
        var before=NetworkForm.capture(p,2);check(before.values().allowedVlans().equals("all"),"表示は初期all");
        check(apply(s,before,"20,10,30-40").accepted(),"ポートフォーム適用");
        check(s.nodes().get("sw").ports[2].allowedVlans.toString().equals("10,20,30-40"),"フォームも正規化");
        check(!apply(s,before,"none").accepted(),"古い許可リストの画面から上書きしない");
        var current=NetworkForm.capture(s.nodes().get("sw"),2);String encoded=s.nodes().get("sw").encode();
        check(!apply(s,current,"").accepted()&&!apply(s,current,"10,4095").accepted()&&encoded.equals(s.nodes().get("sw").encode()),"空欄/範囲不正を原子的に拒否");
        check(!apply(s,NetworkForm.capture(s.nodes().get("sw"),-1),"none").accepted(),"基本ページから許可リストを偽造しない");
        var other=s.nodes().get("sw");other.ports[1].vlan=55;
        check(apply(s,current,"10,20").accepted()&&s.nodes().get("sw").ports[1].vlan==55,"別ページの最新編集は保持");
        current=NetworkForm.capture(s.nodes().get("sw"),2);
        check(!apply(s,current,"20,10").changed(),"正規化で同じ集合なら変更なし");
        check(NetworkForm.decodeView(NetworkForm.encode(current)).equals(current),"View往復に許可リストを含む");
        check(NetworkForm.decodeValues(NetworkForm.encode(current.values())).equals(current.values()),"編集値往復に許可リストを含む");
        boolean denied=false;try{NetworkForm.decodeValues(NetworkForm.encode(fields(current.values(),"1".repeat(257))));}catch(IllegalArgumentException ex){denied=true;}check(denied,"過大許可リストをPayload内でも拒否");
        var draft=new NetworkFormDraft();draft.receive(NetworkForm.captureAll(s.nodes().get("sw")),0,true,"");draft.edit(fields(draft.values(2),"none"));
        draft.receive(NetworkForm.captureAll(s.nodes().get("sw")),0,true,"");check(draft.values(2).allowedVlans().equals("none")&&draft.dirty(2),"周期同期で未保存許可欄を消さない");
        var request=draft.submit(2);draft.receive(NetworkForm.captureAll(s.nodes().get("sw")),request.id()+1,true,"他の応答");check(draft.pending(),"異なる応答IDで成功扱いしない");
        draft.receive(NetworkForm.captureAll(s.nodes().get("sw")),request.id(),false,"競合");check(!draft.pending()&&draft.values(2).allowedVlans().equals("none"),"拒否されても入力を残す");
        draft.reload(2);check(draft.values(2).allowedVlans().equals("10,20"),"明示的再読込で現在値に戻る");
        var backup=ConfigBackup.capture("switch",s.nodes().get("sw"),1);var fresh=new NetworkProfile(DeviceKind.L2_SWITCH);
        check(backup.restored(fresh).ports[2].allowedVlans.toString().equals("10,20"),"設定バックアップ/復元にも保存される");
        System.out.println("TrunkFormTest: "+checks+" checks passed");
    }
}
