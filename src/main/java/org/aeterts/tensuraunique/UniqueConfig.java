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

public final class UniqueConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public boolean enabled = true;

    public boolean logDenials = false;

    public int maxPlayersPerRace = 1;

    public Map<String, Integer> perRaceLimits = new LinkedHashMap<>();

    public List<String> strictlyUniqueRaces = new ArrayList<>();

    public List<String> exemptRaces = new ArrayList<>(List.of("tensura:human", "tensura:enlightened_human", "tensura:human_saint", "tensura:divine_human"));

    /**
     * The whole evolution tree of an exempt race is exempt as well. Off by default: Human also evolves into Vampire,
     * which should stay limited, so the human line is listed in {@link #exemptRaces} explicitly instead.
     */
    public boolean exemptIncludesEvolutions = false;

    public boolean lockEvolutionPaths = true;

    public List<String> noTreeLockRaces = new ArrayList<>();

    public boolean treeStopsAtBaseRaces = true;

    public Map<String, List<String>> extraTreeRaces = new LinkedHashMap<>();

    public Map<String, List<String>> excludeFromTree = new LinkedHashMap<>();

    public List<String> bypassPlayers = new ArrayList<>();

    public int bypassPermissionLevel = -1;

    public int inactiveReleaseDays = 0;

    public boolean checkReincarnationMenu = true;

    public boolean checkEvolutions = true;

    public boolean reopenMenuAfterDenial = true;

    public int reopenDelayTicks = 5;

    public int showPermissionLevel = 0;

    public int adminPermissionLevel = 2;

    public boolean showHolderNames = true;

    public String language = "auto";

    public Messages messages = new Messages();

    /**
     * The template of a message in {@code lang}. A text that was left at its default (or emptied) is replaced by the
     * built-in text of that language; a text the administrator changed is used as written.
     */
    public String message(String key, String lang) {
        Messages d = new Messages();
        String custom;
        String def;
        switch (key) {
            case "taken" -> { custom = messages.taken; def = d.taken; }
            case "full" -> { custom = messages.full; def = d.full; }
            case "someone" -> { custom = messages.someone; def = d.someone; }
            case "noFreeRace" -> { custom = messages.noFreeRace; def = d.noFreeRace; }
            case "noneTaken" -> { custom = messages.noneTaken; def = d.noneTaken; }
            case "showHeader" -> { custom = messages.showHeader; def = d.showHeader; }
            default -> throw new IllegalArgumentException(key);
        }
        if (custom == null || custom.isEmpty() || custom.equals(def)) {
            return Lang.get(lang, "message." + key);
        }
        return custom;
    }

    public static final class Messages {
        public String taken = "&cThe race &e{race}&c is already taken by &f{holders}&c. Choose another one.";
        public String full = "&cThe race &e{race}&c is full (&f{holders}&c). Choose another one.";
        public String someone = "another player";
        public String noFreeRace = "&cEvery race of this pool is already taken.";
        public String noneTaken = "&7No race is taken yet.";
        public String showHeader = "&6Taken races ({count}):";
    }

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

    public static MutableComponent format(String template, Map<String, Component> values) {
        MutableComponent out = Component.empty();
        StringBuilder text = new StringBuilder();
        ChatFormatting[] style = new ChatFormatting[]{null, null};
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
