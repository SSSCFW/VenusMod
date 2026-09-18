package dev.ssscfw.venusmod.treasury;

import dev.ssscfw.venusmod.compat.SlashBladeEnchantmentCompat;
import dev.ssscfw.venusmod.compat.SlashBladeTreasuryCompat;
import dev.ssscfw.venusmod.treasure.RoyalBladeEffectsRules;
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

/** UUID別の宝物庫。保護状態は代表ItemStack自身に保存し、取り出しても失われない。 */
public final class KingsTreasurySavedData extends SavedData {
    private static final String DATA_NAME = "venusmod_kings_treasury";
    private final Map<UUID, PlayerTreasury> players = new HashMap<>();

    public static KingsTreasurySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(KingsTreasurySavedData::new, KingsTreasurySavedData::load, null), DATA_NAME);
    }

    public int insert(UUID playerId, ItemStack stack, int requested) {
        if (stack == null || stack.isEmpty() || requested <= 0 || !SlashBladeEnchantmentCompat.isBlade(stack)) return 0;
        PlayerTreasury treasury = treasury(playerId);
        int remaining = Math.min(requested, stack.getCount());
        int acceptedTotal = 0;
        for (StoredEntry entry : treasury.entries) {
            if (remaining <= 0) break;
            // 保護設定もComponentの一部。通常/お気に入り/幻想禁止を混ぜない。
            if (!ItemStack.isSameItemSameComponents(entry.template, stack)) continue;
            int accepted = TreasuryRules.acceptedIntoStack(entry.count, remaining);
            entry.count += accepted;
            remaining -= accepted;
            acceptedTotal += accepted;
        }
        while (remaining > 0 && TreasuryRules.canCreateStack(treasury.entries.size())) {
            int accepted = TreasuryRules.acceptedIntoStack(0, remaining);
            if (accepted <= 0) break;
            treasury.entries.add(new StoredEntry(stack, accepted));
            remaining -= accepted;
            acceptedTotal += accepted;
        }
        if (acceptedTotal > 0) setDirty();
        return acceptedTotal;
    }

    /** インベントリ満杯時も取り出した刀を保護設定ごと戻す。 */
    int restoreOne(UUID playerId, ItemStack stack) { return insert(playerId, stack, 1); }
    int restoreOne(UUID playerId, ItemStack stack, boolean favorite) {
        return restoreOne(playerId, stack, favorite, false);
    }
    int restoreOne(UUID playerId, ItemStack stack, boolean favorite, boolean phantasmProtected) {
        ItemStack restored = stack.copyWithCount(1);
        TreasuryBladeProtection.migrate(restored, favorite, phantasmProtected);
        return insert(playerId, restored, 1);
    }

    public ItemStack extractOne(UUID playerId, int entryIndex) {
        StoredEntry entry = entry(playerId, entryIndex);
        if (entry == null) return ItemStack.EMPTY;
        ItemStack result = entry.template.copyWithCount(1);
        if (--entry.count <= 0) players.get(playerId).entries.remove(entryIndex);
        setDirty();
        return result;
    }

    private StoredEntry entry(UUID playerId, int index) {
        PlayerTreasury treasury = players.get(playerId);
        return treasury == null || index < 0 || index >= treasury.entries.size() ? null : treasury.entries.get(index);
    }

    private boolean matches(StoredEntry entry, ItemStack expected) {
        return entry != null && expected != null && !expected.isEmpty()
                && ItemStack.isSameItemSameComponents(entry.template, expected);
    }

    /** 既存GameTest/APIとの互換。GUIは3状態のcycleProtectionを使用する。 */
    public boolean toggleFavorite(UUID playerId, int entryIndex, ItemStack expected) {
        StoredEntry entry = entry(playerId, entryIndex);
        if (!matches(entry, expected)) return false;
        TreasuryBladeProtection.set(entry.template, entry.protection() == 1 ? 0 : 1);
        setDirty();
        return true;
    }

    public boolean cycleProtection(UUID playerId, int entryIndex, ItemStack expected) {
        StoredEntry entry = entry(playerId, entryIndex);
        if (!matches(entry, expected)) return false;
        TreasuryBladeProtection.cycle(entry.template);
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
        if (treasury.volleyPriority != priority) { treasury.volleyPriority = priority; setDirty(); }
    }

    public List<ItemStack> selectForVolley(UUID playerId, int limit, IntUnaryOperator nextInt) {
        return selectForVolley(playerId, limit, nextInt, false, List.of());
    }

    public List<ItemStack> selectForVolley(UUID playerId, int limit, IntUnaryOperator nextInt, boolean phantasm) {
        return selectForVolley(playerId, limit, nextInt, phantasm, List.of());
    }

    /**
     * 既に展開準備されている刀を論理予約として差し引いてから追加展開分を選ぶ。
     * 予約済みの刀が現在の宝物庫状態と一致しなくなった場合は安全側に倒して追加選択を止める。
     */
    public List<ItemStack> selectForVolley(UUID playerId, int limit, IntUnaryOperator nextInt,
                                           boolean phantasm, List<ItemStack> reserved) {
        PlayerTreasury treasury = players.get(playerId);
        if (treasury == null) return List.of();

        List<ItemStack> templates = new ArrayList<>(treasury.entries.size());
        int[] storedCounts = new int[treasury.entries.size()];
        for (int i = 0; i < treasury.entries.size(); i++) {
            StoredEntry entry = treasury.entries.get(i);
            templates.add(entry.template);
            storedCounts[i] = entry.count;
        }

        int[] reservedCounts = new int[treasury.entries.size()];
        if (reserved != null && !reserved.isEmpty()) {
            int[] planned = TreasureRules.planConsumption(
                    templates, storedCounts, reserved, ItemStack::isSameItemSameComponents);
            if (planned == null) return List.of();
            reservedCounts = planned;
        }

        List<TreasuryVolleyRules.Candidate> candidates = new ArrayList<>(treasury.entries.size());
        for (int i = 0; i < treasury.entries.size(); i++) {
            StoredEntry entry = treasury.entries.get(i);
            if (!RoyalBladeEffectsRules.eligible(entry.protection(), false, phantasm)) continue;
            int available = PreparedVolleyRules.remainingCount(entry.count, reservedCounts[i]);
            if (available <= 0) continue;
            int remaining = SlashBladeTreasuryCompat.remainingDurability(entry.template);
            if (remaining <= 0) continue;
            boolean rankOrder = treasury.volleyPriority == VolleyPriority.RANK_LOW
                    || treasury.volleyPriority == VolleyPriority.RANK_HIGH;
            candidates.add(new TreasuryVolleyRules.Candidate(i, available, remaining, false, false,
                    rankOrder ? SlashBladeTreasuryCompat.bladeRank(entry.template) : 0,
                    rankOrder ? SlashBladeTreasuryCompat.baseAttackModifier(entry.template) : 0.0F));
        }
        return TreasuryVolleyRules.select(candidates, limit, treasury.volleyPriority, nextInt).stream()
                .map(index -> treasury.entries.get(index).template.copyWithCount(1)).toList();
    }

    public boolean consumeAll(UUID playerId, List<ItemStack> requested) {
        return consumeAll(playerId, requested, false);
    }
    /** 射出直前に保護と在庫を再確認。1本でも不足/変更なら部分消費しない。 */
    public boolean consumeAll(UUID playerId, List<ItemStack> requested, boolean phantasm) {
        if (requested == null || requested.isEmpty()) return false;
        PlayerTreasury treasury = players.get(playerId);
        if (treasury == null || treasury.entries.isEmpty()) return false;
        List<ItemStack> templates = new ArrayList<>(treasury.entries.size());
        int[] counts = new int[treasury.entries.size()];
        for (int i = 0; i < treasury.entries.size(); i++) {
            StoredEntry entry = treasury.entries.get(i);
            templates.add(entry.template);
            counts[i] = RoyalBladeEffectsRules.eligible(entry.protection(),
                    !SlashBladeTreasuryCompat.canLaunch(entry.template), phantasm) ? entry.count : 0;
        }
        int[] consumption = TreasureRules.planConsumption(templates, counts, requested, ItemStack::isSameItemSameComponents);
        if (consumption == null) return false;
        for (int i = 0; i < consumption.length; i++) treasury.entries.get(i).count -= consumption[i];
        treasury.entries.removeIf(entry -> entry.count <= 0);
        setDirty();
        return true;
    }

    public List<TreasuryEntry> page(UUID playerId, int page, int pageSize) {
        PlayerTreasury treasury = players.get(playerId);
        if (treasury == null || pageSize <= 0) return List.of();
        long startLong = (long)Math.max(0, page) * pageSize;
        if (startLong >= treasury.entries.size()) return List.of();
        int end = (int)Math.min(treasury.entries.size(), startLong + pageSize);
        List<TreasuryEntry> result = new ArrayList<>();
        for (int i = (int)startLong; i < end; i++) {
            StoredEntry entry = treasury.entries.get(i);
            result.add(new TreasuryEntry(entry.template, entry.count));
        }
        return List.copyOf(result);
    }
    public int totalCount(UUID playerId) { return entryCount(playerId); }
    public int entryCount(UUID playerId) {
        PlayerTreasury treasury = players.get(playerId);
        return treasury == null ? 0 : treasury.entries.size();
    }
    public int pageCount(UUID playerId, int pageSize) {
        int size = Math.max(1, pageSize);
        return Math.max(1, (entryCount(playerId) + size - 1) / size);
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
        return treasury == null ? TreasureRules.DEFAULT_VOLLEY_LIMIT : TreasureRules.normalizeVolleyLimit(treasury.volleyLimit);
    }
    public int cycleVolleyLimit(UUID playerId) {
        PlayerTreasury treasury = treasury(playerId);
        treasury.volleyLimit = TreasureRules.nextVolleyLimit(treasury.volleyLimit);
        setDirty();
        return treasury.volleyLimit;
    }

    public int convergencePercent(UUID playerId) {
        PlayerTreasury treasury = players.get(playerId);
        return treasury == null ? TreasureRules.DEFAULT_CONVERGENCE_PERCENT
                : TreasureRules.normalizeConvergencePercent(treasury.convergencePercent);
    }

    public double convergence(UUID playerId) {
        return TreasureRules.convergenceFactor(convergencePercent(playerId));
    }

    public void setConvergencePercent(UUID playerId, int percent) {
        PlayerTreasury treasury = treasury(playerId);
        int normalized = TreasureRules.normalizeConvergencePercent(percent);
        if (treasury.convergencePercent != normalized) {
            treasury.convergencePercent = normalized;
            setDirty();
        }
    }

    public SummonPattern summonPattern(UUID playerId) {
        PlayerTreasury treasury = players.get(playerId);
        return treasury == null ? SummonPattern.DEFAULT : treasury.summonPattern;
    }

    public void setSummonPattern(UUID playerId, SummonPattern pattern) {
        if (pattern == null) return;
        PlayerTreasury treasury = treasury(playerId);
        if (treasury.summonPattern != pattern) {
            treasury.summonPattern = pattern;
            setDirty();
        }
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
            PlayerTreasury treasury = new PlayerTreasury();
            treasury.autoCollect = playerTag.getBoolean("AutoCollect");
            treasury.volleyPriority = VolleyPriority.fromId(playerTag.getString("VolleyPriority"));
            if (playerTag.contains("VolleyLimit", Tag.TAG_INT)) {
                treasury.volleyLimit = TreasureRules.normalizeVolleyLimit(playerTag.getInt("VolleyLimit"));
            }
            if (playerTag.contains("ConvergencePercent", Tag.TAG_INT)) {
                treasury.convergencePercent = TreasureRules.normalizeConvergencePercent(playerTag.getInt("ConvergencePercent"));
            }
            treasury.summonPattern = SummonPattern.fromId(playerTag.getString("SummonPattern"));
            ListTag entries = playerTag.getList("Entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < entries.size() && TreasuryRules.canCreateStack(treasury.entries.size()); j++) {
                CompoundTag entryTag = entries.getCompound(j);
                ItemStack stack = ItemStack.parseOptional(registries, entryTag.getCompound("Stack"));
                if (stack.isEmpty() || !SlashBladeEnchantmentCompat.isBlade(stack)) continue;
                TreasuryBladeProtection.migrate(stack, entryTag.getBoolean("Favorite"), entryTag.getBoolean("PhantasmProtected"));
                treasury.entries.add(new StoredEntry(stack, Math.max(1, Math.min(TreasuryRules.MAX_LOGICAL_STACK, entryTag.getInt("Count")))));
            }
            data.players.put(playerTag.getUUID("Player"), treasury);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playerList = new ListTag();
        players.forEach((id, treasury) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Player", id);
            playerTag.putBoolean("AutoCollect", treasury.autoCollect);
            playerTag.putInt("VolleyLimit", TreasureRules.normalizeVolleyLimit(treasury.volleyLimit));
            playerTag.putString("VolleyPriority", treasury.volleyPriority.id());
            playerTag.putInt("ConvergencePercent", TreasureRules.normalizeConvergencePercent(treasury.convergencePercent));
            playerTag.putString("SummonPattern", treasury.summonPattern.id());
            ListTag entries = new ListTag();
            for (StoredEntry entry : treasury.entries) {
                if (entry.template.isEmpty() || entry.count <= 0) continue;
                CompoundTag entryTag = new CompoundTag();
                entryTag.put("Stack", entry.template.copyWithCount(1).save(registries));
                entryTag.putInt("Count", Math.min(TreasuryRules.MAX_LOGICAL_STACK, entry.count));
                // 旧版向け互換値も、刀側の唯一の状態から生成する。
                entryTag.putBoolean("Favorite", entry.protection() == 1);
                entryTag.putBoolean("PhantasmProtected", entry.protection() == 2);
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
        private int convergencePercent = TreasureRules.DEFAULT_CONVERGENCE_PERCENT;
        private SummonPattern summonPattern = SummonPattern.DEFAULT;
    }
    private static final class StoredEntry {
        private final ItemStack template;
        private int count;
        private StoredEntry(ItemStack template, int count) { this.template = template.copyWithCount(1); this.count = count; }
        private int protection() { return TreasuryBladeProtection.get(template); }
    }
}
