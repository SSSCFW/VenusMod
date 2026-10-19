package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** 実ケーブルとメニューから開始し、完了量・割当速度・論理リンクを注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkTransferQosGameTests {
    private record Setup(NetworkWorld world,NetworkDeviceEntity source,NetworkDeviceEntity fast,NetworkDeviceEntity slow,
                         FakePlayer owner,FakePlayer guest,NetworkMenu menu,NetworkMenu guestMenu){}
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z,String ip){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var d=(NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);d.profile().address=ip;
        d.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));return d;
    }
    private static void wire(GameTestHelper h,int x,int z){
        var pos=h.absolutePos(new BlockPos(x,2,z));var block=NetworkContent.DYED_CABLES.get(CableGrade.CAT5E).get(DyeColor.BLUE).get();
        h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(block.defaultBlockState(),h.getLevel(),pos),3);
    }
    private static FakePlayer player(GameTestHelper h,NetworkDeviceEntity source,String name){
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),name));player.setPos(source.getBlockPos().getX()+0.5,source.getBlockPos().getY()+0.5,source.getBlockPos().getZ()+0.5);return player;
    }
    private static Setup setup(GameTestHelper h){
        var a=device(h,DeviceKind.PC,2,6,"10.133.0.10");device(h,DeviceKind.L2_SWITCH,6,6,"");
        var b=device(h,DeviceKind.PC,10,6,"10.133.0.20");var c=device(h,DeviceKind.PC,6,10,"10.133.0.30");
        for(int x=3;x<=9;x++)if(x!=6)wire(h,x,6);for(int z=7;z<=9;z++)wire(h,6,z);
        var owner=player(h,a,"qos-owner");var guest=player(h,a,"qos-guest");a.setOwnerIfAbsent(owner);
        var menu=new NetworkMenu(331,owner.getInventory(),a);owner.containerMenu=menu;var unauthorized=new NetworkMenu(332,guest.getInventory(),a);guest.containerMenu=unauthorized;
        return new Setup(NetworkWorld.get(h.getLevel()),a,b,c,owner,guest,menu,unauthorized);
    }
    private static NetworkSimulator.Flow flow(Setup s,String destination){return s.world.simulator().flows().stream().filter(f->f.source.equals(s.source.id())&&f.destination.equals(destination)).findFirst().orElseThrow();}
    private static void rate(GameTestHelper h,NetworkSimulator.Flow f,double expected,String message){h.assertTrue(Math.abs(f.allocatedBps()-expected)<1,message+" / "+f.allocatedBps());}

    @GameTest(template="test/wide_empty",batch="network_transfer_qos",timeoutTicks=120)
    public static void weightedTransfersRespectMenuOwnership(GameTestHelper h){
        var s=setup(h);long[] ids=new long[2];
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(s.world.ready()&&s.world.simulator().ping(s.source.id(),s.fast.profile().address).success(),"通常の青色ケーブルから到達");
            s.guestMenu.command(s.guest,"transfer "+s.fast.profile().address+" 1000 high");h.assertTrue(s.world.simulator().flows().isEmpty(),"権限外の開始を拒否");
            s.menu.command(s.owner,"transfer "+s.fast.profile().address+" 1000 high");s.menu.command(s.owner,"transfer "+s.slow.profile().address+" 1000 low");
            h.assertTrue(s.world.simulator().flows().stream().filter(f->f.source.equals(s.source.id())).count()==2,"実メニューから二つ開始");
            ids[0]=flow(s,s.fast.id()).id;ids[1]=flow(s,s.slow.id()).id;
        }).thenIdle(20).thenExecute(()->{
            var high=flow(s,s.fast.id());var low=flow(s,s.slow.id());rate(h,high,800_000_000,"高優先度の配分");rate(h,low,200_000_000,"低優先度の配分");
            h.assertTrue(low.remaining<low.totalBytes&&Math.abs((high.totalBytes-high.remaining)/(low.totalBytes-low.remaining)-4)<0.01,"通常サーバーtickで4:1の進捗");
            s.guestMenu.command(s.guest,"transfer cancel "+ids[0]);h.assertTrue(s.world.simulator().flows().contains(high),"権限外の停止拒否");
            s.menu.command(s.owner,"transfer priority "+ids[0]+" low");
        }).thenIdle(3).thenExecute(()->{
            rate(h,flow(s,s.fast.id()),500_000_000,"同じ優先度は均等");rate(h,flow(s,s.slow.id()),500_000_000,"対向転送も均等");
            s.menu.command(s.owner,"flows");h.assertTrue(s.menu.snapshot().contains("最終割当")&&s.menu.snapshot().contains("low"),"GUIに割当と優先度を表示");
            s.menu.command(s.owner,"transfer cancel "+ids[0]);
        }).thenIdle(3).thenExecute(()->{
            rate(h,flow(s,s.slow.id()),1_000_000_000,"停止した転送の帯域を残りへ");
            s.owner.containerMenu=s.owner.inventoryMenu;s.menu.command(s.owner,"transfer cancel "+ids[1]);
            h.assertTrue(s.world.simulator().flows().stream().anyMatch(f->f.id==ids[1]),"閉じた画面の旧命令拒否");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_transfer_slack",timeoutTicks=100)
    public static void slowPeerDoesNotWasteSharedBandwidth(GameTestHelper h){
        var s=setup(h);s.slow.profile().ports[0].speedLimitBps=100_000_000L;s.slow.refreshState();
        h.startSequence().thenIdle(15).thenExecute(()->{
            s.menu.command(s.owner,"transfer "+s.fast.profile().address+" 1000");s.menu.command(s.owner,"transfer "+s.slow.profile().address+" 1000");
        }).thenIdle(20).thenExecute(()->{
            rate(h,flow(s,s.fast.id()),900_000_000,"余った400Mも高速宛先へ再配分");rate(h,flow(s,s.slow.id()),100_000_000,"低速宛先は100M上限");
            s.menu.command(s.owner,"transfer "+s.fast.profile().address+" 100 impossible");
            h.assertTrue(s.world.simulator().flows().stream().filter(f->f.source.equals(s.source.id())).count()==2,"不正優先度で既存転送を消去/追加しない");
            var drops=Block.getDrops(s.source.getBlockState(),h.getLevel(),s.source.getBlockPos(),s.source);
            h.assertTrue(drops.size()==1,"設定とモジュールを通常の機器一個へ保存");
            h.getLevel().destroyBlock(s.source.getBlockPos(),false);
            var copy=device(h,DeviceKind.PC,2,2,"");copy.restoreItem(drops.getFirst());
            h.assertTrue(copy.profile().address.equals("10.133.0.10")&&copy.profile().stability==3&&copy.canUse(s.owner)&&!copy.canUse(s.guest),"通常の保存・所有権・装着品を維持");
            h.assertTrue(s.world.simulator().flows().stream().noneMatch(f->f.source.equals(copy.id())),"持ち運びで転送・優先度を自動再開しない");
        }).thenSucceed();
    }
}
