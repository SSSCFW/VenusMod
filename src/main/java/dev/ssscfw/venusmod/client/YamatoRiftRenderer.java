package dev.ssscfw.venusmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.yamato.YamatoRiftGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** エンドゲートウェイそのもののシェーダーで、表裏を持つ凹曲線の裂け目を描く。 */
@EventBusSubscriber(modid = VenusMod.MOD_ID, value = Dist.CLIENT)
public final class YamatoRiftRenderer {
    private static final int SEGMENTS = 96;
    private static final float FACE_OFFSET = 0.002F;

    private YamatoRiftRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || YamatoRiftsClient.portals().isEmpty()) return;
        String dimension = minecraft.level.dimension().location().toString();
        double now = YamatoRiftsClient.estimatedServerTick()
                + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean rendered = false;

        for (YamatoRiftsClient.PortalView portal : YamatoRiftsClient.portals()) {
            if (!dimension.equals(portal.dimension())) continue;
            double scale = YamatoRiftGeometry.scale(now, portal.openedAt(), portal.closesAt());
            if (scale <= 0.001D) continue;
            Vec3 center = portal.center();
            AABB bounds = new AABB(center.x - 1.4D, center.y - 1.7D, center.z - 1.4D,
                    center.x + 1.4D, center.y + 1.7D, center.z + 1.4D);
            if (!event.getFrustum().isVisible(bounds)) continue;

            pose.pushPose();
            try {
                pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
                // サーバーと同じright=(cos(yaw),0,sin(yaw))、normal=(-sin(yaw),0,cos(yaw))。
                pose.mulPose(Axis.YP.rotationDegrees(-portal.yaw()));
                pose.scale((float) scale, (float) scale, (float) scale);
                Matrix4f matrix = pose.last().pose();

                // 原版のend_gatewayシェーダー＋end_sky/end_portalテクスチャを直接使用する。
                // 平面矩形を重ねず、凹曲線の輪郭までの三角形だけを出すため角は透明になる。
                VertexConsumer gateway = buffers.getBuffer(RenderType.endGateway());
                for (int i = 0; i < SEGMENTS; i++) {
                    double a = Math.PI * 2.0D * i / SEGMENTS;
                    double b = Math.PI * 2.0D * (i + 1) / SEGMENTS;
                    float ax = (float) YamatoRiftGeometry.pointX(a);
                    float ay = (float) YamatoRiftGeometry.pointY(a);
                    float bx = (float) YamatoRiftGeometry.pointX(b);
                    float by = (float) YamatoRiftGeometry.pointY(b);
                    fanTriangle(gateway, matrix, ax, ay, bx, by, FACE_OFFSET);
                    fanTriangle(gateway, matrix, bx, by, ax, ay, -FACE_OFFSET);
                }

                // 細い縁も同じ輪郭を追う。角付きの四角形や別の渦テクスチャは描画しない。
                VertexConsumer edge = buffers.getBuffer(RenderType.debugQuads());
                for (int i = 0; i < SEGMENTS; i++) {
                    double a = Math.PI * 2.0D * i / SEGMENTS;
                    double b = Math.PI * 2.0D * (i + 1) / SEGMENTS;
                    edgeStrip(edge, matrix, a, b, 0.992F, 1.018F, FACE_OFFSET + 0.001F, 150);
                    edgeStrip(edge, matrix, a, b, 0.992F, 1.018F, -FACE_OFFSET - 0.001F, 150);
                }
                rendered = true;
            } finally {
                pose.popPose();
            }
        }
        if (rendered) {
            buffers.endBatch(RenderType.endGateway());
            buffers.endBatch(RenderType.debugQuads());
        }
    }

    /** 原版のQUADSバッファで三角形を描く。4頂点目は中心に戻し、余分な角を作らない。 */
    private static void fanTriangle(VertexConsumer vertices, Matrix4f pose,
                                    float ax, float ay, float bx, float by, float z) {
        vertices.addVertex(pose, 0.0F, 0.0F, z);
        vertices.addVertex(pose, ax, ay, z);
        vertices.addVertex(pose, bx, by, z);
        vertices.addVertex(pose, 0.0F, 0.0F, z);
    }

    private static void edgeStrip(VertexConsumer vertices, Matrix4f pose, double a, double b,
                                  float inner, float outer, float z, int alpha) {
        float ax = (float) YamatoRiftGeometry.pointX(a);
        float ay = (float) YamatoRiftGeometry.pointY(a);
        float bx = (float) YamatoRiftGeometry.pointX(b);
        float by = (float) YamatoRiftGeometry.pointY(b);
        vertices.addVertex(pose, ax * inner, ay * inner, z).setColor(190, 205, 255, alpha);
        vertices.addVertex(pose, ax * outer, ay * outer, z).setColor(190, 205, 255, alpha);
        vertices.addVertex(pose, bx * outer, by * outer, z).setColor(190, 205, 255, alpha);
        vertices.addVertex(pose, bx * inner, by * inner, z).setColor(190, 205, 255, alpha);
    }
}
