package dev.ssscfw.venusmod.registry;

import dev.ssscfw.venusmod.VenusMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Dedicated creative tab for VenusMod content. */
public final class ModCreativeTabs {
    public static final ResourceKey<CreativeModeTab> VENUS_TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(VenusMod.MOD_ID, "venusmod"));

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VenusMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> VENUS_TAB =
            CREATIVE_MODE_TABS.register("venusmod", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.venusmod"))
                    .icon(() -> new ItemStack(ModItems.VENUS_ZOMBIE_SPAWN_EGG.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.DIAMOND_HOPPER.get());
                        output.accept(ModItems.NICKEL_ORE.get());
                        output.accept(ModItems.DEEPSLATE_NICKEL_ORE.get());
                        output.accept(ModItems.RAW_NICKEL.get());
                        output.accept(ModItems.NICKEL_INGOT.get());
                        output.accept(ModItems.NICKEL_NUGGET.get());
                        output.accept(ModItems.KING_TREASURY.get());
                        output.accept(ModItems.VENUS_ZOMBIE_SPAWN_EGG.get());
                        output.accept(ModItems.VENUS_SLASHBLADE_ZOMBIE_SPAWN_EGG.get());
                        output.accept(ModItems.VENUS_SKELETON_SPAWN_EGG.get());
                        output.accept(ModItems.VENUS_CREEPER_SPAWN_EGG.get());
                        output.accept(ModItems.VENUS_SPIDER_SPAWN_EGG.get());
                        output.accept(ModItems.VENUS_ENDERMAN_SPAWN_EGG.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
