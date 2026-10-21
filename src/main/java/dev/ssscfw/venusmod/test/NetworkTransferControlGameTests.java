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

/** 転送操作のフォーム命令を実配線/メニューへ渡し、サーバー認可と対象を確認する。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkTransferControlGameTests {
    private static FakePlayer player(GameTestHelper h, BlockPos p, String name) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5); return player;
    }
    private static NetworkSimulator.Flow flow(NetworkSimulator sim, String source) {
        return sim.flows().stream().filter(f -> f.source.equals(source)).findFirst().orElseThrow();
    }
    @GameTest(template="test/wide_empty", batch="network_transfer_controls", timeoutTicks=160)
    public static void transferControlsRequireOwnerOpenMenuAndOwnSource(GameTestHelper h) {
        var start = h.absolutePos(new BlockPos(2, 2, 2)); var end = start.east(4);
        h.getLevel().setBlock(start, NetworkContent.DEVICES.get(DeviceKind.PC).get().defaultBlockState(), 3);
        h.getLevel().setBlock(end, NetworkContent.DEVICES.get(DeviceKind.SERVER).get().defaultBlockState(), 3);
        var a = (NetworkDeviceEntity) h.getLevel().getBlockEntity(start);
        var b = (NetworkDeviceEntity) h.getLevel().getBlockEntity(end);
        a.profile().address = "10.171.0.10"; b.profile().address = "10.171.0.20";
        for (var d : new NetworkDeviceEntity[]{a,b}) {
            d.modules.setItem(6, new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get())); d.refreshState();
        }
        for (int n = 1; n < 4; n++) {
            var p = start.east(n); var cable = NetworkContent.DYED_CABLES.get(CableGrade.CAT5E).get(DyeColor.BLUE).get();
            h.getLevel().setBlock(p, Block.updateFromNeighbourShapes(cable.defaultBlockState(), h.getLevel(), p), 3);
        }
        var owner = player(h, start, "controls-owner"); var guest = player(h, start, "controls-guest");
        a.setOwnerIfAbsent(owner); b.setOwnerIfAbsent(owner);
        var menu = new NetworkMenu(421, owner.getInventory(), a);
        var other = new NetworkMenu(422, owner.getInventory(), b);
        var denied = new NetworkMenu(423, guest.getInventory(), a);
        owner.containerMenu = menu; guest.containerMenu = denied;
        var world = NetworkWorld.get(h.getLevel()); var sim = world.simulator();
        var draft = new TransferControlDraft(); long[] ids = new long[2]; String original = a.profile().encode();
        h.startSequence().thenIdle(15).thenWaitUntil(() -> h.assertTrue(world.ready(), "実配線の解析完了"))
            .thenExecute(() -> {
                menu.command(owner, "transfer 10.171.0.20 1000000 high");
                owner.containerMenu = other; other.command(owner, "transfer 10.171.0.10 1000000 high"); owner.containerMenu = menu;
                ids[0] = flow(sim, a.id()).id; ids[1] = flow(sim, b.id()).id;
            }).thenIdle(5).thenExecute(() -> {
                draft.idText("#" + ids[0]); draft.priority(TransferPriority.LOW);
                denied.command(guest, draft.priorityCommand());
                h.assertTrue(flow(sim,a.id()).priority() == TransferPriority.HIGH, "権限外の優先度変更は拒否");
                draft.requestCancellation(); denied.command(guest, draft.confirmCancellation());
                h.assertTrue(sim.flows().stream().anyMatch(f -> f.id == ids[0]), "権限外の停止は拒否");
                menu.command(owner, draft.priorityCommand());
            }).thenIdle(3).thenExecute(() -> {
                h.assertTrue(flow(sim,a.id()).priority() == TransferPriority.LOW, "所有者の優先度変更");
                draft.idText("" + ids[1]); menu.command(owner, draft.priorityCommand());
                h.assertTrue(flow(sim,b.id()).priority() == TransferPriority.HIGH, "別送信元を変更しない");
                draft.requestCancellation(); menu.command(owner, draft.confirmCancellation());
                h.assertTrue(sim.flows().stream().anyMatch(f -> f.id == ids[1]), "別送信元は停止しない");
            }).thenIdle(3).thenExecute(() -> {
                draft.idText("" + ids[0]); draft.requestCancellation();
                h.assertTrue(sim.flows().stream().anyMatch(f -> f.id == ids[0]), "確認待ちでは未送信");
                owner.containerMenu = owner.inventoryMenu; menu.command(owner, draft.confirmCancellation());
                h.assertTrue(sim.flows().stream().anyMatch(f -> f.id == ids[0]), "閉じたメニューの停止拒否");
                owner.containerMenu = menu; draft.requestCancellation(); menu.command(owner, draft.confirmCancellation());
            }).thenIdle(3).thenExecute(() -> {
                h.assertTrue(sim.flows().stream().noneMatch(f -> f.id == ids[0]), "指定した自機の転送だけ停止");
                draft.requestCancellation(); menu.command(owner, draft.confirmCancellation());
                h.assertTrue(menu.snapshot().contains("見つかりません"), "既に終了したIDは失敗を表示");
                h.assertTrue(sim.flows().stream().anyMatch(f -> f.id == ids[1]), "反対方向の転送は継続");
                h.assertTrue(original.equals(a.profile().encode()) && a.modules.getItem(6).getCount() == 1, "設定と装着品を維持");
            }).thenSucceed();
    }
}
