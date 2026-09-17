package dev.ssscfw.venusmod.treasure;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.compat.SlashBladeTreasuryCompat;
import dev.ssscfw.venusmod.treasury.KingsTreasuryMenu;
import dev.ssscfw.venusmod.treasury.KingsTreasurySavedData;
import dev.ssscfw.venusmod.treasury.TreasuryEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 王の財宝の登録と、UUIDに紐づくサーバー側の展開・射出管理。 */
public final class KingsTreasure {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VenusMod.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, VenusMod.MOD_ID);
    public static final DeferredItem<TreasureItem> ITEM = ITEMS.register("kings_treasure", TreasureItem::new);
    public static final DeferredHolder<EntityType<?>, EntityType<RoyalBladeEntity>> BLADE = ENTITIES.register(
            "royal_blade", () -> EntityType.Builder.<RoyalBladeEntity>of(RoyalBladeEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F).clientTrackingRange(32).updateInterval(1).noSave().noSummon()
                    .build("venusmod:royal_blade"));
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "royal_blade"));
    private static final Map<UUID, Formation> FORMATIONS = new HashMap<>();

    private KingsTreasure() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(KingsTreasure::registerPayloads);
        modBus.addListener(KingsTreasure::creativeTab);
        NeoForge.EVENT_BUS.addListener(KingsTreasure::tick);
        NeoForge.EVENT_BUS.addListener(KingsTreasure::logout);
        NeoForge.EVENT_BUS.addListener(KingsTreasure::changeDimension);
        NeoForge.EVENT_BUS.addListener(KingsTreasure::death);
        NeoForge.EVENT_BUS.addListener(KingsTreasure::stop);
    }

    public static boolean isHeld(Player player) {
        return player.getMainHandItem().is(ITEM.get());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Action.TYPE, Action.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) request(player, payload.fire());
                }));
        registrar.playToServer(LimitAction.TYPE, LimitAction.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && payload.cycle()) cycleLimit(player);
                }));
    }

    /** クライアントからは操作だけ受け取り、所有者・在庫・照準・期限はサーバーで決定する。 */
    public static void request(ServerPlayer player, boolean fire) {
        if (!player.isAlive() || player.isSpectator() || !isHeld(player)) return;
        if (fire) {
            fire(player);
            return;
        }

        Formation existing = FORMATIONS.get(player.getUUID());
        if (existing != null) {
            if (player.isShiftKeyDown()) cancel(player.getUUID());
            return;
        }

        prepare(player, TreasureRules.VolleyMode.forSummon(player.isShiftKeyDown()));
    }

    private static void cycleLimit(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !isHeld(player)) return;
        int limit = KingsTreasurySavedData.get(player.serverLevel()).cycleVolleyLimit(player.getUUID());
        player.displayClientMessage(Component.translatable("message.venusmod.kings_treasure_limit", limit), true);
    }

    private static void prepare(ServerPlayer player, TreasureRules.VolleyMode mode) {
        if (FORMATIONS.containsKey(player.getUUID())) return;

        KingsTreasurySavedData storage = KingsTreasurySavedData.get(player.serverLevel());
        int limit = storage.volleyLimit(player.getUUID());
        List<TreasuryEntry> entries = storage.page(player.getUUID(), 0, TreasureRules.MAX_BLADES);
        List<Integer> selected = TreasureRules.select(
                entries.stream().mapToInt(TreasuryEntry::count).toArray(), limit);
        if (selected.isEmpty()) {
            player.displayClientMessage(Component.literal("王の宝物庫に抜刀剣が入っていません。"), true);
            return;
        }

        Vec3 aimDirection = stableDirection(player.getLookAngle());
        Vec3 aimOrigin = player.getEyePosition();
        List<RoyalBladeEntity> blades = new ArrayList<>();
        List<ItemStack> costs = new ArrayList<>();
        for (int slot = 0; slot < selected.size(); slot++) {
            ItemStack template = entries.get(selected.get(slot)).template();
            RoyalBladeEntity blade = new RoyalBladeEntity(BLADE.get(), player.level());
            blade.stage(player, template, slot, aimDirection);
            if (player.serverLevel().addFreshEntity(blade)) {
                blades.add(blade);
                costs.add(template.copyWithCount(1));
            } else {
                blade.discard();
            }
        }
        if (blades.isEmpty()) return;

        FORMATIONS.put(player.getUUID(), new Formation(
                new TreasureRules.Wave(now(player)), player.level().dimension(),
                List.copyOf(blades), copyStacks(costs), mode, aimOrigin, aimDirection));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.65F, 1.5F);
        String modeName = mode == TreasureRules.VolleyMode.PARALLEL ? "平行" : "中程度収束";
        player.displayClientMessage(Component.literal("王の財宝：" + blades.size() + "/" + limit
                + "本展開（" + modeName + "） / 左クリックで一斉射出（射出後に耐久1消費して返却）"), true);
    }

    private static void fire(ServerPlayer player) {
        Formation formation = FORMATIONS.remove(player.getUUID());
        if (formation == null) return;
        if (!formation.dimension().equals(player.level().dimension()) || !formation.wave().fire(now(player))) {
            formation.close();
            return;
        }
        if (formation.blades().size() != formation.costs().size()
                || formation.blades().stream().anyMatch(
                        blade -> blade.isRemoved() || blade.level() != player.level())) {
            formation.close();
            player.displayClientMessage(Component.literal("展開した刀が失われたため射出を中止しました。"), true);
            return;
        }

        if (!KingsTreasurySavedData.get(player.serverLevel()).consumeAll(player.getUUID(), formation.costs())) {
            formation.close();
            player.displayClientMessage(Component.literal("王の宝物庫の刀が不足しているため射出を中止しました。"), true);
            return;
        }

        Vec3 focusPoint = formation.aimOrigin().add(
                formation.aimDirection().scale(TreasureRules.CONVERGENCE_DISTANCE));
        for (RoyalBladeEntity blade : formation.blades()) {
            blade.launch(formation.aimDirection(), focusPoint, formation.mode().convergence());
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, 1.0F, 0.75F);
    }

    /** 射出を終えた刀を耐久1消費した状態でUUID紐づけの宝物庫へ戻す。 */
    static void returnSpentBlade(ServerPlayer player, ItemStack original) {
        if (original == null || original.isEmpty()) return;

        ItemStack returned = SlashBladeTreasuryCompat.damageOnePoint(original);
        if (returned.isEmpty()) return;

        KingsTreasurySavedData storage = KingsTreasurySavedData.get(player.serverLevel());
        int accepted = storage.insert(player.getUUID(), returned, 1);
        if (accepted == 1) {
            if (player.containerMenu instanceof KingsTreasuryMenu menu) menu.refreshFromStorage();
            return;
        }

        // 通常は射出時に元の刀を消費しているため容量は戻せる。
        // 万一その間に上限へ達しても刀を消失させず、所有者の足元へ返す。
        ItemEntity fallback = new ItemEntity(
                player.serverLevel(), player.getX(), player.getY() + 0.5D, player.getZ(),
                returned.copyWithCount(1));
        fallback.setTarget(player.getUUID());
        fallback.setNoPickUpDelay();
        player.serverLevel().addFreshEntity(fallback);
    }

    private static Vec3 stableDirection(Vec3 direction) {
        return direction.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
    }

    private static long now(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getGameTime();
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(stack -> stack.copyWithCount(1)).toList();
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Formation formation = FORMATIONS.get(player.getUUID());
        if (formation == null) return;

        boolean keep = TreasureRules.keepPrepared(
                player.isAlive(),
                player.isSpectator(),
                formation.dimension().equals(player.level().dimension()),
                formation.wave().expired(now(player)),
                formation.blades().stream().allMatch(RoyalBladeEntity::isRemoved));
        if (!keep) cancel(player.getUUID());
    }

    private static void cancel(UUID owner) {
        Formation formation = FORMATIONS.remove(owner);
        if (formation != null) formation.close();
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { cancel(event.getEntity().getUUID()); }
    private static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) { cancel(event.getEntity().getUUID()); }
    private static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player.getUUID());
    }
    private static void stop(ServerStoppedEvent event) {
        FORMATIONS.values().forEach(Formation::close);
        FORMATIONS.clear();
    }
    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) event.accept(ITEM);
    }

    private record Formation(TreasureRules.Wave wave, ResourceKey<Level> dimension,
                             List<RoyalBladeEntity> blades, List<ItemStack> costs,
                             TreasureRules.VolleyMode mode, Vec3 aimOrigin, Vec3 aimDirection) {
        void close() {
            wave.cancel();
            blades.forEach(RoyalBladeEntity::discard);
        }
    }

    public record Action(boolean fire) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "kings_treasure_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, Action::fire, Action::new);
        @Override public Type<Action> type() { return TYPE; }
    }

    public record LimitAction(boolean cycle) implements CustomPacketPayload {
        public static final Type<LimitAction> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "kings_treasure_limit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LimitAction> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, LimitAction::cycle, LimitAction::new);
        @Override public Type<LimitAction> type() { return TYPE; }
    }

    public static final class TreasureItem extends Item {
        public TreasureItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)); }
        @Override public Component getName(ItemStack stack) {
            return Component.translatableWithFallback("item.venusmod.kings_treasure", "王の財宝");
        }
        @Override public boolean isFoil(ItemStack stack) { return true; }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(player.getItemInHand(hand));
            if (player instanceof ServerPlayer serverPlayer) request(serverPlayer, false);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                              List<Component> lines, TooltipFlag flag) {
            lines.add(Component.literal("右クリック：平行展開 / Shift＋右クリック：中程度収束展開"));
            lines.add(Component.literal("左クリック：一斉射出 / 展開中Shift＋右クリック：収納"));
            lines.add(Component.literal("F：最大本数 8→12→24→32→48→96→120"));
            lines.add(Component.literal("射出後に耐久1消費して宝物庫へ返却・クールタイムなし・地形破壊なし"));
        }
    }
}
