package dev.ssscfw.venusmod.test;

import com.mojang.authlib.GameProfile;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** フォームが生成する同一命令を実メニュー・実配線へ渡す。画面入力の検証は別のJEIスモーク。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkDiagnosticsGameTests {
    private static FakePlayer player(GameTestHelper h,BlockPos p,String name) {
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),name));
        player.setPos(p.getX()+0.5,p.getY()+0.5,p.getZ()+0.5);return player;
    }
    private static String command(NetworkDiagnostics.Action action,String target) {
        return NetworkDiagnostics.command(action,target,"1000000",TransferPriority.HIGH);
    }
    @GameTest(template="test/wide_empty",batch="network_diagnostics",timeoutTicks=90)
    public static void diagnosticFormCommandsKeepServerAuthority(GameTestHelper h) {
        var start=h.absolutePos(new BlockPos(2,2,2));var end=start.east(4);
        h.getLevel().setBlock(start,NetworkContent.DEVICES.get(DeviceKind.PC).get().defaultBlockState(),3);
        h.getLevel().setBlock(end,NetworkContent.DEVICES.get(DeviceKind.SERVER).get().defaultBlockState(),3);
        var source=(NetworkDeviceEntity)h.getLevel().getBlockEntity(start);
        var target=(NetworkDeviceEntity)h.getLevel().getBlockEntity(end);
        source.profile().address="10.167.0.10";source.profile().dns="10.167.0.20";
        target.profile().address="10.167.0.20";target.profile().dnsRecords.put("diag.local","10.167.0.20");
        for(int n=1;n<4;n++) {
            var p=start.east(n);var cable=NetworkContent.DYED_CABLES.get(CableGrade.CAT5E).get(DyeColor.BLUE).get();
            h.getLevel().setBlock(p,Block.updateFromNeighbourShapes(cable.defaultBlockState(),h.getLevel(),p),3);
        }
        var owner=player(h,start,"diag-owner");var guest=player(h,start,"diag-guest");source.setOwnerIfAbsent(owner);
        var menu=new NetworkMenu(411,owner.getInventory(),source);owner.containerMenu=menu;
        var denied=new NetworkMenu(412,guest.getInventory(),source);guest.containerMenu=denied;
        var sim=NetworkWorld.get(h.getLevel()).simulator();String original=source.profile().encode();
        h.startSequence().thenIdle(15).thenExecute(() -> {
            denied.command(guest,command(NetworkDiagnostics.Action.TRANSFER,"diag.local"));
            h.assertTrue(sim.flows().isEmpty(),"診断からの転送でも管理権限を迂回しない");
            menu.command(owner,command(NetworkDiagnostics.Action.PING,"10.167.0.20"));
            h.assertTrue(menu.snapshot().contains("Reply from 10.167.0.20"),"実配線で診断pingが応答");
        }).thenIdle(3).thenExecute(() -> {
            menu.command(owner,command(NetworkDiagnostics.Action.DNS,"diag.local"));
            h.assertTrue(menu.snapshot().contains("10.167.0.20"),"名前解決も実サービスへ届く");
        }).thenIdle(3).thenExecute(() -> {
            menu.command(owner,command(NetworkDiagnostics.Action.TRANSFER,"diag.local"));
            h.assertTrue(sim.flows().size()==1 && sim.flows().getFirst().priority()==TransferPriority.HIGH,"転送優先度を反映");
        }).thenIdle(5).thenExecute(() -> {
            h.assertTrue(sim.flows().getFirst().remaining<sim.flows().getFirst().totalBytes,"通常サーバーtickで転送が進む");
            owner.containerMenu=owner.inventoryMenu;
            menu.command(owner,command(NetworkDiagnostics.Action.TRANSFER,"diag.local"));
            h.assertTrue(sim.flows().size()==1,"閉じたメニューからの診断要求は拒否");
            h.assertTrue(original.equals(source.profile().encode()),"診断は機器設定を変更しない");
            h.getLevel().destroyBlock(start.east(1),false);
        }).thenIdle(3).thenExecute(() -> {
            owner.containerMenu=menu;menu.command(owner,command(NetworkDiagnostics.Action.PING,"10.167.0.20"));
            h.assertTrue(!menu.snapshot().contains("Reply from"),"断線後の結果を成功としない");
        }).thenSucceed();
    }
}
