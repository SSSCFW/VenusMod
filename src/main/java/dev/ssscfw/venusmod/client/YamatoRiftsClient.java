package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** 各クライアントには現在の次元の描画範囲内にある端だけを同期する。 */
@EventBusSubscriber(modid = VenusMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class YamatoRiftsClient {
    public record PortalView(String dimension, Vec3 center, float yaw, long openedAt, long closesAt) {}
    private static List<PortalView> views = List.of();
    private static long serverTick;
    private static long localTick;
    private YamatoRiftsClient() {}

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> YamatoNetwork.clientHandlers(payload -> {
            if (payload.kind() == 0) YamatoTravelClient.receive(payload.data(), payload.open());
            else if (payload.kind() == 1) receive(payload.data());
        }, payload -> YamatoCutAnimation.start(payload.entityId(),
                payload.hand() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, payload.stage())));
    }

    public static List<PortalView> portals() { return views; }
    public static long estimatedServerTick() {
        var level = Minecraft.getInstance().level;
        return serverTick + (level == null ? 0 : Math.max(0, level.getGameTime() - localTick));
    }

    private static void receive(CompoundTag data) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().toString().equals(data.getString("dimension"))) return;
        serverTick = data.getLong("tick"); localTick = level.getGameTime();
        List<PortalView> next = new ArrayList<>();
        var list = data.getList("portals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            next.add(new PortalView(data.getString("dimension"), new Vec3(tag.getDouble("x"),
                    tag.getDouble("y"), tag.getDouble("z")), tag.getFloat("yaw"),
                    tag.getLong("openedAt"), tag.getLong("closesAt")));
        }
        views = List.copyOf(next);
    }

    @EventBusSubscriber(modid = VenusMod.MOD_ID, value = Dist.CLIENT)
    public static final class Lifecycle {
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            views = List.of(); serverTick = 0; localTick = 0;
        }
    }
}
