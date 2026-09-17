import dev.ssscfw.venusmod.treasury.TreasuryRules;

public final class TreasuryRulesTest {
    public static void main(String[] args) {
        require(TreasuryRules.acceptedIntoStack(0, 2000, 10000) == 1028, "logical stack cap");
        require(TreasuryRules.acceptedIntoStack(1000, 100, 10000) == 28, "fill existing stack");
        require(TreasuryRules.acceptedIntoStack(0, 100, 37) == 37, "global remaining capacity");
        require(TreasuryRules.acceptedIntoStack(1028, 1, 9999) == 0, "full stack rejects");
        require(TreasuryRules.MAX_TOTAL == 10000, "total cap");
        require(TreasuryRules.MAX_LOGICAL_STACK == 1028, "logical cap");
        require(TreasuryRules.PAGE_SIZE == 54, "page size");
        System.out.println("TreasuryRulesTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
