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
 * 機械アイテムの説明がBlockItem側で追加されない場合も、クライアントの
 * ツールチップ生成イベントで用途と操作方法を確実に補う。
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

    private MachineTooltipEvents() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!VenusMod.MOD_ID.equals(id.getNamespace()) || !MACHINE_IDS.contains(id.getPath())) {
            return;
        }

        String prefix = "tooltip.venusmod.machine." + id.getPath();
        List<Component> lines = event.getToolTip();
        // appendHoverText() が先に追加した場合は重複させない。
        insertIfMissing(lines, 1, Component.translatable(prefix + ".description")
                .withStyle(ChatFormatting.GRAY));
        insertIfMissing(lines, 2, Component.translatable(prefix + ".usage")
                .withStyle(ChatFormatting.DARK_GRAY));
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
