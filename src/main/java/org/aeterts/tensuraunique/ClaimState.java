package org.aeterts.tensuraunique;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Who holds which race. Lives in the overworld's data folder, so it survives restarts and covers offline players. */
public final class ClaimState extends SavedData {
    private static final String ID = "tensura_unique_races";

    /**
     * @param root    the race the claim started with; with locked evolution paths its whole tree is reserved
     * @param current the race the player has now
     * @param name    last known player name, for /tensura unique show and offline releases
     */
    public static final class Claim {
        public ResourceLocation root;
        public ResourceLocation current;
        public String name;
        /** Last time the holder was seen online (epoch millis); used to release claims of inactive players. */
        public long lastSeen;

        public Claim(ResourceLocation root, ResourceLocation current, String name) {
            this.root = root;
            this.current = current;
            this.name = name;
            this.lastSeen = System.currentTimeMillis();
        }

        public Claim copy() {
            Claim c = new Claim(root, current, name);
            c.lastSeen = lastSeen;
            return c;
        }
    }

    public final Map<UUID, Claim> claims = new LinkedHashMap<>();

    private static final SavedData.Factory<ClaimState> TYPE = new SavedData.Factory<>(ClaimState::new, ClaimState::fromNbt, null);

    public static ClaimState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, ID);
    }

    static ClaimState fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        ClaimState state = new ClaimState();
        ListTag list = nbt.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (!e.hasUUID("player")) {
                continue;
            }
            ResourceLocation root = ResourceLocation.tryParse(e.getString("root"));
            ResourceLocation current = ResourceLocation.tryParse(e.getString("current"));
            if (root == null || current == null) {
                continue;
            }
            Claim claim = new Claim(root, current, e.getString("name"));
            if (e.contains("seen")) {
                claim.lastSeen = e.getLong("seen");
            }
            state.claims.put(e.getUUID("player"), claim);
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Claim> entry : claims.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("player", entry.getKey());
            e.putString("root", entry.getValue().root.toString());
            e.putString("current", entry.getValue().current.toString());
            e.putString("name", entry.getValue().name == null ? "" : entry.getValue().name);
            e.putLong("seen", entry.getValue().lastSeen);
            list.add(e);
        }
        nbt.put("claims", list);
        return nbt;
    }
}
