package org.aeterts.tensuraunique;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side config: {@code config/tensura_unique_races.json}. Every key is optional: a missing key falls back to its
 * default and the file is rewritten with all keys after loading. Race ids look like {@code "tensura:goblin"}.
 * Apply edits without a restart with {@code /tensura unique reload}. All keys are documented in the README.
 */
public final class UniqueConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // ---------------------------------------------------------------- general

    /** Master switch. When false the mod does nothing (claims are neither checked nor recorded). */
    public boolean enabled = true;

    /** Log a line to the server log every time a player is refused a race. */
    public boolean logDenials = false;

    // ---------------------------------------------------------------- limits

    /** How many players may hold one race. 0 or less means unlimited. */
    public int maxPlayersPerRace = 1;

    /** Limit of single races, overrides {@link #maxPlayersPerRace}: {"tensura:slime": 2, "tensura:goblin": 0 (= unlimited)}. */
    public Map<String, Integer> perRaceLimits = new LinkedHashMap<>();

    /** Races that are limited to one player no matter what the limits above say. */
    public List<String> strictlyUniqueRaces = new ArrayList<>();

    /** Races anyone can take, they never create a claim. By default the human line. */
    public List<String> exemptRaces = new ArrayList<>(List.of("tensura:human", "tensura:enlightened_human", "tensura:human_saint", "tensura:divine_human"));

    /**
     * The whole evolution tree of an exempt race is exempt as well. Off by default: Human also evolves into Vampire,
     * which should stay limited, so the human line is listed in {@link #exemptRaces} explicitly instead.
     */
    public boolean exemptIncludesEvolutions = false;

    // ---------------------------------------------------------------- evolution trees

    /**
     * A claimed race also reserves its whole evolution tree, so nobody else can pick a race that the owner will need
     * to evolve into. When false only the race a player currently has is held.
     */
    public boolean lockEvolutionPaths = true;

    /** Races (by the race a claim started with) whose tree is NOT reserved even though {@link #lockEvolutionPaths} is on. */
    public List<String> noTreeLockRaces = new ArrayList<>();

    /** The reserved tree stops at other base forms from Tensura's reincarnation pools (Goblin leads to Ogre, Wight to Human). */
    public boolean treeStopsAtBaseRaces = true;

    /** Extra races a tree reserves, for add-on races Tensura does not link: {"tensura:slime": ["someaddon:rimuru_form"]}. */
    public Map<String, List<String>> extraTreeRaces = new LinkedHashMap<>();

    /** Races taken out of a tree: {"tensura:goblin": ["tensura:divine_oni"]}. */
    public Map<String, List<String>> excludeFromTree = new LinkedHashMap<>();

    // ---------------------------------------------------------------- who is affected

    /** Players who are never limited and never hold a claim: player names or UUIDs. */
    public List<String> bypassPlayers = new ArrayList<>();

    /** Players with at least this permission level (1-4, ops are usually 4) are never limited. -1 = nobody. */
    public int bypassPermissionLevel = -1;

    /** Claims of players who have not been online for this many days are released. 0 = never. */
    public int inactiveReleaseDays = 0;

    // ---------------------------------------------------------------- what is checked

    /** Refuse taken races in the reincarnation / race selection menu. */
    public boolean checkReincarnationMenu = true;

    /** Refuse ordinary evolutions into a race another player holds. Awakening, harvest festival, curses and admin commands are never refused. */
    public boolean checkEvolutions = true;

    /** Open the reincarnation menu again after a refused choice (the client closes it on confirm). */
    public boolean reopenMenuAfterDenial = true;

    /** Ticks to wait before the menu is opened again. */
    public int reopenDelayTicks = 5;

    // ---------------------------------------------------------------- commands

    /** Permission level needed for /tensura unique show (0 = everyone). */
    public int showPermissionLevel = 0;

    /** Permission level needed for /tensura unique release|reload. */
    public int adminPermissionLevel = 2;

    /** Show who holds a race in /tensura unique show and in refusal messages. When false only the number of holders is shown. */
    public boolean showHolderNames = true;

    // ---------------------------------------------------------------- messages

    /** Texts shown to players. Colour codes with {@code &} (&c red, &e yellow, &f white, &l bold, &r reset). */
    public Messages messages = new Messages();

    public static final class Messages {
        /** Placeholders: {race} {holders} {limit}. Used when the limit is 1. */
        public String taken = "&cThe race &e{race}&c is already taken by &f{holders}&c. Choose another one.";
        /** Used when more than one player may hold the race. */
        public String full = "&cThe race &e{race}&c is full (&f{holders}&c). Choose another one.";
        /** Used instead of the holder names when showHolderNames is off. */
        public String someone = "another player";
        public String noFreeRace = "&cEvery race of this pool is already taken.";
        public String noneTaken = "&7No race is taken yet.";
        public String showHeader = "&6Taken races ({count}):";
    }

    // ---------------------------------------------------------------- parsed / derived

    private transient Set<ResourceLocation> exemptIds = Set.of();
    private transient Set<ResourceLocation> strictIds = Set.of();
    private transient Set<ResourceLocation> noLockIds = Set.of();
    private transient Map<ResourceLocation, Integer> limitIds = Map.of();
    private transient Map<ResourceLocation, Set<ResourceLocation>> extraIds = Map.of();
    private transient Map<ResourceLocation, Set<ResourceLocation>> excludeIds = Map.of();
    private transient Set<UUID> bypassUuids = Set.of();
    private transient Set<String> bypassNames = Set.of();

    public boolean isExempt(ResourceLocation id) {
        return exemptIds.contains(id);
    }

    public Set<ResourceLocation> exemptIds() {
        return exemptIds;
    }

    /** Max holders of {@code id}; {@link Integer#MAX_VALUE} when unlimited. */
    public int limitFor(ResourceLocation id) {
        if (strictIds.contains(id)) {
            return 1;
        }
        Integer own = limitIds.get(id);
        int limit = own != null ? own : maxPlayersPerRace;
        return limit <= 0 ? Integer.MAX_VALUE : limit;
    }

    /** Does a claim that started with {@code root} reserve the whole tree? */
    public boolean treeLocked(ResourceLocation root) {
        return lockEvolutionPaths && !noLockIds.contains(root);
    }

    public Set<ResourceLocation> extraTree(ResourceLocation root) {
        return extraIds.getOrDefault(root, Set.of());
    }

    public Set<ResourceLocation> excludedFromTree(ResourceLocation root) {
        return excludeIds.getOrDefault(root, Set.of());
    }

    public boolean isBypassed(ServerPlayer player) {
        if (bypassUuids.contains(player.getUUID()) || bypassNames.contains(player.getGameProfile().getName().toLowerCase(Locale.ROOT))) {
            return true;
        }
        return bypassPermissionLevel >= 0 && player.hasPermissions(bypassPermissionLevel);
    }

    // ---------------------------------------------------------------- message formatting

    /** Builds a message from a template: {@code &} colour codes and {@code {name}} placeholders (replaced by components). */
    public static MutableComponent format(String template, Map<String, Component> values) {
        MutableComponent out = Component.empty();
        StringBuilder text = new StringBuilder();
        ChatFormatting[] style = new ChatFormatting[]{null, null}; // colour, bold
        Runnable flush = () -> {
            if (text.length() > 0) {
                MutableComponent part = Component.literal(text.toString());
                if (style[0] != null) {
                    part.withStyle(style[0]);
                }
                if (style[1] != null) {
                    part.withStyle(style[1]);
                }
                out.append(part);
                text.setLength(0);
            }
        };
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '&' && i + 1 < template.length()) {
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(template.charAt(i + 1)));
                if (f != null) {
                    flush.run();
                    if (f == ChatFormatting.RESET) {
                        style[0] = null;
                        style[1] = null;
                    } else if (f == ChatFormatting.BOLD) {
                        style[1] = f;
                    } else if (f.isColor()) {
                        style[0] = f;
                    }
                    i++;
                    continue;
                }
            }
            if (c == '{') {
                int end = template.indexOf('}', i);
                if (end > i) {
                    Component v = values.get(template.substring(i + 1, end));
                    if (v != null) {
                        flush.run();
                        MutableComponent part = v.copy();
                        if (style[0] != null) {
                            part.withStyle(style[0]);
                        }
                        out.append(part);
                        i = end;
                        continue;
                    }
                }
            }
            text.append(c);
        }
        flush.run();
        return out;
    }

    // ---------------------------------------------------------------- load / save

    private void parse(Logger log) {
        exemptIds = parseIds(exemptRaces, "exemptRaces", log);
        strictIds = parseIds(strictlyUniqueRaces, "strictlyUniqueRaces", log);
        noLockIds = parseIds(noTreeLockRaces, "noTreeLockRaces", log);
        if (perRaceLimits == null) {
            perRaceLimits = new LinkedHashMap<>();
        }
        if (extraTreeRaces == null) {
            extraTreeRaces = new LinkedHashMap<>();
        }
        if (excludeFromTree == null) {
            excludeFromTree = new LinkedHashMap<>();
        }
        Map<ResourceLocation, Integer> limits = new HashMap<>();
        perRaceLimits.forEach((k, v) -> {
            ResourceLocation id = ResourceLocation.tryParse(k);
            if (id == null || v == null) {
                log.warn("Config key 'perRaceLimits' contains an invalid entry: {}", k);
            } else {
                limits.put(id, v);
            }
        });
        limitIds = limits;
        extraIds = parseIdMap(extraTreeRaces, "extraTreeRaces", log);
        excludeIds = parseIdMap(excludeFromTree, "excludeFromTree", log);
        Set<UUID> uuids = new HashSet<>();
        Set<String> names = new HashSet<>();
        if (bypassPlayers != null) {
            for (String s : bypassPlayers) {
                if (s == null || s.isBlank()) {
                    continue;
                }
                try {
                    uuids.add(UUID.fromString(s.trim()));
                } catch (IllegalArgumentException e) {
                    names.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        bypassUuids = uuids;
        bypassNames = names;
        if (messages == null) {
            messages = new Messages();
        }
        Messages d = new Messages();
        if (messages.taken == null) messages.taken = d.taken;
        if (messages.full == null) messages.full = d.full;
        if (messages.someone == null) messages.someone = d.someone;
        if (messages.noFreeRace == null) messages.noFreeRace = d.noFreeRace;
        if (messages.noneTaken == null) messages.noneTaken = d.noneTaken;
        if (messages.showHeader == null) messages.showHeader = d.showHeader;
    }

    private static Set<ResourceLocation> parseIds(List<String> raw, String key, Logger log) {
        Set<ResourceLocation> out = new HashSet<>();
        if (raw == null) {
            return out;
        }
        for (String s : raw) {
            ResourceLocation id = s == null ? null : ResourceLocation.tryParse(s.trim());
            if (id == null) {
                log.warn("Config key '{}' contains an invalid race id: {}", key, s);
            } else {
                out.add(id);
            }
        }
        return out;
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> parseIdMap(Map<String, List<String>> raw, String key, Logger log) {
        Map<ResourceLocation, Set<ResourceLocation>> out = new HashMap<>();
        raw.forEach((k, v) -> {
            ResourceLocation root = ResourceLocation.tryParse(k);
            if (root == null) {
                log.warn("Config key '{}' contains an invalid race id: {}", key, k);
            } else {
                out.put(root, parseIds(v, key, log));
            }
        });
        return out;
    }

    /** Parses a config from text (also used by the development test). Returns null when the text is not valid JSON. */
    static UniqueConfig parseJson(String json, Logger log) {
        try {
            UniqueConfig cfg = GSON.fromJson(json, UniqueConfig.class);
            if (cfg == null) {
                cfg = new UniqueConfig();
            }
            cfg.parse(log);
            return cfg;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("tensura_unique_races.json");
    }

    public static UniqueConfig load(Logger log) {
        Path file = path();
        UniqueConfig cfg = null;
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, UniqueConfig.class);
            } catch (IOException | JsonSyntaxException e) {
                log.error("Could not read {}, using defaults for now (the file is left untouched): {}", file, e.toString());
                UniqueConfig fallback = new UniqueConfig();
                fallback.parse(log);
                return fallback;
            }
        }
        if (cfg == null) {
            cfg = new UniqueConfig();
        }
        cfg.parse(log);
        cfg.save(log);
        return cfg;
    }

    private void save(Logger log) {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            log.error("Could not write {}: {}", file, e.toString());
        }
    }
}
