package dev.ssscfw.venusmod.entity.ai;

/**
 * 抜刀剣のモーション再生をクライアントの毎tickで連続進行させる時計。
 *
 * EntityDataの攻撃経過tickは毎tick更新されても、クライアントに届くのは
 * 複数tickごとになることがある。同期値をそのまま描画に使うと、腕と胴体の
 * VMDフレームが停止→ジャンプを繰り返し、震えて見える。
 *
 * 技の開始時だけ受信経過tickで位置を合わせ、その後はクライアント自身の
 * tickCountを進める。Noneを観測したら次の同種コンボも正しく再開始する。
 */
public final class VenusBladeAnimationClock {
    private static final int NONE = 0;

    private int observedTechnique = NONE;
    private int startClientTick;

    /** 描画対象外のときもtechniqueの切替を見逃さないよう、client tickから呼ぶ。 */
    public void observe(int technique, int syncedTechniqueTick, int clientTick) {
        if (technique == NONE) {
            observedTechnique = NONE;
            return;
        }

        if (observedTechnique != technique) {
            observedTechnique = technique;
            startClientTick = clientTick - Math.max(0, syncedTechniqueTick);
        }
    }

    /** 同じフレーム中のモデル・胴体・防具で同一の連続した時間を返す。 */
    public float getRenderTick(int technique, int syncedTechniqueTick, int clientTick, float partialTick) {
        observe(technique, syncedTechniqueTick, clientTick);
        if (technique == NONE) {
            return 0.0F;
        }

        return Math.max(0.0F, clientTick - startClientTick
                + Math.clamp(partialTick, 0.0F, 1.0F));
    }
}
