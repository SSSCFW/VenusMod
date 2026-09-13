package dev.ssscfw.venusmod.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** ワールドの再起動・プレイヤー交代後も帰還先を維持する、全ディメンション共通のリンク表。 */
public final class VenusPortalLinks extends SavedData {
    public record Address(String dimension, BlockPos pos) {
        public Address { pos = pos.immutable(); }
        public static Address of(ServerLevel level, VenusPortalFrame frame) {
            return new Address(level.dimension().location().toString(), frame.bottomLeft());
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", dimension);
            tag.putLong("Pos", pos.asLong());
            return tag;
        }
        static Address load(CompoundTag tag) { return new Address(tag.getString("Dimension"), BlockPos.of(tag.getLong("Pos"))); }
    }
    final Set<Address> portals = new HashSet<>();
    final Map<Address, Address> links = new HashMap<>();

    public static VenusPortalLinks get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VenusPortalLinks::new, VenusPortalLinks::load, null), "venusmod_portals");
    }
    public void register(Address address) { if (portals.add(address)) setDirty(); }
    public void forget(Address address) {
        portals.remove(address);
        Address previous = links.remove(address);
        if (previous != null && address.equals(links.get(previous))) links.remove(previous);
        setDirty();
    }
    public void pair(Address a, Address b) {
        register(a); register(b);
        unlink(a); unlink(b);
        links.put(a, b); links.put(b, a); setDirty();
    }
    private void unlink(Address a) {
        Address old = links.remove(a);
        if (old != null && a.equals(links.get(old))) links.remove(old);
    }
    private static VenusPortalLinks load(CompoundTag tag, HolderLookup.Provider registries) {
        VenusPortalLinks data = new VenusPortalLinks();
        ListTag portals = tag.getList("Portals", Tag.TAG_COMPOUND);
        for (int i = 0; i < portals.size(); i++) data.portals.add(Address.load(portals.getCompound(i)));
        ListTag links = tag.getList("Links", Tag.TAG_COMPOUND);
        for (int i = 0; i < links.size(); i++) {
            CompoundTag link = links.getCompound(i);
            Address from = Address.load(link.getCompound("From"));
            Address to = Address.load(link.getCompound("To"));
            if (data.portals.contains(from) && data.portals.contains(to)) data.links.put(from, to);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag portalList = new ListTag();
        portals.forEach(address -> portalList.add(address.save()));
        tag.put("Portals", portalList);
        ListTag linkList = new ListTag();
        links.forEach((from, to) -> {
            CompoundTag link = new CompoundTag(); link.put("From", from.save()); link.put("To", to.save()); linkList.add(link);
        });
        tag.put("Links", linkList);
        return tag;
    }
}
