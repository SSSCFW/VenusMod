package dev.ssscfw.venusmod.infrastructure.network;

import dev.ssscfw.venusmod.infrastructure.network.core.NetworkGogglesReadout;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** サーバー側の照準結果だけを読み、10tickごとに上限付きの状態を送る。 */
public final class NetworkGogglesTracker {
    private NetworkGogglesTracker() {}

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 10 != 0 || player.isSpectator()
                || !NetworkContent.wearingNetworkGoggles(player)) return;

        HitResult target = player.pick(6.0D, 0.0F, false);
        if (!(target instanceof BlockHitResult hit) || target.getType() != HitResult.Type.BLOCK) {
            return;
        }
        var level = player.serverLevel();
        var pos = hit.getBlockPos();
        if (!level.hasChunkAt(pos)
                || player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64D
                || !(level.getBlockEntity(pos) instanceof NetworkDeviceEntity device)) return;

        boolean allowed = device.canUse(player);
        List<NetworkGogglesReadout.Port> ports = allowed
                ? NetworkGogglesReadout.inspect(NetworkWorld.get(level).simulator(), device.id())
                : NetworkGogglesReadout.empty();
        NetworkPackets.GoggleView packet = new NetworkPackets.GoggleView(
                level.dimension().location().toString(), pos, device.profile().kind.ordinal(),
                allowed ? device.profile().name : "", device.profile().failed, allowed, ports);
        PacketDistributor.sendToPlayer(player, packet);
    }
}
