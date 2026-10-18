package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** 通常配線・実メニュー・サーバーtick・通常ドロップ。制御期限をテスト側から注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkControlSafetyGameTests {
    private static int menuId=70;
    private static NetworkDeviceEntity device(GameTestHelper h,DeviceKind kind,int x,String ip){
        var pos=h.absolutePos(new BlockPos(x,2,5));h.getLevel().setBlock(pos,NetworkContent.DEVICES.get(kind).get().defaultBlockState(),3);
        var d=(NetworkDeviceEntity)h.getLevel().getBlockEntity(pos);d.profile().address=ip;return d;
    }
    private static FakePlayer player(GameTestHelper h,String name){return new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),name));}
    private static void wire(GameTestHelper h,int x){
        var p=h.absolutePos(new BlockPos(x,2,5));var b=NetworkContent.CABLES.get(CableGrade.CAT5E).get();h.getLevel().setBlock(p,Block.updateFromNeighbourShapes(b.defaultBlockState(),h.getLevel(),p),3);
    }
    private static void command(NetworkDeviceEntity d,FakePlayer p,String command){
        p.setPos(d.getBlockPos().getX()+0.5,d.getBlockPos().getY()+0.5,d.getBlockPos().getZ()+0.5);
        var menu=new NetworkMenu(menuId++,p.getInventory(),d);p.containerMenu=menu;menu.command(p,command);
    }
    private static void config(GameTestHelper h,NetworkWorld w,NetworkDeviceEntity d,String command){
        var result=NetworkServiceCommands.run(w.simulator(),d.id(),command);h.assertTrue(result.changed(),command+" "+result.lines());
        d.adoptProfile(w.simulator().nodes().get(d.id()));d.setChanged();
    }
    private static void signal(GameTestHelper h,NetworkDeviceEntity d,int value,String reason){
        h.assertTrue(d.redstone()==value,reason+" (端末="+d.redstone()+")");
        h.assertTrue(h.getLevel().getSignal(d.getBlockPos(),Direction.WEST)==value,reason+" (ブロックの実出力)");
    }
    @GameTest(template="test/wide_empty",batch="network_control_safety",timeoutTicks=650)
    public static void maintainedSignalStopsAfterCableLossAndNeedsExplicitRestart(GameTestHelper h){
        var pc=device(h,DeviceKind.PC,1,"10.111.0.10");var terminal=device(h,DeviceKind.REDSTONE_TERMINAL,5,"10.111.0.20");
        var owner=player(h,"control-owner");pc.setOwnerIfAbsent(owner);terminal.setOwnerIfAbsent(owner);for(int x=2;x<5;x++)wire(h,x);var w=NetworkWorld.get(h.getLevel());
        h.startSequence().thenIdle(15).thenExecute(()->{
            h.assertTrue(w.ready(),"実配線の解析");command(terminal,owner,"control-safety stop 3");signal(h,terminal,0,"保護開始は安全値");
            command(pc,owner,"redstone maintain 10.111.0.20 15");signal(h,terminal,15,"所有者の制御命令");
        }).thenIdle(85).thenExecute(()->{
            signal(h,terminal,15,"初回の期限を越えて定期通信で維持");h.getLevel().setBlock(h.absolutePos(new BlockPos(3,2,5)),Blocks.AIR.defaultBlockState(),3);
        }).thenIdle(65).thenExecute(()->{
            signal(h,terminal,0,"実断線の後に停止");h.assertTrue(w.controls().runtime().streams(pc.id()).isEmpty(),"期限切れの維持は撤去");wire(h,3);
        }).thenIdle(30).thenExecute(()->{
            signal(h,terminal,0,"配線だけ直しても自動再開しない");command(pc,owner,"redstone 10.111.0.20 12");signal(h,terminal,12,"明示的な単発命令で復旧");
        }).thenIdle(65).thenExecute(()->{
            signal(h,terminal,0,"単発は期限後に停止");command(terminal,owner,"control-safety value 7 3");signal(h,terminal,7,"指定安全値");command(pc,owner,"redstone maintain 10.111.0.20 15");
        }).thenIdle(65).thenExecute(()->{
            signal(h,terminal,15,"指定値モードでも定期維持");command(pc,owner,"redstone stop");
        }).thenIdle(65).thenExecute(()->{
            signal(h,terminal,7,"更新停止の後に指定値");command(terminal,owner,"control-safety hold");signal(h,terminal,7,"保護解除で古い15を復活させない");command(pc,owner,"redstone 10.111.0.20 11");
        }).thenIdle(65).thenExecute(()->signal(h,terminal,11,"従来のholdは更新なしで保持")).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_control_rights",timeoutTicks=260)
    public static void permissionsAndMonitorCannotKeepControlLeaseAlive(GameTestHelper h){
        var pc=device(h,DeviceKind.SERVER,1,"203.0.113.10");var router=device(h,DeviceKind.ROUTER,5,"");var terminal=device(h,DeviceKind.REDSTONE_TERMINAL,9,"10.112.0.20");terminal.profile().gateway="10.112.0.1";
        for(var p:router.profile().ports)p.address="";router.profile().ports[3].address="203.0.113.1";router.profile().ports[1].address="10.112.0.1";router.profile().dhcp=false;
        for(int x=2;x<5;x++)wire(h,x);for(int x=6;x<9;x++)wire(h,x);
        var owner=player(h,"lease-owner");var other=player(h,"lease-other");pc.setOwnerIfAbsent(owner);terminal.setOwnerIfAbsent(other);terminal.trust(owner.getUUID(),true);var w=NetworkWorld.get(h.getLevel());
        final long[] natDeadline={0};
        h.startSequence().thenIdle(15).thenExecute(()->{
            config(h,w,router,"nat port 3 outside");config(h,w,router,"nat port 1 inside");config(h,w,router,"nat on");config(h,w,router,"nat forward add 3 tcp 3000 10.112.0.20 25570");
            config(h,w,terminal,"control-safety stop 3");command(pc,owner,"redstone maintain 203.0.113.1:3000 13");signal(h,terminal,13,"公開ポート経由で認可した制御");
            natDeadline[0]=w.simulator().nat().sessions(router.id()).getFirst().expiresAt();terminal.trust(owner.getUUID(),false);
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(w.controls().runtime().streams(pc.id()).isEmpty(),"権限取消で更新停止");h.assertTrue(w.simulator().nat().sessions(router.id()).getFirst().expiresAt()==natDeadline[0],"拒否した更新でNAT期限を延長しない");
        }).thenIdle(45).thenExecute(()->{
            signal(h,terminal,0,"権限解除の後に安全出力");terminal.trust(owner.getUUID(),true);command(pc,owner,"redstone 203.0.113.1:3000 14");signal(h,terminal,14,"単発命令");
            config(h,w,pc,"monitor add signal control 203.0.113.1:3000");config(h,w,pc,"monitor interval 1");config(h,w,pc,"monitor thresholds 1 1");config(h,w,pc,"monitor on");
        }).thenIdle(65).thenExecute(()->{
            h.assertTrue(w.simulator().monitor().snapshots(pc.id()).getFirst().state()==MonitorRuntime.State.UP,"サービス監視はUP");signal(h,terminal,0,"監視成功で制御期限や出力を更新しない");
            command(pc,owner,"redstone maintain 203.0.113.1:3000 12");signal(h,terminal,12,"定期更新再開");
            config(h,w,router,"acl add 3 in 10 deny tcp any any 3000");config(h,w,router,"acl add 3 in 20 permit ip any any");
        }).thenIdle(65).thenExecute(()->signal(h,terminal,0,"ACLで更新を遮断すると安全出力")).thenSucceed();
    }
    @GameTest(template="test/wide_empty",batch="network_control_save",timeoutTicks=150)
    public static void protectedDropAndRepairDoNotResumeOldOutput(GameTestHelper h){
        var pc=device(h,DeviceKind.PC,1,"10.113.0.10");var terminal=device(h,DeviceKind.REDSTONE_TERMINAL,5,"10.113.0.20");for(int x=2;x<5;x++)wire(h,x);
        var owner=player(h,"safe-owner");var other=player(h,"safe-other");pc.setOwnerIfAbsent(owner);terminal.setOwnerIfAbsent(owner);var w=NetworkWorld.get(h.getLevel());
        final NetworkDeviceEntity[] copy={null};
        h.startSequence().thenIdle(15).thenExecute(()->{
            command(terminal,owner,"control-safety value 6 3");terminal.modules.setItem(2,new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_port_40g").get()));
            command(pc,owner,"redstone maintain 10.113.0.20 15");signal(h,terminal,15,"持ち運び前の有効出力");
            var drops=Block.getDrops(terminal.getBlockState(),h.getLevel(),terminal.getBlockPos(),terminal);h.assertTrue(drops.size()==1,"通常ドロップは装着品入り機器1個");
            h.getLevel().setBlock(terminal.getBlockPos(),Blocks.AIR.defaultBlockState(),3);copy[0]=device(h,DeviceKind.REDSTONE_TERMINAL,5,"");copy[0].restoreItem(drops.getFirst());
            signal(h,copy[0],6,"再設置は以前の15ではなく安全値");h.assertTrue(copy[0].profile().ports[2].moduleTier==2&&copy[0].canUse(owner)&&!copy[0].canUse(other),"装着品と所有権保持");
            h.assertTrue(w.controls().runtime().streams(pc.id()).isEmpty(),"保存から継続権限を復元しない");
        }).thenIdle(30).thenExecute(()->{
            signal(h,copy[0],6,"再接続だけで出力再開しない");command(pc,owner,"redstone maintain 10.113.0.20 15");signal(h,copy[0],15,"再設置後の明示操作");
            copy[0].profile().failed=true;copy[0].refreshState();signal(h,copy[0],0,"機器故障中は出力0");copy[0].repair();signal(h,copy[0],6,"修理後も安全値");
        }).thenIdle(25).thenExecute(()->{
            signal(h,copy[0],6,"故障前のジョブを復活させない");
            owner.setPos(pc.getBlockPos().getX()+0.5,pc.getBlockPos().getY()+0.5,pc.getBlockPos().getZ()+0.5);
            var old=new NetworkMenu(menuId++,owner.getInventory(),pc);owner.containerMenu=owner.inventoryMenu;old.command(owner,"redstone 10.113.0.20 15");signal(h,copy[0],6,"閉じたメニューの旧命令を拒否");
        }).thenSucceed();
    }
}
