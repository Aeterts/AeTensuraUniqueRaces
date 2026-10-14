package org.aeterts.tensuraunique;

import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.tensura.config.ReincarnationConfig;
import io.github.manasmods.tensura.race.TensuraRace;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RaceGraph {
    private static final Map<ResourceLocation, Set<ResourceLocation>> CLOSURES = new HashMap<>();

    private RaceGraph() {
    }

    public static void clearCache() {
        CLOSURES.clear();
    }

    public static ManasRace race(ResourceLocation id) {
        return RaceAPI.getRaceRegistry().get(id);
    }

    public static List<ResourceLocation> allRaceIds() {
        List<ResourceLocation> ids = new ArrayList<>(RaceAPI.getRaceRegistry().getIds());
        Collections.sort(ids);
        return ids;
    }

    public static Set<ResourceLocation> baseRaces() {
        Set<ResourceLocation> bases = new java.util.HashSet<>();
        ReincarnationConfig.Races c = ConfigRegistry.getConfig(ReincarnationConfig.class).Races;
        for (List<String> pool : List.of(c.startingRaces, c.randomRaces, c.reincarnationRaces, c.reincarnationRacesMastered,
                c.reincarnationRandomRaces, c.reincarnationRandomRacesMastered)) {
            for (String id : pool) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) {
                    bases.add(rl);
                }
            }
        }
        return bases;
    }

    /**
     * The race itself plus every race that can be reached from it through evolutions. The walk does not go on into
     * other base forms (Goblin does lead to Ogre, Wight to Human): those are picked in the menu on their own and one
     * player's tree must not swallow them.
     */
    public static Set<ResourceLocation> closure(ResourceLocation root, ServerPlayer context) {
        Set<ResourceLocation> cached = CLOSURES.get(root);
        if (cached != null) {
            return cached;
        }
        Set<ResourceLocation> bases = TensuraUniqueRaces.config().treeStopsAtBaseRaces ? baseRaces() : Set.of();
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        Deque<ResourceLocation> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            ResourceLocation id = queue.poll();
            if (!id.equals(root) && bases.contains(id)) {
                continue;
            }
            if (!seen.add(id)) {
                continue;
            }
            ManasRace race = race(id);
            if (race == null) {
                continue;
            }
            try {
                ManasRaceInstance instance = race.createDefaultInstance();
                List<ManasRace> edges = new ArrayList<>(race.getNextEvolutions(instance, context));
                if (race instanceof TensuraRace tensura) {
                    // awakening and harvest festival are evolutions too, they just are not listed as "next evolutions"
                    edges.add(tensura.getAwakeningEvolution(instance, context));
                    edges.add(tensura.getHarvestFestivalEvolution(instance, context));
                }
                for (ManasRace next : edges) {
                    ResourceLocation nextId = next == null ? null : next.getRegistryName();
                    if (nextId != null) {
                        queue.add(nextId);
                    }
                }
            } catch (RuntimeException e) {
                TensuraUniqueRaces.LOGGER.warn("Could not read the evolutions of {}: {}", id, e.toString());
            }
        }
        seen.addAll(TensuraUniqueRaces.config().extraTree(root));
        seen.removeAll(TensuraUniqueRaces.config().excludedFromTree(root));
        seen.add(root);
        Set<ResourceLocation> result = Collections.unmodifiableSet(seen);
        CLOSURES.put(root, result);
        return result;
    }
}
