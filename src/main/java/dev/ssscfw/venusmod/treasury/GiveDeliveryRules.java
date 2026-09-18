package dev.ssscfw.venusmod.treasury;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/** /giveの新規生成物だけを直接配達する。インベントリの増分比較には依存しない。 */
public final class GiveDeliveryRules {
    private GiveDeliveryRules() {}
    public static boolean validCount(int count, int maxStackSize) {
        return count > 0 && maxStackSize > 0 && (long) count <= (long) maxStackSize * 100L;
    }
    public static <T> void deliver(int count, int maxStackSize, IntFunction<T> factory, Consumer<T> receiver) {
        if (!validCount(count, maxStackSize)) throw new IllegalArgumentException("Invalid vanilla give count");
        Objects.requireNonNull(factory);
        Objects.requireNonNull(receiver);
        int remaining = count;
        while (remaining > 0) {
            int part = Math.min(remaining, maxStackSize);
            T generated = factory.apply(part);
            receiver.accept(generated);
            remaining -= part;
        }
    }
}
