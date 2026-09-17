package dev.ssscfw.venusmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ssscfw.venusmod.treasure.RoyalBladeEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** SlashBladeの刀身OBJと自前の発光円環だけを描画し、鞘・アイコン表示は使わない。 */
public final class RoyalBladeRenderer extends EntityRenderer<RoyalBladeEntity> {
    public RoyalBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override public void render(RoyalBladeEntity blade, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        if (blade.blade().isEmpty()) return;
        pose.pushPose();
        // Minecraftの視線ベクトルは yaw=0 で +Z、正pitchで下向き。
        // ローカル+ZをEntityに保存した召喚時/射出時の向きへそのまま合わせる。
        pose.mulPose(Axis.YP.rotationDegrees(-blade.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(blade.getXRot()));
        if (!blade.launched()) {
            float age = blade.tickCount + partialTick;
            float scale = Math.min(1.0F, age / 8.0F);
            pose.pushPose();
            pose.scale(scale, scale, scale);
            pose.mulPose(Axis.ZP.rotationDegrees(age * 1.5F));
            VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
            ring(vertices, pose.last().pose(), 0.56F, 0.64F, 190);
            ring(vertices, pose.last().pose(), 0.43F, 0.45F, 130);
            pose.popPose();
        }

        pose.translate(0, 0, -0.35);
        // SlashBlade標準OBJの刀身は負のX方向へ先端が伸びる。
        // +90° Y回転で負Xをローカル+Z（射出方向）へ合わせる。
        pose.mulPose(Axis.YP.rotationDegrees(90.0F));
        SlashBladeNakedRenderCompat.render(
                blade.blade(), pose, buffers, LightTexture.FULL_BRIGHT);
        pose.popPose();
        super.render(blade, yaw, partialTick, pose, buffers, light);
    }

    /** 表裏両方の頂点順を出し、リングを正面・背面どちらから見ても表示する。 */
    private static void ring(VertexConsumer vertices, Matrix4f matrix, float inner, float outer, int alpha) {
        for (int segment = 0; segment < 48; segment++) {
            double a = segment * Math.PI * 2 / 48;
            double b = (segment + 1) * Math.PI * 2 / 48;
            quad(vertices, matrix, inner, outer, a, b, alpha, false);
            quad(vertices, matrix, inner, outer, a, b, alpha, true);
        }
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, float inner, float outer,
                             double a, double b, int alpha, boolean reverse) {
        if (!reverse) {
            vertex(vertices, matrix, inner, a, alpha);
            vertex(vertices, matrix, outer, a, alpha);
            vertex(vertices, matrix, outer, b, alpha);
            vertex(vertices, matrix, inner, b, alpha);
        } else {
            vertex(vertices, matrix, inner, b, alpha);
            vertex(vertices, matrix, outer, b, alpha);
            vertex(vertices, matrix, outer, a, alpha);
            vertex(vertices, matrix, inner, a, alpha);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float radius, double angle, int alpha) {
        consumer.addVertex(matrix,
                        (float) Math.cos(angle) * radius,
                        (float) Math.sin(angle) * radius,
                        0.15F)
                .setColor(255, 196, 48, alpha);
    }

    @Override public ResourceLocation getTextureLocation(RoyalBladeEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
