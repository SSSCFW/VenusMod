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

/** 通常配線と実メニューでACLの認可・内容一致削除を検査。論理リンクは注入しない。 */
@GameTestHolder(VenusMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkAclGameTests {
    private static NetworkDeviceEntity device(GameTestHelper h, BlockPos position, DeviceKind kind) {
        h.getLevel().setBlock(position, NetworkContent.DEVICES.get(kind).get().defaultBlockState(), 3);
        var device = (NetworkDeviceEntity) h.getLevel().getBlockEntity(position);
        device.modules.setItem(6, new ItemStack(NetworkContent.NETWORK_ITEMS.get("network_stability_perfect").get()));
        if (kind.routing()) { device.profile().dhcp = false; for (var port : device.profile().ports) port.address = ""; }
        return device;
    }
    private static FakePlayer player(GameTestHelper h, BlockPos position, String name) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5); return player;
    }
    private static NetworkSimulator.Flow flow(NetworkSimulator simulator, String source) {
        return simulator.flows().stream().filter(f -> f.source.equals(source)).findFirst().orElseThrow();
    }

    @GameTest(template="test/wide_empty", batch="network_acl_gui", timeoutTicks=220)
    public static void confirmedAclDeletionRequiresMatchingContentsAndAuthorizedMenu(GameTestHelper h) {
        var start = h.absolutePos(new BlockPos(1, 2, 2));
        var client = device(h, start, DeviceKind.PC);
        var router = device(h, start.east(3), DeviceKind.ROUTER);
        var server = device(h, start.east(6), DeviceKind.SERVER);
        client.profile().address = "10.182.1.10"; client.profile().gateway = "10.182.1.1";
        server.profile().address = "10.182.2.10"; server.profile().gateway = "10.182.2.1";
        router.profile().ports[3].address = "10.182.1.1"; router.profile().ports[1].address = "10.182.2.1";
        for (var device : new NetworkDeviceEntity[]{client, router, server}) device.refreshState();
        for (int offset : new int[]{1, 2, 4, 5}) {
            var position = start.east(offset);
            var cable = NetworkContent.DYED_CABLES.get(CableGrade.CAT5E).get(DyeColor.BLUE).get();
            h.getLevel().setBlock(position, Block.updateFromNeighbourShapes(cable.defaultBlockState(), h.getLevel(), position), 3);
        }
        var owner = player(h, start.east(3), "acl-owner");
        var guest = player(h, start.east(3), "acl-guest");
        var editor = player(h, start.east(3), "acl-editor");
        router.setOwnerIfAbsent(owner); client.setOwnerIfAbsent(owner); router.trust(editor.getUUID(), true);
        var menu = new NetworkMenu(451, owner.getInventory(), router);
        var source = new NetworkMenu(452, owner.getInventory(), client);
        var denied = new NetworkMenu(453, guest.getInventory(), router);
        var shared = new NetworkMenu(454, editor.getInventory(), router);
        owner.containerMenu = menu; guest.containerMenu = denied; editor.containerMenu = shared;
        var draft = new AclDraft(); draft.port(3); draft.permit(false); draft.protocol(NetworkPolicy.Protocol.ICMP);
        draft.sourceText("10.182.1.42/24");
        String original = router.profile().encode(); long[] flowId = new long[1]; double[] remaining = new double[1];
        var world = NetworkWorld.get(h.getLevel()); var simulator = world.simulator();
        h.startSequence().thenIdle(15).thenWaitUntil(() -> h.assertTrue(world.ready(), "通常配線の解析完了"))
            .thenExecute(() -> {
                h.assertTrue(simulator.ping(client.id(), server.profile().address).success(), "ACL未適用の実配線で往復疎通");
                denied.command(guest, draft.addCommand());
                h.assertTrue(router.profile().services.policy.rules().isEmpty(), "権限外の追加拒否");
                menu.command(owner, draft.addCommand());
            }).thenIdle(4).thenWaitUntil(() -> h.assertTrue(world.ready(), "拒否規則の反映"))
            .thenExecute(() -> {
                var rules = router.profile().services.policy.rules();
                h.assertTrue(rules.size() == 1 && rules.getFirst().source().toString().equals("10.182.1.0/24"), "送信元CIDRを正規化して追加");
                h.assertTrue(!simulator.ping(client.id(), server.profile().address).success(), "ICMP規則でping拒否");
                h.assertTrue(!simulator.request(client.id(), server.profile().address, "tcp", 80).success(), "適用方向の未一致通信は暗黙deny");
                menu.command(owner, "acl add 3 in 20 permit ip any any");
            }).thenIdle(4).thenWaitUntil(() -> h.assertTrue(world.ready(), "許可規則の反映"))
            .thenExecute(() -> {
                h.assertTrue(!simulator.ping(client.id(), server.profile().address).success(), "小さい順序番号のICMP拒否を優先");
                h.assertTrue(simulator.request(client.id(), server.profile().address, "tcp", 80).success(), "他のTCP通信は後続規則で許可");
                draft.requestDeletion();
                h.assertTrue(router.profile().services.policy.rules().size() == 2, "確認だけでは削除しない");
                denied.command(guest, draft.confirmDeletion());
                h.assertTrue(router.profile().services.policy.rules().size() == 2, "権限外の内容一致削除を拒否");
            }).thenIdle(4).thenExecute(() -> {
                draft.requestDeletion(); owner.containerMenu = owner.inventoryMenu;
                menu.command(owner, draft.confirmDeletion());
                h.assertTrue(router.profile().services.policy.rules().size() == 2, "閉じたメニューから削除できない");
                owner.containerMenu = menu; draft.requestDeletion();
                // 確認中に共有者が同じキーを別内容へ置換。各要求は通常のレート制限内で実行する。
                shared.command(editor, "acl delete 3 in 10");
                h.assertTrue(router.profile().services.policy.rules().size() == 1, "共有者が旧規則を削除");
            }).thenIdle(4).thenWaitUntil(() -> h.assertTrue(world.ready(), "旧規則削除の反映"))
            .thenExecute(() -> shared.command(editor, "acl add 3 in 10 permit icmp 10.182.1.0/24 any"))
            .thenIdle(4).thenWaitUntil(() -> h.assertTrue(world.ready(), "置換規則の反映"))
            .thenExecute(() -> {
                var rules = router.profile().services.policy.rules();
                h.assertTrue(rules.size() == 2 && rules.getFirst().sequence() == 10 && rules.getFirst().permit(), "同じキーを許可規則へ置換");
                h.assertTrue(simulator.ping(client.id(), server.profile().address).success(), "置換後はICMPも許可");
                owner.containerMenu = source; source.command(owner, "transfer 10.182.2.10 1000000"); owner.containerMenu = menu;
                flowId[0] = flow(simulator, client.id()).id;
            }).thenIdle(4).thenExecute(() -> {
                var active = flow(simulator, client.id()); remaining[0] = active.remaining;
                h.assertTrue(active.remaining < active.totalBytes, "通常サーバーtickで転送が進む");
                String replacement = router.profile().encode();
                menu.command(owner, draft.confirmDeletion());
                h.assertTrue(menu.snapshot().contains("内容が変わりました"), "古い確認はレート超過でなく内容不一致として拒否");
                h.assertTrue(router.profile().encode().equals(replacement), "置換後の全規則を保持");
                h.assertTrue(flow(simulator, client.id()).id == flowId[0], "内容不一致で稼働転送を失効させない");
            }).thenIdle(4).thenExecute(() -> {
                var active = flow(simulator, client.id());
                h.assertTrue(active.id == flowId[0] && active.remaining < remaining[0], "拒否後も同じ転送が進行");
                draft.permit(true); draft.requestDeletion(); menu.command(owner, draft.confirmDeletion());
            }).thenIdle(4).thenWaitUntil(() -> h.assertTrue(world.ready(), "内容一致削除の反映"))
            .thenExecute(() -> {
                var rules = router.profile().services.policy.rules();
                h.assertTrue(rules.size() == 1 && rules.getFirst().equals(new NetworkPolicy.Rule(3, NetworkPolicy.Direction.IN, 20,
                    true, NetworkPolicy.Protocol.IP, NetworkPolicy.Prefix.parse("any"), NetworkPolicy.Prefix.parse("any"), -1)), "一致した規則だけ削除して後続規則を保持");
                h.assertTrue(simulator.ping(client.id(), server.profile().address).success()
                    && simulator.request(client.id(), server.profile().address, "tcp", 80).success(), "残った規則で往復通信を許可");
                var restored = NetworkProfile.decode(DeviceKind.ROUTER, router.profile().encode());
                h.assertTrue(restored.services.policy.rules().equals(rules), "ACLの保存形式を往復");
                restored.services.policy.clear(3, NetworkPolicy.Direction.IN);
                h.assertTrue(restored.encode().equals(original), "ACL以外のIP・機器設定を保持");
                h.assertTrue(router.modules.getItem(6).getCount() == 1 && router.owner().equals(owner.getUUID())
                    && router.canUse(editor), "装着品・所有者・共有権限を保持");
            }).thenSucceed();
    }
}
