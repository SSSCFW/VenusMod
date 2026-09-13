import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/** 第二段階の元データ。依存なしでPNG・モデル・レシピ・鉱石配置を再現可能に生成する。 */
public final class Phase2AssetGenerator {
    private static Path root;
    private static final String NS = "venusmod:";
    private static Map<String, Object> m(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Odd key/value list");
        for (int i = 0; i < pairs.length; i += 2) {
            String key = (String) pairs[i];
            if (map.put(key, pairs[i + 1]) != null) throw new IllegalArgumentException("Duplicate key: " + key);
        }
        return map;
    }
    private static String json(Object value) {
        if (value instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof List<?> list) return list.stream().map(Phase2AssetGenerator::json).collect(Collectors.joining(",", "[", "]"));
        if (value instanceof Map<?, ?> map) return map.entrySet().stream().map(e -> json(e.getKey()) + ":" + json(e.getValue())).collect(Collectors.joining(",", "{", "}"));
        throw new IllegalArgumentException("Unsupported JSON value " + value);
    }
    private static void document(String path, Object value) throws IOException {
        Path file = root.resolve(path + ".json"); Files.createDirectories(file.getParent()); Files.writeString(file, json(value) + "\n");
    }
    private static void png(String name, BufferedImage image) throws IOException {
        Path file = root.resolve("assets/venusmod/textures/" + name + ".png"); Files.createDirectories(file.getParent());
        if (!ImageIO.write(image, "png", file.toFile())) throw new IOException("PNG encoder missing");
    }
    private static Map<String, Object> item(String name) { return m("item", name); }
    private static Map<String, Object> result(String name, int count) { return m("id", NS + name, "count", count); }
    private static void recipe(String name, Object data) throws IOException { document("data/venusmod/recipe/" + name, data); }
    private static void shaped(String name, List<String> pattern, Map<String, Object> keys, boolean create) throws IOException {
        Map<String, Object> data = m("type", "minecraft:crafting_shaped", "pattern", pattern, "key", keys, "result", result(name, 1));
        if (create) data.put("neoforge:conditions", List.of(m("type", "neoforge:mod_loaded", "modid", "create")));
        recipe(name, data);
    }
    private static void simpleDrop(String name, boolean create) throws IOException {
        Map<String, Object> data = m("type", "minecraft:block", "pools", List.of(m("rolls", 1,
                "conditions", List.of(m("condition", "minecraft:survives_explosion")),
                "entries", List.of(m("type", "minecraft:item", "name", NS + name)))));
        if (create) data.put("neoforge:conditions", List.of(m("type", "neoforge:mod_loaded", "modid", "create")));
        document("data/venusmod/loot_table/blocks/" + name, data);
    }
    private static void ore(String name, String drop, String replace, int veins, int size, int min, int max) throws IOException {
        document("data/venusmod/worldgen/configured_feature/" + name, m("type", "minecraft:ore", "config",
                m("size", size, "discard_chance_on_air_exposure", 0.0, "targets", List.of(m("target",
                        m("predicate_type", "minecraft:tag_match", "tag", replace), "state", m("Name", NS + name))))));
        document("data/venusmod/worldgen/placed_feature/" + name, m("feature", NS + name, "placement", List.of(
                m("type", "minecraft:count", "count", veins), m("type", "minecraft:in_square"),
                m("type", "minecraft:height_range", "height", m("type", "minecraft:uniform", "min_inclusive", m("absolute", min), "max_inclusive", m("absolute", max))),
                m("type", "minecraft:biome"))));
        document("data/venusmod/loot_table/blocks/" + name, m("type", "minecraft:block", "pools", List.of(m("rolls", 1, "entries", List.of(
                m("type", "minecraft:alternatives", "children", List.of(
                        m("type", "minecraft:item", "name", NS + name, "conditions", List.of(m("condition", "minecraft:match_tool", "predicate",
                                m("predicates", m("minecraft:enchantments", List.of(m("enchantments", "minecraft:silk_touch", "levels", m("min", 1)))))))),
                        m("type", "minecraft:item", "name", NS + drop, "functions", List.of(
                                m("function", "minecraft:apply_bonus", "enchantment", "minecraft:fortune", "formula", "minecraft:ore_drops"),
                                m("function", "minecraft:explosion_decay"))))))))));
    }
    private static void data() throws IOException {
        for (String name : List.of("sulfur_crystal", "venesite", "pressure_alloy_blend", "pressure_alloy_ingot", "portable_life_support",
                "pressure_helmet", "pressure_chestplate", "pressure_leggings", "pressure_boots", "acid_condensate_bucket")) {
            document("assets/venusmod/models/item/" + name, m("parent", "minecraft:item/generated", "textures", m("layer0", NS + "item/" + name)));
        }
        for (String block : List.of("sulfur_ore", "venesite_ore", "venus_glass")) {
            document("assets/venusmod/blockstates/" + block, m("variants", m("", m("model", NS + "block/" + block))));
            Map<String, Object> model = m("parent", "minecraft:block/cube_all", "textures", m("all", NS + "block/" + block));
            if (block.equals("venus_glass")) model.put("render_type", "minecraft:translucent");
            document("assets/venusmod/models/block/" + block, model);
            document("assets/venusmod/models/item/" + block, m("parent", NS + "block/" + block));
        }
        document("assets/venusmod/models/item/atmospheric_condenser", m("parent", NS + "block/atmospheric_condenser"));
        // 流体は専用レンダラーが描画するので静的モデルは空。
        document("assets/venusmod/blockstates/acid_condensate", m("variants", m("", m("model", "minecraft:block/water"))));
        document("data/venusmod/tags/item/environment_suit", m("replace", false, "values", List.of(NS + "pressure_helmet", NS + "pressure_chestplate", NS + "pressure_leggings", NS + "pressure_boots")));
        document("data/c/tags/item/ingots/pressure_alloy", m("replace", false, "values", List.of(NS + "pressure_alloy_ingot")));
        document("data/minecraft/tags/block/needs_iron_tool", m("replace", false, "values", List.of(NS + "venesite_ore")));
        ore("sulfur_ore", "sulfur_crystal", "minecraft:stone_ore_replaceables", 10, 8, 32, 160);
        ore("venesite_ore", "venesite", "minecraft:deepslate_ore_replaceables", 5, 4, -56, 16);
        simpleDrop("atmospheric_condenser", true); simpleDrop("venus_glass", false);
        shaped("portable_life_support", List.of("IGI", "RBR", "IGI"), m("I", item("minecraft:iron_ingot"), "G", item("minecraft:glass"),
                "R", item("minecraft:redstone"), "B", item("minecraft:gold_block")), false);
        recipe("pressure_alloy_blend", m("type", "minecraft:crafting_shapeless", "ingredients", List.of(item(NS + "nickel_ingot"), item(NS + "nickel_ingot"),
                item("minecraft:iron_ingot"), item("minecraft:iron_ingot"), item(NS + "sulfur_crystal")), "result", result("pressure_alloy_blend", 4)));
        for (String type : List.of("smelting", "blasting")) recipe("pressure_alloy_" + type, m("type", "minecraft:" + type, "ingredient", item(NS + "pressure_alloy_blend"),
                "result", result("pressure_alloy_ingot", 1), "experience", 0.7, "cookingtime", type.equals("smelting") ? 200 : 100));
        Map<String, Object> alloy = m("A", item(NS + "pressure_alloy_ingot"));
        shaped("pressure_helmet", List.of("AAA", "AGA"), m("A", item(NS + "pressure_alloy_ingot"), "G", item("minecraft:glass")), false);
        shaped("pressure_chestplate", List.of("A A", "AAA", "AAA"), alloy, false);
        shaped("pressure_leggings", List.of("AAA", "A A", "A A"), alloy, false);
        shaped("pressure_boots", List.of("A A", "A A"), alloy, false);
        shaped("atmospheric_condenser", List.of("NGN", "AMA", "NCN"), m("N", item(NS + "nickel_ingot"), "G", item("minecraft:glass"),
                "A", item("create:andesite_alloy"), "M", item("create:precision_mechanism"), "C", item("create:andesite_casing")), true);
        recipe("venus_glass", m("type", "minecraft:crafting_shapeless", "ingredients", List.of(item("minecraft:glass"), item("minecraft:glass"), item("minecraft:glass"), item("minecraft:glass"),
                item(NS + "venesite"), item(NS + "acid_condensate_bucket")), "result", result("venus_glass", 8)));
    }
    private static void textures() throws IOException {
        for (String ore : List.of("sulfur_ore", "venesite_ore")) {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int hash = Math.floorMod(x * 2347 + y * 137 + x * y * 13, 31);
                int grey = (ore.equals("sulfur_ore") ? 104 : 49) + hash;
                int color = 0xff000000 | grey << 16 | grey << 8 | grey;
                if (((x - 4) * (x - 4) + (y - 4) * (y - 4) < 8) || ((x - 11) * (x - 11) + (y - 10) * (y - 10) < 9) || (x > 2 && x < 6 && y > 11 && y < 14))
                    color = ore.equals("sulfur_ore") ? 0xffd6b023 + (hash << 16) + (hash << 8) : 0xff6b2450 + (hash << 16) + (hash << 8) + hash;
                image.setRGB(x, y, color);
            }
            png("block/" + ore, image);
        }
        BufferedImage glass = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) glass.setRGB(x, y,
                x == 0 || y == 0 || x == 15 || y == 15 ? 0xffa58134 : (x + y == 8 || x + y == 9 || x + y == 24) ? 0x88fff1ae : 0x24edca68);
        png("block/venus_glass", glass);
        for (String name : List.of("sulfur_crystal", "venesite", "pressure_alloy_blend", "pressure_alloy_ingot", "portable_life_support",
                "pressure_helmet", "pressure_chestplate", "pressure_leggings", "pressure_boots", "acid_condensate_bucket")) {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 1; y < 15; y++) for (int x = 1; x < 15; x++) {
                boolean draw; int color = 0xffc6a54d;
                if (name.endsWith("helmet")) draw = x >= 3 && x <= 12 && y >= 3 && y <= 10 && !(y >= 8 && x >= 5 && x <= 10);
                else if (name.endsWith("chestplate")) draw = y >= 3 && y <= 13 && !(y <= 5 && x >= 6 && x <= 9) && (y <= 7 || x >= 4 && x <= 11);
                else if (name.endsWith("leggings")) draw = x >= 4 && x <= 11 && y >= 2 && y <= 14 && (y <= 6 || x <= 6 || x >= 9);
                else if (name.endsWith("boots")) draw = y >= 4 && y <= 12 && (x >= 3 && x <= 6 || x >= 9 && x <= 12);
                else if (name.equals("portable_life_support")) { draw = x >= 3 && x <= 12 && y >= 2 && y <= 14; color = x >= 7 && x <= 8 ? 0xff272e35 : y >= 5 && y <= 7 ? 0xff70daba : 0xffc7cbd0; }
                else if (name.endsWith("bucket")) { draw = y >= 6 && y <= 13 && x >= 3 + (y - 6) / 4 && x <= 12 - (y - 6) / 4; color = y <= 8 ? 0xffd0b945 : 0xff8c9399; }
                else if (name.endsWith("ingot")) { draw = y >= 5 && y <= 11 && x >= Math.max(2, 5 - (y - 5)) && x <= 13; color = y <= 7 ? 0xffe4d8a3 : 0xff9d9675; }
                else if (name.endsWith("blend")) { draw = y >= 7 && y <= 13 && Math.abs(x - 8) <= y - 6; color = (x + y) % 3 == 0 ? 0xffd6b023 : 0xff969b95; }
                else { draw = Math.abs(x - 8) + Math.abs(y - 8) <= 6; color = name.equals("sulfur_crystal") ? 0xffedce3a : 0xffbd5384; }
                if (draw) {
                    if ((x + y) % 5 == 0) color = 0xff000000 | ((color & 0xfefefe) >>> 1);
                    image.setRGB(x, y, color);
                }
            }
            png("item/" + name, image);
        }
        for (int layer = 1; layer <= 2; layer++) {
            BufferedImage suit = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) {
                int color = x % 8 == 0 || y % 8 == 0 ? 0xff4c4a39 : 0xffb5a474;
                if (layer == 1 && x >= 9 && x <= 14 && y >= 10 && y <= 12) color = 0xff29494d;
                suit.setRGB(x, y, color);
            }
            png("models/armor/pressure_suit_layer_" + layer, suit);
        }
    }
    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Expected output resource directory");
        root = Path.of(args[0]); data(); textures();
        System.out.println("Generated phase 2 materials, armor, recipes, worldgen and textures");
    }
}
