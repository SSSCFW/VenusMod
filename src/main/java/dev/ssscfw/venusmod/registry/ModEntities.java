package dev.ssscfw.venusmod.registry;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.entity.VenusArrow;
import dev.ssscfw.venusmod.entity.VenusCreeper;
import dev.ssscfw.venusmod.entity.VenusEnderman;
import dev.ssscfw.venusmod.entity.VenusSkeleton;
import dev.ssscfw.venusmod.entity.VenusSpider;
import dev.ssscfw.venusmod.entity.VenusZombie;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, VenusMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<VenusZombie>> VENUS_ZOMBIE =
            ENTITY_TYPES.register("venus_zombie", () ->
                    EntityType.Builder.of(VenusZombie::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(VenusMod.MOD_ID + ":venus_zombie"));

    public static final DeferredHolder<EntityType<?>, EntityType<VenusSkeleton>> VENUS_SKELETON =
            ENTITY_TYPES.register("venus_skeleton", () ->
                    EntityType.Builder.of(VenusSkeleton::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.99F)
                            .clientTrackingRange(8)
                            .build(VenusMod.MOD_ID + ":venus_skeleton"));

    public static final DeferredHolder<EntityType<?>, EntityType<VenusCreeper>> VENUS_CREEPER =
            ENTITY_TYPES.register("venus_creeper", () ->
                    EntityType.Builder.of(VenusCreeper::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.7F)
                            .clientTrackingRange(8)
                            .build(VenusMod.MOD_ID + ":venus_creeper"));

    public static final DeferredHolder<EntityType<?>, EntityType<VenusSpider>> VENUS_SPIDER =
            ENTITY_TYPES.register("venus_spider", () ->
                    EntityType.Builder.of(VenusSpider::new, MobCategory.MONSTER)
                            .sized(1.4F, 0.9F)
                            .clientTrackingRange(8)
                            .build(VenusMod.MOD_ID + ":venus_spider"));

    public static final DeferredHolder<EntityType<?>, EntityType<VenusEnderman>> VENUS_ENDERMAN =
            ENTITY_TYPES.register("venus_enderman", () ->
                    EntityType.Builder.of(VenusEnderman::new, MobCategory.MONSTER)
                            .sized(0.6F, 2.9F)
                            .clientTrackingRange(8)
                            .build(VenusMod.MOD_ID + ":venus_enderman"));

    public static final DeferredHolder<EntityType<?>, EntityType<VenusArrow>> VENUS_ARROW =
            ENTITY_TYPES.register("venus_arrow", () ->
                    EntityType.Builder.<VenusArrow>of(VenusArrow::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build(VenusMod.MOD_ID + ":venus_arrow"));

    private ModEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(VENUS_ZOMBIE.get(), Zombie.createAttributes().build());
        event.put(VENUS_SKELETON.get(), AbstractSkeleton.createAttributes().build());
        event.put(VENUS_CREEPER.get(), Creeper.createAttributes().build());
        event.put(VENUS_SPIDER.get(), Spider.createAttributes().build());
        event.put(VENUS_ENDERMAN.get(), EnderMan.createAttributes().build());
    }
}
