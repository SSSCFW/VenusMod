package dev.ssscfw.venusmod.world;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.registry.ModCreativeTabs;
import dev.ssscfw.venusmod.registry.ModEntities;
import dev.ssscfw.venusmod.entity.AphroditeCore;
import dev.ssscfw.venusmod.entity.VenusGeneral;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VenusDimensionContent {
    public static final ResourceKey<Level> VENUS = ResourceKey.create(Registries.DIMENSION, id("venus"));
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VenusMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VenusMod.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, VenusMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VenusMod.MOD_ID);
    public static final DeferredHolder<Block, VenusPortalBlock> PORTAL = BLOCKS.register("venus_portal",
            () -> new VenusPortalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_PORTAL).mapColor(MapColor.GOLD).noLootTable()));

    /**
     * 自動生成ゲート用。金ブロックと同じ見た目/物性だが、破壊・爆発・シルクタッチを含めて
     * LootTableからアイテムを一切生成しない。BlockItemはCreative専用で登録する。
     */
    public static final DeferredHolder<Block, Block> GOLD_BLOCK_DUMMY = BLOCKS.register("gold_block_dummy",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).noLootTable()));
    public static final DeferredHolder<Block, GuardianAltarBlock> ALTAR = BLOCKS.register("guardian_altar",
            () -> new GuardianAltarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_BLACKSTONE_BRICKS)
                    .strength(-1.0F, 3600000.0F).noLootTable()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GuardianAltarBlockEntity>> ALTAR_ENTITY = BLOCK_ENTITIES.register(
            "guardian_altar", () -> BlockEntityType.Builder.of(GuardianAltarBlockEntity::new, ALTAR.get()).build(null));
    public static final DeferredHolder<Block, BossAltarBlock> GENERAL_ALTAR = BLOCKS.register("venus_general_altar",
            () -> new BossAltarBlock(BossAltarBlock.Kind.GENERAL, BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_BLACKSTONE_BRICKS)
                    .strength(-1.0F, 3600000.0F).noLootTable()));
    public static final DeferredHolder<Block, BossAltarBlock> APHRODITE_ALTAR = BLOCKS.register("aphrodite_altar",
            () -> new BossAltarBlock(BossAltarBlock.Kind.APHRODITE, BlockBehaviour.Properties.ofFullCopy(Blocks.CRYING_OBSIDIAN)
                    .strength(-1.0F, 3600000.0F).noLootTable()));
    public static final DeferredHolder<Block, Block> PRESSURE_CORE = BLOCKS.register("pressure_core",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.CRYING_OBSIDIAN).strength(8.0F, 1200.0F)
                    .lightLevel(state -> 11).noLootTable()));
    public static final DeferredHolder<Block, PressureShieldControllerBlock> SHIELD_CONTROLLER = BLOCKS.register("pressure_shield_controller",
            () -> new PressureShieldControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(-1.0F, 3600000.0F).lightLevel(state -> state.getValue(PressureShieldControllerBlock.POWERED) ? 13 : 5).noLootTable()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BossAltarBlockEntity>> BOSS_ALTAR_ENTITY = BLOCK_ENTITIES.register(
            "boss_altar", () -> BlockEntityType.Builder.of(BossAltarBlockEntity::new, GENERAL_ALTAR.get(), APHRODITE_ALTAR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PressureShieldControllerBlockEntity>> SHIELD_CONTROLLER_ENTITY = BLOCK_ENTITIES.register(
            "pressure_shield_controller", () -> BlockEntityType.Builder.of(PressureShieldControllerBlockEntity::new, SHIELD_CONTROLLER.get()).build(null));
    public static final DeferredHolder<EntityType<?>, EntityType<VenusGuardian>> GUARDIAN = ENTITIES.register("venus_guardian",
            () -> EntityType.Builder.of(VenusGuardian::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .clientTrackingRange(10).build(id("venus_guardian").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<VenusGeneral>> GENERAL = ENTITIES.register("venus_general",
            () -> EntityType.Builder.of(VenusGeneral::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .clientTrackingRange(12).build(id("venus_general").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<AphroditeCore>> APHRODITE_CORE = ENTITIES.register("aphrodite_core",
            () -> EntityType.Builder.of(AphroditeCore::new, MobCategory.MONSTER).sized(0.9F, 1.8F)
                    .clientTrackingRange(14).build(id("aphrodite_core").toString()));
    public static final DeferredHolder<Item, BlockItem> GOLD_BLOCK_DUMMY_ITEM = ITEMS.register("gold_block_dummy",
            () -> new BlockItem(GOLD_BLOCK_DUMMY.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> VENUS_CORE = ITEMS.register("venus_core",
            () -> new Item(new Item.Properties().fireResistant().rarity(Rarity.EPIC)));
    public static final DeferredHolder<Item, Item> VENUS_SOUL_STONE = ITEMS.register("venus_soul_stone",
            () -> new Item(new Item.Properties().fireResistant().rarity(Rarity.EPIC)));
    public static final DeferredHolder<Item, DeferredSpawnEggItem> GUARDIAN_EGG = ITEMS.register("venus_guardian_spawn_egg",
            () -> new DeferredSpawnEggItem(GUARDIAN, 0xC99415, 0x382617, new Item.Properties()));
    public static final DeferredHolder<Item, DeferredSpawnEggItem> GENERAL_EGG = ITEMS.register("venus_general_spawn_egg",
            () -> new DeferredSpawnEggItem(GENERAL, 0x4B3A20, 0xE5B52A, new Item.Properties()));
    public static final DeferredHolder<Item, DeferredSpawnEggItem> APHRODITE_EGG = ITEMS.register("aphrodite_core_spawn_egg",
            () -> new DeferredSpawnEggItem(APHRODITE_CORE, 0x8A3D2B, 0xFFD75A, new Item.Properties()));

    private VenusDimensionContent() {}
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, path); }
    public static boolean allowedSource(Level level) { return level.dimension().equals(Level.OVERWORLD) || level.dimension().equals(VENUS); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); BLOCK_ENTITIES.register(bus);
        bus.addListener(VenusDimensionContent::attributes);
        bus.addListener(VenusDimensionContent::placements);
        bus.addListener(VenusDimensionContent::creative);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VenusDimensionContent::ignite);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(GUARDIAN.get(), VenusGuardian.attributes().build());
        event.put(GENERAL.get(), VenusGeneral.attributes().build());
        event.put(APHRODITE_CORE.get(), AphroditeCore.attributes().build());
    }
    private static <T extends Monster> void placement(RegisterSpawnPlacementsEvent event, EntityType<T> type) {
        event.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, level, reason, pos, random) -> level.getBrightness(LightLayer.BLOCK, pos) == 0
                        && Monster.checkMonsterSpawnRules(entityType, level, reason, pos, random),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
    private static void placements(RegisterSpawnPlacementsEvent event) {
        placement(event, ModEntities.VENUS_ZOMBIE.get()); placement(event, ModEntities.VENUS_SKELETON.get());
        placement(event, ModEntities.VENUS_CREEPER.get()); placement(event, ModEntities.VENUS_SPIDER.get());
        placement(event, ModEntities.VENUS_ENDERMAN.get());
        // 守護者は自然スポーン表に入れず、構造物の一度限りの祭壇から出現する。
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTabs.VENUS_TAB_KEY)) {
            event.accept(GOLD_BLOCK_DUMMY_ITEM.get());
            event.accept(VENUS_CORE.get());
            event.accept(VENUS_SOUL_STONE.get());
            event.accept(GUARDIAN_EGG.get());
            event.accept(GENERAL_EGG.get());
            event.accept(APHRODITE_EGG.get());
        } else if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
            event.accept(GUARDIAN_EGG.get());
            event.accept(GENERAL_EGG.get());
            event.accept(APHRODITE_EGG.get());
        }
    }
    private static void ignite(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        Direction face = event.getFace();
        if (!allowedSource(level) || !stack.is(Items.FLINT_AND_STEEL) || face == null || !player.getAbilities().mayBuild) return;
        BlockPos inside = event.getPos().relative(face);
        VenusPortalFrame frame = VenusPortalFrame.find(level, inside);
        if (frame == null || frame.complete(level)) return;
        for (int u = 0; u < frame.width(); u++) for (int v = 0; v < frame.height(); v++) {
            BlockPos p = frame.at(u, v);
            if (!level.mayInteract(player, p) || !player.mayUseItemAt(p, face, stack)) return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!(level instanceof ServerLevel server)) return;
        frame.fill(server);
        VenusPortalLinks.get(server).register(VenusPortalLinks.Address.of(server, frame));
        server.playSound(null, inside, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1, 1);
        server.gameEvent(player, GameEvent.BLOCK_PLACE, inside);
        if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, event.getPos(), stack);
        stack.hurtAndBreak(1, player, event.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }
}
