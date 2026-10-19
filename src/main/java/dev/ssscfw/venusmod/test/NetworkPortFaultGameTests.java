package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** 通常の青ケーブル・実メニュー・修理Item・Block.getDropsを使い、成功結果は注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkPortFaultGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z,String ip){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var d=(NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);d.profile().address=ip;
        // 手動試験中のランダムイベントは完全安定化で防止する。手動故障は別操作。
        d.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));return d;
    }
    private static void wire(GameTestHelper h,int x,int z){
        var pos=h.absolutePos(new BlockPos(x,2,z));var cable=NetworkContent.DYED_CABLES.get(CableGrade.CAT5E).get(DyeColor.BLUE).get();
        h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(cable.defaultBlockState(),h.getLevel(),pos),3);
    }
    private static FakePlayer player(GameTestHelper h,NetworkDeviceEntity d,String name){
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),name));
        p.setPos(d.getBlockPos().getX()+0.5,d.getBlockPos().getY()+0.5,d.getBlockPos().getZ()+0.5);return p;
    }
    private static NetworkMenu menu(FakePlayer p,NetworkDeviceEntity d,int id){var menu=new NetworkMenu(id,p.getInventory(),d);p.containerMenu=menu;return menu;}
    private static void config(GameTestHelper h,NetworkWorld w,NetworkDeviceEntity d,String text){
        var reply=NetworkCli.run(w.simulator(),d.id(),text);h.assertTrue(reply.changed(),text+" / "+reply.lines());d.adoptProfile(w.simulator().nodes().get(d.id()));d.refreshState();
    }
    private static void repair(FakePlayer p,NetworkDeviceEntity d,Direction face,boolean shift){
        p.setShiftKeyDown(shift);
        var hit=new BlockHitResult(Vec3.atCenterOf(d.getBlockPos()),face,d.getBlockPos(),false);
        var context=new UseOnContext(p,InteractionHand.MAIN_HAND,hit);
        NetworkContent.NETWORK_ITEMS.get("network_repair_kit").get().useOn(context);
        p.setShiftKeyDown(false);
    }
    @GameTest(template="test/wide_empty",batch="network_port_faults",timeoutTicks=100)
    public static void individualPortFailureAndRealRepairItem(GameTestHelper h){
        var sw=device(h,DeviceKind.L2_SWITCH,5,5,"");var a=device(h,DeviceKind.PC,5,1,"10.117.0.10");
        var b=device(h,DeviceKind.PC,1,5,"10.117.0.20");var c=device(h,DeviceKind.PC,5,9,"10.117.0.30");
        for(int z:new int[]{2,3,4,6,7,8})wire(h,5,z);for(int x=2;x<5;x++)wire(h,x,5);
        var w=NetworkWorld.get(h.getLevel());var owner=player(h,sw,"port-owner");var guest=player(h,sw,"port-guest");sw.setOwnerIfAbsent(owner);sw.profile().publicService=true;
        var ownerMenu=menu(owner,sw,201);var guestMenu=menu(guest,sw,202);
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_repair_kit").get(),3));
        guest.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_repair_kit").get(),2));
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(w.ready()&&w.simulator().ping(a.id(),c.profile().address).success(),"実配線で初期通信");
            guestMenu.command(guest,"port 0 fault");h.assertTrue(!sw.profile().ports[0].failed,"publicでも故障試験の管理権限はない");
            ownerMenu.command(owner,"port 0 fault");h.assertTrue(sw.profile().ports[0].failed&&!sw.profile().failed,"所有者のメニューからP0だけ故障");
            h.assertTrue(!w.simulator().ping(a.id(),c.profile().address).success()&&w.simulator().ping(b.id(),c.profile().address).success(),"故障経路だけ不通、正常ポート間は通る");
            h.assertTrue(sw.getBlockState().getValue(NetworkDeviceBlock.FAULTED)&&ownerMenu.snapshot().contains("P0"),"既存故障テクスチャと具体的な箇所表示");
            repair(guest,sw,Direction.NORTH,true);h.assertTrue(sw.profile().ports[0].failed&&guest.getMainHandItem().getCount()==2,"権限外は未修理・未消費");
            repair(owner,sw,Direction.EAST,true);h.assertTrue(sw.profile().ports[0].failed&&owner.getMainHandItem().getCount()==3,"別の健康ポートは消費しない");
        }).thenIdle(3).thenExecute(()->{
            ownerMenu.command(owner,"port 0 enable");h.assertTrue(sw.profile().ports[0].failed,"再有効化で修理しない");
            repair(owner,sw,Direction.NORTH,true);
            h.assertTrue(!sw.profile().ports[0].failed&&owner.getMainHandItem().getCount()==2,"Shift+北でP0だけ修理・1個消費");
            h.assertTrue(w.simulator().ping(a.id(),c.profile().address).success()&&!sw.getBlockState().getValue(NetworkDeviceBlock.FAULTED),"元の配線で復旧し正常テクスチャへ");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_port_fault_storage",timeoutTicks=100)
    public static void dropBackupAndRepairKeepPhysicalState(GameTestHelper h){
        var d=device(h,DeviceKind.REDSTONE_TERMINAL,2,2,"10.118.0.20");var w=NetworkWorld.get(h.getLevel());var owner=player(h,d,"port-storage");var guest=player(h,d,"port-other");d.setOwnerIfAbsent(owner);
        d.profile().ports[2].enabled=false;d.profile().ports[2].vlan=23;d.profile().ports[2].allowedVlans=VlanAllowance.parse("23,100");
        d.modules.setItem(2,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));d.setRedstone(11);
        var copy=new NetworkDeviceEntity[1];
        h.startSequence().thenIdle(15).thenExecute(()->{
            var backup=ConfigBackup.capture(d.id(),d.profile(),h.getLevel().getGameTime());
            config(h,w,d,"port 0 fault");config(h,w,d,"port 2 fault");
            h.assertTrue(d.redstone()==11&&h.getLevel().getSignal(d.getBlockPos(),Direction.WEST)==11,"ポート故障を本体停止と誤認しない(保持設定)");
            d.adoptProfile(backup.restored(d.profile()));d.refreshState();h.assertTrue(d.profile().ports[0].failed&&d.profile().ports[2].failed,"設定復元で物理故障を巻戻さない");
            var drops=Block.getDrops(d.getBlockState(),h.getLevel(),d.getBlockPos(),d);h.assertTrue(drops.size()==1,"機器アイテム1個");
            copy[0]=device(h,DeviceKind.REDSTONE_TERMINAL,6,2,"");copy[0].restoreItem(drops.getFirst());var p=copy[0].profile();
            h.assertTrue(p.ports[0].failed&&p.ports[2].failed&&!p.failed&&copy[0].getBlockState().getValue(NetworkDeviceBlock.FAULTED),"ポート故障とランプを再配置時に復元");
            h.assertTrue(!p.ports[2].enabled&&p.ports[2].vlan==23&&p.ports[2].moduleTier==2&&p.ports[2].allowedVlans.contains(100),"設定・実装着品も保持");
            h.assertTrue(copy[0].canUse(owner)&&!copy[0].canUse(guest)&&!copy[0].id().equals(d.id()),"権限保持・複製IDを区別");
            owner.setPos(copy[0].getBlockPos().getX()+0.5,copy[0].getBlockPos().getY()+0.5,copy[0].getBlockPos().getZ()+0.5);
            owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_repair_kit").get(),3));
            repair(owner,copy[0],Direction.NORTH,true);h.assertTrue(!p.ports[0].failed&&p.ports[2].failed&&owner.getMainHandItem().getCount()==2,"Shift修理は他ポートを直さない");
            config(h,w,copy[0],"fault");repair(owner,copy[0],Direction.SOUTH,true);
            h.assertTrue(copy[0].profile().failed&&!copy[0].profile().ports[2].failed&&!copy[0].profile().ports[2].enabled,"個別修理は本体/管理OFFを変えない");
            repair(owner,copy[0],Direction.EAST,false);
            h.assertTrue(!PortFaults.hasFault(copy[0].profile())&&owner.getMainHandItem().isEmpty(),"通常修理で本体も復旧し1個消費");
            h.assertTrue(copy[0].profile().ports[2].moduleTier==2&&copy[0].modules.getItem(2).getCount()==1,"修理でモジュールを失わない・複製しない");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_port_fault_stp",timeoutTicks=850)
    public static void classicStpReroutesAfterPortFailure(GameTestHelper h){
        var a=device(h,DeviceKind.L2_SWITCH,2,2,"");var b=device(h,DeviceKind.L2_SWITCH,6,2,"");var c=device(h,DeviceKind.L2_SWITCH,10,2,"");
        var x=device(h,DeviceKind.PC,2,0,"10.119.0.10");var y=device(h,DeviceKind.PC,10,0,"10.119.0.20");
        for(var sw:List.of(a,b,c)){sw.profile().spanning.classic=true;sw.profile().spanning.helloTicks=20;sw.profile().spanning.maxAgeTicks=120;sw.profile().spanning.forwardDelayTicks=80;}
        a.profile().spanning.priority=0;a.profile().spanning.costs[1]=10;b.profile().spanning.costs[3]=10;b.profile().spanning.costs[1]=10;c.profile().spanning.costs[3]=10;
        a.profile().spanning.costs[2]=100;c.profile().spanning.costs[2]=100;
        for(int n=3;n<6;n++)wire(h,n,2);for(int n=7;n<10;n++)wire(h,n,2);wire(h,2,1);wire(h,10,1);
        for(int z=3;z<=6;z++){wire(h,2,z);wire(h,10,z);}for(int n=3;n<10;n++)wire(h,n,6);
        var w=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(250).thenExecute(()->{
            var before=w.simulator().ping(x.id(),y.profile().address);h.assertTrue(before.success()&&before.nodes().contains(b.id()),"BPDUから低コストのB経路を使用");
            config(h,w,b,"port 1 fault");h.assertTrue(!b.profile().failed&&w.simulator().linkCapacity(w.simulator().linkAt(b.id(),3))>0,"B本体と西ポートは正常");
        }).thenIdle(10).thenExecute(()->h.assertTrue(!w.simulator().ping(x.id(),y.profile().address).success(),"代替ポートもForward Delayを待つ"))
          .thenIdle(210).thenExecute(()->{
            var alternate=w.simulator().ping(x.id(),y.profile().address);h.assertTrue(alternate.success()&&!alternate.nodes().contains(b.id()),"故障ポートを避けて再収束");
            var owner=player(h,b,"stp-port-repair");b.setOwnerIfAbsent(owner);owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_repair_kit").get()));
            repair(owner,b,Direction.EAST,true);h.assertTrue(!b.profile().ports[1].failed&&owner.getMainHandItem().isEmpty(),"実修理キットで東ポートを修理");
        }).thenIdle(260).thenExecute(()->{
            var restored=w.simulator().ping(x.id(),y.profile().address);h.assertTrue(restored.success()&&restored.nodes().contains(b.id()),"修理後に低コスト経路を再学習");
        }).thenSucceed();
    }
}
