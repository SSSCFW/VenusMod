package dev.ssscfw.venusmod.event;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.item.KingTreasuryItem;
import dev.ssscfw.venusmod.treasury.GiveDeliveryRules;
import dev.ssscfw.venusmod.treasury.KingsTreasuryMenu;
import dev.ssscfw.venusmod.treasury.KingsTreasurySavedData;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** 地面の拾得と、実際に実行された/giveの新規生成物だけを回収する。既存Inventoryは探索しない。 */
public final class KingsTreasuryEvents {
    private KingsTreasuryEvents() {}

    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || event.canPickup() == TriState.FALSE) return;
        ItemEntity itemEntity = event.getItemEntity();
        if (itemEntity.hasPickUpDelay()) return;
        UUID target = itemEntity.getTarget();
        if (target != null && !target.equals(player.getUUID())) return;
        ItemStack liveStack = itemEntity.getItem();
        if (liveStack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(liveStack) || !collects(player)) return;
        KingsTreasurySavedData storage = KingsTreasurySavedData.get(player.serverLevel());
        int accepted = storage.insert(player.getUUID(), liveStack, liveStack.getCount());
        if (accepted <= 0) return;
        player.take(itemEntity, accepted);
        liveStack.shrink(accepted);
        if (liveStack.isEmpty()) itemEntity.discard();
        event.setCanPickup(TriState.FALSE);
        if (player.containerMenu instanceof KingsTreasuryMenu menu) menu.refreshFromStorage();
    }

    /**
     * 実行前の予約・次tickの全Component一致比較は廃止。
     * /executeの子コンテキストもたどり、実行されるgiveコールバックだけを包む。
     * 権限・構文・execute条件は既存のBrigadier実行経路に任せる。解析時には入出庫しない。
     */
    public static void onCommand(CommandEvent event) {
        if (event.isCanceled()) return;
        for (CommandContextBuilder<CommandSourceStack> context = event.getParseResults().getContext();
             context != null; context = context.getChild()) {
            if (context.getCommand() == null || context.getCommand() instanceof CapturingGive) continue;
            if (context.getNodes().isEmpty()) continue;
            String root = context.getNodes().getFirst().getNode().getName();
            if (!"give".equals(root) && !"minecraft:give".equals(root)) continue;
            if (!context.getArguments().containsKey("targets") || !context.getArguments().containsKey("item")) continue;
            context.withCommand(new CapturingGive(context.getCommand(), context.getArguments().containsKey("count")));
        }
    }

    private record CapturingGive(Command<CommandSourceStack> original, boolean hasCount)
            implements Command<CommandSourceStack> {
        @Override public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
            ItemStack prototype = ItemArgument.getItem(context, "item").createItemStack(1, false);
            int count = hasCount ? IntegerArgumentType.getInteger(context, "count") : 1;
            if (!SlashBladeEnchantmentCompat.isBlade(prototype)
                    || !GiveDeliveryRules.validCount(count, prototype.getMaxStackSize())) return original.run(context);
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
            if (targets.stream().noneMatch(KingsTreasuryEvents::collects)) return original.run(context);

            for (ServerPlayer player : targets) {
                boolean autoCollect = collects(player);
                KingsTreasurySavedData storage = KingsTreasurySavedData.get(player.serverLevel());
                GiveDeliveryRules.deliver(count, prototype.getMaxStackSize(), prototype::copyWithCount, stack -> {
                    if (autoCollect) {
                        int accepted = storage.insert(player.getUUID(), stack, stack.getCount());
                        // SavedDataへの追加に成功した本数だけを引く。満杯分は通常の/giveと同じ宛先へ。
                        stack.shrink(accepted);
                    }
                    if (!stack.isEmpty()) player.getInventory().add(stack);
                    if (!stack.isEmpty()) {
                        ItemEntity dropped = player.drop(stack, false);
                        if (dropped != null) {
                            dropped.setNoPickUpDelay();
                            dropped.setTarget(player.getUUID());
                        }
                    }
                });
                player.getInventory().setChanged();
                if (player.containerMenu instanceof KingsTreasuryMenu menu) menu.refreshFromStorage();
                player.containerMenu.broadcastChanges();
            }
            Component display = prototype.copyWithCount(count).getDisplayName();
            if (targets.size() == 1) {
                context.getSource().sendSuccess(() -> Component.translatable("commands.give.success.single",
                        count, display, targets.iterator().next().getDisplayName()), true);
            } else {
                context.getSource().sendSuccess(() -> Component.translatable("commands.give.success.multiple",
                        count, display, targets.size()), true);
            }
            return targets.size();
        }
    }

    private static boolean collects(ServerPlayer player) {
        return KingTreasuryItem.hasTreasury(player)
                && KingsTreasurySavedData.get(player.serverLevel()).autoCollect(player.getUUID());
    }

    // 既存のイベント登録と互換を保つ。tickでの吸収・回収予約は一切行わない。
    public static void onPlayerTick(PlayerTickEvent.Post event) {}
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {}
    public static void onServerStopped(ServerStoppedEvent event) {}
}
