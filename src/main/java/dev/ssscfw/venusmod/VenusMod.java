package dev.ssscfw.venusmod;

import dev.ssscfw.venusmod.compat.create.VenusCreateCompat;
import dev.ssscfw.venusmod.event.BladeUpgradeEvents;
import dev.ssscfw.venusmod.event.KingsTreasuryEvents;
import dev.ssscfw.venusmod.event.VenusCombatEvents;
import dev.ssscfw.venusmod.event.VenusEnchantmentEvents;
import dev.ssscfw.venusmod.registry.ModBlockEntities;
import dev.ssscfw.venusmod.registry.ModBlocks;
import dev.ssscfw.venusmod.registry.ModCreativeTabs;
import dev.ssscfw.venusmod.registry.ModDataComponents;
import dev.ssscfw.venusmod.registry.ModEntities;
import dev.ssscfw.venusmod.registry.ModItems;
import dev.ssscfw.venusmod.registry.ModMenus;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import dev.ssscfw.venusmod.treasure.KingsTreasure;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(VenusMod.MOD_ID)
public final class VenusMod {
    public static final String MOD_ID = "venusmod";

    public VenusMod(IEventBus modBus, ModContainer modContainer) {
        ModDataComponents.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modBus);
        VenusDimensionContent.register(modBus);
        dev.ssscfw.venusmod.registry.VenusPhase2.register(modBus, modContainer);
        KingsTreasure.register(modBus);

        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModItems::addCreativeTabContents);
        modBus.addListener(ModBlockEntities::registerCapabilities);

        if (ModList.get().isLoaded("create")) {
            VenusCreateCompat.register(modBus);
        }

        NeoForge.EVENT_BUS.addListener(VenusCombatEvents::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(VenusEnchantmentEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(VenusEnchantmentEvents::onDamagePost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VenusEnchantmentEvents::onAnvilUpdate);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VenusEnchantmentEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(VenusEnchantmentEvents::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(BladeUpgradeEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(BladeUpgradeEvents::onDamagePost);
        NeoForge.EVENT_BUS.addListener(BladeUpgradeEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, KingsTreasuryEvents::onItemPickup);
        NeoForge.EVENT_BUS.addListener(KingsTreasuryEvents::onCommand);
        NeoForge.EVENT_BUS.addListener(KingsTreasuryEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(KingsTreasuryEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(KingsTreasuryEvents::onServerStopped);
    }
}
