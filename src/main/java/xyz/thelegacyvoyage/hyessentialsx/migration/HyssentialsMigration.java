package xyz.thelegacyvoyage.hyessentialsx.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;

import javax.annotation.Nonnull;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HyssentialsMigration extends ModMigration {

    private static final Gson GSON = new GsonBuilder().create();

    public HyssentialsMigration(@Nonnull Path sourceDir) {
        super(sourceDir, "Hyssentials");
    }

    @Override
    @Nonnull
    public List<WarpModel> migrateWarps() throws Exception {
        Path warpsFile = sourceDir.resolve("warps.json");
        if (!Files.exists(warpsFile)) {
            Log.warn("[HyEssentialsX] Hyssentials warps.json not found");
            return List.of();
        }
        List<WarpModel> warps = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(warpsFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, HyssentialWarpData>>() {}.getType();
            Map<String, HyssentialWarpData> data = GSON.fromJson(reader, type);
            if (data == null || data.isEmpty()) {
                return warps;
            }
            for (Map.Entry<String, HyssentialWarpData> entry : data.entrySet()) {
                HyssentialWarpData warpData = entry.getValue();
                if (warpData == null) {
                    continue;
                }
                String worldName = resolveWorldName(warpData.worldName, null);
                WarpModel warp = new WarpModel(
                        entry.getKey(),
                        worldName,
                        warpData.x,
                        warpData.y,
                        warpData.z,
                        warpData.yaw,
                        warpData.pitch
                );
                warps.add(warp);
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read Hyssentials warps.json: " + e.getMessage());
            throw e;
        }
        Log.info("[HyEssentialsX] Migrated " + warps.size() + " warps from Hyssentials");
        return warps;
    }

    @Override
    @Nonnull
    public List<HomeEntry> migrateHomes() throws Exception {
        Path homesFile = sourceDir.resolve("homes.json");
        if (!Files.exists(homesFile)) {
            Log.warn("[HyEssentialsX] Hyssentials homes.json not found");
            return List.of();
        }
        List<HomeEntry> homes = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(homesFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Map<String, HyssentialHomeData>>>() {}.getType();
            Map<String, Map<String, HyssentialHomeData>> data = GSON.fromJson(reader, type);
            if (data == null || data.isEmpty()) {
                return homes;
            }
            for (Map.Entry<String, Map<String, HyssentialHomeData>> playerEntry : data.entrySet()) {
                UUID playerId = parseUuid(playerEntry.getKey());
                if (playerId == null) {
                    continue;
                }
                Map<String, HyssentialHomeData> playerHomes = playerEntry.getValue();
                if (playerHomes == null) {
                    continue;
                }
                for (Map.Entry<String, HyssentialHomeData> homeEntry : playerHomes.entrySet()) {
                    HyssentialHomeData homeData = homeEntry.getValue();
                    if (homeData == null) {
                        continue;
                    }
                    String worldName = resolveWorldName(homeData.worldName, null);
                    HomeModel home = new HomeModel(
                            homeEntry.getKey(),
                            null,
                            worldName,
                            homeData.x,
                            homeData.y,
                            homeData.z,
                            homeData.yaw,
                            homeData.pitch
                    );
                    homes.add(new HomeEntry(playerId, home));
                }
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to read Hyssentials homes.json: " + e.getMessage());
            throw e;
        }
        Log.info("[HyEssentialsX] Migrated " + homes.size() + " homes from Hyssentials");
        return homes;
    }

    private static final class HyssentialWarpData {
        String worldName;
        double x;
        double y;
        double z;
        float pitch;
        float yaw;
    }

    private static final class HyssentialHomeData {
        String worldName;
        double x;
        double y;
        double z;
        float pitch;
        float yaw;
    }
}
