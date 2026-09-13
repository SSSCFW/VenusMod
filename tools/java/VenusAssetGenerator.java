import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;

/** ビルド用。Minecraftクラスにも外部ライブラリにも依存しないPNG/NBT生成器。 */
public final class VenusAssetGenerator {
    private record Tag(int type, Object value) {}
    private record Values(int type, List<?> values) {}
    private record Pos(int x, int y, int z) {}
    private record State(String name, Map<String, String> properties) {}

    private static Tag text(String s) { return new Tag(8, s); }
    private static Tag integer(int n) { return new Tag(3, n); }
    private static Tag shortTag(int n) { return new Tag(2, n); }
    private static Tag compound(Map<String, Tag> value) { return new Tag(10, value); }
    private static Tag ints(int... values) {
        List<Integer> list = new ArrayList<>();
        for (int value : values) list.add(value);
        return new Tag(9, new Values(3, list));
    }
    private static Tag compounds(List<Map<String, Tag>> values) { return new Tag(9, new Values(10, values)); }
    private static Map<String, Tag> tags(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("Key/value pairs required");
        Map<String, Tag> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) map.put((String) entries[i], (Tag) entries[i + 1]);
        return map;
    }
    private static Map<String, String> properties(String... entries) {
        Map<String, String> map = new TreeMap<>();
        for (int i = 0; i < entries.length; i += 2) map.put(entries[i], entries[i + 1]);
        return map;
    }
    @SuppressWarnings("unchecked")
    private static void payload(DataOutputStream out, int type, Object value) throws IOException {
        switch (type) {
            case 2 -> out.writeShort((Integer) value);
            case 3 -> out.writeInt((Integer) value);
            case 8 -> out.writeUTF((String) value);
            case 9 -> {
                Values list = (Values) value;
                out.writeByte(list.type()); out.writeInt(list.values().size());
                for (Object child : list.values()) payload(out, list.type(), child);
            }
            case 10 -> {
                for (var entry : ((Map<String, Tag>) value).entrySet()) {
                    out.writeByte(entry.getValue().type()); out.writeUTF(entry.getKey());
                    payload(out, entry.getValue().type(), entry.getValue().value());
                }
                out.writeByte(0);
            }
            default -> throw new IllegalArgumentException("Unsupported NBT type " + type);
        }
    }
    private static void writePNG(Path root, String name, BufferedImage image) throws IOException {
        Path file = root.resolve("assets/venusmod/textures/" + name + ".png");
        Files.createDirectories(file.getParent());
        if (!ImageIO.write(image, "png", file.toFile())) throw new IOException("PNG writer unavailable");
    }
    private static void textures(Path root) throws IOException {
        BufferedImage portal = new BufferedImage(16, 256, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < 16; frame++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double phase = frame * Math.PI * 2 / 16;
            double wave = (Math.sin(x * Math.PI * 2 / 16 + phase + Math.sin(y * Math.PI * 2 / 16 - phase))
                    + Math.cos(y * Math.PI * 2 / 8 - phase + Math.cos(x * Math.PI * 2 / 16))) / 2;
            double light = (wave + 1) / 2;
            portal.setRGB(x, frame * 16 + y, (175 << 24) | ((int) (170 + 85 * light) << 16)
                    | ((int) (94 + 122 * light) << 8) | (int) (12 + 58 * light));
        }
        writePNG(root, "block/venus_portal", portal);
        BufferedImage core = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double radius = Math.hypot(x - 7.5, y - 7.5);
            int color;
            if (radius > 6.5) color = 0;
            else if (radius > 5.3) color = 0xff583310;
            else {
                double light = Math.max(0, 1 - Math.hypot(x - 6, y - 5.5) / 8);
                color = 0xffff0000 | ((int) (131 + 118 * light) << 8) | (int) (18 + 172 * light);
            }
            core.setRGB(x, y, color);
        }
        writePNG(root, "item/venus_core", core);
    }
    private static final class Template {
        final Map<State, Integer> indices = new LinkedHashMap<>();
        final List<Map<String, Tag>> palette = new ArrayList<>();
        final Map<Pos, Map<String, Tag>> blocks = new TreeMap<>(Comparator.comparingInt(Pos::y)
                .thenComparingInt(Pos::z).thenComparingInt(Pos::x));
        void put(int x, int y, int z, String name) { put(x, y, z, name, Map.of(), null); }
        void put(int x, int y, int z, String name, Map<String, String> properties, Map<String, Tag> nbt) {
            if (x < 0 || x >= 31 || y < 0 || y >= 16 || z < 0 || z >= 51) throw new IllegalArgumentException("Outside template");
            State state = new State(name, Map.copyOf(properties));
            int index = indices.computeIfAbsent(state, key -> {
                Map<String, Tag> entry = tags("Name", text(name));
                if (!properties.isEmpty()) {
                    Map<String, Tag> props = new LinkedHashMap<>();
                    new TreeMap<>(properties).forEach((k, v) -> props.put(k, text(v)));
                    entry.put("Properties", compound(props));
                }
                palette.add(entry);
                return palette.size() - 1;
            });
            Map<String, Tag> block = tags("pos", ints(x, y, z), "state", integer(index));
            if (nbt != null) block.put("nbt", compound(nbt));
            blocks.put(new Pos(x, y, z), block);
        }
        void box(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
            box(x0, y0, z0, x1, y1, z1, block, Map.of());
        }
        void box(int x0, int y0, int z0, int x1, int y1, int z1, String block, Map<String, String> properties) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
                put(x, y, z, block, properties, null);
        }
        void spawner(int x, int z, String entity) {
            put(x, 4, z, "minecraft:spawner", Map.of(), tags("id", text("minecraft:mob_spawner"),
                    "Delay", shortTag(20), "MinSpawnDelay", shortTag(240), "MaxSpawnDelay", shortTag(480),
                    "SpawnCount", shortTag(2), "MaxNearbyEntities", shortTag(6), "RequiredPlayerRange", shortTag(16),
                    "SpawnRange", shortTag(4), "SpawnData", compound(tags("entity", compound(tags("id", text(entity)))))));
        }
        void write(Path file) throws IOException {
            Files.createDirectories(file.getParent());
            Map<String, Tag> root = tags("DataVersion", integer(3955), "size", ints(31, 16, 51),
                    "palette", compounds(palette), "blocks", compounds(new ArrayList<>(blocks.values())), "entities", compounds(List.of()));
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
                out.writeByte(10); out.writeUTF(""); payload(out, 10, root);
            }
        }
    }
    private static void citadel(Path root) throws IOException {
        Template t = new Template();
        String brick = "minecraft:polished_blackstone_bricks", air = "minecraft:air";
        t.box(0, 0, 4, 30, 3, 50, brick); t.box(0, 4, 4, 30, 12, 50, air);
        t.box(0, 4, 4, 0, 9, 50, brick); t.box(30, 4, 4, 30, 9, 50, brick);
        t.box(0, 4, 4, 30, 9, 4, brick); t.box(0, 4, 50, 30, 12, 50, brick);
        t.box(0, 9, 4, 30, 9, 32, brick); t.box(0, 12, 33, 30, 12, 50, brick);
        t.box(0, 10, 33, 0, 12, 50, brick); t.box(30, 10, 33, 30, 12, 50, brick);
        t.box(12, 4, 5, 12, 8, 31, brick); t.box(18, 4, 5, 18, 8, 31, brick);
        t.box(1, 4, 17, 11, 8, 17, brick); t.box(19, 4, 17, 29, 8, 17, brick);
        for (int x : new int[]{12, 18}) for (int z : new int[]{9, 23}) t.box(x, 4, z, x, 6, z + 2, air);
        t.box(13, 4, 4, 17, 7, 4, air);
        for (int z = 0; z < 4; z++) {
            if (z > 0) t.box(13, 0, z, 17, z - 1, z, brick);
            t.box(13, z, z, 17, z, z, "minecraft:polished_blackstone_brick_stairs",
                    properties("facing", "south", "half", "bottom", "shape", "straight", "waterlogged", "false"));
        }
        t.box(1, 4, 32, 29, 8, 32, brick);
        for (int y = 4; y <= 5; y++) t.put(15, y, 32, "minecraft:iron_door",
                properties("facing", "south", "half", y == 4 ? "lower" : "upper", "hinge", "left", "open", "false", "powered", "false"), null);
        t.put(14, 5, 31, "minecraft:lever", properties("face", "wall", "facing", "north", "powered", "false"), null);
        for (int x : new int[]{3, 21}) {
            t.box(x, 3, 20, x + 5, 3, 28, "minecraft:magma_block");
            t.box(x + 2, 3, 20, x + 3, 3, 28, brick);
        }
        for (int x : new int[]{3, 27}) for (int z : new int[]{35, 47}) {
            t.box(x, 4, z, x, 11, z, "minecraft:polished_basalt", properties("axis", "y"));
            t.put(x, 10, z, "minecraft:gold_block");
        }
        for (int x : new int[]{0, 28}) for (int z : new int[]{4, 48}) {
            t.box(x, 10, z, x + 2, 14, z + 2, brick);
            t.put(x + 1, 15, z + 1, "minecraft:ochre_froglight", properties("axis", "y"), null);
        }
        t.spawner(5, 12, "venusmod:venus_zombie"); t.spawner(25, 12, "venusmod:venus_skeleton");
        for (int x : new int[]{5, 25}) t.put(x, 4, 25, "minecraft:chest",
                properties("facing", "north", "type", "single", "waterlogged", "false"),
                tags("id", text("minecraft:chest"), "LootTable", text("venusmod:chests/venus_citadel")));
        t.put(15, 4, 42, "venusmod:guardian_altar", Map.of(), tags("id", text("venusmod:guardian_altar")));
        t.write(root.resolve("data/venusmod/structure/venus_citadel.nbt"));
        System.out.println("Generated Venus assets: 16-frame portal, core icon, citadel with " + t.blocks.size() + " blocks");
    }
    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: VenusAssetGenerator <resource-output-directory>");
        Path output = Path.of(args[0]);
        textures(output); citadel(output);
    }
}
