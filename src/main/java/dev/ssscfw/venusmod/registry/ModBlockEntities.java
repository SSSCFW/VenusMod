package dev.ssscfw.venusmod.registry;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.blockentity.DiamondHopperBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.VanillaHopperItemHandler;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VenusMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DiamondHopperBlockEntity>> DIAMOND_HOPPER =
            BLOCK_ENTITY_TYPES.register(
                    "diamond_hopper",
                    () -> BlockEntityType.Builder.of(
                            DiamondHopperBlockEntity::new,
                            ModBlocks.DIAMOND_HOPPER.get()).build(null));

    private ModBlockEntities() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                DIAMOND_HOPPER.get(),
                (hopper, side) -> new VanillaHopperItemHandler(hopper));
    }
}
