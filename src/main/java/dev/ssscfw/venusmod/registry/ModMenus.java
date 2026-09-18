package dev.ssscfw.venusmod.registry;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.storagebox.StorageBoxMenu;
import dev.ssscfw.venusmod.treasury.KingsTreasuryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, VenusMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<KingsTreasuryMenu>> KING_TREASURY =
            MENUS.register("king_treasury",
                    () -> new MenuType<>(KingsTreasuryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<StorageBoxMenu>> STORAGE_BOX =
            MENUS.register("storage_box",
                    () -> new MenuType<>(StorageBoxMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}
}
