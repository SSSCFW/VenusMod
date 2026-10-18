package dev.ssscfw.venusmod.infrastructure.network;

import net.minecraft.core.BlockPos;

/** クライアントのHUDが参照する短命な受信キャッシュ。サーバー上では更新しない。 */
public final class NetworkGogglesStatus {
    private static NetworkPackets.GoggleView current;
    private static long timestamp;
    private NetworkGogglesStatus() {}

    public static void accept(NetworkPackets.GoggleView view) {
        current = view;
        timestamp = System.nanoTime();
    }

    public static NetworkPackets.GoggleView recent(String dimension, BlockPos pos) {
        if (current == null || !dimension.equals(current.dimension()) || !pos.equals(current.pos())
                || System.nanoTime() - timestamp > 2_000_000_000L) return null;
        return current;
    }
}
