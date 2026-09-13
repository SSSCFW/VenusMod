package dev.ssscfw.venusmod.registry;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.environment.VenusEnvironment;
import dev.ssscfw.venusmod.environment.VenusEnvironmentConfig;
import dev.ssscfw.venusmod.world.AcidCondensateBlock;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.DispenseFluidContainer;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.*;

public final class VenusPhase2 {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VenusMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VenusMod.MOD_ID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, VenusMod.MOD_ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, VenusMod.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, VenusMod.MOD_ID);
    public static final TagKey<Item> ENVIRONMENT_SUIT = TagKey.create(Registries.ITEM, id("environment_suit"));
    public static final DeferredBlock<Block> SULFUR_ORE = BLOCKS.register("sulfur_ore", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.COAL_ORE)));
    public static final DeferredBlock<Block> VENESITE_ORE = BLOCKS.register("venesite_ore", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_DIAMOND_ORE)));
    public static final DeferredBlock<Block> VENUS_GLASS = BLOCKS.register("venus_glass", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).noOcclusion()));
    public static final DeferredItem<BlockItem> SULFUR_ORE_ITEM = ITEMS.registerSimpleBlockItem(SULFUR_ORE);
    public static final DeferredItem<BlockItem> VENESITE_ORE_ITEM = ITEMS.registerSimpleBlockItem(VENESITE_ORE);
    public static final DeferredItem<BlockItem> VENUS_GLASS_ITEM = ITEMS.registerSimpleBlockItem(VENUS_GLASS);
    public static final DeferredItem<Item> SULFUR = ITEMS.registerSimpleItem("sulfur_crystal");
    public static final DeferredItem<Item> VENESITE = ITEMS.registerSimpleItem("venesite");
    public static final DeferredItem<Item> ALLOY_BLEND = ITEMS.registerSimpleItem("pressure_alloy_blend");
    public static final DeferredItem<Item> PRESSURE_ALLOY = ITEMS.registerSimpleItem("pressure_alloy_ingot");
    public static final DeferredItem<Item> LIFE_SUPPORT = ITEMS.register("portable_life_support", () -> new Item(new Item.Properties().durability(600)));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SUIT_MATERIAL = MATERIALS.register("pressure_suit", () -> new ArmorMaterial(
            Map.of(ArmorItem.Type.HELMET, 2, ArmorItem.Type.CHESTPLATE, 6, ArmorItem.Type.LEGGINGS, 5, ArmorItem.Type.BOOTS, 2, ArmorItem.Type.BODY, 6),
            12, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(PRESSURE_ALLOY.get()),
            List.of(new ArmorMaterial.Layer(id("pressure_suit"))), 1.0F, 0.0F));
    public static final DeferredItem<ArmorItem> HELMET = armor("pressure_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<ArmorItem> CHESTPLATE = armor("pressure_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<ArmorItem> LEGGINGS = armor("pressure_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<ArmorItem> BOOTS = armor("pressure_boots", ArmorItem.Type.BOOTS);
    public static final DeferredHolder<FluidType, FluidType> ACID_TYPE = FLUID_TYPES.register("acid_condensate",
            () -> new FluidType(FluidType.Properties.create().density(1300).viscosity(1500).temperature(380)));
    public static final DeferredHolder<Fluid, FlowingFluid> ACID = FLUIDS.register("acid_condensate", () -> new BaseFlowingFluid.Source(fluidProperties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_ACID = FLUIDS.register("flowing_acid_condensate", () -> new BaseFlowingFluid.Flowing(fluidProperties()));
    public static final DeferredBlock<AcidCondensateBlock> ACID_BLOCK = BLOCKS.register("acid_condensate",
            () -> new AcidCondensateBlock(ACID.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable()));
    public static final DeferredItem<BucketItem> ACID_BUCKET = ITEMS.register("acid_condensate_bucket",
            () -> new BucketItem(ACID.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    private VenusPhase2() {}
    private static DeferredItem<ArmorItem> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new ArmorItem(SUIT_MATERIAL, type, new Item.Properties().durability(type.getDurability(20))));
    }
    private static BaseFlowingFluid.Properties fluidProperties() {
        return new BaseFlowingFluid.Properties(ACID_TYPE, ACID, FLOWING_ACID).bucket(ACID_BUCKET).block(ACID_BLOCK);
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, path); }
    public static void register(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus); ITEMS.register(bus); MATERIALS.register(bus); FLUID_TYPES.register(bus); FLUIDS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, VenusEnvironmentConfig.SPEC);
        bus.addListener(VenusPhase2::creative);
        bus.addListener(VenusPhase2::setup);
        NeoForge.EVENT_BUS.addListener(VenusEnvironment::tick);
        NeoForge.EVENT_BUS.addListener(VenusEnvironment::logout);
        NeoForge.EVENT_BUS.addListener(VenusEnvironment::stopped);
        if (ModList.get().isLoaded("create")) dev.ssscfw.venusmod.compat.create.VenusAtmosphereMachines.register(bus);
    }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DispenserBlock.registerBehavior(ACID_BUCKET.get(), DispenseFluidContainer.getInstance()));
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(ModCreativeTabs.VENUS_TAB_KEY)) return;
        for (var item : List.of(SULFUR_ORE_ITEM, VENESITE_ORE_ITEM, VENUS_GLASS_ITEM, SULFUR, VENESITE, ALLOY_BLEND,
                PRESSURE_ALLOY, LIFE_SUPPORT, HELMET, CHESTPLATE, LEGGINGS, BOOTS, ACID_BUCKET)) event.accept(item.get());
    }
}
