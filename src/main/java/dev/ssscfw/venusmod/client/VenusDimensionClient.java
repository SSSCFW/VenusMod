package dev.ssscfw.venusmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@EventBusSubscriber(modid = VenusMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VenusDimensionClient {
    private VenusDimensionClient() {}

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VenusDimensionContent.GUARDIAN.get(), context -> new ZombieRenderer(context) {
            @Override public ResourceLocation getTextureLocation(Zombie entity) {
                return VenusDimensionContent.id("textures/entity/venus_zombie.png");
            }
        });
    }

    @SubscribeEvent public static void dimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(VenusDimensionContent.id("venus"), new VenusEffects());
    }

    /**
     * 金星地表の厚いCO2/硫酸雲を表現するクライアント専用描画設定。
     * サーバー側の固定夜(地上Mob自然スポーン用)は変更せず、空が見える場所だけ
     * 暖色の散乱光を足すため、洞窟の暗さと松明による湧きつぶしは維持される。
     */
    private static final class VenusEffects extends DimensionSpecialEffects {
        private VenusEffects() {
            // 通常の青空・太陽・月・星・白い雲は描かず、霧色を空として見せる。
            super(Float.NaN, true, SkyType.NONE, false, false);
        }

        @Override public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            double light = 0.86D + 0.14D * Mth.clamp((double) brightness, 0.0D, 1.0D);
            return new Vec3(
                    Mth.clamp(fogColor.x * 1.06D * light, 0.0D, 1.0D),
                    Mth.clamp(fogColor.y * 0.98D * light, 0.0D, 1.0D),
                    Mth.clamp(fogColor.z * 0.78D * light, 0.0D, 1.0D));
        }

        @Override public boolean isFoggyAt(int x, int y) {
            // 金星の濃い大気らしく、常に厚めの距離霧を使う。
            return true;
        }

        @Override public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                              double camX, double camY, double camZ,
                                              Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
            // 地球型の白い雲は表示しない。背景の黄橙色の霞が雲層を兼ねる。
            return true;
        }

        @Override public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken,
                                                   float blockLightRedFlicker, float skyLight,
                                                   int pixelX, int pixelY, Vector3f colors) {
            // pixelYは0..15の生の空光レベル。固定時刻18000でも、屋外だけ暖色の拡散光を与える。
            float exposedToSky = Mth.clamp(pixelY / 15.0F, 0.0F, 1.0F);
            if (exposedToSky <= 0.0F) return;

            float red = Mth.clamp(colors.x() * (1.0F + 0.08F * exposedToSky) + 0.20F * exposedToSky, 0.0F, 1.0F);
            float green = Mth.clamp(colors.y() * (1.0F - 0.02F * exposedToSky) + 0.11F * exposedToSky, 0.0F, 1.0F);
            float blue = Mth.clamp(colors.z() * (1.0F - 0.28F * exposedToSky) + 0.025F * exposedToSky, 0.0F, 1.0F);
            colors.set(red, green, blue);
        }
    }
}
