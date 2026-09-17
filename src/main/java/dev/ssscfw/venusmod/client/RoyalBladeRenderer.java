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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** SlashBladeの刀身OBJと自前の発光円環だけを描画し、鞘・アイコン表示は使わない。 */
public final class RoyalBladeRenderer extends EntityRenderer<RoyalBladeEntity> {
    /** SlashBlade標準OBJの刀身先端はローカル-X方向。 */
    private static final Vector3f MODEL_TIP = new Vector3f(-1.0F, 0.0F, 0.0F);

    public RoyalBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override public void render(RoyalBladeEntity blade, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        if (blade.blade().isEmpty()) return;

        Vec3 syncedDirection = blade.aimDirection();
        Vector3f target = new Vector3f(
                (float) syncedDirection.x, (float) syncedDirection.y, (float) syncedDirection.z);
        if (target.lengthSquared() < 1.0E-8F) {
            target.set(0.0F, 0.0F, 1.0F);
        } else {
            target.normalize();
        }

        pose.pushPose();
        // yaw/pitchの符号規約へ変換し直さず、OBJの先端軸(-X)を同期済みの実方向へ直接合わせる。
        Quaternionf alignment = new Quaternionf().rotationTo(MODEL_TIP, target);
        pose.mulPose(alignment);

        if (!blade.launched()) {
            float age = blade.tickCount + partialTick;
            float scale = Math.min(1.0F, age / 8.0F);
            pose.pushPose();
            pose.scale(scale, scale, scale);
            // リングは刀身軸(-X)に直交するYZ平面。刀身と同じ軸の周りだけ回転させる。
            pose.mulPose(Axis.XP.rotationDegrees(age * 1.5F));
            VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
            ring(vertices, pose.last().pose(), 0.56F, 0.64F, 190);
            ring(vertices, pose.last().pose(), 0.43F, 0.45F, 130);
            pose.popPose();
        }

        // 旧実装の-Z 0.35移動と同じ見た目になるよう、-Xが前方の座標系では+Xへ退避する。
        pose.translate(0.35F, 0.0F, 0.0F);
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
                        -0.15F,
                        (float) Math.cos(angle) * radius,
                        (float) Math.sin(angle) * radius)
                .setColor(255, 196, 48, alpha);
    }

    @Override public ResourceLocation getTextureLocation(RoyalBladeEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
