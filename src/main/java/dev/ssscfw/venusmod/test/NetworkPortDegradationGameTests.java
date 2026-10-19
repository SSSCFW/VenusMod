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

/** 実ケーブル、実メニュー、修理Item、通常ドロップを使う。論理リンクや転送結果を注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkPortDegradationGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z,String ip){
        var pos=h.absolutePos(new BlockPos(x,2,z));
        h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var d=(NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);d.profile().address=ip;
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
    private static NetworkMenu menu(FakePlayer p,NetworkDeviceEntity d,int id){
        var m=new NetworkMenu(id,p.getInventory(),d);p.containerMenu=m;return m;
    }
    private static void repair(FakePlayer p,NetworkDeviceEntity d,Direction face,boolean shift){
        p.setShiftKeyDown(shift);var hit=new BlockHitResult(Vec3.atCenterOf(d.getBlockPos()),face,d.getBlockPos(),false);
        NetworkContent.NETWORK_ITEMS.get("network_repair_kit").get().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit));p.setShiftKeyDown(false);
    }
    private static void config(GameTestHelper h,NetworkWorld w,NetworkDeviceEntity d,String text){
        var r=NetworkCli.run(w.simulator(),d.id(),text);h.assertTrue(r.changed(),text+" / "+r.lines());
        d.adoptProfile(w.simulator().nodes().get(d.id()));d.refreshState();
    }
    @GameTest(template="test/wide_empty",batch="network_port_degradation",timeoutTicks=100)
    public static void linkStaysUpWhileVirtualTransferSlows(GameTestHelper h){
        var a=device(h,DeviceKind.PC,2,2,"10.121.0.10");var b=device(h,DeviceKind.PC,6,2,"10.121.0.20");
        for(int x=3;x<6;x++)wire(h,x,2);
        var world=NetworkWorld.get(h.getLevel());var owner=player(h,a,"slow-owner");var guest=player(h,a,"slow-guest");a.setOwnerIfAbsent(owner);
        var ownMenu=menu(owner,a,301);var guestMenu=menu(guest,a,302);
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_repair_kit").get(),2));
        guest.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_repair_kit").get(),1));
        h.startSequence().thenIdle(15).thenExecute(()->{
            var sim=world.simulator();h.assertTrue(world.ready()&&sim.ping(a.id(),b.profile().address).bps()==1_000_000_000L,"青色ケーブルから通常1Gで通信");
            guestMenu.command(guest,"port 1 degrade");h.assertTrue(!a.profile().ports[1].degraded,"権限外の劣化試験を拒否");
            ownMenu.command(owner,"port 1 degrade");
            var ping=sim.ping(a.id(),b.profile().address);
            h.assertTrue(a.profile().ports[1].degraded&&!a.profile().ports[1].failed&&ping.success()&&ping.bps()==100_000_000L,"所有者の命令でLink Upのまま100Mへ劣化");
            h.assertTrue(a.getBlockState().getValue(NetworkDeviceBlock.FAULTED)&&ownMenu.snapshot().contains("劣化"),"既存の故障テクスチャとGUIの劣化表示");
            var readout=NetworkGogglesReadout.inspect(sim,a.id()).get(1);
            h.assertTrue(readout.linkUp()&&readout.maximumBps()==100_000_000L,"ゴーグルはUpと実効上限を表示");
            h.assertTrue(sim.startTransfer(a.id(),b.profile().address,100_000_000L).success(),"劣化中でも仮想転送開始");
        }).thenIdle(20).thenExecute(()->{
            var sim=world.simulator();var flow=sim.flows().stream().filter(f->f.source.equals(a.id())).findFirst().orElseThrow();
            h.assertTrue(flow.remaining>80_000_000&&flow.remaining<98_000_000,"サーバーtickで転送量が100M相当に制限");
            repair(guest,a,Direction.EAST,true);h.assertTrue(a.profile().ports[1].degraded&&guest.getMainHandItem().getCount()==1,"権限外修理で消費しない");
            repair(owner,a,Direction.NORTH,true);h.assertTrue(a.profile().ports[1].degraded&&owner.getMainHandItem().getCount()==2,"正常な面の修理で消費しない");
            repair(owner,a,Direction.EAST,true);
            h.assertTrue(!a.profile().ports[1].degraded&&owner.getMainHandItem().getCount()==1,"Shift東面で劣化のみ修理");
            h.assertTrue(sim.ping(a.id(),b.profile().address).bps()==1_000_000_000L&&!a.getBlockState().getValue(NetworkDeviceBlock.FAULTED),"通常の速度・テクスチャへ復帰");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_port_degradation_storage",timeoutTicks=100)
    public static void itemAndConfigurationRestoreKeepDegradation(GameTestHelper h){
        var d=device(h,DeviceKind.REDSTONE_TERMINAL,2,2,"10.122.0.20");var world=NetworkWorld.get(h.getLevel());
        var owner=player(h,d,"slow-storage");var guest=player(h,d,"slow-other");d.setOwnerIfAbsent(owner);d.setRedstone(11);
        d.profile().ports[2].enabled=false;d.profile().ports[2].vlan=23;
        d.modules.setItem(2,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
        h.startSequence().thenIdle(15).thenExecute(()->{
            var backup=ConfigBackup.capture(d.id(),d.profile(),h.getLevel().getGameTime());
            config(h,world,d,"port 0 degrade");config(h,world,d,"port 2 degrade");
            h.assertTrue(d.redstone()==11,"劣化は本体停止ではなく、保持出力を変えない");
            d.adoptProfile(backup.restored(d.profile()));d.refreshState();
            h.assertTrue(d.profile().ports[0].degraded&&d.profile().ports[2].degraded,"古い設定を復元しても物理劣化を無料修理しない");
            var drops=Block.getDrops(d.getBlockState(),h.getLevel(),d.getBlockPos(),d);h.assertTrue(drops.size()==1,"装着品と状態を機器1個に保存");
            var copy=device(h,DeviceKind.REDSTONE_TERMINAL,6,2,"");copy.restoreItem(drops.getFirst());
            h.assertTrue(copy.profile().ports[0].degraded&&copy.profile().ports[2].degraded&&!copy.profile().failed,"実ドロップから劣化を復元");
            h.assertTrue(copy.profile().ports[2].vlan==23&&!copy.profile().ports[2].enabled&&copy.profile().ports[2].moduleTier==2,"VLAN・管理OFF・装着品を維持");
            h.assertTrue(copy.canUse(owner)&&!copy.canUse(guest)&&!copy.id().equals(d.id()),"所有権を保持し同時複製のIDを分ける");
            owner.setPos(copy.getBlockPos().getX()+0.5,copy.getBlockPos().getY()+0.5,copy.getBlockPos().getZ()+0.5);
            owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_repair_kit").get(),2));
            repair(owner,copy,Direction.SOUTH,true);
            h.assertTrue(copy.profile().ports[0].degraded&&!copy.profile().ports[2].degraded&&!copy.profile().ports[2].enabled,"個別修理は他ポートや管理OFFを変えない");
            repair(owner,copy,Direction.NORTH,false);
            h.assertTrue(!PortFaults.hasFault(copy.profile())&&owner.getMainHandItem().isEmpty(),"通常修理で残りの劣化も復旧");
        }).thenSucceed();
    }
}
