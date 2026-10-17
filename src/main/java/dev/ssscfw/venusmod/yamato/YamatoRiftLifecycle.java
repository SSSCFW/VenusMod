package dev.ssscfw.venusmod.yamato;

import dev.ssscfw.venusmod.VenusMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** 消滅した対象のロックだけを除去し、未読込・再ログイン時の到着状態は保つ。 */
@EventBusSubscriber(modid = VenusMod.MOD_ID)
public final class YamatoRiftLifecycle {
    private YamatoRiftLifecycle() {}

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            YamatoRifts.get(level.getServer()).forgetDestroyedEntity(entity.getUUID());
        }
    }
}
