import dev.ssscfw.venusmod.storagebox.StorageBoxRules;

public final class StorageBoxRulesChecks {
    private static int checks;
    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        check(StorageBoxRules.accepted(0, 64) == 64, "空箱は要求数を受入");
        check(StorageBoxRules.accepted(Integer.MAX_VALUE - 10, 64) == 10, "上限で切る");
        check(StorageBoxRules.accepted(Integer.MAX_VALUE, 1) == 0, "満杯は拒否");
        check(StorageBoxRules.accepted(-5, 10) == 10, "負の保存数を0へ");
        check(StorageBoxRules.accepted(10, -1) == 0, "負の要求を拒否");
        check(StorageBoxRules.extractable(100, 80, 64) == 64, "1回最大1スタック");
        check(StorageBoxRules.extractable(20, 64, 64) == 20, "残量だけ取り出す");
        check(StorageBoxRules.extractable(20, 0, 64) == 0, "0要求");
        check(StorageBoxRules.largeChestEquivalent(3456, 64) == 1.0D, "54x64でLC1");
        check(StorageBoxRules.largeChestEquivalent(1728, 64) == 0.5D, "半LC");
        check(StorageBoxRules.normalizeCount(-1) == 0, "下限");
        check(StorageBoxRules.normalizeCount(Integer.MAX_VALUE) == Integer.MAX_VALUE, "上限");
        System.out.println("StorageBoxRulesChecks: " + checks + " passed");
    }
}
