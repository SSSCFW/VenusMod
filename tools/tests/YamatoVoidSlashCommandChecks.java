import dev.ssscfw.venusmod.yamato.YamatoVoidSlashCommand;

/** 入力順序・同時入力・猶予と消費を通常のbuild/checkで検証する。 */
public final class YamatoVoidSlashCommandChecks {
    private static int checks;

    private static void check(boolean condition, String explanation) {
        checks++;
        if (!condition) throw new AssertionError(explanation);
    }

    public static void main(String[] args) {
        YamatoVoidSlashCommand command = new YamatoVoidSlashCommand();
        check(!command.use(0), "右クリック単独では発動しない");
        check(!command.pressForward(1), "Wから始めても発動しない");
        check(!command.use(1), "W→右クリックでは発動しない");

        command.pressBack(20);
        check(command.pressForward(25), "S→Wを受け付ける");
        check(command.use(30), "S→W→右クリックで発動");
        check(!command.use(30), "同じ入力から二重発動しない");

        command.pressBack(40);
        check(command.pressForward(40), "SとWが同じtickでもキー順を保つ");
        check(command.use(40), "Wと右クリックが同じtickでも発動");

        command.pressBack(50);
        check(command.pressForward(50 + YamatoVoidSlashCommand.BACK_TO_FORWARD_TICKS),
                "S→Wの猶予最終tickを受け付ける");
        check(command.use(62 + YamatoVoidSlashCommand.FORWARD_TO_USE_TICKS),
                "W→右クリックの猶予最終tickを受け付ける");

        command.pressBack(100);
        check(!command.pressForward(100 + YamatoVoidSlashCommand.BACK_TO_FORWARD_TICKS + 1),
                "S→Wの時間切れは失敗");
        check(!command.use(114), "時間切れのSを再利用しない");

        command.pressBack(200);
        check(command.pressForward(201), "別の入力を開始");
        check(!command.use(201 + YamatoVoidSlashCommand.FORWARD_TO_USE_TICKS + 1),
                "W→右クリックの時間切れは失敗");

        command.pressBack(300);
        command.expire(313);
        check(!command.pressForward(313), "期限切れのSは破棄");
        command.pressBack(400);
        check(command.pressForward(401), "新しいSを受理");
        command.expire(412);
        check(!command.use(412), "期限切れのWは破棄");

        command.pressBack(500);
        check(command.pressForward(501), "入力成立");
        command.reset();
        check(!command.use(502), "武器持ち替えや画面を開くと入力解除");

        command.pressBack(600);
        check(!command.pressForward(599), "逆行するtickは拒否");
        command.pressBack(700);
        check(command.pressForward(701), "新しいtickなら受け付ける");
        check(!command.use(700), "右クリックのtickが逆行しても拒否");
        System.out.println("YamatoVoidSlashCommandChecks: " + checks + " passed");
    }
}
