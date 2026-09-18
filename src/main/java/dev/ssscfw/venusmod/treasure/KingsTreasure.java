package dev.ssscfw.venusmod.treasure;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.treasury.KingsTreasuryMenu;
import dev.ssscfw.venusmod.treasury.KingsTreasurySavedData;
import dev.ssscfw.venusmod.treasury.PreparedVolleyRules;
import dev.ssscfw.venusmod.treasury.SummonPattern;
import dev.ssscfw.venusmod.treasury.VolleyPriority;
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
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, VenusMod.MOD_ID);
    public static final DeferredItem<TreasureItem> ITEM = ITEMS.register("kings_treasure", TreasureItem::new);
    public static final DeferredHolder<EntityType<?>, EntityType<RoyalBladeEntity>> BLADE = ENTITIES.register(
            "royal_blade", () -> EntityType.Builder.<RoyalBladeEntity>of(RoyalBladeEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F).clientTrackingRange(32).updateInterval(1).noSave().noSummon().build("venusmod:royal_blade"));
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "royal_blade"));
    private static final Map<UUID, List<Formation>> FORMATIONS = new HashMap<>();
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
    public static boolean isHeld(Player player) { return player.getMainHandItem().is(ITEM.get()); }
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Action.TYPE, Action.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) request(player, payload.fire());
        }));
        registrar.playToServer(LimitAction.TYPE, LimitAction.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.cycle()) cycleLimit(player);
                else togglePhantasm(player);
            }
        }));
    }
    public static void request(ServerPlayer player, boolean fire) {
        if (!player.isAlive() || player.isSpectator() || !isHeld(player)) return;
        if (fire) { fire(player); return; }
        // 準備中でも右クリックごとに別の展開セットを追加する。
        // Shift+右クリックも収納には使わず、設定済み収束率で追加展開する。
        prepare(player, player.isShiftKeyDown());
    }
    /** モード変更時は未射出の展開だけ解除する。射出済みの刀は召喚時のモードのまま。 */
    private static void togglePhantasm(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !isHeld(player)) return;
        cancel(player.getUUID());
        boolean enabled = !RoyalBladeEffects.isPhantasm(player.getMainHandItem());
        RoyalBladeEffects.setPhantasm(player.getMainHandItem(), enabled);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.literal(enabled
                ? "壊れた幻想：ON（着弾した刀は消滅・火力5倍）" : "壊れた幻想：OFF（通常返却）"), true);
    }
    private static void cycleLimit(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !isHeld(player)) return;
        int limit = KingsTreasurySavedData.get(player.serverLevel()).cycleVolleyLimit(player.getUUID());
        player.displayClientMessage(Component.translatable("message.venusmod.kings_treasure_limit", limit), true);
    }
    private static void prepare(ServerPlayer player, boolean converging) {
        KingsTreasurySavedData storage = KingsTreasurySavedData.get(player.serverLevel());
        int limit = storage.volleyLimit(player.getUUID());
        boolean brokenOnly = storage.volleyPriority(player.getUUID()) == VolleyPriority.BROKEN_ONLY;
        boolean phantasm = brokenOnly || RoyalBladeEffects.isPhantasm(player.getMainHandItem());
        SummonPattern pattern = storage.summonPattern(player.getUUID());
        int convergencePercent = converging ? storage.convergencePercent(player.getUUID()) : 0;
        double convergence = converging ? storage.convergence(player.getUUID()) : 0.0D;
        List<ItemStack> reserved = preparedCosts(player.getUUID());
        List<ItemStack> selected = storage.selectForVolley(
                player.getUUID(), limit, player.getRandom()::nextInt, phantasm, reserved);
        if (selected.isEmpty()) {
            player.displayClientMessage(Component.literal("射出可能な抜刀剣がありません（保護設定・折れた刀は除外）。"), true);
            return;
        }
        Vec3 aimDirection = stableDirection(player.getLookAngle());
        Vec3 aimOrigin = player.getEyePosition();
        List<RoyalBladeEntity> blades = new ArrayList<>();
        List<ItemStack> costs = new ArrayList<>();
        for (int slot = 0; slot < selected.size(); slot++) {
            ItemStack template = selected.get(slot);
            RoyalBladeEntity blade = new RoyalBladeEntity(BLADE.get(), player.level());
            blade.stage(player, template, slot, aimDirection, selected.size(),
                    phantasm, brokenOnly, pattern, convergence);
            if (pattern == SummonPattern.VIEW_RING
                    && !player.serverLevel().noBlockCollision(blade, blade.getBoundingBox())) {
                blade.discard();
                continue;
            }
            if (player.serverLevel().addFreshEntity(blade)) {
                blades.add(blade);
                costs.add(template.copyWithCount(1));
            } else {
                blade.discard();
            }
        }
        if (blades.isEmpty()) {
            player.displayClientMessage(Component.literal("召喚可能な空間がありません。"), true);
            return;
        }
        if (pattern != SummonPattern.VIEW_RING && blades.size() != selected.size()) {
            for (int i = 0; i < blades.size(); i++) {
                blades.get(i).stage(player, costs.get(i), i, aimDirection, blades.size(),
                        phantasm, brokenOnly, pattern, convergence);
            }
        }
        FORMATIONS.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>())
                .add(new Formation(new TreasureRules.Wave(now(player)), player.level().dimension(),
                        List.copyOf(blades), copyStacks(costs), convergence, aimOrigin, aimDirection,
                        phantasm, brokenOnly, pattern));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.65F, 1.5F);
        String convergenceName = converging ? "収束" + convergencePercent + "%" : "平行射出";
        String powerMode = brokenOnly
                ? "折れた刀幻想：着弾で消滅・火力1.1倍"
                : phantasm ? "壊れた幻想：着弾で消滅・火力5倍"
                : "通常：使用後に返却・耐久力で消耗軽減";
        player.displayClientMessage(Component.literal("王の財宝：" + blades.size() + "/" + limit + "本展開（"
                + pattern.label() + " / " + convergenceName + "） / " + powerMode), true);
    }
    private static void fire(ServerPlayer player) {
        List<Formation> formations = FORMATIONS.remove(player.getUUID());
        if (formations == null || formations.isEmpty()) return;

        long gameTime = now(player);
        List<KingsTreasurySavedData.VolleyUse> allUses = new ArrayList<>();
        for (Formation formation : formations) {
            if (!formation.dimension().equals(player.level().dimension())
                    || formation.wave().expired(gameTime)
                    || formation.blades().size() != formation.costs().size()
                    || formation.blades().stream().anyMatch(
                            blade -> blade.isRemoved() || blade.level() != player.level())) {
                closeAll(formations);
                player.displayClientMessage(Component.literal("展開した刀の状態が変化したため射出を中止しました。"), true);
                return;
            }
            for (ItemStack cost : formation.costs()) {
                allUses.add(new KingsTreasurySavedData.VolleyUse(
                        cost, formation.phantasm(), formation.brokenPhantasm()));
            }
        }

        // 複数回展開した全セットを一括で確保する。1本でも不足すれば何も消費しない。
        for (Formation formation : formations) {
            if (!formation.wave().fire(gameTime)) {
                closeAll(formations);
                return;
            }
        }
        if (!KingsTreasurySavedData.get(player.serverLevel())
                .consumeVolleyUses(player.getUUID(), allUses)) {
            closeAll(formations);
            player.displayClientMessage(
                    Component.literal("刀の不足・保護設定・破損状態の変更により射出を中止しました。"), true);
            return;
        }

        for (Formation formation : formations) {
            Vec3 focus = formation.aimOrigin().add(
                    formation.aimDirection().scale(TreasureRules.CONVERGENCE_DISTANCE));
            for (RoyalBladeEntity blade : formation.blades()) {
                blade.launch(formation.aimDirection(), focus, formation.convergence());
            }
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, 1.0F, 0.75F);
    }
    static void returnSpentBlade(ServerPlayer player, ItemStack original) {
        returnSpentBlade(player, original, null);
    }

    static void returnSpentBlade(ServerPlayer player, ItemStack original, Vec3 impactPosition) {
        if (original == null || original.isEmpty()) return;
        RoyalBladeEffects.ReturnResult result = RoyalBladeEffects.returnBladeResult(player.serverLevel(), original);
        if (result.broke() && impactPosition != null) {
            player.serverLevel().playSound(null, impactPosition.x, impactPosition.y, impactPosition.z,
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        ItemStack returned = result.stack();
        if (returned.isEmpty()) return;
        KingsTreasurySavedData storage = KingsTreasurySavedData.get(player.serverLevel());
        int accepted = storage.insert(player.getUUID(), returned, 1);
        if (accepted == 1) {
            if (player.containerMenu instanceof KingsTreasuryMenu menu) menu.refreshFromStorage();
            return;
        }
        ItemEntity fallback = new ItemEntity(player.serverLevel(), player.getX(), player.getY() + 0.5D,
                player.getZ(), returned.copyWithCount(1));
        fallback.setTarget(player.getUUID());
        fallback.setNoPickUpDelay();
        player.serverLevel().addFreshEntity(fallback);
    }
    private static Vec3 stableDirection(Vec3 direction) { return direction.lengthSqr() < 1.0E-8D ? new Vec3(0, 0, 1) : direction.normalize(); }
    private static long now(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getGameTime();
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(stack -> stack.copyWithCount(1)).toList();
    }

    private static List<ItemStack> preparedCosts(UUID owner) {
        List<Formation> formations = FORMATIONS.get(owner);
        if (formations == null || formations.isEmpty()) return List.of();
        List<ItemStack> reserved = new ArrayList<>();
        for (Formation formation : formations) reserved.addAll(copyStacks(formation.costs()));
        return List.copyOf(reserved);
    }

    private static void closeAll(List<Formation> formations) {
        if (formations != null) formations.forEach(Formation::close);
    }

    /** 王の宝物庫のShift+右クリックから、現在準備中の全セットを収納する。 */
    public static int closePrepared(ServerPlayer player) {
        if (player == null) return 0;
        return closePrepared(player.getUUID());
    }

    private static int closePrepared(UUID owner) {
        List<Formation> formations = FORMATIONS.remove(owner);
        if (formations == null || formations.isEmpty()) return 0;
        int[] counts = new int[formations.size()];
        for (int i = 0; i < formations.size(); i++) counts[i] = formations.get(i).blades().size();
        int total = PreparedVolleyRules.totalPrepared(counts);
        closeAll(formations);
        return total;
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        List<Formation> formations = FORMATIONS.get(player.getUUID());
        if (formations == null || formations.isEmpty()) return;
        long gameTime = now(player);
        formations.removeIf(formation -> {
            boolean keep = TreasureRules.keepPrepared(
                    player.isAlive(),
                    player.isSpectator(),
                    formation.dimension().equals(player.level().dimension()),
                    formation.wave().expired(gameTime),
                    formation.blades().stream().allMatch(RoyalBladeEntity::isRemoved));
            if (!keep) formation.close();
            return !keep;
        });
        if (formations.isEmpty()) FORMATIONS.remove(player.getUUID());
    }

    private static void cancel(UUID owner) { closePrepared(owner); }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { cancel(event.getEntity().getUUID()); }
    private static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) { cancel(event.getEntity().getUUID()); }
    private static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer player) cancel(player.getUUID()); }
    private static void stop(ServerStoppedEvent event) {
        FORMATIONS.values().forEach(KingsTreasure::closeAll);
        FORMATIONS.clear();
    }
    private static void creativeTab(BuildCreativeModeTabContentsEvent event) { if (event.getTabKey() == CreativeModeTabs.COMBAT) event.accept(ITEM); }
    private record Formation(TreasureRules.Wave wave, ResourceKey<Level> dimension, List<RoyalBladeEntity> blades,
                             List<ItemStack> costs, double convergence, Vec3 aimOrigin, Vec3 aimDirection,
                             boolean phantasm, boolean brokenPhantasm, SummonPattern pattern) {
        void close() { wave.cancel(); blades.forEach(RoyalBladeEntity::discard); }
    }
    public record Action(boolean fire) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "kings_treasure_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Action::fire, Action::new);
        @Override public Type<Action> type() { return TYPE; }
    }
    /** true=Fで本数、false=Shift+Fで壊れた幻想。既存のパケット形式を維持する。 */
    public record LimitAction(boolean cycle) implements CustomPacketPayload {
        public static final Type<LimitAction> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "kings_treasure_limit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LimitAction> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, LimitAction::cycle, LimitAction::new);
        @Override public Type<LimitAction> type() { return TYPE; }
    }
    public static final class TreasureItem extends Item {
        public TreasureItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)); }
        @Override public Component getName(ItemStack stack) { return Component.translatableWithFallback("item.venusmod.kings_treasure", "王の財宝"); }
        @Override public boolean isFoil(ItemStack stack) { return true; }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(player.getItemInHand(hand));
            if (player instanceof ServerPlayer serverPlayer) request(serverPlayer, false);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.literal("右クリック/長押し：平行射出を連続展開 / Shift＋右クリック/長押し：収束射出を連続展開"));
            lines.add(Component.literal("左クリック：準備中の全セットを一斉射出"));
            lines.add(Component.literal("収納：王の宝物庫をShift＋右クリック"));
            lines.add(Component.literal("F：最大本数 8→24→48→80→120→168→224→288→360→440"));
            lines.add(Component.literal("Shift+F：壊れた幻想切替 / 収束率・召喚パターンは宝物庫で設定"));
            lines.add(Component.literal(RoyalBladeEffects.isPhantasm(stack) ? "壊れた幻想：着弾した刀は消滅・火力5倍" : "通常：耐久消費後に返却（消滅型は寿命で消失）"));
            lines.add(Component.literal("火属性：Lv×4秒 / 耐久力：確率で消耗防止 / 爆発音量1.2"));
            lines.add(Component.literal("折れた刀のみ：強制幻想・火力1.1倍・着弾で消滅"));
            lines.add(Component.literal("お気に入り・幻想禁止・放出優先度は宝物庫で設定 / 地形破壊なし"));
        }
    }
}
