package xyz.thelegacyvoyage.hyessentialsx.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class PluginInfoUtil {

    private static final Gson GSON = new Gson();

    private PluginInfoUtil() {}

    @Nonnull
    public static String getVersion() {
        return getManifestValue("Version", "unknown");
    }

    @Nonnull
    public static String getServerVersion() {
        return getManifestValue("ServerVersion", "unknown");
    }

    @Nonnull
    private static String getManifestValue(@Nonnull String key, @Nonnull String fallback) {
        try (InputStream in = PluginInfoUtil.class.getResourceAsStream("/manifest.json")) {
            if (in == null) return fallback;
            JsonObject obj = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            if (obj != null && obj.has(key)) {
                return obj.get(key).getAsString();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}

