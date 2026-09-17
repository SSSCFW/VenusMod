package dev.ssscfw.venusmod.treasury;

import dev.ssscfw.venusmod.treasure.TreasureRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Minecraft非依存の射出選択。耐久順は「残り耐久ポイント」を比較する。 */
public final class TreasuryVolleyRules {
    public record Candidate(int index, int count, int remainingDurability, boolean broken, boolean favorite) {
        public boolean eligible() { return count > 0 && !broken && !favorite; }
    }

    private TreasuryVolleyRules() {}

    /** 在庫を変更せず、選択した元スロット番号を1本ごとに返す。 */
    public static List<Integer> select(List<Candidate> candidates, int requested,
                                       VolleyPriority priority, IntUnaryOperator nextInt) {
        Objects.requireNonNull(candidates);
        Objects.requireNonNull(priority);
        int limit = Math.max(0, Math.min(TreasureRules.MAX_BLADES, requested));
        List<Candidate> eligible = candidates.stream().filter(Candidate::eligible).toList();
        List<Integer> result = new ArrayList<>(limit);
        if (limit == 0 || eligible.isEmpty()) return List.of();

        if (priority != VolleyPriority.RANDOM) {
            Comparator<Candidate> order = Comparator.comparingInt(Candidate::remainingDurability);
            if (priority == VolleyPriority.DURABILITY_HIGH) order = order.reversed();
            List<Candidate> sorted = eligible.stream().sorted(order.thenComparingInt(Candidate::index)).toList();
            for (Candidate candidate : sorted) {
                int count = Math.min(candidate.count(), limit - result.size());
                for (int i = 0; i < count; i++) result.add(candidate.index());
                if (result.size() == limit) break;
            }
        } else {
            Objects.requireNonNull(nextInt);
            // 10000×1028本をItemStackへ展開せず、論理本数を重みにして非復元抽出する。
            int[] remaining = eligible.stream().mapToInt(Candidate::count).toArray();
            int total = 0;
            for (int count : remaining) total = Math.addExact(total, count);
            while (result.size() < limit && total > 0) {
                int draw = nextInt.applyAsInt(total);
                if (draw < 0 || draw >= total) throw new IllegalArgumentException("Random index outside bound");
                for (int i = 0; i < remaining.length; i++) {
                    if (draw < remaining[i]) {
                        result.add(eligible.get(i).index());
                        remaining[i]--;
                        total--;
                        break;
                    }
                    draw -= remaining[i];
                }
            }
        }
        return List.copyOf(result);
    }
}
