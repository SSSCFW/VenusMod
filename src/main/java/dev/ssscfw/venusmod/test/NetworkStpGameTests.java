package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.neoforged.neoforge.gametest.*;

/** 実配線・サーバーtick・通常ドロップで検証。選出結果や論理Linkを注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkStpGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        return (NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);
    }
    private static void wire(GameTestHelper h,int x,int z){var pos=h.absolutePos(new BlockPos(x,2,z));var state=NetworkContent.CABLES.get(CableGrade.CAT5E).get().defaultBlockState();h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(state,h.getLevel(),pos),3);}
    private static StpEngine.Status status(NetworkWorld world,NetworkDeviceEntity device){return world.simulator().stp().status(new StpEngine.Key(device.id(),1));}
    @GameTest(template="test/wide_empty",batch="network_stp",timeoutTicks=1600)
    public static void bpdusSelectTreeAndRecoverRealWiring(GameTestHelper h){
        var a=device(h,DeviceKind.L2_SWITCH,2,2);var b=device(h,DeviceKind.L2_SWITCH,6,2);var c=device(h,DeviceKind.L2_SWITCH,10,2);
        var x=device(h,DeviceKind.PC,2,0);var y=device(h,DeviceKind.PC,10,0);
        x.profile().address="10.96.1.10";y.profile().address="10.96.1.20";
        for(var sw:List.of(a,b,c)){
            sw.profile().spanning.classic=true;sw.profile().spanning.helloTicks=20;sw.profile().spanning.maxAgeTicks=120;sw.profile().spanning.forwardDelayTicks=80;
            sw.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));
        }
        a.profile().spanning.priority=0;a.profile().spanning.costs[1]=10;b.profile().spanning.costs[3]=10;b.profile().spanning.costs[1]=10;c.profile().spanning.costs[3]=10;
        a.profile().spanning.costs[2]=100;c.profile().spanning.costs[2]=100;
        for(int n=3;n<6;n++)wire(h,n,2);for(int n=7;n<10;n++)wire(h,n,2);
        wire(h,2,1);wire(h,10,1);for(int z=3;z<=6;z++){wire(h,2,z);wire(h,10,z);}for(int n=3;n<10;n++)wire(h,n,6);
        var world=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(world.ready(),"通常の配線解析が完了: "+world.diagnostic());h.assertTrue(!world.simulator().ping(x.id(),y.profile().address).success(),"Listening中は転送しない");
        }).thenIdle(235).thenExecute(()->{
            var result=world.simulator().ping(x.id(),y.profile().address);h.assertTrue(result.success()&&result.nodes().contains(b.id()),"B経由の低コスト経路: "+result.message());
            h.assertTrue(status(world,c).rootPort()==3&&status(world,c).ports().get(2).state()==StpEngine.State.BLOCKING,"Cの南は代替ポート");
            h.assertTrue(status(world,c).ports().get(2).received()>0,"BlockingでもBPDUを受信");b.profile().failed=true;b.refreshState();
        }).thenIdle(10).thenExecute(()->h.assertTrue(!world.simulator().ping(x.id(),y.profile().address).success(),"故障後はForward Delayを待つ"))
          .thenIdle(210).thenExecute(()->{
            var result=world.simulator().ping(x.id(),y.profile().address);h.assertTrue(result.success()&&!result.nodes().contains(b.id()),"予備ケーブルへ切替: "+result.message());b.repair();
        }).thenIdle(260).thenExecute(()->{
            h.assertTrue(world.simulator().ping(x.id(),y.profile().address).nodes().contains(b.id()),"修理後は安い経路へ戻る");
            h.getLevel().removeBlock(h.absolutePos(new BlockPos(7,2,2)),false);
        }).thenIdle(250).thenExecute(()->{
            var result=world.simulator().ping(x.id(),y.profile().address);h.assertTrue(result.success()&&!result.nodes().contains(b.id()),"実ケーブル破壊でも代替へ: "+result.message());wire(h,7,2);
        }).thenIdle(250).thenExecute(()->{
            var result=world.simulator().ping(x.id(),y.profile().address);h.assertTrue(result.success()&&result.nodes().contains(b.id()),"再配線後に再選出");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_stp",timeoutTicks=100)
    public static void dropKeepsStpSettingsModulesAndOwner(GameTestHelper h){
        var a=device(h,DeviceKind.L3_SWITCH,2,10);var p=a.profile();
        var owner=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"stp-owner"),ClientInformation.createDefault());
        var guest=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"stp-guest"),ClientInformation.createDefault());
        a.setOwnerIfAbsent(owner);p.spanning.classic=true;p.spanning.priority=4096;p.spanning.vlanPriorities.put(20,8192);
        p.spanning.costs[1]=123;p.spanning.portPriorities[1]=16;p.spanning.edge[2]=true;p.spanning.guard[2]=true;
        p.spanning.helloTicks=20;p.spanning.maxAgeTicks=120;p.spanning.forwardDelayTicks=80;p.failed=true;
        a.modules.setItem(1,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
        var drops=Block.getDrops(a.getBlockState(),h.getLevel(),a.getBlockPos(),a);h.assertTrue(drops.size()==1,"通常ドロップは機器1個");
        var b=device(h,DeviceKind.L3_SWITCH,6,10);b.restoreItem(drops.getFirst());var restored=b.profile();
        h.assertTrue(restored.spanning.classic&&restored.spanning.priority==4096&&restored.spanning.vlanPriorities.get(20)==8192,"方式と優先度を保持");
        h.assertTrue(restored.spanning.costs[1]==123&&restored.spanning.portPriorities[1]==16&&restored.spanning.edge[2]&&restored.spanning.guard[2],"ポート設定を保持");
        h.assertTrue(restored.spanning.helloTicks==20&&restored.spanning.maxAgeTicks==120&&restored.spanning.forwardDelayTicks==80,"タイマー設定を保持");
        h.assertTrue(restored.failed&&restored.ports[1].moduleTier==2&&b.modules.getItem(1).getCount()==1,"故障・装着品を保持し増殖しない");
        h.assertTrue(b.canUse(owner)&&!b.canUse(guest),"持ち運び後の権限");
        h.assertTrue(!NetworkWorld.get(h.getLevel()).simulator().stp().forwarding(new StpEngine.Key(b.id(),1),1),"動的役割は保存しない");h.succeed();
    }
}
