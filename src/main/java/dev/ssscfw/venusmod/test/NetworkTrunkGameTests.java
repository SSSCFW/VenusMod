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

/** 色付きケーブルの実配線とメニュー/ドロップで検査。論理リンクを直接作らない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkTrunkGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z,String ip){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var d=(NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);d.profile().address=ip;return d;
    }
    private static void wire(GameTestHelper h,int x,int z){
        var pos=h.absolutePos(new BlockPos(x,2,z));var block=NetworkContent.DYED_CABLES.get(CableGrade.CAT5E).get(DyeColor.BLUE).get();
        h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(block.defaultBlockState(),h.getLevel(),pos),3);
    }
    private static void command(GameTestHelper h,NetworkWorld world,NetworkDeviceEntity d,String input){
        var reply=NetworkCli.run(world.simulator(),d.id(),input);h.assertTrue(reply.changed(),input+" / "+reply.lines());d.adoptProfile(world.simulator().nodes().get(d.id()));d.refreshState();
    }
    @GameTest(template="test/wide_empty",batch="network_trunk_wiring",timeoutTicks=90)
    public static void taggedTrafficHonorsBothEnds(GameTestHelper h){
        var left=device(h,DeviceKind.L2_SWITCH,5,5,"");var right=device(h,DeviceKind.L2_SWITCH,11,5,"");
        for(var d:List.of(left,right)){d.profile().ports[0].vlan=10;d.profile().ports[2].vlan=20;}
        left.profile().ports[1].trunk=true;right.profile().ports[3].trunk=true;
        var a=device(h,DeviceKind.PC,5,1,"10.110.0.1");var b=device(h,DeviceKind.PC,11,1,"10.110.0.2");
        var c=device(h,DeviceKind.PC,5,9,"10.120.0.1");device(h,DeviceKind.PC,11,9,"10.120.0.2");
        for(int x=6;x<11;x++)wire(h,x,5);
        for(int x:new int[]{5,11})for(int z:new int[]{2,3,4,6,7,8})wire(h,x,z);
        var world=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(world.ready(),"実イベントから青ケーブルの配線を解析");
            h.assertTrue(world.simulator().ping(a.id(),"10.110.0.2").success()&&world.simulator().ping(c.id(),"10.120.0.2").success(),"既定allは両VLANを中継");
            command(h,world,left,"port 1 allowed 10");
            h.assertTrue(world.simulator().ping(a.id(),"10.110.0.2").success()&&!world.simulator().ping(c.id(),"10.120.0.2").success(),"片側の許可VLANだけ通す");
            h.assertTrue(!world.simulator().portStatus(left.id(),1).contains("Link Down"),"VLANを除外しても物理リンクは残る");
        }).thenIdle(3).thenExecute(()->{
            command(h,world,right,"port 3 allowed 20");
            h.assertTrue(!world.simulator().ping(a.id(),"10.110.0.2").success(),"相手の受信許可も必要");
            command(h,world,left,"port 1 allowed all");
            h.assertTrue(world.simulator().ping(c.id(),"10.120.0.2").success(),"共通のVLAN20だけ復旧");
            String before=right.profile().encode();var rejected=NetworkCli.run(world.simulator(),right.id(),"port 3 allowed 10,");
            h.assertTrue(!rejected.changed()&&before.equals(right.profile().encode()),"不正リストを部分保存しない");
        }).thenIdle(3).thenExecute(()->{
            command(h,world,right,"port 3 allowed all");
            h.assertTrue(world.simulator().ping(a.id(),"10.110.0.2").success()&&world.simulator().ping(c.id(),"10.120.0.2").success(),"許可を戻すと同じ配線で復旧");
        }).thenSucceed();
    }
    private static NetworkForm.Values fields(NetworkForm.Values v,String list){return new NetworkForm.Values(v.scope(),v.name(),v.address(),v.prefix(),v.gateway(),v.dns(),v.enabled(),v.trunk(),v.optical(),v.vlan(),v.speed(),list);}
    @GameTest(template="test/wide_empty",batch="network_trunk_forms",timeoutTicks=90)
    public static void formPermissionsConflictAndDrop(GameTestHelper h){
        var d=device(h,DeviceKind.L2_SWITCH,2,2,"");d.profile().ports[1].trunk=true;d.profile().ports[1].allowedVlans=VlanAllowance.parse("10");
        var owner=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"trunk-owner"));var guest=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"trunk-guest"));
        owner.setPos(d.getBlockPos().getX()+0.5,d.getBlockPos().getY()+0.5,d.getBlockPos().getZ()+0.5);guest.setPos(owner.getX(),owner.getY(),owner.getZ());d.setOwnerIfAbsent(owner);d.profile().publicService=true;
        var menu=new NetworkMenu(61,owner.getInventory(),d);owner.containerMenu=menu;var denied=new NetworkMenu(62,guest.getInventory(),d);guest.containerMenu=denied;
        var old=new NetworkForm.View[1];
        h.startSequence().thenIdle(15).thenExecute(()->{
            old[0]=NetworkForm.capture(d.profile(),1);String view=NetworkForm.encode(old[0]);String edit=NetworkForm.encode(fields(old[0].values(),"20,30-40"));
            h.assertTrue(!denied.editForm(guest,view,edit).accepted(),"サービス公開でも許可VLANの編集権限を与えない");
            h.assertTrue(menu.editForm(owner,view,edit).accepted()&&d.profile().ports[1].allowedVlans.contains(35),"所有者の実メニューから許可リストを一括保存");
        }).thenIdle(3).thenExecute(()->{
            var stale=menu.editForm(owner,NetworkForm.encode(old[0]),NetworkForm.encode(fields(old[0].values(),"none")));
            h.assertTrue(!stale.accepted()&&stale.message().contains("ほかの操作"),"古いフォームからの上書きを競合として拒否");
            d.modules.setItem(1,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));d.profile().failed=true;
            var drops=Block.getDrops(d.getBlockState(),h.getLevel(),d.getBlockPos(),d);h.assertTrue(drops.size()==1,"設定付き機器アイテム1個");
            var copy=device(h,DeviceKind.L2_SWITCH,7,2,"");copy.restoreItem(drops.getFirst());
            h.assertTrue(copy.profile().ports[1].trunk&&copy.profile().ports[1].allowedVlans.toString().equals("20,30-40"),"許可リストをドロップから復元");
            h.assertTrue(copy.profile().failed&&copy.profile().ports[1].moduleTier==2&&copy.canUse(owner)&&!copy.canUse(guest),"故障/装着品/所有権も保持");
            owner.containerMenu=owner.inventoryMenu;var now=NetworkForm.capture(d.profile(),1);
            h.assertTrue(!menu.editForm(owner,NetworkForm.encode(now),NetworkForm.encode(fields(now.values(),"all"))).accepted(),"閉じたメニューの遅延入力を拒否");
        }).thenSucceed();
    }
}
