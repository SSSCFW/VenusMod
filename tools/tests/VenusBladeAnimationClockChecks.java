import dev.ssscfw.venusmod.entity.ai.VenusBladeAnimationClock;

/** CIで実行する、通信が不規則でもVMDの再生時間が飛ばないことの回帰検査。 */
public final class VenusBladeAnimationClockChecks {
    private VenusBladeAnimationClockChecks() {
    }

    public static void main(String[] args) {
        VenusBladeAnimationClock clock = new VenusBladeAnimationClock();

        expect(clock.getRenderTick(1, 0, 100, 0.25F), 0.25F, "開始フレーム");
        // パケット更新の無いフレームでもクライアント時計を進める。
        expect(clock.getRenderTick(1, 0, 101, 0.25F), 1.25F, "パケットなし");
        expect(clock.getRenderTick(1, 0, 102, 0.25F), 2.25F, "パケットなし2");

        // 3tickまとめて届いても補間が巻き戻らない/跳ねない。
        expect(clock.getRenderTick(1, 3, 103, 0.25F), 3.25F, "3tick更新");
        expect(clock.getRenderTick(1, 8, 104, 0.25F), 4.25F, "遅延・前進した更新");
        expect(clock.getRenderTick(1, 3, 105, 0.25F), 5.25F, "古いtick更新");
        expect(clock.getRenderTick(1, 3, 105, 0.75F), 5.75F, "同一tick内の滑らかな補間");

        // 描画されない間にNoneを観測したら、次の同種コンボを正しく0から再生。
        clock.observe(0, 0, 108);
        expect(clock.getRenderTick(1, 0, 120, 0.25F), 0.25F, "同種コンボの再開");
        expect(clock.getRenderTick(1, 4, 121, 0.25F), 1.25F, "再開後のパケット遅延");

        // 別種の技が途中の時点で届く場合だけ初期同期tickを採用する。
        expect(clock.getRenderTick(2, 4, 140, 0.25F), 4.25F, "途中から見た高速斬撃");
        expect(clock.getRenderTick(2, 7, 141, 0.25F), 5.25F, "高速斬撃のパケット更新");
        expect(clock.getRenderTick(0, 0, 142, 0.25F), 0.0F, "終了後の時計");
        expect(clock.getRenderTick(3, 0, 145, 0.25F), 0.25F, "居合い開始");
        expect(clock.getRenderTick(3, 99, 145, 0.75F), 0.75F, "同tickでは同期更新しない");

        System.out.println("Verified monotonic SlashBlade VMD playback across packet jumps and technique restarts.");
    }

    private static void expect(float actual, float expected, String message) {
        if (Math.abs(actual - expected) > 0.00001F) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
