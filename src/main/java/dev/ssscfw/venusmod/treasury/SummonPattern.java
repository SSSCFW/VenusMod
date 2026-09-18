package dev.ssscfw.venusmod.treasury;

/** 王の財宝の召喚配置。保存IDは表示名と独立させる。 */
public enum SummonPattern {
    DEFAULT("default", "デフォルト"),
    VIEW_PARALLEL("view_parallel", "頭の角度に平行"),
    VIEW_RING("view_ring", "頭角度平行・円形");

    private final String id;
    private final String label;

    SummonPattern(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    public static SummonPattern fromId(String id) {
        for (SummonPattern value : values()) if (value.id.equals(id)) return value;
        return DEFAULT;
    }

    public static SummonPattern fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : DEFAULT;
    }
}
