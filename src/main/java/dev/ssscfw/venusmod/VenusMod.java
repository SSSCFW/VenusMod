package dev.ssscfw.venusmod;

import dev.ssscfw.venusmod.registry.ModEntities;
import dev.ssscfw.venusmod.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(VenusMod.MOD_ID)
public final class VenusMod {
    public static final String MOD_ID = "venusmod";

    public VenusMod(IEventBus modBus, ModContainer modContainer) {
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);

        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModItems::addCreativeTabContents);
    }
}
