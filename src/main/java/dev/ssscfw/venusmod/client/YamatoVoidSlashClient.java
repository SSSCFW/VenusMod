package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.VenusMod;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** サーバーが捕捉したMobのクライアント側のアニメーションとtickも停止させる。 */
@EventBusSubscriber(modid = VenusMod.MOD_ID, value = Dist.CLIENT)
public final class YamatoVoidSlashClient {
    private static final Set<Integer> FROZEN_ENTITY_IDS = new HashSet<>();

    private YamatoVoidSlashClient() {}

    /** ネットワークハンドラからクライアントメインスレッドで呼び出す。 */
    public static void receive(int entityId, boolean frozen) {
        if (frozen) FROZEN_ENTITY_IDS.add(entityId);
        else FROZEN_ENTITY_IDS.remove(entityId);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() && entity instanceof Mob
                && FROZEN_ENTITY_IDS.contains(entity.getId())) {
            entity.setDeltaMovement(Vec3.ZERO);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) FROZEN_ENTITY_IDS.remove(event.getEntity().getId());
    }

    /** 次元変更でEntity IDが再利用されても、別のMobを停止させない。 */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) FROZEN_ENTITY_IDS.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        FROZEN_ENTITY_IDS.clear();
    }
}
