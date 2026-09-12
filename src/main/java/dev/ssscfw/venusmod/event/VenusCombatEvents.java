package dev.ssscfw.venusmod.event;

import dev.ssscfw.venusmod.entity.VenusZombie;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class VenusCombatEvents {
    private VenusCombatEvents() {
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof VenusZombie venusZombie) || !venusZombie.isSlashBladeWielder()) {
            return;
        }

        // SlashBlade's area attacks are intentionally broad. A blade-wielding Venus
        // zombie is allowed to hit only the entity type (species) of its selected
        // target, preventing nearby mobs of other species from being caught in the
        // slash effect.
        if (!venusZombie.canBladeDamage(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
