import dev.ssscfw.venusmod.treasure.RoyalVolleyControlRules;

public final class RoyalVolleyControlRulesChecks {
    private static int checks;
    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        check(!RoyalVolleyControlRules.repeatDue(1), "押下直後は重複しない");
        check(!RoyalVolleyControlRules.repeatDue(5), "初回待機");
        check(RoyalVolleyControlRules.repeatDue(6), "6tickで追加召喚");
        check(!RoyalVolleyControlRules.repeatDue(7), "毎tickは召喚しない");
        check(RoyalVolleyControlRules.repeatDue(12), "6tickごとに追加召喚");

        var parallel = RoyalVolleyControlRules.direction(0, 0, 0, 0, 0, 1, 10, 0, 10, 0.0);
        check(Math.abs(parallel.x()) < 1e-9 && Math.abs(parallel.y()) < 1e-9
                && Math.abs(parallel.z() - 1) < 1e-9, "0%は平行方向");

        var full = RoyalVolleyControlRules.direction(0, 0, 0, 0, 0, 1, 10, 0, 10, 1.0);
        double inv = 1.0 / Math.sqrt(2.0);
        check(Math.abs(full.x() - inv) < 1e-9 && Math.abs(full.z() - inv) < 1e-9,
                "100%は収束点方向");

        var half = RoyalVolleyControlRules.direction(0, 0, 0, 0, 0, 1, 10, 0, 10, 0.5);
        check(half.x() > 0 && half.z() > half.x(), "50%は平行と収束の中間");

        var clamped = RoyalVolleyControlRules.direction(0, 0, 0, 0, 0, 1, 10, 0, 10, 4.0);
        check(Math.abs(clamped.x() - full.x()) < 1e-9 && Math.abs(clamped.z() - full.z()) < 1e-9,
                "収束率を100%へクランプ");

        System.out.println("RoyalVolleyControlRulesChecks: " + checks + " passed");
    }
}
