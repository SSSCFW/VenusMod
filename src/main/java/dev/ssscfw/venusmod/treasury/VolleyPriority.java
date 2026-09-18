package dev.ssscfw.venusmod.treasury;

/** 保存IDは安定した文字列。既存モードのordinalを変えず末尾へ追加する。 */
public enum VolleyPriority {
    RANDOM("random", "ランダム"),
    DURABILITY_LOW("durability_low", "耐久度低い順"),
    DURABILITY_HIGH("durability_high", "耐久度高い順"),
    RANK_LOW("rank_low", "低ランク順"),
    RANK_HIGH("rank_high", "高ランク順");

    private final String id;
    private final String label;
    VolleyPriority(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }
    public static VolleyPriority fromId(String id) {
        for (VolleyPriority value : values()) if (value.id.equals(id)) return value;
        return RANDOM;
    }
    public static VolleyPriority fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : RANDOM;
    }
}
