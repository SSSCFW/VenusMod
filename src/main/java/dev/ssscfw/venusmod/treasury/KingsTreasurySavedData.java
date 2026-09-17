package dev.ssscfw.venusmod.treasury;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * プレイヤーUUIDごとの「王の宝物庫」。実ItemStackは1本だけ保持し、同一コンポーネントの刀を論理countでまとめる。
 */
public final class KingsTreasurySavedData extends SavedData {
    private static final String DATA_NAME = "venusmod_kings_treasury";
    private final Map<UUID, PlayerTreasury> players = new HashMap<>();

    public static KingsTreasurySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(KingsTreasurySavedData::new, KingsTreasurySavedData::load, null),
                DATA_NAME);
    }

    public int insert(UUID playerId, ItemStack stack, int requested) {
        if (stack == null || stack.isEmpty() || requested <= 0 || !SlashBladeEnchantmentCompat.isBlade(stack)) {
            return 0;
        }

        PlayerTreasury treasury = treasury(playerId);
        int total = totalCount(treasury);
        int remainingTotal = Math.max(0, TreasuryRules.MAX_TOTAL - total);
        int remaining = Math.min(Math.max(0, requested), stack.getCount());
        int acceptedTotal = 0;

        if (remaining <= 0 || remainingTotal <= 0) {
            return 0;
        }

        for (StoredEntry entry : treasury.entries) {
            if (remaining <= 0 || remainingTotal <= 0) break;
            if (!ItemStack.isSameItemSameComponents(entry.template, stack)) continue;

            int accepted = TreasuryRules.acceptedIntoStack(entry.count, remaining, remainingTotal);
            if (accepted <= 0) continue;
            entry.count += accepted;
            remaining -= accepted;
            remainingTotal -= accepted;
            acceptedTotal += accepted;
        }

        while (remaining > 0 && remainingTotal > 0) {
            int accepted = TreasuryRules.acceptedIntoStack(0, remaining, remainingTotal);
            if (accepted <= 0) break;
            treasury.entries.add(new StoredEntry(stack.copyWithCount(1), accepted));
            remaining -= accepted;
            remainingTotal -= accepted;
            acceptedTotal += accepted;
        }

        if (acceptedTotal > 0) setDirty();
        return acceptedTotal;
    }

    public ItemStack extractOne(UUID playerId, int entryIndex) {
        PlayerTreasury treasury = players.get(playerId);
        if (treasury == null || entryIndex < 0 || entryIndex >= treasury.entries.size()) {
            return ItemStack.EMPTY;
        }
        StoredEntry entry = treasury.entries.get(entryIndex);
        ItemStack result = entry.template.copyWithCount(1);
        entry.count--;
        if (entry.count <= 0) treasury.entries.remove(entryIndex);
        setDirty();
        return result;
    }

    public List<TreasuryEntry> page(UUID playerId, int page, int pageSize) {
        PlayerTreasury treasury = players.get(playerId);
        if (treasury == null || pageSize <= 0) return List.of();
        int safePage = Math.max(0, page);
        int start = safePage * pageSize;
        if (start >= treasury.entries.size()) return List.of();
        int end = Math.min(treasury.entries.size(), start + pageSize);
        List<TreasuryEntry> result = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            StoredEntry entry = treasury.entries.get(i);
            result.add(new TreasuryEntry(entry.template, entry.count));
        }
        return List.copyOf(result);
    }

    public int totalCount(UUID playerId) {
        PlayerTreasury treasury = players.get(playerId);
        return treasury == null ? 0 : totalCount(treasury);
    }

    public int entryCount(UUID playerId) {
        PlayerTreasury treasury = players.get(playerId);
        return treasury == null ? 0 : treasury.entries.size();
    }

    public int pageCount(UUID playerId, int pageSize) {
        int entries = entryCount(playerId);
        int size = Math.max(1, pageSize);
        return Math.max(1, (entries + size - 1) / size);
    }

    public boolean autoCollect(UUID playerId) {
        PlayerTreasury treasury = players.get(playerId);
        return treasury != null && treasury.autoCollect;
    }

    public boolean toggleAutoCollect(UUID playerId) {
        PlayerTreasury treasury = treasury(playerId);
        treasury.autoCollect = !treasury.autoCollect;
        setDirty();
        return treasury.autoCollect;
    }

    private PlayerTreasury treasury(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new PlayerTreasury());
    }

    private static int totalCount(PlayerTreasury treasury) {
        int total = 0;
        for (StoredEntry entry : treasury.entries) total += entry.count;
        return Math.min(TreasuryRules.MAX_TOTAL, total);
    }

    private static KingsTreasurySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        KingsTreasurySavedData data = new KingsTreasurySavedData();
        ListTag playerList = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag playerTag = playerList.getCompound(i);
            if (!playerTag.hasUUID("Player")) continue;

            UUID playerId = playerTag.getUUID("Player");
            PlayerTreasury treasury = new PlayerTreasury();
            treasury.autoCollect = playerTag.getBoolean("AutoCollect");

            int total = 0;
            ListTag entries = playerTag.getList("Entries", Tag.TAG_COMPOUND);
            for (int entryIndex = 0; entryIndex < entries.size() && total < TreasuryRules.MAX_TOTAL; entryIndex++) {
                CompoundTag entryTag = entries.getCompound(entryIndex);
                ItemStack stack = ItemStack.parseOptional(registries, entryTag.getCompound("Stack"));
                if (stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack)) continue;

                int count = Math.max(1, Math.min(TreasuryRules.MAX_LOGICAL_STACK, entryTag.getInt("Count")));
                count = Math.min(count, TreasuryRules.MAX_TOTAL - total);
                if (count <= 0) break;
                treasury.entries.add(new StoredEntry(stack.copyWithCount(1), count));
                total += count;
            }
            data.players.put(playerId, treasury);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playerList = new ListTag();
        players.forEach((playerId, treasury) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Player", playerId);
            playerTag.putBoolean("AutoCollect", treasury.autoCollect);
            ListTag entries = new ListTag();
            for (StoredEntry entry : treasury.entries) {
                if (entry.template.isEmpty() || entry.count <= 0) continue;
                CompoundTag entryTag = new CompoundTag();
                entryTag.put("Stack", entry.template.copyWithCount(1).save(registries));
                entryTag.putInt("Count", Math.min(TreasuryRules.MAX_LOGICAL_STACK, entry.count));
                entries.add(entryTag);
            }
            playerTag.put("Entries", entries);
            playerList.add(playerTag);
        });
        tag.put("Players", playerList);
        return tag;
    }

    private static final class PlayerTreasury {
        private final List<StoredEntry> entries = new ArrayList<>();
        private boolean autoCollect;
    }

    private static final class StoredEntry {
        private final ItemStack template;
        private int count;

        private StoredEntry(ItemStack template, int count) {
            this.template = template.copyWithCount(1);
            this.count = count;
        }
    }
}
