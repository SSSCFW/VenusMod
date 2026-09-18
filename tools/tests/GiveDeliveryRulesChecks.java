import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/** /give実行時の生成・引き渡しだけを検査。既存インベントリは入力にさえ渡さない。 */
public final class GiveDeliveryRulesChecks {
    private static int checks;
    private record Stack(int identity, int count) {}
    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        Class<?> rules;
        try { rules = Class.forName("dev.ssscfw.venusmod.treasury.GiveDeliveryRules"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("/give直接配達が未実装", missing); }
        var deliver = rules.getMethod("deliver", int.class, int.class, IntFunction.class, Consumer.class);
        for (int max : new int[]{1, 16, 64}) {
            for (int count : new int[]{1, max, max + 1, 100 * max}) {
                List<Stack> received = new ArrayList<>();
                int[] serial = {0};
                IntFunction<Stack> factory = size -> new Stack(++serial[0], size);
                Consumer<Stack> receiver = received::add;
                deliver.invoke(null, count, max, factory, receiver);
                check(received.stream().mapToInt(Stack::count).sum() == count, "指定本数だけ配達");
                check(received.stream().allMatch(stack -> stack.count() > 0 && stack.count() <= max), "通常スタック上限");
                check(received.stream().map(Stack::identity).distinct().count() == received.size(), "生成時の個体をそのまま渡す");
            }
            int[] calls = {0};
            try {
                deliver.invoke(null, max * 100 + 1, max, (IntFunction<Object>) size -> { calls[0]++; return new Object(); },
                        (Consumer<Object>) item -> calls[0]++);
                throw new AssertionError("上限超過を受理した");
            } catch (InvocationTargetException exception) {
                check(exception.getCause() instanceof IllegalArgumentException && calls[0] == 0,
                        "無効な/giveは副作用の前に拒否");
            }
        }
        System.out.println("GiveDeliveryRulesChecks: " + checks + " passed");
    }
}
