import java.lang.reflect.Method;

/** 実運用の純Java規則を検査。実装が欠落した状態でもREDを明確に報告する。 */
public final class RoyalBladeEffectsRulesChecks {
    private static int checks;
    private static Class<?> rules;
    private static Object call(String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = args[i] instanceof Boolean ? boolean.class
                : args[i] instanceof Float ? float.class : int.class;
        Method method = rules.getMethod(name, types);
        return method.invoke(null, args);
    }
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        try { rules = Class.forName("dev.ssscfw.venusmod.treasure.RoyalBladeEffectsRules"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("火属性・耐久力・壊れた幻想の規則が未実装", missing); }
        check((int) call("fireSeconds", 0) == 0, "火属性なし");
        check((int) call("fireSeconds", 1) == 4, "火属性Iは4秒");
        check((int) call("fireSeconds", 2) == 8, "火属性IIは8秒");
        check((int) call("fireSeconds", -1) == 0, "負レベル防御");
        for (int level = 0; level <= 10; level++) {
            int used = 0;
            for (int roll = 0; roll <= level; roll++) if ((boolean) call("usesDurability", level, roll)) used++;
            check(used == 1, "耐久消費は厳密に1/(Lv+1): " + level);
        }
        for (float damage : new float[]{0, 5, 13, 21.5F}) {
            check((float) call("damage", damage, false) == damage, "通常倍率");
            check((float) call("damage", damage, true) == damage * 5, "直撃/爆風とも5倍");
        }
        check((int) call("nextProtection", 0) == 1, "通常→お気に入り");
        check((int) call("nextProtection", 1) == 2, "お気に入り→幻想禁止");
        check((int) call("nextProtection", 2) == 0, "幻想禁止→通常");
        for (boolean phantom : new boolean[]{false, true}) {
            check((boolean) call("eligible", 0, false, phantom), "通常刀は両モードで使用");
            check(!(boolean) call("eligible", 1, false, phantom), "お気に入りは常に除外");
            check(!(boolean) call("eligible", 0, true, phantom), "折れた刀は常に除外");
            check((boolean) call("eligible", 2, false, phantom) == !phantom, "赤は幻想のみ除外");
            check((boolean) call("returnsBlade", phantom, false), "未着弾の刀は返す");
        }
        check(!(boolean) call("returnsBlade", true, true), "幻想の着弾刀は返さない");
        check((boolean) call("returnsBlade", false, true), "通常の着弾刀は返す");
        check((float) call("blastRadius", false) == 2F, "通常爆風2");
        check((float) call("blastRadius", true) == 6F, "幻想爆風6");
        System.out.println("RoyalBladeEffectsRulesChecks: " + checks + " passed");
    }
}
