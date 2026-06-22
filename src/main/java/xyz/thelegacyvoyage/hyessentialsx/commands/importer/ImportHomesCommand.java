package xyz.thelegacyvoyage.hyessentialsx.commands.importer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.models.HomeModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.StorageManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ImportHomesCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.import";
    private static final String DEFAULT_WORLD_NAME = "default";

    private final StorageManager storage;
    private final Path dataFolder;
    private final RequiredArg<String> fileArg;
    private final Gson gson;

    public ImportHomesCommand(@Nonnull StorageManager storage, @Nonnull Path dataFolder) {
        super("import", "Import homes from another plugin");
        this.storage = storage;
        this.dataFolder = dataFolder;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"heximport", "homeimport"});
        this.fileArg = withRequiredArg("file", "Import filename", ArgTypes.STRING);
        this.gson = new GsonBuilder().create();
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/import");
            return;
        }

        String fileName = context.get(fileArg);
        if (fileName == null || fileName.isBlank()) {
            Messages.err(context, "Import filename required.");
            return;
        }

        Path file = resolveImportFile(fileName.trim());
        if (file == null) {
            Messages.err(context, "Import file not found in plugin folder.");
            return;
        }

        ImportFile payload;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            payload = gson.fromJson(json, ImportFile.class);
        } catch (Exception e) {
            Messages.err(context, "Failed to read import file: " + e.getMessage());
            return;
        }

        if (payload == null || payload.homes == null || payload.homes.length == 0) {
            Messages.err(context, "No homes found in import file.");
            return;
        }

        ImportResult result = new ImportResult();
        Map<UUID, PlayerDataModel> modified = new HashMap<>();

        for (ImportHome entry : payload.homes) {
            result.total++;
            UUID playerId = parseUuid(entry != null ? entry.uuid : null);
            if (playerId == null) {
                result.invalid++;
                continue;
            }
            String name = entry != null ? entry.name : null;
            if (name == null || name.isBlank()) {
                result.invalid++;
                continue;
            }
            if (entry.position == null) {
                result.invalid++;
                continue;
            }

            double x = entry.position.x;
            double y = entry.position.y;
            double z = entry.position.z;

            float pitch = 0f;
            float yaw = 0f;
            if (entry.rotation != null) {
                pitch = (float) entry.rotation.x;
                yaw = (float) entry.rotation.y;
            }

            String worldRaw = firstNonBlank(entry.world, entry.worldId, entry.worldName, entry.worldUuid, entry.worldUUID);
            String worldId = null;
            String worldName = null;
            if (worldRaw != null) {
                UUID parsedWorld = parseUuid(worldRaw);
                if (parsedWorld != null) {
                    worldId = parsedWorld.toString();
                } else {
                    worldName = worldRaw;
                }
            }
            if (worldName == null || worldName.isBlank()) {
                worldName = DEFAULT_WORLD_NAME;
            }

            PlayerDataModel data = modified.computeIfAbsent(playerId, storage::getPlayerData);
            Map<String, HomeModel> homes = data.getHomes();

            String finalName = name.trim();
            String key = finalName.toLowerCase(Locale.ROOT);
            if (homes.containsKey(key)) {
                finalName = resolveUniqueName(homes, finalName);
                result.renamed++;
            }

            HomeModel model = new HomeModel(
                    finalName,
                    worldId,
                    worldName,
                    x, y, z,
                    yaw, pitch
            );
            homes.put(finalName.toLowerCase(Locale.ROOT), model);
            result.imported++;
        }

        for (Map.Entry<UUID, PlayerDataModel> entry : modified.entrySet()) {
            storage.savePlayerDataAsync(entry.getKey(), entry.getValue());
        }

        if (result.imported == 0) {
            Messages.err(context, "No homes were imported. Invalid entries: " + result.invalid + ".");
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Imported ").append(result.imported).append(" home(s) for ")
                .append(modified.size()).append(" player(s).");
        if (result.renamed > 0) {
            summary.append(" Renamed ").append(result.renamed).append(" due to duplicates.");
        }
        if (result.invalid > 0) {
            summary.append(" Skipped ").append(result.invalid).append(" invalid entr");
            summary.append(result.invalid == 1 ? "y." : "ies.");
        }
        Messages.ok(context, summary.toString());
    }

    @Nullable
    private Path resolveImportFile(@Nonnull String fileName) {
        Path direct = dataFolder.resolve(fileName);
        if (Files.exists(direct)) return direct;
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            Path withJson = dataFolder.resolve(fileName + ".json");
            if (Files.exists(withJson)) return withJson;
        }
        return null;
    }

    @Nullable
    private static UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static String firstNonBlank(@Nullable String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String resolveUniqueName(@Nonnull Map<String, HomeModel> homes, @Nonnull String baseName) {
        String trimmed = baseName.trim();
        String candidate = trimmed;
        int counter = 1;
        while (homes.containsKey(candidate.toLowerCase(Locale.ROOT))) {
            candidate = trimmed + "_" + counter;
            counter++;
        }
        return candidate;
    }

    private static final class ImportResult {
        int total;
        int imported;
        int renamed;
        int invalid;
    }

    private static final class ImportFile {
        String version;
        ImportHome[] homes;
    }

    private static final class ImportHome {
        String uuid;
        String name;
        ImportPosition position;
        ImportRotation rotation;
        String world;
        String worldName;
        String worldId;
        String worldUuid;
        String worldUUID;
    }

    private static final class ImportPosition {
        double x;
        double y;
        double z;
    }

    private static final class ImportRotation {
        double x;
        double y;
        double z;
    }
}
