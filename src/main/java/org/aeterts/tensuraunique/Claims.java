package org.aeterts.tensuraunique;

import dev.architectury.registry.menu.MenuRegistry;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The claim rules. Everything here runs on the server thread: the race-set event is fired from the server,
 * so a check and the reservation that follows it can not interleave with another player's request.
 */
public final class Claims {
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final int EXPIRE_CHECK_TICKS = 20 * 60 * 30;

    /** Players whose race changed this tick -> was it an evolution. */
    private static final Map<UUID, Boolean> PENDING = new HashMap<>();
    /** Players whose reincarnation menu is to be opened again, with the ticks left to wait. */
    private static final Map<UUID, Reopen> REOPEN = new LinkedHashMap<>();
    private static int expireCounter;
    /** Finds an online player by id; replaced by the development test, which uses players that are not in the player list. */
    static java.util.function.BiFunction<MinecraftServer, UUID, ServerPlayer> lookup = (server, id) -> server.getPlayerList().getPlayer(id);

    private record Reopen(boolean changeRaceOnly, int pool, float maxAP, float maxMP, int[] ticksLeft) {
    }

    private Claims() {
    }

    public static UniqueConfig cfg() {
        return TensuraUniqueRaces.config();
    }

    // ---------- queries ----------

    /** The race a player really has right now, according to Tensura/ManasCore. */
    public static ResourceLocation actualRace(ServerPlayer player) {
        Optional<ManasRaceInstance> race = RaceAPI.getRaceFrom(player).getRace();
        return race.map(ManasRaceInstance::getRaceId).orElse(null);
    }

    public static boolean isExempt(ResourceLocation id, ServerPlayer context) {
        UniqueConfig cfg = cfg();
        if (cfg.isExempt(id)) {
            return true;
        }
        if (cfg.exemptIncludesEvolutions) {
            for (ResourceLocation root : cfg.exemptIds()) {
                if (RaceGraph.closure(root, context).contains(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int holdersOf(ResourceLocation race, UUID except, ClaimState state, ServerPlayer ctx, List<ClaimState.Claim> out) {
        UniqueConfig cfg = cfg();
        int n = 0;
        for (Map.Entry<UUID, ClaimState.Claim> e : state.claims.entrySet()) {
            if (e.getKey().equals(except)) {
                continue;
            }
            ClaimState.Claim c = e.getValue();
            boolean holds = c.current.equals(race) || (cfg.treeLocked(c.root) && RaceGraph.closure(c.root, ctx).contains(race));
            if (holds) {
                n++;
                if (out != null) {
                    out.add(c);
                }
            }
        }
        return n;
    }

    /** @return null when the player may have {@code next}, otherwise the message to show them. */
    public static Component check(ServerPlayer player, ResourceLocation next) {
        UniqueConfig cfg = cfg();
        if (cfg.isBypassed(player) || isExempt(next, player)) {
            return null;
        }
        ClaimState state = ClaimState.get(player.getServer());
        UUID me = player.getUUID();
        ClaimState.Claim mine = state.claims.get(me);
        int limit = cfg.limitFor(next);
        if (mine != null && mine.current.equals(next)) {
            return null;
        }
        List<ClaimState.Claim> holders = new ArrayList<>();
        int count;
        if (mine != null && cfg.treeLocked(mine.root) && RaceGraph.closure(mine.root, player).contains(next)) {
            // Own evolution tree: only blocked when a tree that shares this race has someone standing on it already.
            count = 0;
            for (Map.Entry<UUID, ClaimState.Claim> e : state.claims.entrySet()) {
                if (!e.getKey().equals(me) && e.getValue().current.equals(next)) {
                    count++;
                    holders.add(e.getValue());
                }
            }
        } else {
            count = holdersOf(next, me, state, player, holders);
        }
        if (count < limit) {
            return null;
        }
        if (cfg.logDenials) {
            TensuraUniqueRaces.LOGGER.info("{} was refused {} (held by {})", player.getGameProfile().getName(), next,
                    holders.stream().map(h -> h.name).toList());
        }
        return takenMessage(next, holders, limit);
    }

    private static Component takenMessage(ResourceLocation race, List<ClaimState.Claim> holders, int limit) {
        UniqueConfig cfg = cfg();
        String holderText;
        if (cfg.showHolderNames) {
            StringBuilder names = new StringBuilder();
            for (ClaimState.Claim c : holders) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(c.name == null || c.name.isEmpty() ? "?" : c.name);
            }
            holderText = names.toString();
        } else {
            holderText = cfg.messages.someone;
        }
        Map<String, Component> values = Map.of(
                "race", raceName(race),
                "holders", Component.literal(holderText),
                "limit", Component.literal(limit == Integer.MAX_VALUE ? "-" : String.valueOf(limit)));
        return UniqueConfig.format(limit == 1 ? cfg.messages.taken : cfg.messages.full, values);
    }

    public static Component raceName(ResourceLocation id) {
        ManasRace race = RaceGraph.race(id);
        Component name = race == null ? null : race.getName();
        return name != null ? name : Component.literal(id.toString());
    }

    // ---------- bookkeeping ----------

    /** Make the claim of {@code player} agree with {@code actual}. Safe to call any number of times. */
    public static void sync(ServerPlayer player, ResourceLocation actual) {
        sync(player, actual, false);
    }

    /** @param evolution the change was an evolution: the claim keeps its root even if the target is not in the known tree */
    public static void sync(ServerPlayer player, ResourceLocation actual, boolean evolution) {
        ClaimState state = ClaimState.get(player.getServer());
        UUID me = player.getUUID();
        ClaimState.Claim c = state.claims.get(me);
        String name = player.getGameProfile().getName();
        if (actual == null || cfg().isBypassed(player) || isExempt(actual, player)) {
            if (c != null) {
                state.claims.remove(me);
                state.setDirty();
            }
            return;
        }
        if (c == null) {
            state.claims.put(me, new ClaimState.Claim(actual, actual, name));
            state.setDirty();
        } else if (c.current.equals(actual)) {
            if (!name.equals(c.name)) {
                c.name = name;
                state.setDirty();
            }
        } else {
            c.name = name;
            if (evolution || RaceGraph.closure(c.root, player).contains(actual)) {
                c.current = actual; // evolution (or a race inside the same tree): the claim stays
            } else {
                c.root = actual;    // a different race: the old claim is released, a new one starts
                c.current = actual;
            }
            state.setDirty();
        }
    }

    /** Called right after the race-set event let a change through; confirms it (or rolls it back) at the end of the tick. */
    public static void reserve(ServerPlayer player, ResourceLocation next, boolean evolution) {
        sync(player, next, evolution);
        PENDING.merge(player.getUUID(), evolution, (a, b) -> a || b);
    }

    /**
     * The client closes the reincarnation screen as soon as it confirms. When the choice is refused the menu is opened
     * again a few ticks later (after the close packet has been handled) with the same settings.
     */
    public static void reopenReincarnation(ServerPlayer player, boolean changeRaceOnly, int pool, float maxAP, float maxMP) {
        if (cfg().reopenMenuAfterDenial) {
            REOPEN.put(player.getUUID(), new Reopen(changeRaceOnly, pool, maxAP, maxMP, new int[]{Math.max(1, cfg().reopenDelayTicks)}));
        }
    }

    /** Forget everything that belongs to one server run (single player starts several servers in one JVM). */
    public static void reset() {
        PENDING.clear();
        REOPEN.clear();
        expireCounter = 0;
        lookup = (server, id) -> server.getPlayerList().getPlayer(id);
        RaceGraph.clearCache();
    }

    public static boolean hasPendingReopen(UUID id) {
        return REOPEN.containsKey(id);
    }

    private static void processReopen(MinecraftServer server) {
        if (REOPEN.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Reopen>> it = REOPEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Reopen> e = it.next();
            Reopen r = e.getValue();
            if (--r.ticksLeft()[0] > 0) {
                continue;
            }
            it.remove();
            ServerPlayer p = lookup.apply(server, e.getKey());
            if (p == null) {
                continue; // offline: Tensura reopens the menu by itself on the next login while the reset is unfinished
            }
            MenuRegistry.openExtendedMenu(p,
                    new SimpleMenuProvider((id, inv, pl) -> new ReincarnationMenu(id, inv, pl, r.changeRaceOnly(), r.pool(), r.maxAP(), r.maxMP()),
                            Component.translatable("tensura.reincarnation")),
                    buf -> {
                        buf.writeBoolean(r.changeRaceOnly());
                        buf.writeInt(r.pool());
                        buf.writeFloat(r.maxAP());
                        buf.writeFloat(r.maxMP());
                    });
        }
    }

    public static void settle(MinecraftServer server) {
        processReopen(server);
        if (++expireCounter >= EXPIRE_CHECK_TICKS) {
            expireCounter = 0;
            expireInactive(server);
        }
        if (PENDING.isEmpty()) {
            return;
        }
        Map<UUID, Boolean> pending = new HashMap<>(PENDING);
        PENDING.clear();
        for (Map.Entry<UUID, Boolean> e : pending.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                sync(p, actualRace(p), e.getValue());
            }
        }
    }

    public static void onJoin(ServerPlayer player) {
        ResourceLocation actual = actualRace(player);
        sync(player, actual);
        ClaimState.Claim mine = ClaimState.get(player.getServer()).claims.get(player.getUUID());
        if (mine != null) {
            mine.lastSeen = System.currentTimeMillis();
            ClaimState.get(player.getServer()).setDirty();
        }
        if (actual != null && mine != null) {
            ClaimState state = ClaimState.get(player.getServer());
            int others = holdersOf(actual, player.getUUID(), state, player, null);
            if (others >= cfg().limitFor(actual)) {
                TensuraUniqueRaces.LOGGER.warn("{} has race {} which is also held by another player (limit {}); it predates the claim rules or was set by an admin.",
                        player.getGameProfile().getName(), actual, cfg().limitFor(actual));
            }
        }
    }

    public static void onLeave(ServerPlayer player) {
        ClaimState state = ClaimState.get(player.getServer());
        ClaimState.Claim mine = state.claims.get(player.getUUID());
        if (mine != null) {
            mine.lastSeen = System.currentTimeMillis();
            state.setDirty();
        }
    }

    /** Releases the claims of players who have been away longer than {@code inactiveReleaseDays}. */
    public static void expireInactive(MinecraftServer server) {
        int days = cfg().inactiveReleaseDays;
        if (days <= 0 || !cfg().enabled) {
            return;
        }
        ClaimState state = ClaimState.get(server);
        long limit = System.currentTimeMillis() - days * DAY_MS;
        Iterator<Map.Entry<UUID, ClaimState.Claim>> it = state.claims.entrySet().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Map.Entry<UUID, ClaimState.Claim> e = it.next();
            if (server.getPlayerList().getPlayer(e.getKey()) == null && e.getValue().lastSeen < limit) {
                TensuraUniqueRaces.LOGGER.info("Released {} of {}: away for more than {} days", e.getValue().current, e.getValue().name, days);
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            state.setDirty();
        }
    }

    // ---------- admin ----------

    /** @return how many claims were removed */
    public static int releaseRace(MinecraftServer server, ResourceLocation race) {
        ClaimState state = ClaimState.get(server);
        int before = state.claims.size();
        state.claims.values().removeIf(c -> c.current.equals(race) || c.root.equals(race));
        if (state.claims.size() != before) {
            state.setDirty();
        }
        return before - state.claims.size();
    }

    public static boolean releasePlayer(MinecraftServer server, String name) {
        ClaimState state = ClaimState.get(server);
        boolean removed = state.claims.values().removeIf(c -> name.equalsIgnoreCase(c.name));
        if (removed) {
            state.setDirty();
        }
        return removed;
    }
}
