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
        var shield = BossCombatRules.AphroditeShield.full();
        require(shield.active() && shield.durability() == 200.0F, "shield starts full");
        require(shield.absorb(0.0F).equals(shield), "zero damage cannot drain the shield");
        require(shield.absorb(Float.NaN).equals(shield), "invalid damage cannot drain the shield");
        shield = shield.absorb(70.0F);
        require(shield.active() && shield.durability() == 130.0F, "shield loses actual incoming damage");
        shield = shield.absorb(999.0F);
        require(!shield.active() && shield.durability() == 0.0F
                && shield.recoveryTicks() == 200, "shield breaks, discards overkill and starts 10s cooldown");
        require(shield.absorb(50.0F).equals(shield), "shield does not absorb attacks while broken");
        for (int i = 0; i < 199; i++) shield = shield.tick();
        require(!shield.active() && shield.recoveryTicks() == 1, "shield stays down through tick 199");
        shield = shield.tick();
        require(shield.active() && shield.durability() == 200.0F
                && shield.recoveryTicks() == 0, "shield returns at exactly 200 ticks");
        require(shield.absorb(200.0F).recoveryTicks() == 200, "next shield can break again");
        require(new BossCombatRules.AphroditeShield(0.0F, 57).tick().recoveryTicks() == 56,
                "saved cooldown resumes after reload");
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
