package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LanguageManager {

    private static final String FALLBACK_LANGUAGE = "en-us";
    private static final java.util.Set<String> BUNDLED_LANGUAGES = java.util.Set.of(
            "en-us",
            "zh-cn",
            "es-es",
            "hi-in",
            "ar",
            "bn",
            "pt-br",
            "ru",
            "ja-jp",
            "pa",
            "de-de",
            "it-it"
    );

    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .create();
    private final Gson prettyGson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private final Path langDir;
    private final StorageManager storage;
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    private String defaultLanguage = FALLBACK_LANGUAGE;

    public LanguageManager(@Nonnull Path dataFolder,
                           @Nonnull ConfigManager config,
                           @Nonnull StorageManager storage) {
        this.langDir = dataFolder.resolve("lang");
        this.storage = storage;
        ensureBundledLanguageFiles();
        reload(config.getLanguage());
    }

    public void reload(@Nonnull String languageCode) {
        this.defaultLanguage = normalize(languageCode);
        ensureBundledLanguageFile(this.defaultLanguage);
        cache.clear();
        loadLanguage(FALLBACK_LANGUAGE);
        loadLanguage(this.defaultLanguage);
    }

    @Nonnull
    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    @Nonnull
    public Set<String> getAvailableLanguages() {
        if (!Files.exists(langDir)) return BUNDLED_LANGUAGES;
        Set<String> out = new HashSet<>();
        try (var stream = Files.list(langDir)) {
            stream.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        if (dot > 0) {
                            out.add(name.substring(0, dot).toLowerCase());
                        }
                    });
        } catch (Exception ignored) {
        }
        if (!out.contains(FALLBACK_LANGUAGE)) {
            out.add(FALLBACK_LANGUAGE);
        }
        out.addAll(BUNDLED_LANGUAGES);
        return Collections.unmodifiableSet(out);
    }

    public boolean hasLanguage(@Nonnull String code) {
        String normalized = normalize(code);
        Path file = langDir.resolve(normalized + ".json");
        return Files.exists(file) || BUNDLED_LANGUAGES.contains(normalized);
    }

    public boolean hasKey(@Nonnull String key) {
        if (loadLanguage(defaultLanguage).containsKey(key)) return true;
        return loadLanguage(FALLBACK_LANGUAGE).containsKey(key);
    }

    public void setPlayerLanguage(@Nonnull UUID uuid, @Nonnull String code) {
        String normalized = normalize(code);
        PlayerDataModel data = storage.getPlayerData(uuid);
        data.setLanguage(normalized);
        storage.savePlayerDataAsync(uuid, data);
    }

    @Nullable
    public String getPlayerLanguage(@Nonnull UUID uuid) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        String lang = data.getLanguage();
        return lang != null && !lang.isBlank() ? normalize(lang) : null;
    }

    @Nonnull
    public String translate(@Nullable PlayerRef player, @Nonnull String key) {
        return translate(player, key, Map.of());
    }

    @Nonnull
    public String translate(@Nullable PlayerRef player,
                            @Nonnull String key,
                            @Nonnull Map<String, String> placeholders) {
        String lang = defaultLanguage;
        if (player != null) {
            String playerLang = getPlayerLanguage(player.getUuid());
            if (playerLang != null) {
                lang = playerLang;
            }
        }
        return translate(lang, key, placeholders);
    }

    @Nonnull
    public String translate(@Nonnull String language,
                            @Nonnull String key,
                            @Nonnull Map<String, String> placeholders) {
        String normalized = normalize(language);
        String value = lookup(normalized, key);
        if (value == null) {
            value = lookup(FALLBACK_LANGUAGE, key);
        }
        if (value == null) {
            value = key;
        }
        return applyPlaceholders(value, placeholders);
    }

    @Nullable
    private String lookup(@Nonnull String language, @Nonnull String key) {
        Map<String, String> map = loadLanguage(language);
        return map.get(key);
    }

    @Nonnull
    private Map<String, String> loadLanguage(@Nonnull String language) {
        String normalized = normalize(language);
        return cache.computeIfAbsent(normalized, lang -> {
            Path file = langDir.resolve(lang + ".json");
            if (!Files.exists(file)) {
                return new HashMap<>();
            }
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject json = gson.fromJson(content, JsonObject.class);
                if (json == null) return new HashMap<>();
                Map<String, String> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        map.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                return map;
            } catch (Exception e) {
                Log.warn("Failed to load language '" + lang + "': " + e.getMessage());
                return new HashMap<>();
            }
        });
    }

    private void ensureBundledLanguageFiles() {
        for (String code : BUNDLED_LANGUAGES) {
            ensureBundledLanguageFile(code);
        }
    }

    private void ensureBundledLanguageFile(@Nonnull String languageCode) {
        try {
            Files.createDirectories(langDir);
            String normalized = normalize(languageCode);
            Path target = langDir.resolve(normalized + ".json");
            try (InputStream in = LanguageManager.class.getClassLoader().getResourceAsStream("lang/" + normalized + ".json")) {
                if (in == null) {
                    Log.warn("Missing bundled language resource: " + normalized);
                    return;
                }
                JsonObject bundled = gson.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
                if (bundled == null) {
                    Log.warn("Failed to parse bundled language resource: " + normalized);
                    return;
                }
                if (Files.exists(target)) {
                    if (mergeMissingKeys(target, bundled)) {
                        Log.info("Updated language file with new keys: " + target);
                    }
                } else {
                    Files.writeString(target, prettyGson.toJson(bundled), StandardCharsets.UTF_8);
                    Log.info("Created language file: " + target);
                }
            }
        } catch (Exception e) {
            Log.warn("Failed to write language file: " + e.getMessage());
        }
    }

    private boolean mergeMissingKeys(@Nonnull Path target, @Nonnull JsonObject bundled) {
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            JsonObject existing = gson.fromJson(content, JsonObject.class);
            if (existing == null) existing = new JsonObject();
            boolean changed = false;
            for (Map.Entry<String, JsonElement> entry : bundled.entrySet()) {
                if (!existing.has(entry.getKey())) {
                    existing.add(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(target, prettyGson.toJson(existing), StandardCharsets.UTF_8);
            }
            return changed;
        } catch (Exception e) {
            Log.warn("Failed to merge language file: " + target + " (" + e.getMessage() + ")");
            return false;
        }
    }

    @Nonnull
    private static String applyPlaceholders(@Nonnull String text, @Nonnull Map<String, String> placeholders) {
        String out = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return out;
    }

    @Nonnull
    private static String normalize(@Nonnull String code) {
        return code.trim().toLowerCase();
    }
}

