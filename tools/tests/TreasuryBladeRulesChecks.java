import java.lang.reflect.Method;

/** 新規仕様の実ルールを検査する。未実装時は明示的なAssertionErrorにする。 */
public final class TreasuryBladeRulesChecks {
    private static int checks;
    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Class<?> rules;
        try { rules = Class.forName("dev.ssscfw.venusmod.treasury.TreasuryBladeRules"); }
        catch (ClassNotFoundException e) { throw new AssertionError("刀ごとの保護・消滅・低ランク規則が未実装", e); }
        Method protection = rules.getMethod("protection", int.class, boolean.class, boolean.class);
        check((int)protection.invoke(null, -1, true, false) == 1, "旧お気に入りを移行");
        check((int)protection.invoke(null, -1, false, true) == 2, "旧幻想禁止を移行");
        check((int)protection.invoke(null, -1, false, false) == 0, "新しい刀は通常");
        check((int)protection.invoke(null, 0, true, true) == 0, "刀の通常指定が旧フラグより優先");
        check((int)protection.invoke(null, 2, true, false) == 2, "刀の幻想禁止が旧フラグより優先");
        Method vanish = rules.getMethod("vanishesOnBreak", boolean.class, boolean.class);
        check((boolean)vanish.invoke(null, true, true), "消滅型は最後の耐久で消える");
        check(!(boolean)vanish.invoke(null, false, true), "通常刀は折れ状態を残す");
        check(!(boolean)vanish.invoke(null, true, false), "消滅型でも耐久が残っていれば返却");
        Method vanishWithCurse = rules.getMethod("vanishesOnBreak", boolean.class, boolean.class, boolean.class);
        check((boolean)vanishWithCurse.invoke(null, false, true, true), "消滅の呪いは折れた瞬間に消滅");
        check(!(boolean)vanishWithCurse.invoke(null, false, true, false), "消滅の呪いでも折れていなければ残る");
        check((boolean)vanishWithCurse.invoke(null, true, false, true), "既存の消滅型も維持");
        Method rank = rules.getMethod("rank", boolean.class, boolean.class, boolean.class);
        check((int)rank.invoke(null, true, false, false) == 0, "木偶など消滅型");
        check((int)rank.invoke(null, false, false, false) == 1, "通常刀");
        check((int)rank.invoke(null, false, true, false) == 2, "印");
        check((int)rank.invoke(null, false, true, true) == 3, "妖刀は印より後");
        System.out.println("TreasuryBladeRulesChecks: " + checks + " passed");
    }
}
