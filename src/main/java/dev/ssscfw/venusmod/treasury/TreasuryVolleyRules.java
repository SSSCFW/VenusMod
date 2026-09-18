package dev.ssscfw.venusmod.treasury;

import dev.ssscfw.venusmod.treasure.TreasureRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Minecraft非依存の射出選択。ランク同値はベース攻撃力、残り耐久、スロット順で安定化する。 */
public final class TreasuryVolleyRules {
    public record Candidate(int index, int count, int remainingDurability, boolean broken,
                            boolean favorite, int rank, float baseDamage) {
        public Candidate(int index, int count, int durability, boolean broken, boolean favorite) {
            this(index, count, durability, broken, favorite, 1, 0.0F);
        }
        public Candidate {
            baseDamage = Float.isFinite(baseDamage) ? Math.max(0, baseDamage) : Float.MAX_VALUE;
        }
        public boolean eligible() { return count > 0 && !broken && !favorite; }
    }
    private TreasuryVolleyRules() {}

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
            if (priority == VolleyPriority.RANK_LOW) {
                order = Comparator.comparingInt(Candidate::rank)
                        .thenComparingDouble(Candidate::baseDamage)
                        .thenComparingInt(Candidate::remainingDurability);
            } else if (priority == VolleyPriority.RANK_HIGH) {
                order = Comparator.comparingInt(Candidate::rank).reversed()
                        .thenComparing(Comparator.comparingDouble(Candidate::baseDamage).reversed())
                        .thenComparing(Comparator.comparingInt(Candidate::remainingDurability).reversed());
            }
            for (Candidate candidate : eligible.stream().sorted(order.thenComparingInt(Candidate::index)).toList()) {
                int count = Math.min(candidate.count(), limit - result.size());
                for (int i = 0; i < count; i++) result.add(candidate.index());
                if (result.size() == limit) break;
            }
        } else {
            Objects.requireNonNull(nextInt);
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
