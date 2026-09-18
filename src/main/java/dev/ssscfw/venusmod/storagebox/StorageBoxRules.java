package dev.ssscfw.venusmod.storagebox;

/** Minecraft非依存のStorage Box容量・取り出し・LC換算規則。 */
public final class StorageBoxRules {
    public static final int CAPACITY = Integer.MAX_VALUE;
    private StorageBoxRules() {}

    public static int normalizeCount(int count) {
        return Math.max(0, count);
    }

    public static int accepted(int stored, int requested) {
        int safeStored = normalizeCount(stored);
        int safeRequested = Math.max(0, requested);
        long room = (long) CAPACITY - safeStored;
        return (int) Math.min(room, (long) safeRequested);
    }

    public static int extractable(int stored, int requested, int maxStackSize) {
        int safeStored = normalizeCount(stored);
        int safeRequested = Math.max(0, requested);
        int safeMaxStack = Math.max(1, maxStackSize);
        return Math.min(safeStored, Math.min(safeRequested, safeMaxStack));
    }

    public static double largeChestEquivalent(int stored, int maxStackSize) {
        int safeStored = normalizeCount(stored);
        int safeMaxStack = Math.max(1, maxStackSize);
        return safeStored / (54.0D * safeMaxStack);
    }
}
