package xyz.thelegacyvoyage.hyessentialsx.migration;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

public final class KuksoHyWarpsMigration extends ModMigration {

    public KuksoHyWarpsMigration(@Nonnull Path sourceDir) {
        super(sourceDir, "KuksoHyWarps");
    }

    @Override
    @Nonnull
    public List<WarpModel> migrateWarps() throws Exception {
        Path warpsFile = sourceDir.resolve("warps.json");
        if (!Files.exists(warpsFile)) {
            Log.warn("[HyEssentialsX] KuksoHyWarps warps.json not found");
            return List.of();
        }
        List<WarpModel> warps = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(warpsFile, StandardCharsets.UTF_8)) {
            JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root == null || !root.has("Warps")) {
                return warps;
            }
            JsonObject warpsObject = root.getAsJsonObject("Warps");
            if (warpsObject == null || warpsObject.size() == 0) {
                return warps;
            }
            for (Map.Entry<String, JsonElement> entry : warpsObject.entrySet()) {
                String warpName = entry.getKey();
                JsonObject data = entry.getValue().getAsJsonObject();
                if (data == null) {
                    continue;
                }
                String worldName = data.has("WorldName") ? data.get("WorldName").getAsString() : null;
                if (worldName == null && data.has("World")) {
                    worldName = data.get("World").getAsString();
                }
                String worldUuid = data.has("WorldUuid") ? data.get("WorldUuid").getAsString() : null;
                String resolvedWorld = resolveWorldName(worldName, worldUuid);

                double x = data.get("X").getAsDouble();
                double y = data.get("Y").getAsDouble();
                double z = data.get("Z").getAsDouble();
                float yaw = data.get("Yaw").getAsFloat();
                float pitch = data.get("Pitch").getAsFloat();

                if (warpName == null || warpName.isBlank()) {
                    if (data.has("Name")) {
                        warpName = data.get("Name").getAsString();
                    }
                }
                if (warpName == null || warpName.isBlank()) {
                    continue;
                }

                WarpModel warp = new WarpModel(
                        warpName,
                        resolvedWorld,
                        x, y, z,
                        yaw, pitch
                );
                warps.add(warp);
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read KuksoHyWarps warps.json: " + e.getMessage());
            throw e;
        }
        Log.info("[HyEssentialsX] Migrated " + warps.size() + " warps from KuksoHyWarps");
        return warps;
    }

    @Override
    @Nonnull
    public List<HomeEntry> migrateHomes() {
        return List.of();
    }
}
