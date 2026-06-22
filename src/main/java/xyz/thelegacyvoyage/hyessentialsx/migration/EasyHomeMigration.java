package xyz.thelegacyvoyage.hyessentialsx.migration;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EasyHomeMigration extends ModMigration {

    public EasyHomeMigration(@Nonnull Path sourceDir) {
        super(sourceDir, "EasyHome");
    }

    @Override
    @Nonnull
    public List<HomeEntry> migrateHomes() throws Exception {
        Path homesDir = sourceDir.resolve("homes");
        if (!Files.exists(homesDir) || !Files.isDirectory(homesDir)) {
            Log.warn("[HyEssentialsX] EasyHome homes directory not found: " + homesDir.toAbsolutePath());
            return List.of();
        }

        List<HomeEntry> homes = new ArrayList<>();
        try (var stream = Files.list(homesDir)) {
            stream.filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json"))
                    .forEach(path -> readPlayerHomes(path, homes));
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read EasyHome homes: " + e.getMessage());
            throw e;
        }

        Log.info("[HyEssentialsX] Migrated " + homes.size() + " homes from EasyHome");
        return homes;
    }

    @Override
    @Nonnull
    public List<WarpModel> migrateWarps() {
        return List.of();
    }

    private void readPlayerHomes(@Nonnull Path file, @Nonnull List<HomeEntry> homes) {
        String filename = file.getFileName().toString();
        String uuidRaw = filename.endsWith(".json")
                ? filename.substring(0, filename.length() - 5)
                : filename;
        UUID playerId = parseUuid(uuidRaw);
        if (playerId == null) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root == null || !root.has("homes")) {
                return;
            }
            JsonObject homesObject = root.getAsJsonObject("homes");
            if (homesObject == null || homesObject.size() == 0) {
                return;
            }

            for (Map.Entry<String, JsonElement> entry : homesObject.entrySet()) {
                if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject homeData = entry.getValue().getAsJsonObject();
                String homeName = normalize(entry.getKey());
                if (homeName == null) {
                    continue;
                }
                String worldName = resolveWorldName(
                        homeData.has("world") ? homeData.get("world").getAsString() : null,
                        null
                );
                if (!homeData.has("x") || !homeData.has("y") || !homeData.has("z")) {
                    continue;
                }
                double x = homeData.get("x").getAsDouble();
                double y = homeData.get("y").getAsDouble();
                double z = homeData.get("z").getAsDouble();
                float yaw = homeData.has("yaw") ? homeData.get("yaw").getAsFloat() : 0f;
                float pitch = homeData.has("pitch") ? homeData.get("pitch").getAsFloat() : 0f;

                HomeModel home = new HomeModel(
                        homeName,
                        null,
                        worldName,
                        x, y, z,
                        yaw, pitch
                );
                homes.add(new HomeEntry(playerId, home));
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read EasyHome file " + file.getFileName() + ": " + e.getMessage());
        }
    }
}
