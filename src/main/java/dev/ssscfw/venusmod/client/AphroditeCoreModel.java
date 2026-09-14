package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.entity.AphroditeCore;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * アフロディーテ・コア専用モデル。
 * ブレイズの体型を完全に廃止し、浮遊する圧力炉コア＋装甲花弁＋二重回転リングとして描画する。
 */
public final class AphroditeCoreModel extends HierarchicalModel<AphroditeCore> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            VenusDimensionContent.id("aphrodite_core"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart coreFront;
    private final ModelPart coreBack;
    private final ModelPart panelNorth;
    private final ModelPart panelSouth;
    private final ModelPart panelEast;
    private final ModelPart panelWest;
    private final ModelPart panelTop;
    private final ModelPart panelBottom;
    private final ModelPart ringA;
    private final ModelPart ringB;

    public AphroditeCoreModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.coreFront = body.getChild("core_front");
        this.coreBack = body.getChild("core_back");
        this.panelNorth = body.getChild("panel_north");
        this.panelSouth = body.getChild("panel_south");
        this.panelEast = body.getChild("panel_east");
        this.panelWest = body.getChild("panel_west");
        this.panelTop = body.getChild("panel_top");
        this.panelBottom = body.getChild("panel_bottom");
        this.ringA = root.getChild("ring_a");
        this.ringB = root.getChild("ring_b");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-6.0F, -6.0F, -6.0F, 12.0F, 12.0F, 12.0F),
                PartPose.offset(0.0F, 12.0F, 0.0F));

        body.addOrReplaceChild(
                "core_front",
                CubeListBuilder.create()
                        .texOffs(0, 44)
                        .addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 2.0F),
                PartPose.offset(0.0F, 0.0F, -7.0F));
        body.addOrReplaceChild(
                "core_back",
                CubeListBuilder.create()
                        .texOffs(0, 44)
                        .addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.0F, 0.0F, (float) Math.PI, 0.0F));

        addPanel(body, "panel_north", 0.0F, 0.0F, -7.25F, 0.0F);
        addPanel(body, "panel_south", 0.0F, 0.0F, 7.25F, (float) Math.PI);
        addPanel(body, "panel_east", 7.25F, 0.0F, 0.0F, -(float) Math.PI / 2.0F);
        addPanel(body, "panel_west", -7.25F, 0.0F, 0.0F, (float) Math.PI / 2.0F);

        body.addOrReplaceChild(
                "panel_top",
                CubeListBuilder.create()
                        .texOffs(24, 28)
                        .addBox(-5.0F, -1.0F, -5.0F, 10.0F, 2.0F, 10.0F),
                PartPose.offset(0.0F, -7.25F, 0.0F));
        body.addOrReplaceChild(
                "panel_bottom",
                CubeListBuilder.create()
                        .texOffs(24, 28)
                        .addBox(-5.0F, -1.0F, -5.0F, 10.0F, 2.0F, 10.0F),
                PartPose.offset(0.0F, 7.25F, 0.0F));

        PartDefinition ringA = root.addOrReplaceChild(
                "ring_a", CubeListBuilder.create(), PartPose.offset(0.0F, 12.0F, 0.0F));
        addRingStruts(ringA);

        PartDefinition ringB = root.addOrReplaceChild(
                "ring_b", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, (float) Math.PI / 2.0F, 0.0F, 0.0F));
        addRingStruts(ringB);

        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void addPanel(PartDefinition body, String name, float x, float y, float z, float yRot) {
        body.addOrReplaceChild(
                name,
                CubeListBuilder.create()
                        .texOffs(0, 28)
                        .addBox(-7.0F, -5.0F, -1.0F, 14.0F, 10.0F, 2.0F),
                PartPose.offsetAndRotation(x, y, z, 0.0F, yRot, 0.0F));
    }

    private static void addRingStruts(PartDefinition ring) {
        for (int i = 0; i < 4; i++) {
            ring.addOrReplaceChild(
                    "strut_" + i,
                    CubeListBuilder.create()
                            .texOffs(24, 42)
                            .addBox(-1.0F, -1.0F, -13.0F, 2.0F, 2.0F, 6.0F),
                    PartPose.rotation(0.0F, i * ((float) Math.PI / 2.0F), 0.0F));
        }
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(AphroditeCore entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        float bob = Mth.sin(ageInTicks * 0.11F) * 0.65F;
        float pulse = (Mth.sin(ageInTicks * 0.18F) + 1.0F) * 0.5F;

        body.y = 12.0F + bob;
        body.xRot = Mth.sin(ageInTicks * 0.035F) * 0.04F;
        body.yRot = Mth.sin(ageInTicks * 0.03F) * 0.12F;
        body.zRot = Mth.cos(ageInTicks * 0.04F) * 0.035F;

        panelNorth.z = -7.25F - pulse * 0.85F;
        panelSouth.z = 7.25F + pulse * 0.85F;
        panelEast.x = 7.25F + pulse * 0.85F;
        panelWest.x = -7.25F - pulse * 0.85F;
        panelTop.y = -7.25F - pulse * 0.55F;
        panelBottom.y = 7.25F + pulse * 0.55F;

        coreFront.z = -7.0F - pulse * 0.20F;
        coreBack.z = 7.0F + pulse * 0.20F;

        ringA.y = 12.0F + bob;
        ringA.xRot = 0.24F;
        ringA.yRot = ageInTicks * 0.065F;
        ringA.zRot = 0.12F;

        ringB.y = 12.0F + bob;
        ringB.xRot = 1.18F;
        ringB.yRot = -ageInTicks * 0.052F;
        ringB.zRot = ageInTicks * 0.028F;
    }
}
