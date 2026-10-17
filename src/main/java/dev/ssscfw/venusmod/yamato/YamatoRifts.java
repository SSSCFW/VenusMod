package dev.ssscfw.venusmod.yamato;

import dev.ssscfw.venusmod.compat.YamatoCompat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 両端を一つの対として保存・同期し、転送の可否はサーバーだけで決める。 */
public final class YamatoRifts extends SavedData {
    private static final TicketType<UUID> RIFT_TICKET = TicketType.<UUID>create("venusmod_yamato_rift", Comparator.naturalOrder(), 60);
    private static final int MAX_ACTIVE_PER_PLAYER = 64;
    private final Map<UUID, Pair> pairs = new LinkedHashMap<>();
    private final YamatoRiftRules.ArrivalLocks arrivalLocks = new YamatoRiftRules.ArrivalLocks();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> viewers = new HashMap<>();
    private final Map<UUID, Budget> requests = new HashMap<>();
    private boolean syncNeeded = true;
    private boolean ticketsRestored;

    public static YamatoRifts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(YamatoRifts::new, YamatoRifts::load, null), "venusmod_yamato_rifts");
    }

    public static void onTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        get(server).tick(server, server.overworld().getGameTime());
    }

    public boolean allowRequest(UUID player, long tick) {
        Budget budget = requests.get(player);
        if (budget == null || tick - budget.start >= 20) {
            requests.put(player, new Budget(tick, 1)); return true;
        }
        if (budget.used >= 20) return false;
        budget.used++; return true;
    }

    public void watch(ServerPlayer player) {
        viewers.put(player.getUUID(), player.server.overworld().getGameTime() + 100);
    }

    public void requestTravel(ServerPlayer player, UUID destinationId) {
        if (destinationId == null || pending.containsKey(player.getUUID())) return;
        CompoundTag destination = YamatoTravelData.get(player.server).destination(player.getUUID(), destinationId);
        if (destination == null) { YamatoNetwork.message(player, "missing_destination"); return; }
        if (ownedCount(player.getUUID()) >= MAX_ACTIVE_PER_PLAYER) {
            YamatoNetwork.message(player, "too_many_rifts"); return;
        }
        ItemStack stack = YamatoCompat.heldYamato(player);
        if (stack.isEmpty()) return;
        Endpoint target = Endpoint.at(destination);
        Endpoint source = inFront(player);
        if (target == null || level(player.server, target) == null) {
            YamatoNetwork.message(player, "missing_dimension"); return;
        }
        if (source == null || !clearSite(player.server, target)) {
            YamatoNetwork.message(player, "obstructed"); return;
        }
        InteractionHand hand = player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        Pending opening = new Pending(player.getUUID(), hand, stack, player.level().dimension(),
                target, destination.getString("name"), YamatoTravelData.get(player.server).settings(player.getUUID()),
                player.server.overworld().getGameTime());
        pending.put(player.getUUID(), opening);
        player.stopUsingItem();
        YamatoCompat.playCut(player, 0);
        YamatoNetwork.sendCut(player, hand, 0);
    }

    /** 二振りが終わった時点で再検証し、同じtickに両端を追加する。 */
    private void advanceOpening(MinecraftServer server, long now) {
        Iterator<Pending> iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            Pending opening = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(opening.owner);
            if (player == null) { iterator.remove(); continue; }
            if (!player.isAlive() || player.isSpectator() || player.isRemoved()
                    || !player.level().dimension().equals(opening.dimension)
                    || player.getItemInHand(opening.hand) != opening.stack
                    || !YamatoCompat.isYamato(opening.stack)) {
                finish(player, opening.hand); iterator.remove(); continue;
            }
            long elapsed = now - opening.started;
            if (!opening.secondCut && elapsed >= YamatoCompat.SECOND_CUT_TICK) {
                opening.secondCut = true;
                YamatoCompat.playCut(player, 1);
                YamatoNetwork.sendCut(player, opening.hand, 1);
            }
            if (elapsed < YamatoCompat.OPEN_TICK) continue;
            Endpoint source = inFront(player);
            if (source == null || openPair(opening.owner, opening.name, source, opening.target,
                    opening.settings.getInt("access"), opening.settings.getInt("lifetime"),
                    opening.settings.getInt("seconds"), server) == null) YamatoNetwork.message(player, "obstructed");
            finish(player, opening.hand); iterator.remove();
        }
    }

    private static void finish(ServerPlayer player, InteractionHand hand) {
        YamatoCompat.finishCut(player);
        YamatoNetwork.sendCut(player, hand, -1);
    }

    public Pair openPair(UUID owner, String name, Endpoint source, Endpoint target,
                         int access, int lifetime, int seconds, MinecraftServer server) {
        if (owner == null || source == null || target == null || access < 0 || access > 3
                || lifetime < 0 || lifetime > 2 || seconds < 1 || seconds > 180
                || ownedCount(owner) >= MAX_ACTIVE_PER_PLAYER
                || !clearSite(server, source) || !clearSite(server, target)
                || source.dimension.equals(target.dimension) && source.feet.distanceToSqr(target.feet) < 1) return null;
        long now = server.overworld().getGameTime();
        Pair pair = new Pair(UUID.randomUUID(), owner, name, source, target, access, lifetime, seconds, now);
        pairs.put(pair.id, pair);
        holdTickets(server, pair, true);
        sound(server, source, SoundEvents.PORTAL_TRIGGER);
        sound(server, target, SoundEvents.PORTAL_TRIGGER);
        setDirty(); syncNeeded = true;
        return pair;
    }

    private long ownedCount(UUID owner) { return pairs.values().stream().filter(pair -> pair.owner.equals(owner)).count(); }

    public void close(UUID owner, UUID pairId, MinecraftServer server) {
        Pair pair = pairs.get(pairId);
        if (pair == null || !pair.owner.equals(owner)) return;
        long now = server.overworld().getGameTime();
        long closes = pair.closesAt();
        pair.manualClose = closes < 0 ? now : Math.min(now, closes);
        setDirty(); syncNeeded = true;
    }

    public void closeAll(UUID owner, MinecraftServer server) {
        for (Pair pair : pairs.values()) if (pair.owner.equals(owner)) close(owner, pair.id, server);
        Pending opening = pending.remove(owner);
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (opening != null && player != null) finish(player, opening.hand);
    }

    public void tick(MinecraftServer server, long now) {
        advanceOpening(server, now);
        Set<UUID> transferred = new HashSet<>();
        Iterator<Pair> iterator = pairs.values().iterator();
        while (iterator.hasNext()) {
            Pair pair = iterator.next();
            long closes = pair.closesAt();
            if (closes >= 0 && now >= closes + YamatoRiftGeometry.FADE_TICKS) {
                holdTickets(server, pair, false);
                arrivalLocks.removeEndpoint(pair.source.id);
                arrivalLocks.removeEndpoint(pair.target.id);
                iterator.remove(); setDirty(); syncNeeded = true; continue;
            }
            if (level(server, pair.source) == null || level(server, pair.target) == null) {
                if (pair.manualClose < 0) { pair.manualClose = now; setDirty(); syncNeeded = true; }
                continue;
            }
            if (!ticketsRestored || now % 20 == 0) holdTickets(server, pair, true);
            releaseExited(server, pair.source);
            releaseExited(server, pair.target);
            if (now <= pair.openedAt || closes >= 0 && now >= closes) continue;
            processEndpoint(server, pair, pair.source, pair.target, transferred, now);
            processEndpoint(server, pair, pair.target, pair.source, transferred, now);
        }
        ticketsRestored = true;
        // 別次元から戻ったクライアント・描画範囲へ近付いたクライアントにも再送する。
        if (syncNeeded || now % 20 == 0 && !pairs.isEmpty()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) YamatoNetwork.sendWorld(player);
            syncNeeded = false;
        }
        if (now % 20 == 0) {
            viewers.entrySet().removeIf(entry -> entry.getValue() < now || server.getPlayerList().getPlayer(entry.getKey()) == null);
            for (UUID owner : viewers.keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(owner);
                if (player != null) YamatoNetwork.sendBook(player, false);
            }
            requests.entrySet().removeIf(entry -> now - entry.getValue().start > 200);
        }
    }

    private void processEndpoint(MinecraftServer server, Pair pair, Endpoint from, Endpoint to, Set<UUID> transferred, long now) {
        ServerLevel source = level(server, from), target = level(server, to);
        if (source == null || target == null) return;
        double scale = YamatoRiftGeometry.scale(now, pair.openedAt, pair.closesAt());
        if (scale <= 0) return;
        List<Entity> candidates = source.getEntities((Entity)null, from.bounds(), entity -> entity.isAlive() && !entity.isSpectator());
        for (Entity entity : candidates) {
            if (entity.isRemoved() || transferred.contains(entity.getUUID()) || !entity.level().dimension().equals(source.dimension())
                    || arrivalLocks.blocked(entity.getUUID(), from.id) || !touching(entity.getBoundingBox(), from, scale)
                    || !allowed(pair, entity)) continue;
            // 許可された乗り物全体は根から一度だけ転送する。許可されない乗り物からは対象自身だけ降りられる。
            if (entity.isPassenger() && entity.getRootVehicle().getSelfAndPassengers().allMatch(member -> allowed(pair, member))) continue;
            List<Entity> group = entity.getSelfAndPassengers().toList();
            if (group.stream().anyMatch(member -> !allowed(pair, member) || !member.canUsePortal(true)
                    || !member.canChangeDimensions(source, target) || transferred.contains(member.getUUID())
                    || arrivalLocks.blocked(member.getUUID(), from.id))) continue;
            if (!safeGroup(target, entity, to.feet, 0, 0)) {
                if (entity instanceof ServerPlayer player && pair.warned.add(entity.getUUID())) YamatoNetwork.message(player, "obstructed");
                continue;
            }
            entity.stopRiding();
            Entity arrived = transferGroup(entity, new DimensionTransition(target, to.feet, Vec3.ZERO,
                    to.yaw, entity.getXRot(), result -> {
                        result.fallDistance = 0;
                        result.setDeltaMovement(Vec3.ZERO);
                        // UUIDは次元移動時にEntityが作り直されても同じ。乗客もコールバックを通る。
                        transferred.add(result.getUUID());
                        arrivalLocks.arrived(result.getUUID(), to.id);
                        blockOverlapping(result);
                        if (result instanceof ServerPlayer player) YamatoNetwork.sendWorld(player);
                    }));
            if (arrived == null) continue;
            // startRidingだけでは次tickまで座席位置へ動かないため、直ちに位置を確定する。
            positionPassengers(arrived);
            // 最終座席位置でも重複する全端をロックする。
            arrived.getSelfAndPassengers().forEach(member -> {
                arrivalLocks.arrived(member.getUUID(), to.id);
                transferred.add(member.getUUID()); blockOverlapping(member);
            });
            if (pair.firstTransfer < 0) { pair.firstTransfer = now; syncNeeded = true; }
            setDirty();
            sound(server, to, SoundEvents.ENDERMAN_TELEPORT);
        }
    }

    private static boolean allowed(Pair pair, Entity entity) {
        boolean friendly = entity instanceof Mob mob && !(entity instanceof Enemy)
                && entity.getType().getCategory() != MobCategory.MONSTER && mob.getTarget() == null
                && (!(entity instanceof NeutralMob neutral) || !neutral.isAngry());
        return YamatoRiftRules.canEnter(pair.access, pair.owner.equals(entity.getUUID()), entity instanceof Player, friendly);
    }

    private void releaseExited(MinecraftServer server, Endpoint endpoint) {
        for (UUID id : arrivalLocks.entities(endpoint.id)) {
            Entity entity = null;
            for (ServerLevel candidate : server.getAllLevels()) {
                entity = candidate.getEntity(id);
                if (entity != null) break;
            }
            // 未ログイン/未読込を「離れた」と扱わない。再ログイン直後の往復を防ぐ。
            if (entity == null) continue;
            boolean touching = entity.isAlive() && entity.level().dimension().location().toString().equals(endpoint.dimension)
                    && touching(entity.getBoundingBox(), endpoint, 1);
            if (arrivalLocks.observe(id, endpoint.id, touching)) setDirty();
        }
    }

    /** 破棄された個体だけを除外し、ログアウト・チャンク退避時の到着ロックは維持する。 */
    public void forgetDestroyedEntity(UUID entity) {
        if (arrivalLocks.removeEntity(entity)) setDirty();
    }

    private void blockOverlapping(Entity entity) {
        String dimension = entity.level().dimension().location().toString();
        for (Pair candidate : pairs.values()) for (Endpoint endpoint : List.of(candidate.source, candidate.target)) {
            if (dimension.equals(endpoint.dimension) && touching(entity.getBoundingBox(), endpoint, 1))
                arrivalLocks.arrived(entity.getUUID(), endpoint.id);
        }
    }

    public static boolean touching(AABB box, Endpoint endpoint, double scale) {
        Vec3 center = endpoint.center();
        return YamatoRiftGeometry.intersectsWorldBox(box.minX-center.x, box.minY-center.y, box.minZ-center.z,
                box.maxX-center.x, box.maxY-center.y, box.maxZ-center.z, endpoint.yaw, scale);
    }

    private static Endpoint inFront(ServerPlayer player) {
        double radians = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(radians), 0, Math.cos(radians));
        for (double distance : new double[]{2.25, 2, 1.75, 1.5}) {
            Vec3 feet = player.position().add(forward.scale(distance));
            Endpoint result = Endpoint.at(player.serverLevel(), feet, player.getYRot());
            if (player.serverLevel().clip(new ClipContext(player.getEyePosition(), feet.add(0, player.getEyeHeight(), 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
            if (clearSite(player.server, result)) return result;
        }
        return null;
    }

    private static boolean clearSite(MinecraftServer server, Endpoint endpoint) {
        ServerLevel level = level(server, endpoint);
        if (level == null || !validPosition(endpoint.feet, endpoint.yaw)) return false;
        AABB playerSpace = new AABB(endpoint.feet.x-.31, endpoint.feet.y, endpoint.feet.z-.31,
                endpoint.feet.x+.31, endpoint.feet.y+1.85, endpoint.feet.z+.31);
        AABB portalSpace = endpoint.bounds();
        if (!withinBounds(level, playerSpace) || !withinBounds(level, portalSpace)) return false;
        loadChunks(level, portalSpace.minmax(playerSpace));
        return level.noCollision(null, playerSpace) && level.noCollision(null, portalSpace);
    }

    /** 乗車位置の回転範囲と転送直後の同一点配置を両方確認する。壁際で乗客だけ埋まることを防ぐ。 */
    private static boolean safeGroup(ServerLevel target, Entity member, Vec3 feet, double radius, double height) {
        AABB atFeet = member.getBoundingBox().move(feet.subtract(member.position()));
        AABB seated = atFeet.move(0, height, 0).inflate(radius, 0, radius);
        if (!safeBox(target, member, atFeet.minmax(seated))) return false;
        for (Entity passenger : member.getPassengers()) {
            Vec3 seat = member.getPassengerRidingPosition(passenger).subtract(member.position());
            Vec3 attachment = passenger.getVehicleAttachmentPoint(member);
            double reach = Math.hypot(seat.x, seat.z) + Math.hypot(attachment.x, attachment.z);
            if (!safeGroup(target, passenger, feet, radius + reach, height + seat.y - attachment.y)) return false;
        }
        return true;
    }

    private static void positionPassengers(Entity vehicle) {
        for (Entity passenger : vehicle.getPassengers()) {
            vehicle.positionRider(passenger);
            positionPassengers(passenger);
        }
    }

    /** ServerPlayerの次元移動は乗客を運ばないため、全Entityで同じ順序に統一する。 */
    private static Entity transferGroup(Entity entity, DimensionTransition transition) {
        List<Entity> passengers = List.copyOf(entity.getPassengers());
        entity.ejectPassengers();
        Entity arrived = entity.changeDimension(transition);
        if (arrived == null) {
            // 他Modが転送を拒否した場合、元の乗車関係を戻す。
            for (Entity passenger : passengers) if (!passenger.isRemoved()) passenger.startRiding(entity, true);
            return null;
        }
        for (Entity passenger : passengers) {
            Entity moved = transferGroup(passenger, transition);
            if (moved != null) moved.startRiding(arrived, true);
        }
        return arrived;
    }

    private static boolean safeBox(ServerLevel level, Entity entity, AABB box) {
        if (!withinBounds(level, box)) return false;
        loadChunks(level, box);
        return level.noCollision(entity, box);
    }

    private static boolean withinBounds(ServerLevel level, AABB box) {
        return box.minY >= level.getMinBuildHeight() && box.maxY < level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(box);
    }

    private static void loadChunks(ServerLevel level, AABB box) {
        for (int x = Mth.floor(box.minX) >> 4; x <= (Mth.floor(box.maxX) >> 4); x++)
            for (int z = Mth.floor(box.minZ) >> 4; z <= (Mth.floor(box.maxZ) >> 4); z++) level.getChunk(x,z);
    }

    private static boolean validPosition(Vec3 pos, float yaw) {
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)
                && Math.abs(pos.x) <= 30_000_000 && Math.abs(pos.z) <= 30_000_000 && Math.abs(pos.y) <= 30_000_000
                && Float.isFinite(yaw);
    }

    private static ServerLevel level(MinecraftServer server, Endpoint endpoint) {
        ResourceLocation id = ResourceLocation.tryParse(endpoint.dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static void holdTickets(MinecraftServer server, Pair pair, boolean hold) {
        for (Endpoint endpoint : List.of(pair.source, pair.target)) {
            ServerLevel level = level(server, endpoint);
            if (level == null) continue;
            ChunkPos chunk = new ChunkPos(BlockPos.containing(endpoint.feet));
            if (hold) level.getChunkSource().addRegionTicket(RIFT_TICKET, chunk, 3, pair.id, true);
            else level.getChunkSource().removeRegionTicket(RIFT_TICKET, chunk, 3, pair.id, true);
        }
    }

    /** 全端の保有分だけを外し、他Modや他の対のチャンク保持を変更しない。 */
    public void releaseTickets(MinecraftServer server) {
        for (Pair pair : pairs.values()) holdTickets(server, pair, false);
        ticketsRestored = false;
    }

    private static void sound(MinecraftServer server, Endpoint endpoint, net.minecraft.sounds.SoundEvent sound) {
        ServerLevel level = level(server, endpoint);
        if (level != null) level.playSound(null, endpoint.feet.x, endpoint.feet.y + 1, endpoint.feet.z,
                sound, SoundSource.PLAYERS, .65F, 1.2F);
    }

    public ListTag active(UUID owner, MinecraftServer server) {
        ListTag result = new ListTag(); long now = server.overworld().getGameTime();
        for (Pair pair : pairs.values()) {
            if (!pair.owner.equals(owner)) continue;
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id",pair.id); tag.putString("name",pair.name);
            putAddress(tag,"source",pair.source); putAddress(tag,"target",pair.target);
            tag.putInt("access",pair.access); tag.putInt("lifetime",pair.lifetime); tag.putInt("seconds",pair.seconds);
            tag.putInt("remaining",pair.closesAt()<0 ? -1 : (int)Math.max(0,(pair.closesAt()-now+19)/20));
            result.add(tag);
        }
        return result;
    }

    private static void putAddress(CompoundTag tag, String prefix, Endpoint endpoint) {
        tag.putString(prefix+"Dimension",endpoint.dimension);
        tag.putDouble(prefix+"X",endpoint.feet.x);tag.putDouble(prefix+"Y",endpoint.feet.y);tag.putDouble(prefix+"Z",endpoint.feet.z);
    }

    public CompoundTag worldView(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        String dimension = player.level().dimension().location().toString();
        tag.putString("dimension",dimension); tag.putLong("tick",player.server.overworld().getGameTime());
        ListTag endpoints = new ListTag();
        for (Pair pair : pairs.values()) for (Endpoint endpoint : List.of(pair.source,pair.target)) {
            if (!dimension.equals(endpoint.dimension) || player.position().distanceToSqr(endpoint.feet)>160*160) continue;
            CompoundTag view = new CompoundTag(); Vec3 center = endpoint.center();
            view.putDouble("x",center.x);view.putDouble("y",center.y);view.putDouble("z",center.z);
            view.putFloat("yaw",endpoint.yaw);view.putLong("openedAt",pair.openedAt);view.putLong("closesAt",pair.closesAt());
            endpoints.add(view);
        }
        tag.put("portals",endpoints);return tag;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version",1); ListTag list = new ListTag();
        for (Pair pair : pairs.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id",pair.id);entry.putUUID("owner",pair.owner);entry.putString("name",pair.name);
            entry.put("source",pair.source.save());entry.put("target",pair.target.save());
            entry.putInt("access",pair.access);entry.putInt("lifetime",pair.lifetime);entry.putInt("seconds",pair.seconds);
            entry.putLong("openedAt",pair.openedAt);entry.putLong("firstTransfer",pair.firstTransfer);entry.putLong("manualClose",pair.manualClose);
            entry.put("sourceLocks",saveLocks(pair.source));entry.put("targetLocks",saveLocks(pair.target));list.add(entry);
        }
        tag.put("pairs",list);return tag;
    }

    private ListTag saveLocks(Endpoint endpoint) {
        ListTag result = new ListTag();
        for (UUID entity : arrivalLocks.entities(endpoint.id)) {
            CompoundTag tag=new CompoundTag();tag.putUUID("entity",entity);result.add(tag);
        }
        return result;
    }

    public static YamatoRifts load(CompoundTag tag, HolderLookup.Provider registries) {
        YamatoRifts result = new YamatoRifts(); ListTag list = tag.getList("pairs",Tag.TAG_COMPOUND);
        Set<UUID> endpoints=new HashSet<>();
        for (int i=0;i<list.size();i++) {
            CompoundTag entry=list.getCompound(i);
            if (!entry.hasUUID("id") || !entry.hasUUID("owner")) continue;
            Endpoint source=Endpoint.load(entry.getCompound("source")),target=Endpoint.load(entry.getCompound("target"));
            int access=entry.getInt("access"),mode=entry.getInt("lifetime"),seconds=entry.getInt("seconds");
            if(source==null||target==null||source.id.equals(target.id)||access<0||access>3||mode<0||mode>2||seconds<1||seconds>180
                    ||result.pairs.containsKey(entry.getUUID("id"))||endpoints.contains(source.id)||endpoints.contains(target.id))continue;
            Pair pair=new Pair(entry.getUUID("id"),entry.getUUID("owner"),entry.getString("name"),source,target,
                    access,mode,seconds,Math.max(0,entry.getLong("openedAt")));
            pair.firstTransfer=entry.contains("firstTransfer")?entry.getLong("firstTransfer"):-1;
            pair.manualClose=entry.contains("manualClose")?entry.getLong("manualClose"):-1;
            result.pairs.put(pair.id,pair);endpoints.add(source.id);endpoints.add(target.id);
            result.loadLocks(entry.getList("sourceLocks",Tag.TAG_COMPOUND),source);
            result.loadLocks(entry.getList("targetLocks",Tag.TAG_COMPOUND),target);
        }
        return result;
    }

    private void loadLocks(ListTag list, Endpoint endpoint) {
        for(int i=0;i<list.size();i++)if(list.getCompound(i).hasUUID("entity"))
            arrivalLocks.arrived(list.getCompound(i).getUUID("entity"),endpoint.id);
    }

    public record Endpoint(UUID id, String dimension, Vec3 feet, float yaw) {
        public static Endpoint at(ServerLevel level, Vec3 feet, float yaw) {
            return new Endpoint(UUID.randomUUID(),level.dimension().location().toString(),feet,Mth.wrapDegrees(yaw));
        }
        private static Endpoint at(CompoundTag destination) {
            Vec3 position=new Vec3(destination.getDouble("x"),destination.getDouble("y"),destination.getDouble("z"));
            float yaw=destination.getFloat("yaw");String dimension=destination.getString("dimension");
            if(!validPosition(position,yaw)||ResourceLocation.tryParse(dimension)==null)return null;
            return new Endpoint(UUID.randomUUID(),dimension,position,Mth.wrapDegrees(yaw));
        }
        public Vec3 center() { return feet.add(0,YamatoRiftGeometry.CENTER_HEIGHT,0); }
        public AABB bounds() {
            Vec3 center=center();double angle=Math.toRadians(yaw),cos=Math.abs(Math.cos(angle)),sin=Math.abs(Math.sin(angle));
            double radiusX=cos*YamatoRiftGeometry.HALF_WIDTH+sin*YamatoRiftGeometry.HALF_DEPTH;
            double radiusZ=sin*YamatoRiftGeometry.HALF_WIDTH+cos*YamatoRiftGeometry.HALF_DEPTH;
            return new AABB(center.x-radiusX,center.y-YamatoRiftGeometry.HALF_HEIGHT,center.z-radiusZ,
                    center.x+radiusX,center.y+YamatoRiftGeometry.HALF_HEIGHT,center.z+radiusZ);
        }
        private CompoundTag save() {
            CompoundTag tag=new CompoundTag();tag.putUUID("id",id);tag.putString("dimension",dimension);
            tag.putDouble("x",feet.x);tag.putDouble("y",feet.y);tag.putDouble("z",feet.z);tag.putFloat("yaw",yaw);return tag;
        }
        private static Endpoint load(CompoundTag tag) {
            if(!tag.hasUUID("id"))return null;Endpoint value=at(tag);
            return value==null?null:new Endpoint(tag.getUUID("id"),value.dimension,value.feet,value.yaw);
        }
    }

    public static final class Pair {
        private final UUID id,owner;
        private final String name;
        private final Endpoint source,target;
        private final int access,lifetime,seconds;
        private final long openedAt;
        private long firstTransfer=-1,manualClose=-1;
        private final Set<UUID> warned=new HashSet<>();
        private Pair(UUID id,UUID owner,String name,Endpoint source,Endpoint target,int access,int lifetime,int seconds,long openedAt) {
            this.id=id;this.owner=owner;this.name=name;this.source=source;this.target=target;
            this.access=access;this.lifetime=lifetime;this.seconds=seconds;this.openedAt=openedAt;
        }
        public UUID id(){return id;}
        public long closesAt(){return manualClose>=0?manualClose:YamatoRiftRules.closingTime(lifetime,seconds,openedAt,firstTransfer);}
    }

    private static final class Budget {
        private final long start;private int used;
        private Budget(long start,int used){this.start=start;this.used=used;}
    }

    private static final class Pending {
        private final UUID owner;private final InteractionHand hand;private final ItemStack stack;
        private final ResourceKey<Level> dimension;private final Endpoint target;private final String name;
        private final CompoundTag settings;private final long started;private boolean secondCut;
        private Pending(UUID owner,InteractionHand hand,ItemStack stack,ResourceKey<Level> dimension,Endpoint target,
                        String name,CompoundTag settings,long started) {
            this.owner=owner;this.hand=hand;this.stack=stack;this.dimension=dimension;this.target=target;
            this.name=name;this.settings=settings;this.started=started;
        }
    }
}
