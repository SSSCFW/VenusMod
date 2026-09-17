package dev.ssscfw.venusmod.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.item.KingTreasuryItem;
import dev.ssscfw.venusmod.treasury.GiveCaptureRules;
import dev.ssscfw.venusmod.treasury.KingsTreasuryMenu;
import dev.ssscfw.venusmod.treasury.KingsTreasurySavedData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 王の宝物庫への自動回収。
 * 地面で拾った刀と、/giveコマンドによって実際に増えた刀だけを対象にし、既存Inventory全走査は行わない。
 */
public final class KingsTreasuryEvents {
    private static final Map<UUID, List<PendingGive>> PENDING_GIVES = new HashMap<>();

    private KingsTreasuryEvents() {}

    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || event.canPickup() == TriState.FALSE) return;

        ItemEntity itemEntity = event.getItemEntity();
        if (itemEntity.hasPickUpDelay()) return;
        UUID target = itemEntity.getTarget();
        if (target != null && !target.equals(player.getUUID())) return;

        ItemStack liveStack = itemEntity.getItem();
        if (liveStack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(liveStack)) return;
        if (!KingTreasuryItem.hasTreasury(player)) return;

        KingsTreasurySavedData storage = KingsTreasurySavedData.get(player.serverLevel());
        if (!storage.autoCollect(player.getUUID())) return;

        int accepted = storage.insert(player.getUUID(), liveStack, liveStack.getCount());
        if (accepted <= 0) return;

        player.take(itemEntity, accepted);
        liveStack.shrink(accepted);
        if (liveStack.isEmpty()) itemEntity.discard();

        event.setCanPickup(TriState.FALSE);
        if (player.containerMenu instanceof KingsTreasuryMenu menu) menu.refreshFromStorage();
    }

    /**
     * Vanilla /giveはInventoryへ直接ItemStackを追加するためPickupEventを通らない。
     * 実行前に対象刀の所持数を記録し、次のPlayerTickで増分だけを宝物庫へ移す。
     */
    public static void onCommand(CommandEvent event) {
        CommandContextBuilder<CommandSourceStack> builder = event.getParseResults().getContext();
        if (!isDirectGive(builder)) return;
        if (!builder.getArguments().containsKey("targets") || !builder.getArguments().containsKey("item")) return;

        CommandContext<CommandSourceStack> context = builder.build(event.getParseResults().getReader().getString());
        try {
            ItemInput input = ItemArgument.getItem(context, "item");
            ItemStack template = input.createItemStack(1, false);
            if (template.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(template)) return;

            int count = builder.getArguments().containsKey("count")
                    ? IntegerArgumentType.getInteger(context, "count")
                    : 1;
            if (count <= 0) return;

            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
            for (ServerPlayer player : targets) {
                if (!KingTreasuryItem.hasTreasury(player)) continue;
                KingsTreasurySavedData storage = KingsTreasurySavedData.get(player.serverLevel());
                if (!storage.autoCollect(player.getUUID())) continue;

                int before = countMatching(player, template);
                PENDING_GIVES.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>())
                        .add(new PendingGive(template.copyWithCount(1), before, count));
            }
        } catch (CommandSyntaxException | IllegalArgumentException ignored) {
            // Vanilla側が通常どおりエラーを処理する。ここでは回収予約だけを作らない。
        }
    }

    /** /giveで増えた分だけを処理する。autoCollectをONにしただけでは既存の刀に触れない。 */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        List<PendingGive> pending = PENDING_GIVES.remove(player.getUUID());
        if (pending == null || pending.isEmpty()) return;
        if (!KingTreasuryItem.hasTreasury(player)) return;

        KingsTreasurySavedData storage = KingsTreasurySavedData.get(player.serverLevel());
        if (!storage.autoCollect(player.getUUID())) return;

        boolean changed = false;
        // 同一刀へ同tickに複数回/giveされた場合、後のsnapshotから先に戻すと各増分を正しく分離できる。
        for (int index = pending.size() - 1; index >= 0; index--) {
            PendingGive give = pending.get(index);
            int after = countMatching(player, give.template());
            int toCapture = GiveCaptureRules.newlyAdded(give.beforeCount(), after, give.commandCount());
            if (toCapture <= 0) continue;
            changed |= captureMatching(player, storage, give.template(), toCapture) > 0;
        }

        if (!changed) return;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        if (player.containerMenu instanceof KingsTreasuryMenu menu) menu.refreshFromStorage();
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_GIVES.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_GIVES.clear();
    }

    private static boolean isDirectGive(CommandContextBuilder<CommandSourceStack> builder) {
        if (builder.getNodes().isEmpty()) return false;
        String name = builder.getNodes().getFirst().getNode().getName();
        return "give".equals(name) || "minecraft:give".equals(name);
    }

    private static int countMatching(ServerPlayer player, ItemStack template) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, template)) total += stack.getCount();
        }
        return total;
    }

    private static int captureMatching(ServerPlayer player, KingsTreasurySavedData storage,
                                       ItemStack template, int requested) {
        int remaining = Math.max(0, requested);
        int captured = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) continue;

            int accepted = storage.insert(player.getUUID(), stack, Math.min(remaining, stack.getCount()));
            if (accepted <= 0) continue;
            stack.shrink(accepted);
            if (stack.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
            remaining -= accepted;
            captured += accepted;
        }
        return captured;
    }

    private record PendingGive(ItemStack template, int beforeCount, int commandCount) {}
}
