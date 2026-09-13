package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.world.VenusDimensionContent;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

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
}
