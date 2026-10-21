package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** 通常配線・実メニューで経路の追加/削除と認可を検査。論理リンクは注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkStaticRouteGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h,BlockPos position,DeviceKind kind) {
        h.getLevel().setBlock(position,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var device=(NetworkDeviceEntity)h.getLevel().getBlockEntity(position);
        device.modules.setItem(6,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));
        if(kind.routing()){device.profile().dhcp=false;for(var p:device.profile().ports)p.address="";}
        return device;
    }
    private static FakePlayer player(GameTestHelper h,BlockPos position,String name) {
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),name));
        p.setPos(position.getX()+0.5,position.getY()+0.5,position.getZ()+0.5);return p;
    }
    @GameTest(template="test/wide_empty",batch="network_static_routes",timeoutTicks=220)
    public static void individualRoutesRequireAuthorizedMenuAndReturnPath(GameTestHelper h) {
        var start=h.absolutePos(new BlockPos(1,2,2));
        var a=device(h,start,DeviceKind.PC);var r1=device(h,start.east(3),DeviceKind.ROUTER);
        var r2=device(h,start.east(6),DeviceKind.L3_SWITCH);var b=device(h,start.east(9),DeviceKind.SERVER);
        a.profile().address="10.181.1.10";a.profile().gateway="10.181.1.1";
        b.profile().address="10.181.2.10";b.profile().gateway="10.181.2.1";
        r1.profile().ports[3].address="10.181.1.1";r1.profile().ports[1].address="10.181.0.1";
        r2.profile().ports[3].address="10.181.0.2";r2.profile().ports[1].address="10.181.2.1";
        for(var d:new NetworkDeviceEntity[]{a,b,r1,r2})d.refreshState();
        for(int n:new int[]{1,2,4,5,7,8}){
            var pos=start.east(n);var cable=NetworkContent.DYED_CABLES.get(CableGrade.CAT5E).get(DyeColor.BLUE).get();
            h.getLevel().setBlock(pos,Block.updateFromNeighbourShapes(cable.defaultBlockState(),h.getLevel(),pos),3);
        }
        var owner=player(h,start.east(4),"route-owner");var guest=player(h,start.east(4),"route-guest");
        r1.setOwnerIfAbsent(owner);r2.setOwnerIfAbsent(owner);
        var menu=new NetworkMenu(441,owner.getInventory(),r1);var back=new NetworkMenu(442,owner.getInventory(),r2);
        var denied=new NetworkMenu(443,guest.getInventory(),r1);owner.containerMenu=menu;guest.containerMenu=denied;
        var draft=new StaticRouteDraft();draft.networkText("10.181.2.42/24");draft.gatewayText("10.181.0.2");draft.port(1);
        var world=NetworkWorld.get(h.getLevel());var sim=world.simulator();
        h.startSequence().thenIdle(15).thenWaitUntil(()->h.assertTrue(world.ready(),"通常配線解析完了"))
            .thenExecute(()->{
                h.assertTrue(!sim.ping(a.id(),b.profile().address).success(),"経路未設定では不通");
                denied.command(guest,draft.addCommand());h.assertTrue(r1.profile().routes.isEmpty(),"権限外追加拒否");
                menu.command(owner,draft.addCommand());
            }).thenIdle(4).thenWaitUntil(()->h.assertTrue(world.ready(),"追加反映"))
            .thenExecute(()->{
                h.assertTrue(r1.profile().routes.size()==1 && r1.profile().routes.getFirst().network().equals("10.181.2.0"),"正規化した1経路だけ追加");
                h.assertTrue(!sim.ping(a.id(),b.profile().address).success(),"戻り経路が必要");
                owner.containerMenu=back;back.command(owner,"route add 10.181.1.0/24 via 10.181.0.1 port 3");owner.containerMenu=menu;
            }).thenIdle(4).thenWaitUntil(()->h.assertTrue(world.ready(),"戻り反映"))
            .thenExecute(()->{
                h.assertTrue(sim.ping(a.id(),b.profile().address).success(),"実配線で両方向疎通");
                menu.command(owner,"route add 203.0.113.0/24 via 10.181.0.2 port 1");
            }).thenIdle(4).thenExecute(()->{
                menu.command(owner,draft.addCommand());h.assertTrue(r1.profile().routes.size()==2,"同値追加で重複しない");
                draft.requestDeletion();h.assertTrue(r1.profile().routes.size()==2,"確認だけでは未削除");
                denied.command(guest,draft.confirmDeletion());h.assertTrue(r1.profile().routes.size()==2,"権限外削除拒否");
            }).thenIdle(4).thenExecute(()->{
                draft.requestDeletion();owner.containerMenu=owner.inventoryMenu;menu.command(owner,draft.confirmDeletion());
                h.assertTrue(r1.profile().routes.size()==2,"閉じたメニューの削除拒否");
                owner.containerMenu=menu;draft.requestDeletion();menu.command(owner,draft.confirmDeletion());
            }).thenIdle(4).thenWaitUntil(()->h.assertTrue(world.ready(),"削除反映"))
            .thenExecute(()->{
                h.assertTrue(!sim.ping(a.id(),b.profile().address).success(),"個別削除後は対象LANに届かない");
                h.assertTrue(r1.profile().routes.size()==1 && r1.profile().routes.getFirst().network().equals("203.0.113.0"),"他経路は保持");
                h.assertTrue(r2.profile().routes.size()==1,"対向機器の経路は保持");
                h.assertTrue(r1.profile().ports[1].address.equals("10.181.0.1") && r1.modules.getItem(6).getCount()==1 && r1.owner().equals(owner.getUUID()),"IP装着品所有者を保持");
                h.assertTrue(NetworkProfile.decode(DeviceKind.ROUTER,r1.profile().encode()).routes.equals(r1.profile().routes),"保存形式互換");
            }).thenSucceed();
    }
}
