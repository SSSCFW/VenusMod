package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.api.stress.BlockStressValues;
import dev.ssscfw.venusmod.VenusMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Optional Create integration. This class must only be initialized when Create is loaded.
 */
public final class VenusCreateCompat {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VenusMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VenusMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VenusMod.MOD_ID);

    public static final DeferredHolder<Block, BladeMachineBlock> BLADE_REPAIR_STATION =
            BLOCKS.register("blade_repair_station", () -> new BladeMachineBlock(
                    BladeMachineBlock.Mode.REPAIR,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F)
                            .sound(SoundType.METAL)));

    public static final DeferredHolder<Block, BladeMachineBlock> BLADE_BREAKER =
            BLOCKS.register("blade_breaker", () -> new BladeMachineBlock(
                    BladeMachineBlock.Mode.BREAK,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F)
                            .sound(SoundType.METAL)));

    public static final DeferredHolder<Item, BlockItem> BLADE_REPAIR_STATION_ITEM =
            ITEMS.register("blade_repair_station",
                    () -> new BlockItem(BLADE_REPAIR_STATION.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> BLADE_BREAKER_ITEM =
            ITEMS.register("blade_breaker",
                    () -> new BlockItem(BLADE_BREAKER.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BladeRepairStationBlockEntity>>
            BLADE_REPAIR_STATION_BE = BLOCK_ENTITY_TYPES.register(
                    "blade_repair_station",
                    () -> BlockEntityType.Builder.of(
                            BladeRepairStationBlockEntity::new,
                            BLADE_REPAIR_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BladeBreakerBlockEntity>>
            BLADE_BREAKER_BE = BLOCK_ENTITY_TYPES.register(
                    "blade_breaker",
                    () -> BlockEntityType.Builder.of(
                            BladeBreakerBlockEntity::new,
                            BLADE_BREAKER.get()).build(null));

    private VenusCreateCompat() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);

        modBus.addListener(VenusCreateCompat::registerCapabilities);
        modBus.addListener(VenusCreateCompat::addCreativeTabContents);
        modBus.addListener(VenusCreateCompat::commonSetup);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                BLADE_REPAIR_STATION_BE.get(),
                (blockEntity, context) -> blockEntity.getItemHandler());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                BLADE_BREAKER_BE.get(),
                (blockEntity, context) -> blockEntity.getItemHandler());
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(BLADE_REPAIR_STATION_ITEM.get());
            event.accept(BLADE_BREAKER_ITEM.get());
        }
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Comparable to Create's millstone; the breaker is intentionally heavier.
            BlockStressValues.IMPACTS.register(BLADE_REPAIR_STATION.get(), () -> 4.0D);
            BlockStressValues.IMPACTS.register(BLADE_BREAKER.get(), () -> 8.0D);
        });
    }
}
