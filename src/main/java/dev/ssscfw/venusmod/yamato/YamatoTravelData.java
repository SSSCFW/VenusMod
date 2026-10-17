package dev.ssscfw.venusmod.yamato;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** 閻魔刀の地点・履歴・フォルダ・設定を、アイテムやプレイヤー個体から独立してUUID別に保存する。 */
public final class YamatoTravelData extends SavedData {
    private static final String DATA_NAME = "venusmod_yamato_travel";
    private final Map<UUID, PlayerDestinations> players = new LinkedHashMap<>();

    public static YamatoTravelData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(YamatoTravelData::new, YamatoTravelData::load, null), DATA_NAME);
    }

    /** 手動一覧は保存順、履歴は最新先頭。sortは手動一覧のクライアント表示だけに使用する。 */
    public CompoundTag snapshot(UUID owner) {
        PlayerDestinations book = players.get(owner);
        ListTag entries = new ListTag();
        ListTag history = new ListTag();
        if (book != null) {
            for (CompoundTag entry : book.entries) entries.add(entry.copy());
            for (CompoundTag entry : book.history) history.add(entry.copy());
        }
        CompoundTag result = new CompoundTag();
        result.put("entries", entries);
        result.put("history", history);
        result.put("settings", settings(owner));
        result.putInt("sort", book == null ? 0 : book.sort);
        return result;
    }

    /** 存在する所有者本人の地点だけを返す。取得側の変更は保存データへ伝播しない。 */
    public CompoundTag destination(UUID owner, UUID id) {
        PlayerDestinations book = players.get(owner);
        CompoundTag entry = entry(book, id);
        if (entry == null) entry = historyEntry(book, id);
        return entry == null || entry.getBoolean("folder") ? null : entry.copy();
    }

    public CompoundTag settings(UUID owner) {
        PlayerDestinations book = players.get(owner);
        CompoundTag result = new CompoundTag();
        result.putInt("access", book == null ? YamatoTravelRules.DEFAULT_ACCESS : book.access);
        result.putInt("lifetime", book == null ? YamatoTravelRules.DEFAULT_LIFETIME : book.lifetime);
        result.putInt("seconds", book == null ? YamatoTravelRules.DEFAULT_SECONDS : book.seconds);
        result.putDouble("size", book == null ? YamatoTravelRules.DEFAULT_SIZE : book.size);
        return result;
    }

    /** 成功した開通の出発端だけを、サーバースレッドから追記する。失敗した開通では呼び出さない。 */
    public boolean recordOpening(UUID owner, String name, String dimension, double x, double y, double z,
                                 float yaw, float pitch) {
        if (owner == null || dimension == null || !finitePosition(x, y, z, yaw, pitch)) return false;
        ResourceLocation location = ResourceLocation.tryParse(dimension);
        if (location == null) return false;
        PlayerDestinations book = players.computeIfAbsent(owner, ignored -> new PlayerDestinations());
        UUID id;
        do { id = UUID.randomUUID(); } while (entry(book, id) != null || historyEntry(book, id) != null);
        CompoundTag opening = new CompoundTag();
        opening.putUUID("id", id);
        opening.putString("parent", "");
        opening.putString("name", YamatoTravelRules.historyName(name, x, y, z));
        opening.putBoolean("folder", false);
        opening.putString("dimension", location.toString());
        opening.putDouble("x", x);
        opening.putDouble("y", y);
        opening.putDouble("z", z);
        opening.putFloat("yaw", yaw);
        opening.putFloat("pitch", pitch);
        opening.putLong("created", System.currentTimeMillis());
        book.history.add(0, opening);
        trimHistory(book);
        setDirty();
        return true;
    }

    /**
     * サーバースレッドから実行する編集入口。全検査を終えてから変更し、拒否時はfalseで内容を維持する。
     * ownerと現在地は送信NBTを使用せず、実際に要求したServerPlayerから採取する。
     */
    public boolean apply(ServerPlayer player, String action, CompoundTag request) {
        if (player == null || action == null || request == null) return false;
        UUID owner = player.getUUID();
        PlayerDestinations book = players.get(owner);
        if (book == null) book = new PlayerDestinations();
        boolean accepted = switch (action) {
            case "save" -> add(book, player, request, false);
            case "folder" -> add(book, player, request, true);
            case "rename" -> rename(book, request);
            case "move" -> move(book, request);
            case "reorder" -> reorder(book, request);
            case "delete" -> delete(book, request);
            case "settings" -> updateSettings(book, request);
            case "sort" -> updateSort(book, request);
            default -> false;
        };
        if (accepted) {
            players.putIfAbsent(owner, book);
            setDirty();
        }
        return accepted;
    }

    private static boolean add(PlayerDestinations book, ServerPlayer player, CompoundTag request, boolean folder) {
        if (book.entries.size() >= YamatoTravelRules.MAX_ENTRIES) return false;
        String name = name(request);
        String parent = parent(request);
        if (name == null || parent == null || !YamatoTravelRules.canUseParent(
                null, YamatoTravelRules.parseId(parent), parents(book), folders(book))) return false;
        double x = folder ? 0.0D : player.getX();
        double y = folder ? 0.0D : player.getY();
        double z = folder ? 0.0D : player.getZ();
        float yaw = folder ? 0.0F : player.getYRot();
        float pitch = folder ? 0.0F : player.getXRot();
        if (!finitePosition(x, y, z, yaw, pitch)) return false;
        UUID id;
        do { id = UUID.randomUUID(); } while (entry(book, id) != null || historyEntry(book, id) != null);
        CompoundTag entry = new CompoundTag();
        entry.putUUID("id", id);
        entry.putString("parent", parent);
        entry.putString("name", name);
        entry.putBoolean("folder", folder);
        entry.putString("dimension", folder ? "" : player.serverLevel().dimension().location().toString());
        entry.putDouble("x", x);
        entry.putDouble("y", y);
        entry.putDouble("z", z);
        entry.putFloat("yaw", yaw);
        entry.putFloat("pitch", pitch);
        entry.putLong("created", System.currentTimeMillis());
        book.entries.add(entry);
        return true;
    }

    private static boolean rename(PlayerDestinations book, CompoundTag request) {
        CompoundTag entry = entry(book, id(request, "id"));
        String name = name(request);
        if (entry == null || name == null) return false;
        entry.putString("name", name);
        return true;
    }

    private static boolean move(PlayerDestinations book, CompoundTag request) {
        UUID id = id(request, "id");
        CompoundTag entry = entry(book, id);
        String parent = parent(request);
        if (entry == null || parent == null || !YamatoTravelRules.canUseParent(
                id, YamatoTravelRules.parseId(parent), parents(book), folders(book))) return false;
        if (entry.getString("parent").equals(parent)) return true;
        entry.putString("parent", parent);
        // 移動元の子孫の親UUIDは保ち、移動先の兄弟順の末尾へ配置する。
        book.entries.remove(entry);
        book.entries.add(entry);
        return true;
    }

    private static boolean reorder(PlayerDestinations book, CompoundTag request) {
        if (!request.contains("delta", Tag.TAG_INT)) return false;
        UUID id = id(request, "id");
        List<UUID> order = book.entries.stream().map(entry -> entry.getUUID("id")).toList();
        int target = YamatoTravelRules.siblingTargetIndex(order, parents(book), id, request.getInt("delta"));
        if (target < 0) return false;
        Collections.swap(book.entries, order.indexOf(id), target);
        return true;
    }

    private static boolean delete(PlayerDestinations book, CompoundTag request) {
        UUID id = id(request, "id");
        if (entry(book, id) == null) return false;
        Set<UUID> deleted = YamatoTravelRules.descendants(id, parents(book));
        book.entries.removeIf(entry -> deleted.contains(entry.getUUID("id")));
        return true;
    }

    private static boolean updateSettings(PlayerDestinations book, CompoundTag request) {
        if (!request.contains("access", Tag.TAG_INT) || !request.contains("lifetime", Tag.TAG_INT)
                || !request.contains("seconds", Tag.TAG_INT)
                || (request.contains("size") && !request.contains("size", Tag.TAG_DOUBLE))) return false;
        int access = request.getInt("access");
        int lifetime = request.getInt("lifetime");
        int seconds = request.getInt("seconds");
        double size = request.contains("size") ? request.getDouble("size") : YamatoTravelRules.DEFAULT_SIZE;
        if (!YamatoTravelRules.validSettings(access, lifetime, seconds, size)) return false;
        book.access = access;
        book.lifetime = lifetime;
        book.seconds = seconds;
        book.size = size;
        return true;
    }

    private static boolean updateSort(PlayerDestinations book, CompoundTag request) {
        if (!request.contains("sort", Tag.TAG_INT) || !YamatoTravelRules.validSort(request.getInt("sort"))) return false;
        book.sort = request.getInt("sort");
        return true;
    }

    private static CompoundTag entry(PlayerDestinations book, UUID id) {
        if (book == null || id == null) return null;
        for (CompoundTag entry : book.entries) if (id.equals(entry.getUUID("id"))) return entry;
        return null;
    }

    private static CompoundTag historyEntry(PlayerDestinations book, UUID id) {
        if (book == null || id == null) return null;
        for (CompoundTag entry : book.history) if (id.equals(entry.getUUID("id"))) return entry;
        return null;
    }

    /** 履歴は最新先頭のため、上限を超えた末尾がFIFOで除去する最古の項目になる。 */
    private static void trimHistory(PlayerDestinations book) {
        if (book.history.size() > YamatoTravelRules.DEFAULT_HISTORY_LIMIT) {
            book.history.subList(YamatoTravelRules.DEFAULT_HISTORY_LIMIT, book.history.size()).clear();
        }
    }

    private static Map<UUID, UUID> parents(PlayerDestinations book) {
        Map<UUID, UUID> parents = new HashMap<>();
        for (CompoundTag entry : book.entries) {
            parents.put(entry.getUUID("id"), YamatoTravelRules.parseId(entry.getString("parent")));
        }
        return parents;
    }

    private static Set<UUID> folders(PlayerDestinations book) {
        Set<UUID> folders = new HashSet<>();
        for (CompoundTag entry : book.entries) if (entry.getBoolean("folder")) folders.add(entry.getUUID("id"));
        return folders;
    }

    private static UUID id(CompoundTag tag, String key) {
        if (tag.hasUUID(key)) return tag.getUUID(key);
        return tag.contains(key, Tag.TAG_STRING) ? YamatoTravelRules.parseId(tag.getString(key)) : null;
    }

    private static String name(CompoundTag tag) {
        return tag.contains("name", Tag.TAG_STRING) ? YamatoTravelRules.normalizeName(tag.getString("name")) : null;
    }

    /** 欠落した親はルートとし、明示された不正な親は拒否する。 */
    private static String parent(CompoundTag tag) {
        if (!tag.contains("parent")) return "";
        if (!tag.contains("parent", Tag.TAG_STRING)) return null;
        String value = tag.getString("parent");
        if (value.isEmpty()) return "";
        UUID id = YamatoTravelRules.parseId(value);
        return id == null ? null : id.toString();
    }

    private static boolean finitePosition(double x, double y, double z, float yaw, float pitch) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Float.isFinite(yaw) && Float.isFinite(pitch);
    }

    public static YamatoTravelData load(CompoundTag tag, HolderLookup.Provider registries) {
        YamatoTravelData data = new YamatoTravelData();
        if (tag == null) return data;
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            UUID owner = id(playerTag, "owner");
            if (owner == null) continue;
            PlayerDestinations book = data.players.get(owner);
            if (book == null) {
                book = new PlayerDestinations();
                CompoundTag settings = playerTag.getCompound("settings");
                if (settings.contains("access", Tag.TAG_INT) && YamatoTravelRules.validAccess(settings.getInt("access"))) {
                    book.access = settings.getInt("access");
                }
                if (settings.contains("lifetime", Tag.TAG_INT) && settings.getInt("lifetime") >= 0 && settings.getInt("lifetime") <= 2) {
                    book.lifetime = settings.getInt("lifetime");
                }
                if (settings.contains("seconds", Tag.TAG_INT) && settings.getInt("seconds") >= 1
                        && settings.getInt("seconds") <= YamatoTravelRules.MAX_SECONDS) book.seconds = settings.getInt("seconds");
                if (settings.contains("size", Tag.TAG_DOUBLE) && YamatoTravelRules.validSize(settings.getDouble("size"))) {
                    book.size = settings.getDouble("size");
                }
                if (playerTag.contains("sort", Tag.TAG_INT) && YamatoTravelRules.validSort(playerTag.getInt("sort"))) {
                    book.sort = playerTag.getInt("sort");
                }
                data.players.put(owner, book);
            }
            Set<UUID> existing = new HashSet<>();
            for (CompoundTag entry : book.entries) existing.add(entry.getUUID("id"));
            ListTag entries = playerTag.getList("entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) {
                CompoundTag entry = readEntry(entries.getCompound(j));
                if (entry != null && existing.add(entry.getUUID("id"))) book.entries.add(entry);
            }
        }
        // 全所有者行の手動地点を先に読み、履歴の重複UUIDで手動地点を隠さない。
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            PlayerDestinations book = data.players.get(id(playerTag, "owner"));
            if (book == null) continue;
            Set<UUID> existing = new HashSet<>();
            for (CompoundTag entry : book.entries) existing.add(entry.getUUID("id"));
            for (CompoundTag entry : book.history) existing.add(entry.getUUID("id"));
            ListTag history = playerTag.getList("history", Tag.TAG_COMPOUND);
            for (int j = 0; j < history.size() && book.history.size() < YamatoTravelRules.DEFAULT_HISTORY_LIMIT; j++) {
                CompoundTag opening = readEntry(history.getCompound(j));
                if (opening == null || opening.getBoolean("folder") || !existing.add(opening.getUUID("id"))) continue;
                opening.putString("parent", "");
                opening.putString("name", YamatoTravelRules.historyName(opening.getString("name"),
                        opening.getDouble("x"), opening.getDouble("y"), opening.getDouble("z")));
                book.history.add(opening);
            }
        }
        // 既存の正常な手動保存項目を件数上限で切り捨てない。手動上限は新規作成時だけに適用する。
        for (PlayerDestinations book : data.players.values()) repairParents(book);
        return data;
    }

    private static CompoundTag readEntry(CompoundTag source) {
        UUID id = id(source, "id");
        String name = name(source);
        if (id == null || name == null || !source.contains("folder", Tag.TAG_BYTE)) return null;
        boolean folder = source.getBoolean("folder");
        String dimension = "";
        double x = 0.0D, y = 0.0D, z = 0.0D;
        float yaw = 0.0F, pitch = 0.0F;
        if (!folder) {
            if (!source.contains("dimension", Tag.TAG_STRING)) return null;
            ResourceLocation location = ResourceLocation.tryParse(source.getString("dimension"));
            if (location == null) return null;
            for (String coordinate : List.of("x", "y", "z", "yaw", "pitch")) {
                if (!source.contains(coordinate, Tag.TAG_ANY_NUMERIC)) return null;
            }
            dimension = location.toString();
            x = source.getDouble("x");
            y = source.getDouble("y");
            z = source.getDouble("z");
            yaw = source.getFloat("yaw");
            pitch = source.getFloat("pitch");
            if (!finitePosition(x, y, z, yaw, pitch)) return null;
        }
        String parent = parent(source);
        CompoundTag result = new CompoundTag();
        result.putUUID("id", id);
        result.putString("parent", parent == null ? "" : parent);
        result.putString("name", name);
        result.putBoolean("folder", folder);
        result.putString("dimension", dimension);
        result.putDouble("x", x);
        result.putDouble("y", y);
        result.putDouble("z", z);
        result.putFloat("yaw", yaw);
        result.putFloat("pitch", pitch);
        result.putLong("created", Math.max(0L, source.getLong("created")));
        return result;
    }

    /** 破損した親だけを切り離し、正常な地点やフォルダとその子孫を捨てない。 */
    private static void repairParents(PlayerDestinations book) {
        Map<UUID, UUID> parents = parents(book);
        Set<UUID> folders = folders(book);
        for (CompoundTag entry : book.entries) {
            UUID id = entry.getUUID("id");
            UUID parent = parents.get(id);
            if (parent != null && !folders.contains(parent)) {
                entry.putString("parent", "");
                parents.put(id, null);
            }
        }
        for (CompoundTag entry : book.entries) {
            Set<UUID> visited = new HashSet<>();
            UUID cursor = entry.getUUID("id");
            while (cursor != null) {
                if (!visited.add(cursor)) {
                    entry(book, cursor).putString("parent", "");
                    parents.put(cursor, null);
                    break;
                }
                cursor = parents.get(cursor);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playerList = new ListTag();
        for (UUID owner : players.keySet()) {
            trimHistory(players.get(owner));
            CompoundTag player = snapshot(owner);
            player.putUUID("owner", owner);
            playerList.add(player);
        }
        tag.putInt("version", 2);
        tag.put("players", playerList);
        return tag;
    }

    private static final class PlayerDestinations {
        private final List<CompoundTag> entries = new ArrayList<>();
        private final List<CompoundTag> history = new ArrayList<>();
        private int access = YamatoTravelRules.DEFAULT_ACCESS;
        private int lifetime = YamatoTravelRules.DEFAULT_LIFETIME;
        private int seconds = YamatoTravelRules.DEFAULT_SECONDS;
        private double size = YamatoTravelRules.DEFAULT_SIZE;
        private int sort;
    }
}
