package dev.ssscfw.venusmod.infrastructure.network.client;

import dev.ssscfw.venusmod.VenusMod;
import dev.ssscfw.venusmod.infrastructure.network.*;
import dev.ssscfw.venusmod.infrastructure.network.core.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.DeltaTracker;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** HUDだけをクライアントで描く。機器設定はサーバーから届いた情報以外を読まない。 */
@EventBusSubscriber(modid = VenusMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NetworkGogglesOverlay {
    private NetworkGogglesOverlay() {}

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(
                VenusMod.MOD_ID, "network_goggles"), NetworkGogglesOverlay::render);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("hud.venusmod.network_goggles." + key, args);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null
                || mc.options.hideGui || !NetworkContent.wearingNetworkGoggles(mc.player)
                || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;

        var pos = hit.getBlockPos();
        var block = mc.level.getBlockState(pos).getBlock();
        List<Component> lines = new ArrayList<>();
        if (block instanceof NetworkCableBlock cable) {
            lines.add(tr("cable", Component.translatable(block.getDescriptionId()))
                    .withStyle(ChatFormatting.AQUA));
            lines.add(tr("max_bandwidth", CableGrade.speed(cable.grade.bps())));
        } else if (block instanceof NetworkDeviceBlock device) {
            var view = NetworkGogglesStatus.recent(
                    mc.level.dimension().location().toString(), pos);
            if (view == null || view.kind() != device.kind.ordinal()) {
                lines.add(Component.translatable(block.getDescriptionId())
                        .withStyle(ChatFormatting.AQUA));
                lines.add(tr("loading").withStyle(ChatFormatting.GRAY));
            } else {
                String title = view.name().isBlank()
                        ? Component.translatable(block.getDescriptionId()).getString()
                        : view.name();
                lines.add(tr("device", title, tr(view.faulted() ? "faulted" : "working"))
                        .withStyle(view.faulted() ? ChatFormatting.RED : ChatFormatting.GREEN));
                int face = NetworkCableBlock.port(hit.getDirection());
                lines.add(tr("selected", "P" + face, tr("direction." + face))
                        .withStyle(ChatFormatting.YELLOW));
                if (!view.permitted()) {
                    lines.add(tr("denied").withStyle(ChatFormatting.RED));
                } else {
                    for (int i = 0; i < view.ports().size(); i++) {
                        var p = view.ports().get(i);
                        if (!p.visible()) continue;
                        Component link = tr(p.connected() ? (p.linkUp() ? "linked" : "down") : "unused");
                        String prefix = i == face ? "▶ " : "    ";
                        lines.add(tr("port", prefix + "P" + i, link,
                                p.cidr().isBlank() ? tr("no_ip") : p.cidr())
                                .withStyle(i == face ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
                        lines.add(tr("details", CableGrade.speed(p.maximumBps()),
                                p.enabled() ? tr("enabled") : tr("disabled"),
                                device.kind == DeviceKind.L3_SWITCH
                                        ? tr("vlan", p.vlan(), p.trunk() ? "Trunk" : "Access")
                                        : Component.empty())
                                .withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        } else {
            return;
        }
        if (lines.isEmpty()) return;
        int textWidth = 0;
        for (Component line : lines) textWidth = Math.max(textWidth, mc.font.width(line));
        int width = Math.min(textWidth + 16, mc.getWindow().getGuiScaledWidth() - 16);
        if (width <= 30) return;
        int x = 8, y = 42, height = lines.size() * 11 + 10;
        graphics.fill(x, y, x + width, y + height, 0xBD0C1520);
        graphics.fill(x, y, x + 3, y + height, 0xFF40ADB2);
        graphics.enableScissor(x + 4, y + 3, x + width - 4, y + height - 3);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(mc.font, lines.get(i), x + 9, y + 6 + i * 11, 0xFFF4F7F9, false);
        }
        graphics.disableScissor();
    }
}
