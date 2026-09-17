package dev.ssscfw.venusmod.treasure;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** 宝物庫の本数・配置・射出状態だけを扱う、Minecraft非依存の規則。 */
public final class TreasureRules {
    public static final int MAX_BLADES = 24;
    public static final int PREPARE_TICKS = 20 * 60;
    public static final int COOLDOWN_TICKS = 40;
    public static final int FLIGHT_TICKS = 80;
    public static final double SPEED = 2.8;
    public static final double RANGE = 80;
    public static final double BLAST_RADIUS = 2.0;
    public static final float DAMAGE = 8.0F;
    public static final double CONVERGENCE_DISTANCE = 32.0D;

    public enum VolleyMode {
        PARALLEL(0.0D),
        MEDIUM_CONVERGENCE(0.45D);

        private final double convergence;

        VolleyMode(double convergence) {
            this.convergence = convergence;
        }

        public double convergence() {
            return convergence;
        }

        public static VolleyMode forSummon(boolean shiftDown) {
            return shiftDown ? MEDIUM_CONVERGENCE : PARALLEL;
        }
    }

    private TreasureRules() {}

    /** 種類を優先した巡回選択。同じ刀も所蔵本数以上には展開せず、入力を変更しない。 */
    public static List<Integer> select(int[] counts) {
        List<Integer> result = new ArrayList<>(MAX_BLADES);
        for (int round = 0; result.size() < MAX_BLADES; round++) {
            boolean added = false;
            for (int i = 0; i < counts.length && result.size() < MAX_BLADES; i++) {
                if (counts[i] > round) {
                    result.add(i);
                    added = true;
                }
            }
            if (!added) break;
        }
        return List.copyOf(result);
    }

    public record FormationOffset(double right, double up, double back) {}

    /** 8本×3段の半円。旧配置より約1.5倍広げて刀同士の重なりを減らす。 */
    public static FormationOffset formationOffset(int slot) {
        int safeSlot = Math.max(0, Math.min(MAX_BLADES - 1, slot));
        int row = safeSlot / 8;
        double angle = Math.PI * ((safeSlot % 8) + 0.5) / 8.0;
        double radius = 2.20 + row * 1.25;
        return new FormationOffset(
                Math.cos(angle) * radius,
                Math.sin(angle) * radius + 0.35,
                1.85 + row * 0.30);
    }

    /**
     * 要求されたキーを全て確保できる場合だけ各在庫スロットの消費数を返す。
     * 不足時はnullを返し、availableCounts自体は一切変更しない。
     */
    public static <T> int[] planConsumption(List<T> availableKeys, int[] availableCounts,
                                            List<T> requestedKeys, BiPredicate<T, T> same) {
        if (availableKeys == null || availableCounts == null || requestedKeys == null || same == null
                || availableKeys.size() != availableCounts.length) {
            return null;
        }
        int[] remaining = availableCounts.clone();
        int[] consumed = new int[availableCounts.length];
        for (T requested : requestedKeys) {
            boolean found = false;
            for (int i = 0; i < availableKeys.size(); i++) {
                if (remaining[i] <= 0 || !same.test(availableKeys.get(i), requested)) continue;
                remaining[i]--;
                consumed[i]++;
                found = true;
                break;
            }
            if (!found) return null;
        }
        return consumed;
    }

    public static boolean validAge(long created, long now) {
        return now >= created && now - created < PREPARE_TICKS;
    }

    /** 押しっぱなしの入力を1回にまとめる。期限切れ直後の自動再展開も防ぐ。 */
    public static final class PressLatch {
        private boolean attack;
        private boolean use;
        public boolean press(boolean isAttack) {
            if (isAttack) {
                if (attack) return false;
                attack = true;
            } else {
                if (use) return false;
                use = true;
            }
            return true;
        }
        public void release(boolean attackDown, boolean useDown) {
            if (!attackDown) attack = false;
            if (!useDown) use = false;
        }
    }

    /** サーバーの1回限りの展開トークン。時計の逆行も安全側に倒す。 */
    public static final class Wave {
        private final long created;
        private boolean closed;
        public Wave(long created) { this.created = created; }
        public boolean expired(long now) { return !validAge(created, now); }
        public boolean fire(long now) {
            if (closed || expired(now)) return false;
            closed = true;
            return true;
        }
        public void cancel() { closed = true; }
    }
}
