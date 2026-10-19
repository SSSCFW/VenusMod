import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;

public final class TrunkAllowanceTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        var all=VlanAllowance.parse("all");var none=VlanAllowance.parse("none");
        check(all.contains(1)&&all.contains(4094)&&!all.contains(0)&&!all.contains(4095),"全許可も予約IDを通さない");
        check(!none.contains(1)&&!none.contains(4094),"明示noneは全拒否");
        var list=VlanAllowance.parse("40,20-30,10,22,29-35");
        check(list.toString().equals("10,20-35,40"),"順序/重複/重なりを正規化");
        check(list.contains(10)&&list.contains(35)&&!list.contains(19)&&!list.contains(36),"範囲端点");
        check(all.equals(VlanAllowance.parse("1-4094")),"全範囲の意味比較");
        check(VlanAllowance.parse("  ALL  ").equals(all),"前後空白とキーワードの大文字");
        check(VlanAllowance.parse("0001,2-3").toString().equals("1-3"),"ゼロ埋めを正規化");
        for(String bad:Arrays.asList(null,""," ","0","4095","-1","30-20","1,","1,,2",",1","all,10","none,10","10 - 20","1.0","1-2-3","65536","１","1\n2","1,".repeat(129))){
            boolean refused=false;try{VlanAllowance.parse(bad);}catch(IllegalArgumentException ex){refused=true;}check(refused,"不正な許可リストを拒否: "+bad);
        }
        var port=new NetworkProfile.Port();port.allowedVlans=none;port.vlan=10;
        check(port.carriesVlan(10)&&!port.carriesVlan(11),"Accessは保存済みtrunkリストに影響されない");
        port.trunk=true;check(!port.carriesVlan(10),"Trunk切替で保存したnoneが有効");
        port.allowedVlans=list;check(port.carriesVlan(10),"Trunk許可VLAN");
        port.enabled=false;check(!port.carriesVlan(10),"管理停止を許可リストで迂回しない");
        System.out.println("TrunkAllowanceTest: "+checks+" checks passed");
    }
}
