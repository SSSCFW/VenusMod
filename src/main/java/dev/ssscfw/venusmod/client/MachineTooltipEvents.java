package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.VenusMod;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 機械・携帯生命維持装置・耐圧防具の説明が各Item側で追加されない場合も、
 * クライアントのツールチップ生成イベントで表示を補う。
 */
@EventBusSubscriber(modid = VenusMod.MOD_ID, value = Dist.CLIENT)
public final class MachineTooltipEvents {
    private static final Set<String> MACHINE_IDS = Set.of(
            "diamond_hopper",
            "atmospheric_condenser",
            "metal_separator",
            "blade_repair_station",
            "blade_breaker",
            "venus_blade_forge");
    private static final Set<String> EQUIPMENT_IDS = Set.of(
            "portable_life_support",
            "pressure_helmet",
            "pressure_chestplate",
            "pressure_leggings",
            "pressure_boots");

    private MachineTooltipEvents() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!VenusMod.MOD_ID.equals(id.getNamespace())) {
            return;
        }

        boolean machine = MACHINE_IDS.contains(id.getPath());
        if (!machine && !EQUIPMENT_IDS.contains(id.getPath())) {
            return;
        }

        String prefix = "tooltip.venusmod."
                + (machine ? "machine." : "equipment.") + id.getPath();
        List<Component> lines = event.getToolTip();
        // appendHoverText() が先に追加した場合は重複させない。
        insertIfMissing(lines, 1, Component.translatable(prefix + ".description")
                .withStyle(ChatFormatting.GRAY));
        insertIfMissing(lines, 2, Component.translatable(prefix + ".usage")
                .withStyle(ChatFormatting.DARK_GRAY));
        if ("portable_life_support".equals(id.getPath())) {
            insertIfMissing(lines, 3, Component.translatable(prefix + ".details")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void insertIfMissing(List<Component> lines, int index, Component description) {
        for (Component line : lines) {
            if (line.getString().equals(description.getString())) {
                return;
            }
        }
        lines.add(Math.min(index, lines.size()), description);
    }
}
