package dev.ssscfw.venusmod.registry;

import dev.ssscfw.venusmod.VenusMod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VenusMod.MOD_ID);

    public static final DeferredItem<DeferredSpawnEggItem> VENUS_ZOMBIE_SPAWN_EGG =
            ITEMS.register("venus_zombie_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.VENUS_ZOMBIE, 0xD4AF37, 0xFFF2A6, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> VENUS_SKELETON_SPAWN_EGG =
            ITEMS.register("venus_skeleton_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.VENUS_SKELETON, 0xC89B3C, 0xFFF7CC, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> VENUS_CREEPER_SPAWN_EGG =
            ITEMS.register("venus_creeper_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.VENUS_CREEPER, 0xE0B93F, 0x8C6A13, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> VENUS_SPIDER_SPAWN_EGG =
            ITEMS.register("venus_spider_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.VENUS_SPIDER, 0xB8860B, 0xFFE066, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> VENUS_ENDERMAN_SPAWN_EGG =
            ITEMS.register("venus_enderman_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.VENUS_ENDERMAN, 0x9C7A18, 0xFFE680, new Item.Properties()));

    private ModItems() {
    }

    public static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(VENUS_ZOMBIE_SPAWN_EGG);
            event.accept(VENUS_SKELETON_SPAWN_EGG);
            event.accept(VENUS_CREEPER_SPAWN_EGG);
            event.accept(VENUS_SPIDER_SPAWN_EGG);
            event.accept(VENUS_ENDERMAN_SPAWN_EGG);
        }
    }
}
