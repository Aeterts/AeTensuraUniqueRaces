package org.aeterts.tensuraunique;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The texts of the mod in the languages it comes with. The mod is server-side only, so the texts can not be translated by
 * the client: the server picks the language of the player (or the configured one) and sends plain text.
 * <p>
 * The texts live in {@code assets/tensura_unique_races_fabric/lang/<code>.json}; a new language is a file there plus its code in
 * {@code languages.json}, which also maps regional variants (zh_hk to zh_tw). A variant without a file of its own uses another
 * file of the same language (de_at to de_de). English is the fallback for a missing language and for a missing key.
 */
public final class Lang {
    public static final String FALLBACK = "en_us";
    private static final Pattern CODE = Pattern.compile("[a-z]{2,3}(_[a-z0-9]{2,4})?");
    private static final Type MAP = new TypeToken<Map<String, String>>() { }.getType();
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private Lang() {
    }

    public static String of(ServerPlayer player) {
        String configured = TensuraUniqueRaces.config().language;
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("auto")) {
            return normalize(player == null ? FALLBACK : player.clientInformation().language());
        }
        return normalize(configured);
    }

    static String normalize(String code) {
        String c = code == null ? FALLBACK : code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        Index idx = index();
        if (idx.languages().contains(c)) {
            return c;
        }
        String alias = idx.aliases().get(c);
        if (alias != null) {
            return alias;
        }
        int cut = c.indexOf('_');
        String family = (cut < 0 ? c : c.substring(0, cut)) + "_";
        for (String language : idx.languages()) {
            if (language.startsWith(family)) {
                return language;
            }
        }
        return c;
    }

    public static List<String> languages() {
        return index().languages();
    }

    private record Index(List<String> languages, Map<String, String> aliases) {
    }

    private static volatile Index index;

    private static Index index() {
        Index i = index;
        if (i == null) {
            i = readIndex();
            index = i;
        }
        return i;
    }

    private static Index readIndex() {
        try (InputStream in = Lang.class.getResourceAsStream("/assets/tensura_unique_races_fabric/lang/languages.json")) {
            if (in != null) {
                Index parsed = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Index.class);
                if (parsed != null && parsed.languages() != null) {
                    return new Index(List.copyOf(parsed.languages()), parsed.aliases() == null ? Map.of() : Map.copyOf(parsed.aliases()));
                }
            }
        } catch (IOException | RuntimeException e) {
            TensuraUniqueRaces.LOGGER.warn("Could not read the language index: {}", e.toString());
        }
        return new Index(List.of(FALLBACK), Map.of());
    }

    private static Map<String, String> load(String code) {
        if (!CODE.matcher(code).matches()) {
            return Map.of();
        }
        return CACHE.computeIfAbsent(code, c -> {
            try (InputStream in = Lang.class.getResourceAsStream("/assets/tensura_unique_races_fabric/lang/" + c + ".json")) {
                if (in == null) {
                    return Map.of();
                }
                Map<String, String> map = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), MAP);
                return map == null ? Map.of() : map;
            } catch (IOException | RuntimeException e) {
                TensuraUniqueRaces.LOGGER.warn("Could not read the language file {}: {}", c, e.toString());
                return Map.of();
            }
        });
    }

    public static boolean has(String code) {
        return !load(code).isEmpty();
    }

    public static Set<String> keys(String code) {
        return load(code).keySet();
    }

    public static String get(String code, String key) {
        String value = load(code).get(key);
        if (value == null) {
            value = load(FALLBACK).get(key);
        }
        return value == null ? key : value;
    }

    public static String format(String code, String key, Map<String, String> args) {
        String text = get(code, key);
        for (Map.Entry<String, String> e : args.entrySet()) {
            text = text.replace("{" + e.getKey() + "}", e.getValue());
        }
        return text;
    }
}
