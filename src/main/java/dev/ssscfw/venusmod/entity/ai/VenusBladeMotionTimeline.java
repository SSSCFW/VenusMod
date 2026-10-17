package dev.ssscfw.venusmod.entity.ai;

import dev.ssscfw.venusmod.entity.VenusZombie;

/**
 * 抜刀剣Mobの攻撃tickを、SlashBladeがプレイヤーに使うVMDのフレームへ変換する。
 * 実際のプレイヤーと同じA1→A2→A3(2斬)→A4(2斬)の4モーションを再生する。
 *
 * 元のVMDポーズ自体は変更せず、Mob固有の攻撃間隔に合わせて再生位置だけを補間する。
 * サーバー側の斬撃時刻はVenusZombieの定数が基準。
 */
public final class VenusBladeMotionTimeline {
    public enum Motion {
        COMBO_A1,
        COMBO_A2,
        COMBO_A3,
        COMBO_A4,
        RAPID_SLASH,
        JUDGEMENT_CUT
    }

    /** SlashBladeの該当VMDクリップ先頭からの相対フレーム（30fps）。 */
    public record Sample(Motion motion, float frameOffset) {
    }

    private VenusBladeMotionTimeline() {
    }

    public static Sample sample(int technique, float elapsedTicks) {
        float tick = Math.max(0.0F, elapsedTicks);
        if (technique == VenusZombie.BLADE_TECHNIQUE_COMBO) {
            if (tick < 6.0F) {
                return new Sample(Motion.COMBO_A1, between(tick, 0.0F, 6.0F, 0.0F, 9.0F));
            }
            if (tick < 11.0F) {
                return new Sample(Motion.COMBO_A2, between(tick, 6.0F, 11.0F, 0.0F, 9.0F));
            }
            if (tick < 20.0F) {
                // プレイヤーのA3は1クリップ内で200+2、200+6フレームの2連撃。
                // Mobのヒット時刻13tickと18tickが同じ原作ポーズを通る。
                float frame = tick < 13.0F
                        ? between(tick, 11.0F, 13.0F, 0.0F, 2.0F)
                        : tick < 18.0F
                        ? between(tick, 13.0F, 18.0F, 2.0F, 6.0F)
                        : between(tick, 18.0F, 20.0F, 6.0F, 10.0F);
                return new Sample(Motion.COMBO_A3, frame);
            }
            // プレイヤーのA4は500+8、500+9フレームで2連撃する。
            // この2発を25tickと29tickへ伸ばしても、元のVMDポーズを使い続ける。
            float frame = tick < 25.0F
                    ? between(tick, 20.0F, 25.0F, 0.0F, 8.0F)
                    : tick < 29.0F
                    ? between(tick, 25.0F, 29.0F, 8.0F, 9.0F)
                    : between(tick, 29.0F, VenusZombie.BLADE_COMBO_END_TICK + 1.0F, 9.0F, 20.0F);
            return new Sample(Motion.COMBO_A4, frame);
        }

        if (technique == VenusZombie.BLADE_TECHNIQUE_RAPID_SLASH) {
            return new Sample(Motion.RAPID_SLASH, between(tick, 0.0F, 9.0F, 0.0F, 19.0F));
        }
        if (technique == VenusZombie.BLADE_TECHNIQUE_IAI) {
            return new Sample(Motion.JUDGEMENT_CUT, between(tick, 0.0F,
                    VenusZombie.BLADE_IAI_END_TICK + 1.0F, 0.0F, 23.0F));
        }
        return null;
    }

    private static float between(float tick, float fromTick, float toTick, float fromFrame, float toFrame) {
        float fraction = Math.clamp((tick - fromTick) / (toTick - fromTick), 0.0F, 1.0F);
        return fromFrame + (toFrame - fromFrame) * fraction;
    }
}
