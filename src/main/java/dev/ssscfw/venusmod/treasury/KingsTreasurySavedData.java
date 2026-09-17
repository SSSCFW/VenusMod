package dev.ssscfw.venusmod.treasury;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.compat.SlashBladeTreasuryCompat;
import dev.ssscfw.venusmod.treasure.TreasureRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** プレイヤーUUIDごとの宝物庫。代表ItemStack 1本と論理count、スロットのお気に入りを保存する。 */
public final class KingsTreasurySavedData extends SavedData {
    private static final String DATA_NAME = "venusmod_kings_treasury";
    private final Map<UUID, PlayerTreasury> players = new HashMap<>();

    public static KingsTreasurySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(KingsTreasurySavedData::new, KingsTreasurySavedData::load, null),
                DATA_NAME);
    }

    public int insert(UUID playerId, ItemStack stack, int requested) {
        return insert(playerId, stack, requested, null);
    }

    /** Shift取り出し失敗時のロールバックではお気に入りも元どおりにする。 */
    int restoreOne(UUID playerId, ItemStack stack, boolean favorite) {
        return insert(playerId, stack, 1, favorite);
    }

    private int insert(UUID playerId, ItemStack stack, int requested, Boolean favorite) {
        if (stack == null || stack.isEmpty() || requested <= 0 || !SlashBladeEnchantmentCompat.isBlade(stack)) {
            return 0;
        }
        PlayerTreasury treasury = treasury(playerId);
        int remaining = Math.min(Math.max(0, requested), stack.getCount());
        int acceptedTotal = 0;
        for (StoredEntry entry : treasury.entries) {
            if (remaining <= 0) break;
            if (favorite != null && entry.favorite != favorite) continue;
            if (!ItemStack.isSameItemSameComponents(entry.template, stack)) continue;
            int accepted = TreasuryRules.acceptedIntoStack(entry.count, remaining);
            if (accepted <= 0) continue;
            entry.count += accepted;
            remaining -= accepted;
            acceptedTotal += accepted;
        }
        while (remaining > 0 && TreasuryRules.canCreateStack(treasury.entries.size())) {
            int accepted = TreasuryRules.acceptedIntoStack(0, remaining);
            if (accepted <= 0) break;
            treasury.entries.add(new StoredEntry(stack, accepted, Boolean.TRUE.equals(favorite)));
            remaining -= accepted;
            acceptedTotal += accepted;
        }
        if (acceptedTotal > 0) setDirty();
        return acceptedTotal;
    }

    public ItemStack extractOne(UUID playerId, int entryIndex) {
        PlayerTreasury treasury = players.get(playerId);
        if (treasury == null || entryIndex < 0 || entryIndex >= treasury.entries.size()) return ItemStack.EMPTY;
        StoredEntry entry = treasury.entries.get(entryIndex);
        ItemStack result = entry.template.copyWithCount(1);
        entry.count--;
        if (entry.count <= 0) treasury.entries.remove(entryIndex);
        setDirty();
        return result;
    }

    /** スロットの中身が変わっていた場合、古い画面から別の刀を保護/解除しない。 */
    public boolean toggleFavorite(UUID playerId, int entryIndex, ItemStack expected) {
        PlayerTreasury treasury = players.get(playerId);
        if (treasury == null || entryIndex < 0 || entryIndex >= treasury.entries.size()) return false;
        StoredEntry entry = treasury.entries.get(entryIndex);
        if (expected == null || expected.isEmpty()
                || !ItemStack.isSameItemSameComponents(entry.template, expected)) return false;
        entry.favorite = !entry.favorite;
        setDirty();
        return true;
    }

    public VolleyPriority volleyPriority(UUID playerId) {
        PlayerTreasury treasury = players.get(playerId);
        return treasury == null ? VolleyPriority.RANDOM : treasury.volleyPriority;
    }

    public void setVolleyPriority(UUID playerId, VolleyPriority priority) {
        if (priority == null) return;
        PlayerTreasury treasury = treasury(playerId);
        if (treasury.volleyPriority != priority) {
            treasury.volleyPriority = priority;
            setDirty();
        }
    }

    /** 全ページから未破損・未保護の刀だけを、プレイヤー指定の優先度で選ぶ。 */
    public List<ItemStack> selectForVolley(UUID playerId, int limit, IntUnaryOperator nextInt) {
        PlayerTreasury treasury = players.get(playerId);
        if (treasury == null) return List.of();
        List<TreasuryVolleyRules.Candidate> candidates = new ArrayList<>(treasury.entries.size());
        for (int i = 0; i < treasury.entries.size(); i++) {
            StoredEntry entry = treasury.entries.get(i);
            int remaining = entry.favorite ? -1 : SlashBladeTreasuryCompat.remainingDurability(entry.template);
            candidates.add(new TreasuryVolleyRules.Candidate(
                    i, entry.count, Math.max(0, remaining), remaining <= 0, entry.favorite));
        }
        return TreasuryVolleyRules.select(candidates, limit, treasury.volleyPriority, nextInt).stream()
                .map(index -> treasury.entries.get(index).template.copyWithCount(1)).toList();
    }

    /**
     * 射出直前にも保護/破損状態を再検査する。準備後にお気に入りへ変更した刀は取り出さない。
     * 1本でも不足すればA仕様どおり全体を中止し、部分消費は行わない。
     */
    public boolean consumeAll(UUID playerId, List<ItemStack> requested) {
        if (requested == null || requested.isEmpty()) return false;
        PlayerTreasury treasury = players.get(playerId);
        if (treasury == null || treasury.entries.isEmpty()) return false;
        List<ItemStack> templates = new ArrayList<>(treasury.entries.size());
        int[] counts = new int[treasury.entries.size()];
        for (int i = 0; i < treasury.entries.size(); i++) {
            StoredEntry entry = treasury.entries.get(i);
            templates.add(entry.template);
            counts[i] = !entry.favorite && SlashBladeTreasuryCompat.canLaunch(entry.template) ? entry.count : 0;
        }
        int[] consumption = TreasureRules.planConsumption(
                templates, counts, requested, ItemStack::isSameItemSameComponents);
        if (consumption == null) return false;
        for (int i = 0; i < consumption.length; i++) treasury.entries.get(i).count -= consumption[i];
        treasury.entries.removeIf(entry -> entry.count <= 0);
        setDirty();
        return true;
    }

    public List<TreasuryEntry> page(UUID playerId, int page, int pageSize) {
        PlayerTreasury treasury = players.get(playerId);
        if (treasury == null || pageSize <= 0) return List.of();
        long startLong = (long) Math.max(0, page) * pageSize;
        if (startLong >= treasury.entries.size()) return List.of();
        int start = (int) startLong;
        int end = (int) Math.min(treasury.entries.size(), startLong + pageSize);
        List<TreasuryEntry> result = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            StoredEntry entry = treasury.entries.get(i);
            result.add(new TreasuryEntry(entry.template, entry.count, entry.favorite));
        }
        return List.copyOf(result);
    }

    public int totalCount(UUID playerId) { return entryCount(playerId); }

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

    public int volleyLimit(UUID playerId) {
        PlayerTreasury treasury = players.get(playerId);
        return treasury == null ? TreasureRules.DEFAULT_VOLLEY_LIMIT
                : TreasureRules.normalizeVolleyLimit(treasury.volleyLimit);
    }

    public int cycleVolleyLimit(UUID playerId) {
        PlayerTreasury treasury = treasury(playerId);
        treasury.volleyLimit = TreasureRules.nextVolleyLimit(treasury.volleyLimit);
        setDirty();
        return treasury.volleyLimit;
    }

    private PlayerTreasury treasury(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new PlayerTreasury());
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
            treasury.volleyPriority = VolleyPriority.fromId(playerTag.getString("VolleyPriority"));
            if (playerTag.contains("VolleyLimit", Tag.TAG_INT)) {
                treasury.volleyLimit = TreasureRules.normalizeVolleyLimit(playerTag.getInt("VolleyLimit"));
            }
            ListTag entries = playerTag.getList("Entries", Tag.TAG_COMPOUND);
            for (int entryIndex = 0;
                 entryIndex < entries.size() && TreasuryRules.canCreateStack(treasury.entries.size());
                 entryIndex++) {
                CompoundTag entryTag = entries.getCompound(entryIndex);
                ItemStack stack = ItemStack.parseOptional(registries, entryTag.getCompound("Stack"));
                if (stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack)) continue;
                int count = Math.max(1, Math.min(TreasuryRules.MAX_LOGICAL_STACK, entryTag.getInt("Count")));
                treasury.entries.add(new StoredEntry(stack, count, entryTag.getBoolean("Favorite")));
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
            playerTag.putInt("VolleyLimit", TreasureRules.normalizeVolleyLimit(treasury.volleyLimit));
            playerTag.putString("VolleyPriority", treasury.volleyPriority.id());
            ListTag entries = new ListTag();
            for (StoredEntry entry : treasury.entries) {
                if (entry.template.isEmpty() || entry.count <= 0) continue;
                CompoundTag entryTag = new CompoundTag();
                entryTag.put("Stack", entry.template.copyWithCount(1).save(registries));
                entryTag.putInt("Count", Math.min(TreasuryRules.MAX_LOGICAL_STACK, entry.count));
                entryTag.putBoolean("Favorite", entry.favorite);
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
        private int volleyLimit = TreasureRules.DEFAULT_VOLLEY_LIMIT;
        private VolleyPriority volleyPriority = VolleyPriority.RANDOM;
    }

    private static final class StoredEntry {
        private final ItemStack template;
        private int count;
        private boolean favorite;

        private StoredEntry(ItemStack template, int count, boolean favorite) {
            this.template = template.copyWithCount(1);
            this.count = count;
            this.favorite = favorite;
        }
    }
}
