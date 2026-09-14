package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.entity.AphroditeCore;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** ブレイズ描画を使わないアフロディーテ・コア専用レンダラー。 */
public final class AphroditeCoreRenderer extends MobRenderer<AphroditeCore, AphroditeCoreModel> {
    private static final ResourceLocation TEXTURE =
            VenusDimensionContent.id("textures/entity/aphrodite_core.png");

    public AphroditeCoreRenderer(EntityRendererProvider.Context context) {
        super(context, new AphroditeCoreModel(context.bakeLayer(AphroditeCoreModel.LAYER)), 0.85F);
    }

    @Override
    public ResourceLocation getTextureLocation(AphroditeCore entity) {
        return TEXTURE;
    }
}
