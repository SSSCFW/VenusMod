package dev.ssscfw.venusmod.registry;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.entity.VenusZombie;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VenusMod.MOD_ID);

    public static final DeferredItem<DeferredSpawnEggItem> VENUS_ZOMBIE_SPAWN_EGG =
            ITEMS.register("venus_zombie_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.VENUS_ZOMBIE, 0xD4AF37, 0xFFF2A6, new Item.Properties()));

    /**
     * Same entity type/class as VENUS_ZOMBIE, but ENTITY_DATA marks this spawned
     * instance as a SlashBlade wielder.
     */
    public static final DeferredItem<DeferredSpawnEggItem> VENUS_SLASHBLADE_ZOMBIE_SPAWN_EGG =
            ITEMS.register("venus_slashblade_zombie_spawn_egg",
                    () -> new DeferredSpawnEggItem(
                            ModEntities.VENUS_ZOMBIE,
                            0xD4AF37,
                            0x5C3A21,
                            bladeZombieEggProperties()));

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

    private static Item.Properties bladeZombieEggProperties() {
        CompoundTag entityData = new CompoundTag();
        entityData.putString("id", VenusMod.MOD_ID + ":venus_zombie");
        entityData.putBoolean(VenusZombie.NBT_SLASHBLADE_WIELDER, true);
        return new Item.Properties().component(DataComponents.ENTITY_DATA, CustomData.of(entityData));
    }

    public static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(VENUS_ZOMBIE_SPAWN_EGG);
            event.accept(VENUS_SLASHBLADE_ZOMBIE_SPAWN_EGG);
            event.accept(VENUS_SKELETON_SPAWN_EGG);
            event.accept(VENUS_CREEPER_SPAWN_EGG);
            event.accept(VENUS_SPIDER_SPAWN_EGG);
            event.accept(VENUS_ENDERMAN_SPAWN_EGG);
        }
    }
}
