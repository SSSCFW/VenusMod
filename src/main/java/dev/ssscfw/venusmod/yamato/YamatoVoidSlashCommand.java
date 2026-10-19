package dev.ssscfw.venusmod.yamato;

/**
 * 閻魔刀専用の S（後退）→W（前進）→使用コマンド。
 * クライアントの実入力とサーバーが受信した入力段階を同じ猶予で検証する。
 * Minecraftのキー設定や通信処理には依存しない。
 */
public final class YamatoVoidSlashCommand {
    public static final int BACK_TO_FORWARD_TICKS = 12;
    public static final int FORWARD_TO_USE_TICKS = 10;
    private static final int IDLE = 0;
    private static final int BACK = 1;
    private static final int FORWARD = 2;

    private int stage = IDLE;
    private long lastTick = -1;

    public void pressBack(long tick) {
        stage = BACK;
        lastTick = tick;
    }

    /** Sを先に押していない場合、あるいは猶予が切れた場合は受け付けない。 */
    public boolean pressForward(long tick) {
        if (stage != BACK || !within(tick, BACK_TO_FORWARD_TICKS)) {
            reset();
            return false;
        }
        stage = FORWARD;
        lastTick = tick;
        return true;
    }

    public boolean awaitingForward(long tick) {
        return stage == BACK && within(tick, BACK_TO_FORWARD_TICKS);
    }

    /** Wと右クリックが同じtickでも成立。一度使用するとコマンドを消費する。 */
    public boolean use(long tick) {
        boolean accepted = stage == FORWARD && within(tick, FORWARD_TO_USE_TICKS);
        reset();
        return accepted;
    }

    public void expire(long tick) {
        if (stage == BACK && !within(tick, BACK_TO_FORWARD_TICKS)
                || stage == FORWARD && !within(tick, FORWARD_TO_USE_TICKS)) {
            reset();
        }
    }

    public void reset() {
        stage = IDLE;
        lastTick = -1;
    }

    private boolean within(long tick, long window) {
        return lastTick >= 0 && tick >= lastTick && tick - lastTick <= window;
    }
}
