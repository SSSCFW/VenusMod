package dev.ssscfw.venusmod;

import dev.ssscfw.venusmod.event.VenusCombatEvents;
import dev.ssscfw.venusmod.event.VenusEnchantmentEvents;
import dev.ssscfw.venusmod.registry.ModEntities;
import dev.ssscfw.venusmod.registry.ModItems;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(VenusMod.MOD_ID)
public final class VenusMod {
    public static final String MOD_ID = "venusmod";

    public VenusMod(IEventBus modBus, ModContainer modContainer) {
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);

        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModItems::addCreativeTabContents);

        NeoForge.EVENT_BUS.addListener(VenusCombatEvents::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(VenusEnchantmentEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(VenusEnchantmentEvents::onDamagePost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VenusEnchantmentEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(VenusEnchantmentEvents::onPlayerClone);
    }
}
