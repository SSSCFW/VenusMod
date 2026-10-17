import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** 金星将軍の城塞とアフロディーテ神殿。既存構造物と同じ一度限りの祭壇を利用する。 */
public final class BossStructureAssets {
    private static final String AIR = "minecraft:air";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: BossStructureAssets <resource-output-directory>");
        Path output = Path.of(args[0]);
        generalFortress(output);
        aphroditeSanctum(output);
    }

    /** 基礎・外壁・屋根を一体で作り、内部を空気で確実に空ける。入口以外の外周は閉じる。 */
    private static VenusAssetGenerator.Template hall(int width, int height, int depth, int roof,
                                                    String foundation, String wall, int doorMin, int doorMax, int doorTop) {
        var t = new VenusAssetGenerator.Template(width, height, depth);
        t.box(0, 0, 4, width - 1, 3, depth - 1, foundation);
        t.box(0, 4, 4, width - 1, roof, depth - 1, wall);
        t.box(1, 4, 5, width - 2, roof - 1, depth - 2, AIR);
        t.box(doorMin, 4, 4, doorMax, doorTop, 4, AIR);
        for (int z = 0; z < 4; z++) {
            if (z > 0) t.box(doorMin, 0, z, doorMax, z - 1, z, foundation);
            t.box(doorMin, z, z, doorMax, z, z, "minecraft:polished_blackstone_brick_stairs",
                    VenusAssetGenerator.properties("facing", "south", "half", "bottom", "shape", "straight", "waterlogged", "false"));
            // 斜面上でも入口階段の頭上に地形が残らないよう明示的に空ける。
            t.box(doorMin, z + 1, z, doorMax, roof, z, AIR);
        }
        return t;
    }

    private static void generalFortress(Path root) throws IOException {
        String wall = "minecraft:deepslate_tiles", brick = "minecraft:deepslate_bricks";
        var t = hall(41, 20, 59, 14, brick, wall, 17, 23, 9);

        // 正面の大門から兵舎を抜け、後半の広い玉座の間へ進む。
        for (int x : new int[]{12, 28}) {
            t.box(x, 4, 5, x, 13, 33, brick);
            for (int z : new int[]{10, 25}) t.box(x, 4, z, x, 7, z + 2, AIR);
        }
        t.box(1, 4, 20, 11, 13, 20, brick);
        t.box(29, 4, 20, 39, 13, 20, brick);
        t.box(1, 4, 34, 39, 13, 34, brick);
        t.box(17, 4, 34, 23, 9, 34, AIR);
        t.box(16, 10, 34, 24, 10, 34, "minecraft:gold_block");
        t.box(20, 3, 5, 20, 3, 46, "minecraft:gold_block");

        for (int x : new int[]{6, 34}) {
            t.spawner(x, 14, "venusmod:venus_zombie");
            t.spawner(x, 24, "venusmod:venus_skeleton");
            t.put(x, 4, 29, "minecraft:chest",
                    VenusAssetGenerator.properties("facing", "north", "type", "single", "waterlogged", "false"),
                    VenusAssetGenerator.tags("id", VenusAssetGenerator.text("minecraft:chest"),
                            "LootTable", VenusAssetGenerator.text("venusmod:chests/venus_general_keep")));
        }
        for (int x = 9; x <= 31; x++) for (int z = 36; z <= 57; z++) {
            int radius = Math.max(Math.abs(x - 20), Math.abs(z - 47));
            if (radius == 8 || radius == 10) t.put(x, 3, z, "minecraft:polished_blackstone_bricks");
        }
        for (int x : new int[]{5, 35}) for (int z : new int[]{39, 53}) {
            t.box(x - 1, 4, z - 1, x + 1, 4, z + 1, brick);
            t.box(x, 5, z, x, 12, z, "minecraft:polished_basalt", VenusAssetGenerator.properties("axis", "y"));
            t.put(x, 13, z, "minecraft:ochre_froglight", VenusAssetGenerator.properties("axis", "y"), null);
        }
        t.box(18, 4, 56, 22, 5, 57, brick);
        t.box(19, 6, 57, 21, 8, 57, "minecraft:gold_block");
        altar(t, 20, 47, "venusmod:venus_general_altar");

        // 四隅の塔・閉じた窓・金の門飾りで従来の砦と見分けられる外観にする。
        for (int x : new int[]{0, 34}) for (int z : new int[]{4, 52}) {
            t.box(x, 15, z, x + 6, 17, z + 6, brick);
            t.box(x, 18, z, x + 6, 18, z + 6, wall);
            t.put(x + 3, 19, z + 3, "minecraft:ochre_froglight", VenusAssetGenerator.properties("axis", "y"), null);
        }
        for (int x : new int[]{0, 40}) for (int z : new int[]{11, 24, 42})
            t.box(x, 7, z, x, 10, z + 3, "minecraft:yellow_stained_glass");
        t.box(16, 4, 4, 16, 10, 4, "minecraft:polished_blackstone_bricks");
        t.box(24, 4, 4, 24, 10, 4, "minecraft:polished_blackstone_bricks");
        t.box(16, 10, 4, 24, 10, 4, "minecraft:gold_block");
        t.write(root.resolve("data/venusmod/structure/venus_general_fortress.nbt"));
        System.out.println("Generated Venus General fortress (41x20x59)");
    }

    private static void aphroditeSanctum(Path root) throws IOException {
        String quartz = "minecraft:quartz_bricks", blackstone = "minecraft:polished_blackstone_bricks";
        var t = hall(49, 24, 57, 18, blackstone, quartz, 20, 28, 10);
        t.box(1, 3, 5, 47, 3, 55, "minecraft:smooth_quartz");
        t.box(22, 3, 5, 26, 3, 53, blackstone);
        t.box(24, 3, 5, 24, 3, 53, "minecraft:gold_block");
        t.box(1, 4, 24, 47, 17, 24, quartz);
        t.box(18, 4, 24, 30, 12, 24, AIR);
        t.box(17, 13, 24, 31, 13, 24, "minecraft:gold_block");

        // 前室と、遮蔽制御装置・圧力コア3基を持つ主神殿。
        t.spawner(9, 16, "venusmod:venus_skeleton");
        t.spawner(39, 16, "venusmod:venus_enderman");
        for (int x : new int[]{7, 41}) for (int z : new int[]{10, 20, 33, 52}) {
            t.box(x - 1, 4, z - 1, x + 1, 4, z + 1, blackstone);
            t.box(x, 5, z, x, 15, z, "minecraft:quartz_pillar", VenusAssetGenerator.properties("axis", "y"));
            t.box(x - 1, 16, z - 1, x + 1, 16, z + 1, quartz);
            t.put(x, 17, z, "minecraft:sea_lantern");
        }
        for (int x = 10; x <= 38; x++) for (int z = 27; z <= 55; z++) {
            double radius = Math.hypot(x - 24, z - 41);
            if (radius >= 12.5D && radius <= 14.0D) t.put(x, 3, z, "minecraft:crying_obsidian");
        }
        t.box(22, 3, 27, 26, 3, 31, "minecraft:gold_block");
        t.put(24, 4, 29, "venusmod:pressure_shield_controller", VenusAssetGenerator.properties("powered", "false"),
                VenusAssetGenerator.tags("id", VenusAssetGenerator.text("venusmod:pressure_shield_controller"),
                        "Charge", VenusAssetGenerator.integer(0)));
        for (int[] p : new int[][]{{12, 39}, {36, 39}, {24, 51}}) {
            t.box(p[0] - 1, 3, p[1] - 1, p[0] + 1, 3, p[1] + 1, "minecraft:crying_obsidian");
            t.put(p[0], 4, p[1], "venusmod:pressure_core");
            t.box(p[0], 5, p[1], p[0], 8, p[1], "minecraft:end_rod", VenusAssetGenerator.properties("facing", "up"));
        }
        altar(t, 24, 41, "venusmod:aphrodite_altar");

        // ガラスで塞いだ天窓と側窓。高い屋根でも外壁の隙間は作らない。
        t.box(15, 18, 30, 33, 18, 51, "minecraft:light_blue_stained_glass");
        for (int x : new int[]{0, 48}) for (int z : new int[]{10, 29, 43})
            t.box(x, 7, z, x, 13, z + 5, "minecraft:light_blue_stained_glass");
        for (int x : new int[]{4, 44}) for (int z : new int[]{8, 52}) {
            t.box(x - 1, 19, z - 1, x + 1, 19, z + 1, blackstone);
            t.box(x, 20, z, x, 21, z, "minecraft:quartz_pillar", VenusAssetGenerator.properties("axis", "y"));
            t.put(x, 22, z, "minecraft:gold_block");
            t.put(x, 23, z, "minecraft:sea_lantern");
        }
        t.box(19, 4, 4, 19, 11, 4, blackstone);
        t.box(29, 4, 4, 29, 11, 4, blackstone);
        t.box(19, 11, 4, 29, 11, 4, "minecraft:gold_block");
        t.write(root.resolve("data/venusmod/structure/aphrodite_sanctum.nbt"));
        System.out.println("Generated Aphrodite sanctum (49x24x57)");
    }

    private static void altar(VenusAssetGenerator.Template t, int x, int z, String block) {
        t.put(x, 4, z, block, Map.of(), VenusAssetGenerator.tags("id", VenusAssetGenerator.text("venusmod:boss_altar")));
    }
}
