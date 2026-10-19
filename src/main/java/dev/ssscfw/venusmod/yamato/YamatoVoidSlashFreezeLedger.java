package dev.ssscfw.venusmod.yamato;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 一段目の斬撃で捕捉したMobを、対応する納刀攻撃が終わるまで拘束する。
 * 別のプレイヤーの斬撃が重なっても、最後の斬撃が終わるまで解除しない。
 */
public final class YamatoVoidSlashFreezeLedger {
    private final Map<UUID, Set<UUID>> activeEffects = new HashMap<>();

    /** 新しく拘束されたときだけtrue。同期通知を重複して送らない。 */
    public boolean hold(UUID target, UUID slash) {
        if (target == null || slash == null) return false;
        Set<UUID> holding = activeEffects.computeIfAbsent(target, ignored -> new HashSet<>());
        boolean wasFree = holding.isEmpty();
        holding.add(slash);
        return wasFree;
    }

    /** 最後の斬撃による拘束を解除したときだけtrue。 */
    public boolean release(UUID target, UUID slash) {
        Set<UUID> holding = activeEffects.get(target);
        if (holding == null || !holding.remove(slash)) return false;
        if (!holding.isEmpty()) return false;
        activeEffects.remove(target);
        return true;
    }

    public boolean isHeld(UUID target) {
        return activeEffects.containsKey(target);
    }

    /** 死亡、次元離脱、アンロードで古い拘束を持ち越さない。 */
    public void forgetTarget(UUID target) {
        activeEffects.remove(target);
    }

    public void clear() {
        activeEffects.clear();
    }
}
