package dev.ssscfw.venusmod.test;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.neoforged.neoforge.gametest.*;

/** 通常の実ブロック配線・ドロップで検証。学習経路やリンクは注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkNatGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,int z){
        var pos=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        return (NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);
    }
    private static void wire(GameTestHelper h,int x,int z){var pos=h.absolutePos(new BlockPos(x,2,z));var state=NetworkContent.CABLES.get(CableGrade.CAT5E).get().defaultBlockState();h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(state,h.getLevel(),pos),3);}
    private static void config(GameTestHelper h,NetworkWorld world,NetworkDeviceEntity device,String command){
        var reply=NetworkServiceCommands.run(world.simulator(),device.id(),command);h.assertTrue(reply.changed(),command+" / "+reply.lines());
        device.adoptProfile(world.simulator().nodes().get(device.id()));device.refreshState();
    }
    @GameTest(template="test/wide_empty",batch="network_nat",timeoutTicks=160)
    public static void actualWiringNaptForwardingAndAcl(GameTestHelper h){
        var router=device(h,DeviceKind.ROUTER,5,5);var wan=device(h,DeviceKind.SERVER,5,1);
        var sw=device(h,DeviceKind.L2_SWITCH,5,9);var a=device(h,DeviceKind.PC,1,9);var b=device(h,DeviceKind.SERVER,9,9);
        router.profile().dhcp=false;for(var port:router.profile().ports)port.address="";
        router.profile().ports[2].address="192.168.91.1";router.profile().ports[0].address="203.0.113.1";
        wan.profile().address="203.0.113.20";wan.profile().web=true;
        a.profile().address="192.168.91.10";a.profile().gateway="192.168.91.1";
        b.profile().address="192.168.91.20";b.profile().gateway="192.168.91.1";b.profile().web=true;
        for(int z=2;z<5;z++)wire(h,5,z);for(int z=6;z<9;z++)wire(h,5,z);
        for(int x=2;x<5;x++)wire(h,x,9);for(int x=6;x<9;x++)wire(h,x,9);
        var world=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(world.ready(),"通常イベントで配線が完成: "+world.diagnostic());var sim=world.simulator();
            h.assertTrue(!sim.request(a.id(),wan.profile().address,"tcp",80).success(),"NATなしでは外側からprivateへの応答経路がない");
            config(h,world,router,"nat port 2 inside");config(h,world,router,"nat port 0 outside");config(h,world,router,"nat on");
            h.assertTrue(sim.requestService(a.id(),wan.profile().address,"tcp",80,80).success(),"実配線でNAPT往復");
            h.assertTrue(sim.requestService(b.id(),wan.profile().address,"tcp",80,80).success(),"2台目も外側IPを共有");
            h.assertTrue(sim.nat().sessions(router.id()).size()==2,"端末別変換表");
            config(h,world,router,"nat forward add 0 tcp 8080 192.168.91.20 80");
            var reply=sim.requestService(wan.id(),"203.0.113.1","tcp",8080,80);
            h.assertTrue(reply.success()&&reply.destination().equals(b.id())&&reply.servicePort()==80,"ポート転送で内部の実サービスを特定");
            config(h,world,router,"acl add 2 in 10 deny tcp 192.168.91.10/32 any");config(h,world,router,"acl add 2 in 20 permit ip any any");
            reply=sim.requestService(a.id(),wan.profile().address,"tcp",80,80);
            h.assertTrue(!reply.success()&&reply.message().contains("ACL"),"入力ACLは変換前のprivate IPを検査");
            h.assertTrue(sim.nat().sessions(router.id()).isEmpty(),"拒否された通信で状態を残さない");
        }).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_nat",timeoutTicks=100)
    public static void natSettingsOwnerAndUpgradesSurviveDrop(GameTestHelper h){
        var a=device(h,DeviceKind.ROUTER,2,2);
        var owner=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"nat-owner"),ClientInformation.createDefault());
        var guest=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"nat-guest"),ClientInformation.createDefault());
        a.setOwnerIfAbsent(owner);a.profile().nat.role(0,NatSettings.Role.INSIDE);a.profile().nat.role(1,NatSettings.Role.OUTSIDE);a.profile().nat.enabled=true;
        a.profile().nat.add(new NatSettings.Forward(1,NetworkPolicy.Protocol.TCP,8080,"192.168.1.20",80));
        a.profile().failed=true;a.modules.setItem(1,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
        h.startSequence().thenIdle(10).thenExecute(()->{
            var drops=Block.getDrops(a.getBlockState(),h.getLevel(),a.getBlockPos(),a);h.assertTrue(drops.size()==1,"機器1個に保存");
            var b=device(h,DeviceKind.ROUTER,5,2);b.restoreItem(drops.getFirst());
            h.assertTrue(b.profile().nat.enabled&&b.profile().nat.role(0)==NatSettings.Role.INSIDE&&b.profile().nat.role(1)==NatSettings.Role.OUTSIDE,"NATの有効状態と役割を復元");
            h.assertTrue(b.profile().nat.forwards().size()==1&&b.profile().nat.forwards().getFirst().publicPort()==8080,"転送規則を復元");
            h.assertTrue(b.profile().failed&&b.profile().ports[1].moduleTier==2,"故障・ポート強化を保持");
            h.assertTrue(b.canUse(owner)&&!b.canUse(guest)&&!b.canControl(guest),"機器の所有権を維持");
            b.profile().publicService=true;h.assertTrue(b.canControl(guest)&&!b.canUse(guest),"サービス公開と管理権限は別");
            h.assertTrue(NetworkWorld.get(h.getLevel()).simulator().nat().sessions(b.id()).isEmpty(),"稼働変換表は機器に入れない");
        }).thenSucceed();
    }
}
