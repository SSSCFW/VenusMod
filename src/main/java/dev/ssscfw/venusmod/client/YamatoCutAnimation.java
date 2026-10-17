package dev.ssscfw.venusmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.compat.YamatoCompat;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

/** サーバー承認後に、横一閃→縦一閃を主手・副手それぞれの実刀身で描く。 */
@EventBusSubscriber(modid = VenusMod.MOD_ID, value = Dist.CLIENT)
public final class YamatoCutAnimation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation PLAYER_MOTION =
            ResourceLocation.fromNamespaceAndPath("slashblade", "model/pa/player_motion.vmd");
    private static final int ANIMATION_PRIORITY = 18420;
    private static final Vector3f MODEL_TIP = new Vector3f(-1.0F, 0.0F, 0.0F);
    private static final Map<Integer, CutState> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;
    private static boolean renderingOwnBlade;
    private static boolean animationLookupDone;
    private static boolean filterLookupDone;
    private static boolean failureLogged;
    private static Constructor<?> vmdConstructor;
    private static Method vmdPlay;
    private static Method vmdSpeed;
    private static Method getAnimationStack;
    private static Method addAnimation;
    private static Method removeAnimation;
    private static Method renderEventStack;
    private static Method renderEventTarget;

    private YamatoCutAnimation() {}

    /** 単独利用時の二段シーケンス。通常のネットワーク経路は各段をサーバーが通知する。 */
    public static void start(int entityId, InteractionHand hand) {
        start(entityId, hand, 0);
        CutState state = ACTIVE.get(entityId);
        if (state != null) state.automaticSequence = true;
    }

    /** stage=0が左から右、1が上から下、-1が中断または完了。 */
    public static void start(int entityId, InteractionHand hand, int stage) {
        if (stage < 0) {
            stop(entityId);
            return;
        }
        if (stage > 1 || hand == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (activeLevel != minecraft.level) clear();
        activeLevel = minecraft.level;
        if (!(minecraft.level.getEntity(entityId) instanceof AbstractClientPlayer player)
                || !YamatoCompat.isYamato(player.getItemInHand(hand))) return;

        CutState previous = ACTIVE.remove(entityId);
        if (previous != null) detachAnimation(previous);
        CutState state = new CutState(player, hand, stage, player.level().getGameTime());
        state.slashEffect = YamatoCompat.createClientCutEffect(player, hand, stage);
        state.automaticSequence = previous != null && previous.automaticSequence;
        // SlashBladeの既定刀身レイヤーは右利き主手用。その他は同じOBJを専用レイヤーで描く。
        state.nativeBladeMotion = hand == InteractionHand.MAIN_HAND && player.getMainArm() == HumanoidArm.RIGHT
                && YamatoCompat.applyClientCut(player, hand, stage);
        ACTIVE.put(entityId, state);
        registerNativeRenderFilter();
        if (state.nativeBladeMotion) installPlayerAnimation(state);
    }

    public static void stop(int entityId) {
        CutState state = ACTIVE.remove(entityId);
        if (state == null) return;
        detachAnimation(state);
        if (state.nativeBladeMotion) YamatoCompat.applyClientCut(state.player, state.hand, -1);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || activeLevel != minecraft.level) {
            clear();
            return;
        }
        long now = minecraft.level.getGameTime();
        for (CutState state : List.copyOf(ACTIVE.values())) {
            if (state.player.isRemoved() || !state.player.isAlive()
                    || state.player.level() != minecraft.level
                    || !YamatoCompat.isYamato(state.player.getItemInHand(state.hand))) {
                stop(state.player.getId());
                continue;
            }
            long elapsed = now - state.startedAt;
            if (state.automaticSequence && state.stage == 0 && elapsed >= YamatoCompat.SECOND_CUT_TICK) {
                start(state.player.getId(), state.hand, 1);
            } else if (elapsed >= YamatoCompat.duration(state.stage) + (state.automaticSequence ? 0 : 2)) {
                stop(state.player.getId());
            }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        for (int entityId : List.copyOf(ACTIVE.keySet())) stop(entityId);
        activeLevel = null;
    }

    /** HumanoidModel.setupAnimの末尾から呼ぶ。通常のswingでは二方向を表現できないため専用姿勢を使う。 */
    public static void applyFallbackPose(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        CutState state = ACTIVE.get(entity.getId());
        if (state == null || state.player != entity || state.animation != null) return;
        float partial = ageInTicks - (float) Math.floor(ageInTicks);
        float progress = strokeProgress(state, partial);
        HumanoidArm arm = activeArm(state);
        ModelPart bladeArm = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart otherArm = arm == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
        float side = arm == HumanoidArm.RIGHT ? -1.0F : 1.0F;

        if (state.stage == 0) {
            bladeArm.xRot = -(float) Math.PI / 2.0F;
            bladeArm.yRot = Mth.lerp(progress, -1.12F, 1.12F);
            bladeArm.zRot = side * 0.10F;
            model.body.yRot = Mth.lerp(progress, -0.20F, 0.20F);
        } else {
            bladeArm.xRot = Mth.lerp(progress, -2.95F, -0.20F);
            bladeArm.yRot = -side * 0.14F;
            bladeArm.zRot = -side * 0.13F;
            model.body.yRot = 0.0F;
        }
        otherArm.xRot = -0.55F;
        otherArm.yRot = side * 0.12F;
        otherArm.zRot = side * 0.08F;
        model.attackTime = 0.0F;
    }

    /** 副手・左利き・互換API変更時にも、実際の閻魔刀OBJを二方向へ動かす。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void renderFirstPerson(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        CutState state = ACTIVE.get(minecraft.player.getId());
        if (state == null || state.nativeBladeMotion || state.hand != event.getHand()
                || !YamatoCompat.isYamato(event.getItemStack())) return;

        float progress = strokeProgress(state, event.getPartialTick());
        Vector3f direction;
        float gripX;
        float gripY;
        if (state.stage == 0) {
            float x = Mth.lerp(progress, -0.90F, 0.90F);
            direction = new Vector3f(x, 0.18F, -(float) Math.sqrt(Math.max(0.02F, 1.0F - x * x))).normalize();
            gripX = Mth.lerp(progress, -0.40F, 0.40F);
            gripY = -0.30F;
        } else {
            float y = Mth.lerp(progress, 0.94F, -0.90F);
            direction = new Vector3f(0.0F, y, -(float) Math.sqrt(Math.max(0.02F, 1.0F - y * y))).normalize();
            gripX = activeArm(state) == HumanoidArm.RIGHT ? 0.10F : -0.10F;
            gripY = Mth.lerp(progress, 0.10F, -0.65F);
        }

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        boolean rendered;
        try {
            // NakedRenderCompatの中心から柄までは約0.61block。柄が握り位置へ来るよう先端方向へ戻す。
            pose.translate(gripX + direction.x * 0.49F, gripY + direction.y * 0.49F,
                    -0.85F + direction.z * 0.49F);
            pose.mulPose(new Quaternionf().rotationTo(MODEL_TIP, direction));
            pose.scale(0.80F, 0.80F, 0.80F);
            rendered = renderOwnBlade(event.getItemStack(), pose, event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            pose.popPose();
        }
        if (rendered) event.setCanceled(true);
    }

    /** 既存SlashEffectRendererのOBJ・テクスチャ・残光を、攻撃tickを実行せずに描く。 */
    @SubscribeEvent
    public static void renderNativeCutEffects(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean rendered = false;
        for (CutState state : ACTIVE.values()) {
            Entity effect = state.slashEffect;
            if (effect == null || state.player.level() != minecraft.level || state.player.isInvisible()) continue;
            float age = elapsed(state, partial) - YamatoCompat.cutWindup(state.stage);
            int lifetime = YamatoCompat.duration(state.stage) - YamatoCompat.cutWindup(state.stage);
            if (age < 0.0F || age >= lifetime) continue;
            float yaw = Mth.rotLerp(partial, state.player.yRotO, state.player.getYRot());
            float pitch = Mth.lerp(partial, state.player.xRotO, state.player.getXRot());
            // SlashBlade通常斬撃と同じ中心位置。両手とも実際に持つ刀の色・集中ランクを使う。
            Vec3 position = state.player.getPosition(partial).add(0.0D, state.player.getEyeHeight() * 0.75D, 0.0D)
                    .add(Vec3.directionFromRotation(pitch, yaw).scale(0.3D));
            effect.setPos(position);
            effect.setYRot(yaw);
            // 既存Rendererはyawの後にZ軸へpitchを掛けるため、視線の下向き正とは符号が逆。
            effect.setXRot(-pitch);
            // tickしないEntityでも、補間した採光位置がワールド原点へ戻らないよう揃える。
            effect.setOldPosAndRot();
            // tick()は攻撃処理を持つため呼ばず、Rendererが読む表示時計だけを進める。
            effect.tickCount = (int) age;
            float effectPartial = age - effect.tickCount;
            pose.pushPose();
            try {
                pose.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
                EntityRenderer<? super Entity> renderer = minecraft.getEntityRenderDispatcher().getRenderer(effect);
                renderer.render(effect, yaw, effectPartial, pose, buffers,
                        renderer.getPackedLightCoords(effect, effectPartial));
                rendered = true;
            } catch (LinkageError | RuntimeException exception) {
                state.slashEffect = null;
                logFailureOnce("抜刀剣の既存斬撃Rendererを利用できませんでした", exception);
            } finally {
                pose.popPose();
            }
        }
        if (rendered) buffers.endBatch();
    }

    private static float elapsed(CutState state, float partial) {
        return Math.max(0.0F, state.player.level().getGameTime() - state.startedAt + partial);
    }

    private static float strokeProgress(CutState state, float partial) {
        float time = elapsed(state, partial);
        float windup = YamatoCompat.cutWindup(state.stage);
        float cutDuration = state.stage == 0 ? 4.5F : 5.0F;
        float t = Mth.clamp((time - windup) / cutDuration, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static HumanoidArm activeArm(CutState state) {
        return state.hand == InteractionHand.MAIN_HAND ? state.player.getMainArm() : state.player.getMainArm().getOpposite();
    }

    private static boolean renderOwnBlade(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean previous = renderingOwnBlade;
        renderingOwnBlade = true;
        try {
            return SlashBladeNakedRenderCompat.render(stack, pose, buffers, light);
        } finally {
            renderingOwnBlade = previous;
        }
    }

    /** 他modのレイヤーは削除せず、このシーケンスが追加したインスタンスだけを付け外しする。 */
    private static void installPlayerAnimation(CutState state) {
        if (!resolvePlayerAnimator()) return;
        try {
            Object animation = vmdConstructor.newInstance(PLAYER_MOTION,
                    (double) YamatoCompat.startFrame(state.stage), (double) YamatoCompat.endFrame(state.stage), false);
            vmdSpeed.invoke(animation, (double) YamatoCompat.motionSpeed(state.stage));
            vmdPlay.invoke(animation, 0);
            Object stack = getAnimationStack.invoke(null, state.player);
            addAnimation.invoke(stack, ANIMATION_PRIORITY, animation);
            state.animation = animation;
            state.animationStack = stack;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("閻魔刀のPlayerAnimatorモーションを使えないため専用姿勢へ切り替えます", exception);
        }
    }

    private static void detachAnimation(CutState state) {
        if (state.animation == null || state.animationStack == null || removeAnimation == null) return;
        try {
            removeAnimation.invoke(state.animationStack, state.animation);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("閻魔刀の人体モーションの解除に失敗しました", exception);
        } finally {
            state.animation = null;
            state.animationStack = null;
        }
    }

    private static boolean resolvePlayerAnimator() {
        if (animationLookupDone) return vmdConstructor != null;
        animationLookupDone = true;
        if (!ModList.get().isLoaded("slashblade") || !ModList.get().isLoaded("playeranimator")) return false;
        try {
            Class<?> animation = Class.forName("dev.kosmx.playerAnim.api.layered.IAnimation");
            Class<?> stack = Class.forName("dev.kosmx.playerAnim.api.layered.AnimationStack");
            Class<?> access = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess");
            Class<?> vmd = Class.forName("mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation");
            getAnimationStack = access.getMethod("getPlayerAnimLayer", AbstractClientPlayer.class);
            addAnimation = stack.getMethod("addAnimLayer", int.class, animation);
            removeAnimation = stack.getMethod("removeLayer", animation);
            vmdPlay = vmd.getMethod("play", int.class);
            vmdSpeed = vmd.getMethod("setSpeed", double.class);
            vmdConstructor = vmd.getConstructor(ResourceLocation.class, double.class, double.class, boolean.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            vmdConstructor = null;
            logFailureOnce("閻魔刀のPlayerAnimator任意依存を初期化できませんでした", exception);
            return false;
        }
    }

    private static void registerNativeRenderFilter() {
        if (filterLookupDone) return;
        filterLookupDone = true;
        try {
            Class<? extends Event> event = Class.forName("mods.flammpfeil.slashblade.event.client.RenderOverrideEvent")
                    .asSubclass(Event.class);
            renderEventStack = event.getMethod("getStack");
            renderEventTarget = event.getMethod("getTarget");
            registerFilter(event);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("閻魔刀の副手待機描画を抑制できませんでした", exception);
        }
    }

    private static <T extends Event> void registerFilter(Class<T> eventType) {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, eventType, YamatoCutAnimation::filterNativeBlade);
    }

    private static void filterNativeBlade(Event event) {
        if (renderingOwnBlade || ACTIVE.isEmpty() || !(event instanceof ICancellableEvent cancellable)) return;
        try {
            Object stack = renderEventStack.invoke(event);
            Object target = renderEventTarget.invoke(event);
            if (!(target instanceof String part) || !(part.startsWith("blade") || part.startsWith("sheath"))) return;
            for (CutState state : ACTIVE.values()) {
                if (!state.nativeBladeMotion && stack == state.player.getItemInHand(state.hand)) {
                    cancellable.setCanceled(true);
                    return;
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailureOnce("閻魔刀の二重描画抑制に失敗しました", exception);
        }
    }

    private static void logFailureOnce(String message, Throwable exception) {
        if (failureLogged) return;
        failureLogged = true;
        LOGGER.warn(message, exception);
    }

    @EventBusSubscriber(modid = VenusMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class LayerRegistration {
        private LayerRegistration() {}

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) renderer.addLayer(new HeldCutLayer(renderer));
            }
        }
    }

    /** SlashBladeが待機表示しか持たない副手にも、現在の腕へ取り付けた刀身を表示する。 */
    private static final class HeldCutLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private HeldCutLayer(PlayerRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbAmount, float partial, float age, float headYaw, float headPitch) {
            CutState state = ACTIVE.get(player.getId());
            if (state == null || state.player != player || state.nativeBladeMotion || player.isInvisible()) return;
            ItemStack stack = player.getItemInHand(state.hand);
            if (!YamatoCompat.isYamato(stack)) return;
            pose.pushPose();
            try {
                getParentModel().translateToHand(activeArm(state), pose);
                // 腕の先端は約0.60block。柄から刀身中心までの約0.61blockを合わせる。
                pose.translate(0.0D, 1.20D, 0.0D);
                pose.mulPose(new Quaternionf().rotationTo(MODEL_TIP, new Vector3f(0.0F, 1.0F, 0.0F)));
                renderOwnBlade(stack, pose, buffers, light);
            } finally {
                pose.popPose();
            }
        }
    }

    private static final class CutState {
        private final AbstractClientPlayer player;
        private final InteractionHand hand;
        private final int stage;
        private final long startedAt;
        private boolean nativeBladeMotion;
        private Entity slashEffect;
        private boolean automaticSequence;
        private Object animation;
        private Object animationStack;

        private CutState(AbstractClientPlayer player, InteractionHand hand, int stage, long startedAt) {
            this.player = player;
            this.hand = hand;
            this.stage = stage;
            this.startedAt = startedAt;
        }
    }
}
