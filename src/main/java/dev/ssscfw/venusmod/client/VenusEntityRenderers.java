package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.compat.create.client.VenusCreateClientCompat;
import dev.ssscfw.venusmod.entity.VenusArrow;
import dev.ssscfw.venusmod.entity.VenusSkeleton;
import dev.ssscfw.venusmod.entity.VenusSpider;
import dev.ssscfw.venusmod.entity.VenusZombie;
import dev.ssscfw.venusmod.registry.ModEntities;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = VenusMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VenusEntityRenderers {
    private static final ResourceLocation ZOMBIE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "textures/entity/venus_zombie.png");
    private static final ResourceLocation SKELETON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "textures/entity/venus_skeleton.png");
    private static final ResourceLocation CREEPER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "textures/entity/venus_creeper.png");
    private static final ResourceLocation SPIDER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "textures/entity/venus_spider.png");
    private static final ResourceLocation ENDERMAN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "textures/entity/venus_enderman.png");
    private static final ResourceLocation ARROW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/projectiles/arrow.png");

    private VenusEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.VENUS_ZOMBIE.get(), GoldZombieRenderer::new);
        event.registerEntityRenderer(ModEntities.VENUS_SKELETON.get(), GoldSkeletonRenderer::new);
        event.registerEntityRenderer(ModEntities.VENUS_CREEPER.get(), GoldCreeperRenderer::new);
        event.registerEntityRenderer(ModEntities.VENUS_SPIDER.get(), GoldSpiderRenderer::new);
        event.registerEntityRenderer(ModEntities.VENUS_ENDERMAN.get(), GoldEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.VENUS_ARROW.get(), VenusArrowRenderer::new);

        if (ModList.get().isLoaded("create") && ModList.get().isLoaded("slashblade")) {
            VenusCreateClientCompat.registerRenderers(event);
        }
    }

    private static final class GoldZombieRenderer extends AbstractZombieRenderer<VenusZombie, VenusZombieModel> {
        private GoldZombieRenderer(EntityRendererProvider.Context context) {
            super(
                    context,
                    new VenusZombieModel(context.bakeLayer(ModelLayers.ZOMBIE)),
                    new VenusZombieModel(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                    new VenusZombieModel(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
        }

        @Override
        public ResourceLocation getTextureLocation(Zombie entity) {
            return ZOMBIE_TEXTURE;
        }
    }

    private static final class GoldSkeletonRenderer extends SkeletonRenderer<VenusSkeleton> {
        private GoldSkeletonRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(VenusSkeleton entity) {
            return SKELETON_TEXTURE;
        }
    }

    private static final class GoldCreeperRenderer extends CreeperRenderer {
        private GoldCreeperRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(Creeper entity) {
            return CREEPER_TEXTURE;
        }
    }

    private static final class GoldSpiderRenderer extends SpiderRenderer<VenusSpider> {
        private GoldSpiderRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(VenusSpider entity) {
            return SPIDER_TEXTURE;
        }
    }

    private static final class GoldEndermanRenderer extends EndermanRenderer {
        private GoldEndermanRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(EnderMan entity) {
            return ENDERMAN_TEXTURE;
        }
    }

    private static final class VenusArrowRenderer extends ArrowRenderer<VenusArrow> {
        private VenusArrowRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(VenusArrow entity) {
            return ARROW_TEXTURE;
        }
    }
}
