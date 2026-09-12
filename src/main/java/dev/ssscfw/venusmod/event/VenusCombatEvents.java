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

        // SlashBlade's visual slash entity performs its own delayed area damage. The
        // Venus zombie already applies the authoritative technique damage itself so it
        // can attack players even when SlashBlade PVP is disabled and can enforce the
        // target-species rule. Cancel every other damage path from the blade wielder.
        if (!venusZombie.isApplyingBladeDamage() || !venusZombie.canBladeDamage(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
