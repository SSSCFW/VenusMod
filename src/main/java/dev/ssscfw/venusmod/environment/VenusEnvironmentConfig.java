package dev.ssscfw.venusmod.environment;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 初期バランス値。高温/高圧/腐食を個別調整できるサーバー側設定。 */
public final class VenusEnvironmentConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue GRACE, HEAT, PRESSURE, CORROSION, RECOVERY;
    public static final ModConfigSpec.DoubleValue DAMAGE;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("environment");
        ENABLED = builder.comment("Enable atmospheric exposure on Venus.").define("enabled", true);
        GRACE = builder.comment("Arrival grace period in seconds.").defineInRange("entryGraceSeconds", 60, 0, 3600);
        HEAT = builder.defineInRange("heatPointsPerSecond", 1, 0, 20);
        PRESSURE = builder.defineInRange("pressurePointsPerSecond", 1, 0, 20);
        CORROSION = builder.defineInRange("corrosionPointsPerSecond", 1, 0, 20);
        RECOVERY = builder.defineInRange("recoveryPointsPerSecond", 4, 0, 100);
        DAMAGE = builder.comment("Damage every two seconds at maximum exposure.").defineInRange("damageAtMaximum", 2.0, 0.0, 20.0);
        builder.pop();
        SPEC = builder.build();
    }
    private VenusEnvironmentConfig() {}
}
