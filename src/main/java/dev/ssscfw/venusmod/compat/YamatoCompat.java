package dev.ssscfw.venusmod.compat;

import com.mojang.logging.LogUtils;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoVoidSlashEffects;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

/** 閻魔刀の内部識別と、攻撃処理を持たない抜刀剣モーションの任意依存ブリッジ。 */
@EventBusSubscriber(modid = VenusMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class YamatoCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String YAMATO_TRANSLATION_KEY = "item.slashblade.yamato";
    private static final ResourceLocation COMBO_REGISTRY = slashBlade("combo_state");
    private static final ResourceLocation NONE = slashBlade("none");
    private static final ResourceLocation BLADE_MOTION = slashBlade("combostate/motion.vmd");
    private static final ResourceLocation SLASH_EFFECT = slashBlade("slash_effect");
    private static final ResourceLocation CONCENTRATION = slashBlade("concentration");
    // 時空裂閃はSlashBlade Resharpedの既存ComboStateを使用する（斬撃・ダメージを再実装しない）。
    private static final ResourceLocation VOID_SLASH = slashBlade("void_slash");
    private static final ResourceLocation VOID_SLASH_SHEATH = slashBlade("void_slash_sheath");

    public static final ResourceLocation HORIZONTAL_COMBO = venus("yamato_rift_horizontal");
    public static final ResourceLocation VERTICAL_COMBO = venus("yamato_rift_vertical");
    public static final int SECOND_CUT_TICK = 10;
    public static final int OPEN_TICK = 22;
    public static final int HORIZONTAL_START_FRAME = 1;
    public static final int HORIZONTAL_END_FRAME = 15;
    // aerial_cleave冒頭は落下移動込みの構え。着地モーション中の実際の振り下ろしだけを抜き出す。
    public static final int VERTICAL_START_FRAME = 1836;
    public static final int VERTICAL_END_FRAME = 1846;

    private static boolean identityLookupDone;
    private static boolean motionLookupDone;
    private static boolean effectLookupDone;
    private static boolean cutMotionsRegistered;
    private static boolean failureLogged;
    private static Class<?> bladeItemClass;
    private static Method stateOf;
    private static Method translationKey;
    private static Method isBroken;
    private static Method isSealed;
    private static Method updateComboSeq;
    private static Method resolveCurrentComboState;
    private static Method getComboSeq;
    private static Method setComboSeq;
    private static Method setLastActionTime;
    private static Constructor<? extends Projectile> slashEffectConstructor;
    private static Method getColorCode;
    private static Method getRankLevel;
    private static Method setEffectDamage;
    private static Method setEffectLifetime;
    private static Method setEffectRoll;
    private static Method setEffectColor;
    private static Method setEffectRank;

    private YamatoCompat() {}

    /** 金床の表示名は参照しない。読み取り時のComponent補完はコピーに限定する。 */
    public static boolean isYamato(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !resolveIdentity() || !bladeItemClass.isInstance(stack.getItem())) {
            return false;
        }
        try {
            Object state = bladeState(stack.copyWithCount(1));
            return state != null && YAMATO_TRANSLATION_KEY.equals(translationKey.invoke(state))
                    && !Boolean.TRUE.equals(isBroken.invoke(state))
                    && !Boolean.TRUE.equals(isSealed.invoke(state));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("閻魔刀の内部識別APIを読み取れませんでした", exception);
            return false;
        }
    }

    public static ItemStack heldYamato(LivingEntity entity) {
        if (entity == null) return ItemStack.EMPTY;
        if (isYamato(entity.getMainHandItem())) return entity.getMainHandItem();
        return isYamato(entity.getOffhandItem()) ? entity.getOffhandItem() : ItemStack.EMPTY;
    }

    /**
     * SlashBladeの既存斬撃Rendererへ渡す、描画専用のEntitySlashEffectを作る。
     * Levelへ追加せずtickも呼ばない。所有者なし・damage=0を保ち、攻撃、反射、
     * ノックバック、着地粒子などを持つ通常の斬撃処理へは入らない。
     */
    public static Entity createClientCutEffect(LivingEntity player, InteractionHand hand, int stage) {
        if (player == null || !player.level().isClientSide || hand == null || stage < 0 || stage > 1
                || !isYamato(player.getItemInHand(hand)) || !resolveEffect()
                || !BuiltInRegistries.ENTITY_TYPE.containsKey(SLASH_EFFECT)) return null;
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(SLASH_EFFECT);
            if (type == null) return null;
            Projectile effect = slashEffectConstructor.newInstance(type, player.level());
            effect.setOwner(null);
            effect.setSilent(true);
            effect.noPhysics = true;
            setEffectDamage.invoke(effect, 0.0D);
            setEffectLifetime.invoke(effect, duration(stage) - cutWindup(stage));
            // 既存斬撃のroll=0は横一閃、roll=90は同じ軌道を上から下へ回転したもの。
            setEffectRoll.invoke(effect, stage == 1 ? 90.0F : 0.0F);
            Object state = bladeState(player.getItemInHand(hand).copyWithCount(1));
            if (state != null) setEffectColor.invoke(effect, ((Number) getColorCode.invoke(state)).intValue());
            AttachmentType<?> attachment = NeoForgeRegistries.ATTACHMENT_TYPES.get(CONCENTRATION);
            Object rank = attachment == null ? null : player.getExistingData(attachment).orElse(null);
            if (rank != null) {
                setEffectRank.invoke(effect,
                        ((Number) getRankLevel.invoke(rank, player.level().getGameTime())).floatValue());
            }
            return effect;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("抜刀剣の既存斬撃を描画できないため刀身モーションのみを使います", exception);
            return null;
        }
    }

    /**
     * 元の剣技を直接発動しない。既存剣技には範囲攻撃、落下、矢反射の副作用があるため、
     * 同じVMD区間を持つ独立した無攻撃ComboStateをSlashBladeの登録時に追加する。
     */
    @SubscribeEvent
    public static void registerCutMotions(RegisterEvent event) {
        if (!COMBO_REGISTRY.equals(event.getRegistryKey().location())) return;
        try {
            Object horizontal = createCutMotion(0);
            Object vertical = createCutMotion(1);
            ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(COMBO_REGISTRY);
            event.register(key, HORIZONTAL_COMBO, () -> horizontal);
            event.register(key, VERTICAL_COMBO, () -> vertical);
            cutMotionsRegistered = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            cutMotionsRegistered = false;
            logFailureOnce("抜刀剣の無攻撃モーションを登録できないため専用描画へ切り替えます", exception);
        }
    }

    public static boolean hasCutMotions() {
        return cutMotionsRegistered;
    }

    /** サーバー承認済みの演出通知を、そのクライアントの一時モーション状態へ反映する。 */
    public static boolean applyClientCut(LivingEntity entity, InteractionHand hand, int stage) {
        if (entity == null || !entity.level().isClientSide || !cutMotionsRegistered || !resolveMotion()) return false;
        ItemStack stack = entity.getItemInHand(hand);
        if (stack.isEmpty() || !bladeItemClass.isInstance(stack.getItem())) return false;
        try {
            Object state = bladeState(stack);
            if (state == null) return false;
            if (stage < 0) {
                if (!isCutCombo(getComboSeq.invoke(state))) return false;
                setComboSeq.invoke(state, NONE);
            } else {
                if (stage > 1 || !isYamato(stack)) return false;
                setComboSeq.invoke(state, combo(stage));
            }
            setLastActionTime.invoke(state, entity.level().getGameTime());
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("閻魔刀のクライアントモーションを反映できませんでした", exception);
            return false;
        }
    }

    /**
     * 主手の刀身とPlayerAnimatorへ通常のモーション通知を送る。副手はクライアントの
     * 専用刀身描画が担当する。耐久・魂・攻撃力など永続する刀の値は変更しない。
     */
    public static boolean playCut(ServerPlayer player, int stage) {
        if (player == null || stage < 0 || stage > 1 || !cutMotionsRegistered
                || !isYamato(player.getMainHandItem()) || !resolveMotion()) return false;
        try {
            Object state = bladeState(player.getMainHandItem());
            if (state == null) return false;
            ResourceLocation combo = combo(stage);
            updateComboSeq.invoke(state, player, combo);
            return combo.equals(getComboSeq.invoke(state));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("閻魔刀のモーション通知に失敗したため専用描画へ切り替えます", exception);
            return false;
        }
    }

    /** 中断・完了時にこの機能自身の一時Comboだけを解除する。通常の剣技は上書きしない。 */
    public static void finishCut(ServerPlayer player) {
        if (player == null || !cutMotionsRegistered || !resolveMotion()) return;
        try {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.isEmpty() || !bladeItemClass.isInstance(stack.getItem())) continue;
                Object state = bladeState(stack);
                if (state == null || !isCutCombo(getComboSeq.invoke(state))) continue;
                setComboSeq.invoke(state, NONE);
                setLastActionTime.invoke(state, player.level().getGameTime());
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            // 未解除の無攻撃Comboも有限時間でnoneへ戻る。
            logFailureOnce("閻魔刀のモーション終了通知に失敗しました", exception);
        }
    }

    /**
     * 専用コマンドをサーバーが検証した後だけ実行する。
     * 他のSlashBladeの技設定は変更せず、元Modの時空裂閃ComboStateへ遷移させる。
     * 斬撃中の再発動は拒否し、タイムアウトの進行判定は元Modに委ねる。
     */
    public static boolean performVoidSlash(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()
                || !isYamato(player.getMainHandItem()) || !resolveMotion()) return false;
        try {
            Object state = bladeState(player.getMainHandItem());
            if (state == null) return false;
            Object current = resolveCurrentComboState.invoke(state, player);
            if (VOID_SLASH.equals(current) || VOID_SLASH_SHEATH.equals(current)) return false;
            updateComboSeq.invoke(state, player, VOID_SLASH);
            boolean started = VOID_SLASH.equals(getComboSeq.invoke(state));
            if (started) YamatoVoidSlashEffects.begin(player);
            return started;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("閻魔刀の時空裂閃を発動できませんでした", exception);
            return false;
        }
    }

    /** 既存Void Slashの一段目が実際に再生中のときだけ斬撃Entityを認可する。 */
    public static boolean isVoidSlashActive(ServerPlayer player) {
        if (player == null || !isYamato(player.getMainHandItem()) || !resolveMotion()) return false;
        try {
            Object state = bladeState(player.getMainHandItem());
            return state != null && VOID_SLASH.equals(getComboSeq.invoke(state));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("閻魔刀の時空裂閃状態を確認できませんでした", exception);
            return false;
        }
    }

    public static ResourceLocation combo(int stage) {
        return stage == 1 ? VERTICAL_COMBO : HORIZONTAL_COMBO;
    }

    public static int duration(int stage) {
        return stage == 1 ? OPEN_TICK - SECOND_CUT_TICK : SECOND_CUT_TICK;
    }

    public static int cutWindup(int stage) {
        return stage == 1 ? 3 : 2;
    }

    public static int startFrame(int stage) {
        return stage == 1 ? VERTICAL_START_FRAME : HORIZONTAL_START_FRAME;
    }

    public static int endFrame(int stage) {
        return stage == 1 ? VERTICAL_END_FRAME : HORIZONTAL_END_FRAME;
    }

    /** VMDは30fps、ゲームは20tick/s。刀身と人体へ必ず同じ速度を使う。 */
    public static float motionSpeed(int stage) {
        return (endFrame(stage) - startFrame(stage)) / (duration(stage) * 1.5F);
    }

    private static Object createCutMotion(int stage) throws ReflectiveOperationException {
        Class<?> builderType = Class.forName("mods.flammpfeil.slashblade.registry.combo.ComboState$Builder");
        Object builder = builderType.getMethod("newInstance").invoke(null);
        builderType.getMethod("startAndEnd", int.class, int.class).invoke(builder, startFrame(stage), endFrame(stage));
        builderType.getMethod("motionLoc", ResourceLocation.class).invoke(builder, BLADE_MOTION);
        builderType.getMethod("speed", float.class).invoke(builder, motionSpeed(stage));
        builderType.getMethod("timeout", int.class).invoke(builder, 0);
        Consumer<LivingEntity> noAction = entity -> {};
        BiConsumer<LivingEntity, LivingEntity> noHit = (target, attacker) -> {};
        // Builder既定のtickActionにも矢反射が入るので、追加ではなく完全に置き換える。
        builderType.getMethod("setTickAction", Consumer.class).invoke(builder, noAction);
        builderType.getMethod("setHoldAction", Consumer.class).invoke(builder, noAction);
        builderType.getMethod("clickAction", Consumer.class).invoke(builder, noAction);
        builderType.getMethod("setHitEffect", BiConsumer.class).invoke(builder, noHit);
        return builderType.getMethod("build").invoke(builder);
    }

    private static Object bladeState(ItemStack stack) throws ReflectiveOperationException {
        Object raw = stateOf.invoke(null, stack);
        return raw instanceof Optional<?> optional ? optional.orElse(null) : null;
    }

    private static boolean resolveIdentity() {
        if (identityLookupDone) return stateOf != null;
        identityLookupDone = true;
        if (!ModList.get().isLoaded("slashblade")) return false;
        try {
            bladeItemClass = Class.forName("mods.flammpfeil.slashblade.item.ItemSlashBlade");
            Class<?> access = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess");
            Class<?> state = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
            translationKey = state.getMethod("getTranslationKey");
            isBroken = state.getMethod("isBroken");
            isSealed = state.getMethod("isSealed");
            stateOf = access.getMethod("of", ItemStack.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            stateOf = null;
            logFailureOnce("抜刀剣の閻魔刀識別APIを初期化できませんでした", exception);
            return false;
        }
    }

    private static boolean resolveMotion() {
        if (!resolveIdentity()) return false;
        if (motionLookupDone) return updateComboSeq != null;
        motionLookupDone = true;
        try {
            Class<?> state = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
            getComboSeq = state.getMethod("getComboSeq");
            setComboSeq = state.getMethod("setComboSeq", ResourceLocation.class);
            setLastActionTime = state.getMethod("setLastActionTime", long.class);
            updateComboSeq = state.getMethod("updateComboSeq", LivingEntity.class, ResourceLocation.class);
            resolveCurrentComboState = state.getMethod("resolvCurrentComboState", LivingEntity.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            updateComboSeq = null;
            logFailureOnce("抜刀剣のモーションAPIを初期化できませんでした", exception);
            return false;
        }
    }

    private static boolean resolveEffect() {
        if (!resolveIdentity()) return false;
        if (effectLookupDone) return slashEffectConstructor != null;
        effectLookupDone = true;
        try {
            Class<? extends Projectile> effect = Class.forName("mods.flammpfeil.slashblade.entity.EntitySlashEffect")
                    .asSubclass(Projectile.class);
            Class<?> state = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
            Class<?> rank = Class.forName("mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank");
            getColorCode = state.getMethod("getColorCode");
            getRankLevel = rank.getMethod("getRankLevel", long.class);
            setEffectDamage = effect.getMethod("setDamage", double.class);
            setEffectLifetime = effect.getMethod("setLifetime", int.class);
            setEffectRoll = effect.getMethod("setRotationRoll", float.class);
            setEffectColor = effect.getMethod("setColor", int.class);
            setEffectRank = effect.getMethod("setRank", float.class);
            slashEffectConstructor = effect.getConstructor(EntityType.class, Level.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            slashEffectConstructor = null;
            logFailureOnce("抜刀剣の既存斬撃描画APIを初期化できませんでした", exception);
            return false;
        }
    }

    private static boolean isCutCombo(Object combo) {
        return HORIZONTAL_COMBO.equals(combo) || VERTICAL_COMBO.equals(combo);
    }

    private static void logFailureOnce(String message, Throwable error) {
        if (failureLogged) return;
        failureLogged = true;
        LOGGER.warn(message, error);
    }

    private static ResourceLocation slashBlade(String path) {
        return ResourceLocation.fromNamespaceAndPath("slashblade", path);
    }

    private static ResourceLocation venus(String path) {
        return ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, path);
    }
}
