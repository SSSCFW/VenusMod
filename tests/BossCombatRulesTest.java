import dev.ssscfw.venusmod.entity.BossCombatRules;

public final class BossCombatRulesTest {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        require(BossCombatRules.generalPhase(100, 100) == 1, "general phase 1");
        require(BossCombatRules.generalPhase(70, 100) == 2, "general phase 2 boundary");
        require(BossCombatRules.generalPhase(35, 100) == 3, "general phase 3 boundary");
        require(BossCombatRules.generalDamage(100, 3, 100, false) == 50, "phase3 reduction");
        require(Math.abs(BossCombatRules.generalDamage(100, 3, 100, true) - 17.5F) < 0.001F, "guard stacks");
        require(BossCombatRules.postureAfterHit(100, 25) == 50, "posture loss");
        require(BossCombatRules.postureAfterHit(20, 30) == 0, "posture clamp");
        require(Math.abs(BossCombatRules.aphroditeDamage(100, 3) - 55) < 0.001F, "three core reduction");
        require(Math.abs(BossCombatRules.aphroditeDamage(100, 0) - 100) < 0.001F, "zero core reduction");
        int charge = 0;
        for (int i = 0; i < 100; i++) charge = BossCombatRules.controllerCharge(charge, 128, true, false);
        require(charge == 100, "five second Create charge");
        charge = BossCombatRules.controllerCharge(charge, 0, true, false);
        require(charge == 98, "discharge");
        require(BossCombatRules.controllerCharge(0, 0, false, true) == 1, "redstone fallback without Create");
        require(BossCombatRules.controllerCharge(0, Float.NaN, true, true) == 0, "Create requires kinetic power");
        System.out.println("BossCombatRulesTest: " + checks + " checks passed");
    }
}
