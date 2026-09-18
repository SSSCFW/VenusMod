package dev.ssscfw.venusmod.registry;

import com.mojang.serialization.Codec;
import dev.ssscfw.venusmod.VenusMod;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Storage BoxのItemStack内に保存・同期する専用Data Components。 */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, VenusMod.MOD_ID);

    public static final Supplier<DataComponentType<ItemStack>> STORAGE_BOX_TEMPLATE =
            DATA_COMPONENTS.registerComponentType("storage_box_template",
                    builder -> builder.persistent(ItemStack.SINGLE_ITEM_CODEC)
                            .networkSynchronized(ItemStack.STREAM_CODEC));

    public static final Supplier<DataComponentType<Integer>> STORAGE_BOX_COUNT =
            DATA_COMPONENTS.registerComponentType("storage_box_count",
                    builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final Supplier<DataComponentType<Boolean>> STORAGE_BOX_AUTO_COLLECT =
            DATA_COMPONENTS.registerComponentType("storage_box_auto_collect",
                    builder -> builder.persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL));

    private ModDataComponents() {}

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
    }
}
