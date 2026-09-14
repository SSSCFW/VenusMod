package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.api.stress.BlockStressValues;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.registry.ModCreativeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;

/** Createが存在するときだけ初期化する第二段階の機械。 */
public final class VenusAtmosphereMachines {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VenusMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VenusMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VenusMod.MOD_ID);
    public static final DeferredBlock<AtmosphericCondenserBlock> CONDENSER = BLOCKS.register("atmospheric_condenser",
            () -> new AtmosphericCondenserBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5F)));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CONDENSER_ITEM = ITEMS.registerSimpleBlockItem(CONDENSER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AtmosphericCondenserBlockEntity>> CONDENSER_ENTITY = ENTITIES.register(
            "atmospheric_condenser", () -> BlockEntityType.Builder.of(AtmosphericCondenserBlockEntity::new, CONDENSER.get()).build(null));
    private VenusAtmosphereMachines() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus);
        bus.addListener(VenusAtmosphereMachines::capabilities);
        bus.addListener(VenusAtmosphereMachines::creative);
        bus.addListener(VenusAtmosphereMachines::setup);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CONDENSER_ENTITY.get(), (be, side) -> be.output());
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTabs.VENUS_TAB_KEY)) event.accept(CONDENSER_ITEM.get());
    }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockStressValues.IMPACTS.register(CONDENSER.get(), () -> 8.0D));
    }
}
