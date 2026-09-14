import dev.ssscfw.venusmod.upgrade.BladeUpgradeRules;

public final class BladeUpgradeRulesTest {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (BladeUpgradeRules.Category category : BladeUpgradeRules.Category.values()) {
            int max = BladeUpgradeRules.maxLevel(category);
            BladeUpgradeRules.Cost previous = null;
            for (int level = 1; level <= max; level++) {
                BladeUpgradeRules.Cost cost = BladeUpgradeRules.cost(category, level);
                require(cost.pressureAlloy() >= 0 && cost.nickel() >= 0 && cost.venesite() >= 0 && cost.sulfur() >= 0,
                        "negative cost: " + category + " lv" + level);
                if (previous != null) {
                    int before = previous.pressureAlloy() + previous.nickel() + previous.venesite() + previous.sulfur();
                    int now = cost.pressureAlloy() + cost.nickel() + cost.venesite() + cost.sulfur();
                    require(now > before, "total cost must increase every level: " + category + " lv" + level);
                }
                previous = cost;
            }
        }
        require(BladeUpgradeRules.bladePowerBonus(10) == 15.0F, "blade power cap");
        require(BladeUpgradeRules.summonedSwordBonus(10) == 10.0F, "summoned sword cap");
        require(Math.abs(BladeUpgradeRules.concentrationBonusMultiplier(5) - 0.5D) < 0.0001D, "concentration cap");
        require(BladeUpgradeRules.durabilityReductionNumerator(5) == 5, "durability cap");
        require(Math.abs(BladeUpgradeRules.venusDamageMultiplier(5) - 1.25F) < 0.0001F, "venus slayer cap");
        require(BladeUpgradeRules.bladePowerBonus(999) == 15.0F, "clamp prevents inflation");
        System.out.println("BladeUpgradeRulesTest: " + checks + " checks passed");
    }
}
