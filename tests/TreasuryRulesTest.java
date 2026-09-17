import dev.ssscfw.venusmod.treasury.TreasuryRules;

public final class TreasuryRulesTest {
    public static void main(String[] args) {
        require(TreasuryRules.acceptedIntoStack(0, 2000) == 1028, "logical stack cap");
        require(TreasuryRules.acceptedIntoStack(1000, 100) == 28, "fill existing stack");
        require(TreasuryRules.acceptedIntoStack(1028, 1) == 0, "full logical stack rejects");
        require(TreasuryRules.canCreateStack(9999), "9999 stacks accepts one more");
        require(!TreasuryRules.canCreateStack(10000), "10000 stacks rejects new stack");
        require(TreasuryRules.MAX_STACKS == 10000, "stack cap");
        require(TreasuryRules.MAX_LOGICAL_STACK == 1028, "logical cap");
        require(TreasuryRules.PAGE_SIZE == 54, "page size");
        System.out.println("TreasuryRulesTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
