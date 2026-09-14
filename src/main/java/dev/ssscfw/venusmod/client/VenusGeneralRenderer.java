package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.entity.VenusGeneral;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/** 金星将軍専用。VenusZombieModelを使うことで刀のVMD/フォールバック斬撃姿勢を描画する。 */
public final class VenusGeneralRenderer extends AbstractZombieRenderer<VenusGeneral, VenusZombieModel<VenusGeneral>> {
    private static final ResourceLocation TEXTURE =
            VenusDimensionContent.id("textures/entity/venus_zombie.png");

    public VenusGeneralRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new VenusZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),
                new VenusZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new VenusZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return TEXTURE;
    }
}
