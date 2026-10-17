package dev.ssscfw.venusmod.event;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.registry.ModEnchantments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Applies Rapid Slash Speed only to the grounded rapid_slash forward burst. */
public final class VenusRapidSlashEvents {
    private static final Vec3 FORWARD = new Vec3(0.0D, 0.0D, 1.0D);
    private static final float BONUS_PER_LEVEL = 0.25F;
    private static final int MAX_LEVEL = 3;

    private VenusRapidSlashEvents() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !SlashBladeEnchantmentCompat.isRapidSlashMovementTick(player)) {
            return;
        }

        int level = ModEnchantments.getLevel(
                player.getMainHandItem(),
                ModEnchantments.RAPID_SLASH_SPEED,
                player.registryAccess());
        if (level <= 0) {
            return;
        }

        // SlashBlade applies 0.8 forward (0.35 in water) on rapid_slash ticks 0-3.
        // Stack +25%, +50%, or +75% of that impulse without altering other moves.
        float nativeImpulse = player.isInWater() ? 0.35F : 0.8F;
        player.moveRelative(nativeImpulse * BONUS_PER_LEVEL * Math.min(level, MAX_LEVEL), FORWARD);
    }
}
