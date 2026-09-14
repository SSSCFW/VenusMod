package dev.ssscfw.venusmod.compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ssscfw.venusmod.compat.create.AbstractBladeMachineBlockEntity;
import dev.ssscfw.venusmod.compat.create.BladeMachineBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Renders the actual SlashBlade resting in the machine's open work chamber. */
public final class BladeMachineRenderer<T extends AbstractBladeMachineBlockEntity>
        implements BlockEntityRenderer<T> {
    private final ItemRenderer itemRenderer;

    public BladeMachineRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            T machine,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {

        ItemStack blade = machine.getDisplayedBlade();
        if (blade.isEmpty()) {
            return;
        }

        Direction facing = machine.getBlockState().hasProperty(BladeMachineBlock.FACING)
                ? machine.getBlockState().getValue(BladeMachineBlock.FACING)
                : Direction.NORTH;

        float facingRotation = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.57D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(facingRotation + 45.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(0.72F, 0.72F, 0.72F);

        itemRenderer.renderStatic(
                blade,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                buffer,
                machine.getLevel(),
                0);
        poseStack.popPose();
    }
}
