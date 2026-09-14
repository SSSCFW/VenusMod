package dev.ssscfw.venusmod.upgrade;

import net.minecraft.util.StringRepresentable;

public enum BladeUpgradeType implements StringRepresentable {
    BLADE_POWER("blade_power", BladeUpgradeRules.Category.BLADE_POWER),
    SUMMONED_SWORD("summoned_sword", BladeUpgradeRules.Category.SUMMONED_SWORD),
    CONCENTRATION("concentration", BladeUpgradeRules.Category.CONCENTRATION),
    DURABILITY("durability", BladeUpgradeRules.Category.DURABILITY),
    VENUS_SLAYER("venus_slayer", BladeUpgradeRules.Category.VENUS_SLAYER);

    private final String serializedName;
    private final BladeUpgradeRules.Category category;

    BladeUpgradeType(String serializedName, BladeUpgradeRules.Category category) {
        this.serializedName = serializedName;
        this.category = category;
    }

    @Override public String getSerializedName() { return serializedName; }
    public BladeUpgradeRules.Category category() { return category; }
    public int maxLevel() { return BladeUpgradeRules.maxLevel(category); }

    public BladeUpgradeType next() {
        BladeUpgradeType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
