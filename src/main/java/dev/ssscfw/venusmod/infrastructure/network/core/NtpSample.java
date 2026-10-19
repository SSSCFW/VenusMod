package dev.ssscfw.venusmod.infrastructure.network.core;

/** RFC5905の4時刻式。単位はゲーム内のミリ秒で、実NTPのバイト形式ではない。 */
public record NtpSample(double sent,double receivedByServer,double sentByServer,double received,
                        double offsetMillis,double delayMillis) {
    public static NtpSample calculate(double t1,double t2,double t3,double t4){
        if(!Double.isFinite(t1)||!Double.isFinite(t2)||!Double.isFinite(t3)||!Double.isFinite(t4)||t4<t1||t3<t2)
            throw new IllegalArgumentException("NTPの4時刻が不正です");
        double offset=((t2-t1)+(t3-t4))/2.0,delay=(t4-t1)-(t3-t2);
        if(!Double.isFinite(offset)||!Double.isFinite(delay)||delay< -0.001)
            throw new IllegalArgumentException("NTP時刻差が範囲外です");
        return new NtpSample(t1,t2,t3,t4,offset,Math.max(0,delay));
    }
}
