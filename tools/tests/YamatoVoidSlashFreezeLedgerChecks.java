import dev.ssscfw.venusmod.yamato.YamatoVoidSlashFreezeLedger;
import java.util.UUID;

/** 二段目までの拘束と、複数攻撃の重複・退出時の解放を回帰確認する。 */
public final class YamatoVoidSlashFreezeLedgerChecks {
    private static int checks;

    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        YamatoVoidSlashFreezeLedger ledger = new YamatoVoidSlashFreezeLedger();
        UUID mob = UUID.randomUUID(), other = UUID.randomUUID();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        check(!ledger.isHeld(mob), "攻撃前は停止しない");
        check(ledger.hold(mob, first), "初撃の命中時だけ停止を通知");
        check(ledger.isHeld(mob), "二段目まで拘束する");
        check(!ledger.hold(mob, first), "同じ斬撃は重複しない");
        check(!ledger.hold(mob, second), "重複斬撃では再通知しない");
        check(!ledger.release(mob, first), "最初の斬撃の納刀で早期解除しない");
        check(ledger.isHeld(mob), "二つ目の攻撃が残れば拘束する");
        check(ledger.release(mob, second), "最後の斬撃の納刀で解除する");
        check(!ledger.isHeld(mob), "二段目の直後は行動可能");
        check(!ledger.release(mob, second), "重複解除は行わない");
        check(ledger.hold(mob, first), "次の時空裂閃でも拘束可能");
        check(ledger.hold(other, second), "別Mobは独立");
        ledger.forgetTarget(mob);
        check(!ledger.isHeld(mob), "Mobの死亡・アンロードで破棄");
        check(ledger.isHeld(other), "別Mobに影響しない");
        ledger.clear();
        check(!ledger.isHeld(other), "ワールド停止で全解除");
        check(!ledger.hold(null, first), "不正なMob IDは無視");
        check(!ledger.hold(mob, null), "不正な斬撃IDは無視");
        System.out.println("YamatoVoidSlashFreezeLedgerChecks: " + checks + " passed");
    }
}
