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

public final class HomesPlusMigration extends ModMigration {

    public HomesPlusMigration(@Nonnull Path sourceDir) {
        super(sourceDir, "HomesPlus");
    }

    @Override
    @Nonnull
    public List<HomeEntry> migrateHomes() throws Exception {
        Path homesFile = sourceDir.resolve("homes.json");
        if (!Files.exists(homesFile)) {
            Log.warn("[HyEssentialsX] HomesPlus homes.json not found: " + homesFile.toAbsolutePath());
            return List.of();
        }
        List<HomeEntry> homes = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(homesFile, StandardCharsets.UTF_8)) {
            JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root == null || root.size() == 0) {
                return homes;
            }
            for (Map.Entry<String, JsonElement> playerEntry : root.entrySet()) {
                UUID playerId = parseUuid(playerEntry.getKey());
                if (playerId == null) {
                    continue;
                }
                JsonObject playerHomes = playerEntry.getValue().getAsJsonObject();
                for (Map.Entry<String, JsonElement> homeEntry : playerHomes.entrySet()) {
                    String homeName = homeEntry.getKey();
                    JsonObject homeData = homeEntry.getValue().getAsJsonObject();
                    String worldNameRaw = homeData.has("worldName") ? homeData.get("worldName").getAsString() : null;
                    String worldName = resolveWorldName(worldNameRaw, null);
                    double x = homeData.get("x").getAsDouble();
                    double y = homeData.get("y").getAsDouble();
                    double z = homeData.get("z").getAsDouble();
                    float yaw = homeData.get("yaw").getAsFloat();
                    float pitch = homeData.get("pitch").getAsFloat();
                    HomeModel home = new HomeModel(
                            homeName,
                            null,
                            worldName,
                            x, y, z,
                            yaw, pitch
                    );
                    homes.add(new HomeEntry(playerId, home));
                }
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read HomesPlus homes.json: " + e.getMessage());
            throw e;
        }
        Log.info("[HyEssentialsX] Migrated " + homes.size() + " homes from HomesPlus");
        return homes;
    }

    @Override
    @Nonnull
    public List<WarpModel> migrateWarps() {
        return List.of();
    }
}
