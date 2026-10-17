import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** 実際に生成したNBTを検査し、閉じた外壁・到達経路・ボス用設備を固定する。 */
public final class BossStructureChecks {
    private record Pos(int x, int y, int z) {
        Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    }
    private record Template(List<Integer> size, List<Map<String, Object>> palette,
                            Map<Pos, Map<String, Object>> blocks) {
        String name(Pos pos) {
            var block = blocks.get(pos);
            return block == null ? "minecraft:air" : (String) palette.get((Integer) block.get("state")).get("Name");
        }
        List<Pos> positions(String name) {
            return blocks.keySet().stream().filter(p -> name(p).equals(name)).toList();
        }
    }

    public static void main(String[] args) throws Exception {
        Path structures = Path.of(args[0]).resolve("data/venusmod/structure");
        check(structures, "venus_general_fortress", List.of(41, 20, 59), 14, 17, 23, 9,
                "venusmod:venus_general_altar", new Pos(20, 4, 47), 4, 2);
        check(structures, "aphrodite_sanctum", List.of(49, 24, 57), 18, 20, 28, 10,
                "venusmod:aphrodite_altar", new Pos(24, 4, 41), 2, 0);
        if (args.length > 1) {
            Path existing = Path.of(args[1]).resolve("data/venusmod/structure");
            checkExistingShell(existing, "venus_citadel", 9, 12, 33, 7);
            checkExistingShell(existing, "venus_general_keep", 11, 13, 35, 7);
            checkExistingShell(existing, "aphrodite_reactor", 13, 15, 32, 8);
        }
        System.out.println("Verified both generated boss structures: shell, entrances, paths, spawn clearance and arena equipment.");
    }

    private static void checkExistingShell(Path root, String id, int lowRoof, int highRoof, int step, int doorTop) throws Exception {
        Template t = read(root.resolve(id + ".nbt"));
        require(t.size.equals(List.of(31, 16, 51)), id + ": preserve existing template size");
        for (int x = 0; x < 31; x++) for (int z = 4; z < 51; z++) {
            int roof = z < step ? lowRoof : highRoof;
            solid(t, new Pos(x, 3, z), id);
            solid(t, new Pos(x, roof, z), id);
            for (int y = 4; y < roof; y++) {
                if (x != 0 && x != 30 && z != 4 && z != 50) continue;
                Pos p = new Pos(x, y, z);
                if (z == 4 && x >= 13 && x <= 17 && y <= doorTop)
                    require(t.name(p).equals("minecraft:air"), id + ": preserve entrance");
                else solid(t, p, id);
            }
            if (z == step) for (int y = lowRoof + 1; y < highRoof; y++) solid(t, new Pos(x, y, z), id);
        }
        for (int x = 13; x <= 17; x++) for (int z = 0; z < 4; z++)
            require(t.name(new Pos(x, z, z)).endsWith("_stairs"), id + ": preserve entrance stairs");
        if (id.equals("venus_citadel")) {
            for (int y = 4; y <= 5; y++) require(t.name(new Pos(15, y, 32)).equals("minecraft:iron_door"), id + ": preserve boss door");
            require(t.name(new Pos(14, 5, 31)).equals("minecraft:lever"), id + ": preserve door lever");
        } else {
            int doorway = id.equals("venus_general_keep") ? 34 : 31;
            for (int y = 4; y <= 7; y++) require(t.name(new Pos(15, y, doorway)).equals("minecraft:air"), id + ": preserve arena entrance");
        }
        System.out.println("PASS " + id + ": original shell is closed, doorways and stairs preserved");
    }

    private static void check(Path root, String id, List<Integer> size, int roof, int doorMin, int doorMax,
                              int doorTop, String altarName, Pos altar, int spawners, int chests) throws Exception {
        Path file = root.resolve(id + ".nbt");
        require(Files.isRegularFile(file), "Missing generated boss structure: " + id);
        Template t = read(file);
        require(t.size.equals(size), id + ": template dimensions");
        require(t.positions(altarName).equals(List.of(altar)), id + ": exactly one altar in the arena");
        require(t.positions("minecraft:spawner").size() == spawners, id + ": spawners");
        require(t.positions("minecraft:chest").size() == chests, id + ": treasure chests");
        require("venusmod:boss_altar".equals(nbt(t, altar).get("id")), id + ": altar block entity");
        int maxX = size.get(0) - 1, maxZ = size.get(2) - 1;
        // 天井・床とすべての外周面を調べ、正面の正式な入口だけを除外する。
        for (int x = 0; x <= maxX; x++) for (int z = 4; z <= maxZ; z++) {
            solid(t, new Pos(x, 3, z), id);
            solid(t, new Pos(x, roof, z), id);
            for (int y = 4; y < roof; y++) {
                if (x != 0 && x != maxX && z != 4 && z != maxZ) continue;
                Pos p = new Pos(x, y, z);
                if (z == 4 && x >= doorMin && x <= doorMax && y <= doorTop)
                    require(t.name(p).equals("minecraft:air"), id + ": blocked entrance " + p);
                else solid(t, p, id);
            }
        }
        for (int x = doorMin; x <= doorMax; x++) for (int z = 0; z < 4; z++)
            require(t.name(new Pos(x, z, z)).endsWith("_stairs"), id + ": entrance stairs");
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 1; y <= 3; y++)
            require(t.name(altar.offset(x, y, z)).equals("minecraft:air"), id + ": obstructed boss spawn");

        Set<Pos> visited = walk(t, new Pos((doorMin + doorMax) / 2, 4, 4));
        reachableNeighbour(visited, altar, id + ": altar unreachable from entrance");
        for (Pos chest : t.positions("minecraft:chest")) {
            reachableNeighbour(visited, chest, id + ": chest unreachable");
            require(nbt(t, chest).get("LootTable").equals("venusmod:chests/venus_general_keep"), id + ": chest loot");
        }
        for (Pos spawner : t.positions("minecraft:spawner")) {
            Map<?, ?> spawnData = (Map<?, ?>) nbt(t, spawner).get("SpawnData");
            require(((Map<?, ?>) spawnData.get("entity")).get("id").toString().startsWith("venusmod:venus_"), id + ": spawner entity");
        }
        if (id.equals("aphrodite_sanctum")) {
            List<Pos> cores = t.positions("venusmod:pressure_core");
            List<Pos> controllers = t.positions("venusmod:pressure_shield_controller");
            require(cores.size() == 3 && controllers.size() == 1, id + ": three cores and one controller required");
            List<Pos> equipment = new ArrayList<>(cores);
            equipment.addAll(controllers);
            for (Pos p : equipment) {
                int dx = p.x - altar.x, dz = p.z - altar.z;
                require(Math.abs(dx) <= 18 && Math.abs(dz) <= 18 && p.y >= altar.y - 5 && p.y <= altar.y + 8,
                        id + ": equipment outside boss scan " + p);
                reachableNeighbour(visited, p, id + ": inaccessible arena equipment " + p);
            }
            Pos controller = controllers.getFirst();
            require(nbt(t, controller).get("id").equals("venusmod:pressure_shield_controller"), id + ": controller block entity");
            require(nbt(t, controller).get("Charge").equals(0), id + ": controller must start uncharged");
            require(((Map<?, ?>) t.palette.get((Integer) t.blocks.get(controller).get("state")).get("Properties"))
                    .get("powered").equals("false"), id + ": controller must start unpowered");
            for (Pos p : neighbours(controller))
                require(t.name(p).equals("minecraft:air"), id + ": reserve adjacent controller space for power input");
        }
        System.out.println("PASS " + id + ": " + t.blocks.size() + " blocks, " + visited.size() + " walkable floor positions");
    }

    private static void solid(Template t, Pos p, String id) {
        require(t.blocks.containsKey(p) && !t.name(p).equals("minecraft:air"), id + ": hole in structure shell at " + p);
    }
    private static List<Pos> neighbours(Pos p) {
        return List.of(p.offset(-1, 0, 0), p.offset(1, 0, 0), p.offset(0, 0, -1), p.offset(0, 0, 1));
    }
    private static Set<Pos> walk(Template t, Pos start) {
        Set<Pos> visited = new HashSet<>();
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        queue.add(start); visited.add(start);
        while (!queue.isEmpty()) for (Pos p : neighbours(queue.removeFirst())) {
            if (p.x < 0 || p.x >= t.size.get(0) || p.z < 4 || p.z >= t.size.get(2)) continue;
            if (t.name(p).equals("minecraft:air") && t.name(p.offset(0, 1, 0)).equals("minecraft:air")
                    && !t.name(p.offset(0, -1, 0)).equals("minecraft:air") && visited.add(p)) queue.add(p);
        }
        return visited;
    }
    private static void reachableNeighbour(Set<Pos> visited, Pos target, String message) {
        require(neighbours(target).stream().anyMatch(visited::contains), message);
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> nbt(Template t, Pos p) {
        require(t.blocks.get(p).get("nbt") instanceof Map, "Missing block entity NBT at " + p);
        return (Map<String, Object>) t.blocks.get(p).get("nbt");
    }
    @SuppressWarnings("unchecked")
    private static Template read(Path path) throws IOException {
        try (var in = new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            require(in.readUnsignedByte() == 10 && in.readUTF().isEmpty(), "Invalid NBT root");
            Map<String, Object> root = (Map<String, Object>) value(in, 10);
            require(in.read() == -1 && ((List<?>) root.get("entities")).isEmpty(), "Unexpected template entities/data");
            List<Integer> size = (List<Integer>) root.get("size");
            List<Map<String, Object>> palette = (List<Map<String, Object>>) root.get("palette");
            Map<Pos, Map<String, Object>> blocks = new HashMap<>();
            for (Map<String, Object> block : (List<Map<String, Object>>) root.get("blocks")) {
                List<Integer> p = (List<Integer>) block.get("pos");
                Pos pos = new Pos(p.get(0), p.get(1), p.get(2));
                require(blocks.put(pos, block) == null, "Duplicate template position");
                for (int axis = 0; axis < 3; axis++) require(p.get(axis) >= 0 && p.get(axis) < size.get(axis), "Position outside template");
                int index = (Integer) block.get("state");
                require(index >= 0 && index < palette.size(), "Invalid block palette index");
            }
            return new Template(size, palette, blocks);
        }
    }
    private static Object value(DataInputStream in, int type) throws IOException {
        return switch (type) {
            case 2 -> (int) in.readShort();
            case 3 -> in.readInt();
            case 8 -> in.readUTF();
            case 9 -> {
                int child = in.readUnsignedByte(), count = in.readInt();
                require(count >= 0, "Invalid NBT list length");
                List<Object> values = new ArrayList<>();
                for (int i = 0; i < count; i++) values.add(value(in, child));
                yield values;
            }
            case 10 -> {
                Map<String, Object> values = new HashMap<>();
                int child;
                while ((child = in.readUnsignedByte()) != 0) {
                    String name = in.readUTF();
                    require(values.put(name, value(in, child)) == null, "Duplicate NBT key");
                }
                yield values;
            }
            default -> throw new IOException("Unsupported template NBT type: " + type);
        };
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
