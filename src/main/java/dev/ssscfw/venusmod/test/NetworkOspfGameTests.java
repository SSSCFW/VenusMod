package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;

/** 論理Linkを注入せず、実ブロック配線・通常tick・実アイテム保存で検査する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkOspfGameTests {
    private NetworkOspfGameTests(){}
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z){
        BlockPos pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        return (NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);
    }
    private static void wire(GameTestHelper h,int x,int z){
        BlockPos pos=h.absolutePos(new BlockPos(x,2,z));var block=NetworkContent.CABLES.get(CableGrade.CAT5E).get();
        h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(block.defaultBlockState(),h.getLevel(),pos),3);
    }
    private static void router(NetworkDeviceEntity device,String rid){
        var p=device.profile();p.dhcp=false;p.ospf.enabled=true;p.ospf.routerId=rid;for(var port:p.ports)port.address="";
    }
    private static void port(NetworkDeviceEntity device,int n,String ip,boolean passive,int cost){
        var p=device.profile();p.ports[n].address=ip;p.ospf.modes[n]=passive?OspfSettings.Mode.PASSIVE:OspfSettings.Mode.ACTIVE;p.ospf.costs[n]=cost;
    }
    @GameTest(template="test/wide_empty",batch="network_ospf",timeoutTicks=500)
    public static void learnsAndBypassesFailedRouter(GameTestHelper h){
        var a=device(h,DeviceKind.ROUTER,2,2);var b=device(h,DeviceKind.ROUTER,6,2);var c=device(h,DeviceKind.ROUTER,10,2);
        var x=device(h,DeviceKind.PC,2,0);var y=device(h,DeviceKind.PC,10,0);
        router(a,"1.1.1.1");router(b,"2.2.2.2");router(c,"3.3.3.3");
        port(a,1,"10.0.12.1",false,10);port(b,3,"10.0.12.2",false,10);port(b,1,"10.0.23.2",false,10);port(c,3,"10.0.23.3",false,10);
        port(a,2,"10.0.13.1",false,50);port(c,2,"10.0.13.3",false,50);port(a,0,"192.168.1.1",true,1);port(c,0,"192.168.3.1",true,1);
        x.profile().address="192.168.1.10";x.profile().gateway="192.168.1.1";y.profile().address="192.168.3.10";y.profile().gateway="192.168.3.1";
        for(int i=3;i<=5;i++)wire(h,i,2);for(int i=7;i<=9;i++)wire(h,i,2);
        wire(h,2,1);wire(h,10,1);for(int z=3;z<=6;z++){wire(h,2,z);wire(h,10,z);}for(int i=3;i<10;i++)wire(h,i,6);
        var world=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(10).thenExecute(()->{
            h.assertTrue(world.ready(),"配線解析が完了");h.assertTrue(!world.simulator().ping(x.id(),y.profile().address).success(),"Hello/DB同期の完了前は経路を知らない");
        }).thenIdle(110).thenExecute(()->{
            var result=world.simulator().ping(x.id(),y.profile().address);h.assertTrue(result.success()&&result.nodes().contains(b.id()),"低コストのR-B経由で往復: "+result.message());
            h.assertTrue(a.profile().routes.isEmpty(),"自動学習が静的経路を改変しない");b.profile().failed=true;b.refreshState();
        }).thenIdle(110).thenExecute(()->{
            var result=world.simulator().ping(x.id(),y.profile().address);h.assertTrue(result.success()&&!result.nodes().contains(b.id()),"故障したR-Bを迂回: "+result.message());b.repair();
        }).thenIdle(110).thenExecute(()->{
            var result=world.simulator().ping(x.id(),y.profile().address);h.assertTrue(result.success()&&result.nodes().contains(b.id()),"修理後に低コスト経路へ復帰");
            h.assertTrue(world.simulator().ospf().neighbors(a.id()).stream().allMatch(n->n.state().equals("FULL")),"隣接同期が再確立");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_ospf",timeoutTicks=100)
    public static void ospfSettingsSurviveDeviceDrop(GameTestHelper h){
        var a=device(h,DeviceKind.ROUTER,2,9);a.profile().ospf.routerId="7.7.7.7";a.profile().ospf.area=42;
        a.profile().ospf.modes[1]=OspfSettings.Mode.ACTIVE;a.profile().ospf.costs[1]=77;a.profile().ospf.helloTicks=40;a.profile().ospf.deadTicks=160;
        a.profile().failed=true;a.modules.setItem(1,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
        var drops=Block.getDrops(a.getBlockState(),h.getLevel(),a.getBlockPos(),a);h.assertTrue(drops.size()==1,"通常採掘の機器1個");
        ItemStack stack=drops.getFirst();var b=device(h,DeviceKind.ROUTER,4,9);b.restoreItem(stack);
        h.assertTrue(b.profile().ospf.routerId.equals("7.7.7.7")&&b.profile().ospf.area==42,"Router ID/areaを復元");
        h.assertTrue(b.profile().ospf.modes[1]==OspfSettings.Mode.ACTIVE&&b.profile().ospf.costs[1]==77,"ポート参加/コストを復元");
        h.assertTrue(b.profile().ospf.helloTicks==40&&b.profile().ospf.deadTicks==160,"タイマーを復元");
        h.assertTrue(b.profile().failed&&b.profile().ports[1].moduleTier==2,"故障・装着品を保持");
        h.assertTrue(b.profile().routes.isEmpty(),"動的OSPF経路をアイテムへ混入しない");h.succeed();
    }
}
