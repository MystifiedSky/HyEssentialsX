package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.scoreboard.ScoreboardHud;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.LuckPermsUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.MultipleHudBridge;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.hologram.ByteArrayCommonAsset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ScoreboardManager {

    private static final int INITIAL_DELAY_MS = 500;
    private static final int MAX_PLAYER_OFFSET = 500;
    private static final int DEFAULT_WIDTH = 240;
    private static final String SCOREBOARD_ASSET_PACK = "HyEssentialsX_Assets";
    private static final String SCOREBOARD_ASSET_GROUP = "xyz.thelegacyvoyage.hyessentialsx.assets";
    private static final String SCOREBOARD_ASSET_ZIP = "HyEssentialsX_Assets.zip";
    private static final String SCOREBOARD_ASSET_PATH_PREFIX = "UI/Custom/Textures/HyEssentialsX/Scoreboard/";
    private static final String SCOREBOARD_TEXTURE_PATH_PREFIX = "Common/UI/Custom/Textures/HyEssentialsX/Scoreboard/";
    private static final String SCOREBOARD_LOGO_TOKEN = "scoreboard_logo";
    private static final String LOGO_FILE_PREFIX = "file:";
    private static final String DEFAULT_SCOREBOARD_LOGO_RESOURCE = "scoreboard/HyEssentialsX.png";
    private static final String DEFAULT_SCOREBOARD_LOGO_FILE = "HyEssentialsX.png";

    private final ConfigManager config;
    private final StorageManager storage;
    private final EconomyManager economy;
    private final PlaytimeManager playtime;
    private final Path dataDirectory;
    private final ScheduledExecutorService scheduler;
    private final Map<UUID, ScoreboardHud> huds = new ConcurrentHashMap<>();
    private final Set<UUID> loggedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> suppressedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> defaultOffsetPreview = ConcurrentHashMap.newKeySet();
    private final Object logoLock = new Object();
    @Nullable
    private volatile String cachedLogoSource;
    @Nullable
    private volatile String cachedLogoTexture;

    public ScoreboardManager(@Nonnull ConfigManager config,
                             @Nonnull StorageManager storage,
                             @Nonnull EconomyManager economy,
                             @Nonnull PlaytimeManager playtime,
                             @Nonnull Path dataDirectory) {
        this.config = config;
        this.storage = storage;
        this.economy = economy;
        this.playtime = playtime;
        this.dataDirectory = dataDirectory;
        ensureDefaultScoreboardLogo();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-Scoreboard");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!config.isScoreboardEnabled()) {
            return;
        }
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            return;
        }
        int interval = config.getScoreboardUpdateIntervalMs();
        scheduler.scheduleAtFixedRate(this::tick, interval, interval, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
        huds.clear();
    }

    public void scheduleInitial(@Nonnull PlayerRef playerRef) {
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            return;
        }
        scheduler.schedule(() -> refreshPlayer(playerRef), INITIAL_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public void refreshAll() {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            refreshPlayer(playerRef);
        }
    }

    private void ensureDefaultScoreboardLogo() {
        Path scoreboardDir = dataDirectory.resolve("scoreboard");
        Path logoPath = scoreboardDir.resolve(DEFAULT_SCOREBOARD_LOGO_FILE);
        if (Files.exists(logoPath)) {
            return;
        }
        try (InputStream in = ScoreboardManager.class.getClassLoader()
                .getResourceAsStream(DEFAULT_SCOREBOARD_LOGO_RESOURCE)) {
            if (in == null) {
                Log.warn("[HyEssentialsX] Default scoreboard logo resource missing: "
                        + DEFAULT_SCOREBOARD_LOGO_RESOURCE);
                return;
            }
            Files.createDirectories(scoreboardDir);
            Files.copy(in, logoPath, StandardCopyOption.REPLACE_EXISTING);
            Log.info("[HyEssentialsX] Wrote default scoreboard logo to " + logoPath + ".");
        } catch (IOException e) {
            Log.warn("[HyEssentialsX] Failed to write default scoreboard logo: " + e.getMessage());
        }
    }

    public void refreshPlayer(@Nonnull PlayerRef playerRef) {
        if (!config.isScoreboardEnabled()) {
            hidePlayer(playerRef);
            return;
        }
        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        if (data.getScoreboardHidden() == null) {
            data.setScoreboardHidden(config.isScoreboardDefaultHidden());
            storage.savePlayerDataAsync(playerRef.getUuid(), data);
        }
        if (data.isScoreboardHidden()) {
            hidePlayer(playerRef);
            return;
        }
        runOnWorldThread(playerRef, () -> updatePlayer(playerRef));
    }

    public void onPlayerDisconnect(@Nonnull PlayerRef playerRef) {
        hidePlayer(playerRef);
        huds.remove(playerRef.getUuid());
    }

    public void setPlayerHidden(@Nonnull UUID uuid, boolean hidden) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        data.setScoreboardHidden(hidden);
        storage.savePlayerDataAsync(uuid, data);
    }

    public int getPlayerOffsetX(@Nonnull UUID uuid) {
        return storage.getPlayerData(uuid).getScoreboardOffsetX();
    }

    public int getPlayerOffsetY(@Nonnull UUID uuid) {
        return storage.getPlayerData(uuid).getScoreboardOffsetY();
    }

    public void adjustPlayerOffset(@Nonnull UUID uuid, int deltaX, int deltaY) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        int nextX = clampPlayerOffset(data.getScoreboardOffsetX() + deltaX);
        int nextY = clampPlayerOffset(data.getScoreboardOffsetY() + deltaY);
        data.setScoreboardOffsetX(nextX);
        data.setScoreboardOffsetY(nextY);
        data.setScoreboardOffsetCustomized(true);
        storage.savePlayerDataAsync(uuid, data);
    }

    public void resetPlayerOffset(@Nonnull UUID uuid) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        data.setScoreboardOffsetX(0);
        data.setScoreboardOffsetY(0);
        data.setScoreboardOffsetCustomized(false);
        storage.savePlayerDataAsync(uuid, data);
    }

    public void adjustDefaultOffsets(int deltaX, int deltaY) {
        int oldX = config.getScoreboardOffsetX();
        int oldY = config.getScoreboardOffsetY();
        int nextX = Math.max(0, oldX + deltaX);
        int nextY = Math.max(0, oldY + deltaY);
        setDefaultOffsetsInternal(oldX, oldY, nextX, nextY);
    }

    public void setDefaultOffsets(int offsetX, int offsetY) {
        int oldX = config.getScoreboardOffsetX();
        int oldY = config.getScoreboardOffsetY();
        int nextX = Math.max(0, offsetX);
        int nextY = Math.max(0, offsetY);
        setDefaultOffsetsInternal(oldX, oldY, nextX, nextY);
    }

    private void setDefaultOffsetsInternal(int oldX, int oldY, int nextX, int nextY) {
        if (oldX == nextX && oldY == nextY) {
            return;
        }
        config.setScoreboardOffsets(nextX, nextY);
        int deltaBaseX = nextX - oldX;
        int deltaBaseY = nextY - oldY;
        if (deltaBaseX != 0 || deltaBaseY != 0) {
            for (UUID uuid : storage.listPlayerIds()) {
                PlayerDataModel data = storage.getPlayerData(uuid);
                if (!data.isScoreboardOffsetCustomized()) {
                    continue;
                }
                int adjustedX = clampPlayerOffset(data.getScoreboardOffsetX() - deltaBaseX);
                int adjustedY = clampPlayerOffset(data.getScoreboardOffsetY() - deltaBaseY);
                if (adjustedX == data.getScoreboardOffsetX() && adjustedY == data.getScoreboardOffsetY()) {
                    continue;
                }
                data.setScoreboardOffsetX(adjustedX);
                data.setScoreboardOffsetY(adjustedY);
                storage.savePlayerDataAsync(uuid, data);
            }
        }
        refreshAll();
    }

    public int getResolvedOffsetX(@Nonnull UUID uuid) {
        int base = config.getScoreboardOffsetX();
        if (defaultOffsetPreview.contains(uuid)) {
            return Math.max(0, base);
        }
        int personal = getPlayerOffsetX(uuid);
        return Math.max(0, base + personal);
    }

    public int getResolvedOffsetY(@Nonnull UUID uuid) {
        int base = config.getScoreboardOffsetY();
        if (defaultOffsetPreview.contains(uuid)) {
            return Math.max(0, base);
        }
        int personal = getPlayerOffsetY(uuid);
        return Math.max(0, base + personal);
    }

    public void enableDefaultOffsetPreview(@Nonnull UUID uuid) {
        defaultOffsetPreview.add(uuid);
    }

    public void disableDefaultOffsetPreview(@Nonnull UUID uuid) {
        defaultOffsetPreview.remove(uuid);
    }

    private void tick() {
        if (!config.isScoreboardEnabled()) {
            return;
        }
        refreshAll();
    }

    private void hidePlayer(@Nonnull PlayerRef playerRef) {
        runOnWorldThread(playerRef, () -> {
            Player player = resolvePlayer(playerRef);
            if (player == null) {
                return;
            }
            ScoreboardHud hud = huds.get(playerRef.getUuid());
            if (hud != null) {
                hud.hide(player, playerRef, MultipleHudBridge.isAvailable());
            }
        });
    }

    private void updatePlayer(@Nonnull PlayerRef playerRef) {
        Player player = resolvePlayer(playerRef);
        if (player == null) {
            return;
        }
        boolean useMultipleHud = MultipleHudBridge.isAvailable();
        if (useMultipleHud && !MultipleHudBridge.canAttachToPlayer(player)) {
            if (suppressedPlayers.add(playerRef.getUuid())) {
                String hudName = "unknown";
                if (player.getHudManager().getCustomHud() != null) {
                    hudName = player.getHudManager().getCustomHud().getClass().getName();
                }
                Log.warn("[HyEssentialsX] Scoreboard suppressed for " + playerRef.getUsername()
                        + " (active custom HUD: " + hudName + ").");
            }
            return;
        }
        suppressedPlayers.remove(playerRef.getUuid());
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        ScoreboardHud.State state = buildState(playerRef, player, world);
        ScoreboardHud hud = huds.computeIfAbsent(playerRef.getUuid(), id -> new ScoreboardHud(playerRef));
        if (loggedPlayers.add(playerRef.getUuid())) {
            Log.info("[HyEssentialsX] Scoreboard HUD initialized for " + playerRef.getUsername() + ".");
        }
        hud.update(player, playerRef, state, useMultipleHud);
    }

    @Nonnull
    private ScoreboardHud.State buildState(@Nonnull PlayerRef playerRef,
                                           @Nonnull Player player,
                                           @Nonnull World world) {
        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        Map<String, String> placeholders = buildPlaceholders(playerRef, player, world, data);
        List<String> resolvedLines = new ArrayList<>();
        for (String line : config.getScoreboardLines()) {
            if (line == null) continue;
            String resolved = applyPlaceholders(line, placeholders);
            resolved = PlaceholderApiUtil.applyString(playerRef, resolved);
            resolved = resolved.replace("\n", " ").replace("\r", "");
            resolvedLines.add(resolved);
        }

        int width = config.getScoreboardWidth();
        if (width <= 0) {
            width = DEFAULT_WIDTH;
        }
        int lineHeight = config.getScoreboardLineHeight();
        int lineSpacing = config.getScoreboardLineSpacing();
        int paddingTop = config.getScoreboardPaddingTop();
        int paddingBottom = config.getScoreboardPaddingBottom();
        int paddingLeft = config.getScoreboardPaddingLeft();
        int paddingRight = config.getScoreboardPaddingRight();
        String logoTexture = resolveLogoTexture(config.getScoreboardLogoTexture());
        boolean logoVisible = config.isScoreboardLogoEnabled() && !logoTexture.isBlank();
        int logoHeight = config.getScoreboardLogoHeight();
        int logoPaddingBottom = config.getScoreboardLogoPaddingBottom();
        int headerHeight = logoVisible ? logoHeight + logoPaddingBottom : 0;
        int linesHeight = resolvedLines.isEmpty() ? 0
                : resolvedLines.size() * lineHeight + (resolvedLines.size() - 1) * lineSpacing;

        int height = config.getScoreboardHeight();
        if (height <= 0) {
            height = Math.max(1, paddingTop + paddingBottom + headerHeight + linesHeight);
        }

        int personalOffsetX = data.getScoreboardOffsetX();
        int personalOffsetY = data.getScoreboardOffsetY();
        if (defaultOffsetPreview.contains(playerRef.getUuid())) {
            personalOffsetX = 0;
            personalOffsetY = 0;
        }
        int offsetX = Math.max(0, config.getScoreboardOffsetX() + personalOffsetX);
        int offsetY = Math.max(0, config.getScoreboardOffsetY() + personalOffsetY);

        return new ScoreboardHud.State(
                List.copyOf(resolvedLines),
                config.getScoreboardAnchor(),
                offsetX,
                offsetY,
                width,
                height,
                paddingTop,
                paddingBottom,
                paddingLeft,
                paddingRight,
                lineHeight,
                lineSpacing,
                config.getScoreboardFontSize(),
                config.getScoreboardBackgroundColor(),
                config.getScoreboardTextColor(),
                logoVisible,
                logoTexture,
                config.getScoreboardLogoWidth(),
                logoHeight,
                logoPaddingBottom
        );
    }

    @Nonnull
    private Map<String, String> buildPlaceholders(@Nonnull PlayerRef playerRef,
                                                  @Nonnull Player player,
                                                  @Nonnull World world,
                                                  @Nonnull PlayerDataModel data) {
        Map<String, String> placeholders = new HashMap<>(config.getScoreboardPlaceholders());
        String serverName = placeholders.get("server_name");
        if (serverName == null || serverName.isBlank()) {
            try {
                serverName = HytaleServer.get().getServerName();
            } catch (Exception ignored) {
                serverName = "";
            }
        }
        placeholders.put("server_name", serverName);
        placeholders.put("server", serverName);
        placeholders.put("player", playerRef.getUsername());
        placeholders.put("uuid", playerRef.getUuid().toString());
        placeholders.put("world", world.getName());
        placeholders.put("world_id", world.getWorldConfig().getUuid().toString());
        String playerCount = String.valueOf(Universe.get().getPlayers().size());
        String maxPlayers = String.valueOf(resolveMaxPlayers());
        placeholders.put("player_count", playerCount);
        placeholders.put("max_players", maxPlayers);
        placeholders.put("online", playerCount);
        placeholders.put("max", maxPlayers);
        placeholders.put("server_online", playerCount);
        placeholders.put("server_max_players", maxPlayers);
        placeholders.put("rank", resolveRank(playerRef.getUuid()));
        placeholders.put("playtime", TimeUtil.formatDurationSeconds(playtime.getPlaytimeSeconds(playerRef.getUuid())));
        placeholders.put("playtime_seconds", String.valueOf(playtime.getPlaytimeSeconds(playerRef.getUuid())));

        long balance = economy.isEnabled() ? economy.getBalance(playerRef.getUuid()) : data.getBalance();
        placeholders.put("balance_raw", String.valueOf(balance));
        placeholders.put("balance", formatBalance(balance));
        placeholders.put("currency_symbol", economy.getCurrencySymbol());

        TransformComponent transform = player.getTransformComponent();
        if (transform != null && transform.getPosition() != null) {
            Vector3d pos = transform.getPosition();
            String x = formatCoord(pos.getX());
            String y = formatCoord(pos.getY());
            String z = formatCoord(pos.getZ());
            placeholders.put("x", x);
            placeholders.put("y", y);
            placeholders.put("z", z);
            placeholders.put("coords", x + ", " + y + ", " + z);
        }

        placeholders.put("tps", String.format(Locale.US, "%.1f", (double) world.getTps()));
        return placeholders;
    }

    private int resolveMaxPlayers() {
        int configured = config.getScoreboardMaxPlayers();
        if (configured > 0) {
            return configured;
        }
        try {
            return HytaleServer.get().getConfig().getMaxPlayers();
        } catch (Exception ignored) {
            return 0;
        }
    }

    @Nonnull
    private String resolveRank(@Nonnull UUID uuid) {
        String primary = LuckPermsUtil.getPrimaryGroup(uuid);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        Set<String> groups = LuckPermsUtil.getGroupsFallback(uuid);
        if (!groups.isEmpty()) {
            return groups.iterator().next();
        }
        return "default";
    }

    @Nonnull
    private String formatCoord(double value) {
        return String.format(Locale.US, "%.0f", value);
    }

    @Nonnull
    private String formatBalance(long amount) {
        if (!economy.isEnabled()) {
            return String.valueOf(amount);
        }
        String format = config.getScoreboardBalanceFormat();
        if (format == null) {
            format = "";
        }
        String mode = format.trim().toLowerCase(Locale.ROOT);
        String symbol = economy.getCurrencySymbol();
        return switch (mode) {
            case "compact" -> symbol + formatCompact(amount);
            case "full", "commas", "comma" -> symbol + String.format(Locale.US, "%,d", amount);
            case "raw" -> String.valueOf(amount);
            default -> symbol + amount;
        };
    }

    @Nonnull
    private String formatCompact(long amount) {
        double value = amount;
        String[] suffixes = {"k", "m", "b", "t"};
        int idx = 0;
        while (Math.abs(value) >= 1000.0 && idx < suffixes.length) {
            value /= 1000.0;
            idx++;
        }
        if (idx == 0) {
            return String.valueOf(amount);
        }
        String formatted = Math.abs(value) >= 10.0
                ? String.format(Locale.US, "%.0f", value)
                : String.format(Locale.US, "%.1f", value);
        return formatted + suffixes[idx - 1];
    }

    @Nonnull
    private String applyPlaceholders(@Nonnull String line, @Nonnull Map<String, String> placeholders) {
        String out = line;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = entry.getValue();
            if (value == null) {
                value = "";
            }
            out = out.replace("{" + key + "}", value);
            out = out.replace("%" + key + "%", value);
        }
        return out;
    }

    private int clampPlayerOffset(int value) {
        return Math.max(-MAX_PLAYER_OFFSET, Math.min(MAX_PLAYER_OFFSET, value));
    }

    private void runOnWorldThread(@Nonnull PlayerRef playerRef, @Nonnull Runnable task) {
        World world = resolveWorld(playerRef);
        if (world == null) {
            return;
        }
        if (world.isInThread()) {
            task.run();
        } else {
            world.execute(task);
        }
    }

    @Nullable
    private World resolveWorld(@Nonnull PlayerRef playerRef) {
        try {
            UUID worldId = playerRef.getWorldUuid();
            if (worldId == null) {
                return null;
            }
            return Universe.get().getWorld(worldId);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private Player resolvePlayer(@Nonnull PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        return store.getComponent(ref, Player.getComponentType());
    }

    @Nonnull
    private String resolveLogoTexture(@Nullable String configured) {
        if (configured == null) {
            return "";
        }
        String trimmed = configured.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (!trimmed.regionMatches(true, 0, LOGO_FILE_PREFIX, 0, LOGO_FILE_PREFIX.length())) {
            if (trimmed.regionMatches(true, 0, "Common/", 0, 7)) {
                trimmed = trimmed.substring(7);
            }
            if (trimmed.regionMatches(true, 0, "UI/Custom/Textures/", 0, 19)) {
                return "Common/" + trimmed;
            }
            if (trimmed.regionMatches(true, 0, "Custom/Textures/", 0, 16)) {
                return "Common/UI/" + trimmed;
            }
            return trimmed;
        }
        synchronized (logoLock) {
            if (trimmed.equalsIgnoreCase(cachedLogoSource) && cachedLogoTexture != null) {
                return cachedLogoTexture;
            }
            cachedLogoSource = trimmed;
            cachedLogoTexture = "";
            String filePart = trimmed.substring(LOGO_FILE_PREFIX.length()).trim();
            if (filePart.isEmpty()) {
                Log.warn("[HyEssentialsX] Scoreboard logo file path is empty.");
                return "";
            }
            Path rawPath = Path.of(filePart);
            Path filePath = rawPath.isAbsolute() ? rawPath : dataDirectory.resolve(rawPath).normalize();
            if (!Files.exists(filePath)) {
                Log.warn("[HyEssentialsX] Scoreboard logo file not found: " + filePath);
                return "";
            }
            if (Files.isDirectory(filePath)) {
                Log.warn("[HyEssentialsX] Scoreboard logo path is a directory: " + filePath);
                return "";
            }
            byte[] logoBytes = readLogoBytes(filePath);
            if (logoBytes == null || logoBytes.length == 0) {
                Log.warn("[HyEssentialsX] Scoreboard logo could not be read: " + filePath);
                return "";
            }
            CommonAssetModule commonAssetModule = CommonAssetModule.get();
            if (commonAssetModule == null) {
                Log.warn("[HyEssentialsX] CommonAssetModule not available; cannot load scoreboard logo.");
                return "";
            }
            try {
                String assetName = SCOREBOARD_ASSET_PATH_PREFIX + SCOREBOARD_LOGO_TOKEN + ".png";
                writeScoreboardAssetPack(assetName, logoBytes);
                ByteArrayCommonAsset asset = new ByteArrayCommonAsset(assetName, logoBytes);
                commonAssetModule.addCommonAsset(SCOREBOARD_ASSET_PACK, asset, false);
                if (Universe.get().getPlayerCount() > 0) {
                    commonAssetModule.sendAssets(List.<CommonAsset>of(asset), false);
                }
                cachedLogoTexture = SCOREBOARD_TEXTURE_PATH_PREFIX + SCOREBOARD_LOGO_TOKEN + ".png";
                Log.info("[HyEssentialsX] Loaded scoreboard logo from " + filePath + ".");
                return cachedLogoTexture;
            } catch (Exception e) {
                Log.warn("[HyEssentialsX] Failed to load scoreboard logo: " + e.getMessage());
                return "";
            }
        }
    }

    private void writeScoreboardAssetPack(@Nonnull String assetName, @Nonnull byte[] logoBytes) {
        Path modsDirectory = resolveModsDirectory();
        Path packPath = modsDirectory.resolve(SCOREBOARD_ASSET_ZIP);
        Path tempPath = modsDirectory.resolve(SCOREBOARD_ASSET_ZIP + ".tmp");
        String logoEntryName = "Common/" + assetName;
        byte[] manifestBytes = buildScoreboardManifest().getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(modsDirectory);
            if (Files.exists(packPath)) {
                mergeAssetPack(packPath, tempPath, logoEntryName, manifestBytes, logoBytes);
            } else {
                writeAssetPack(packPath, logoEntryName, manifestBytes, logoBytes);
            }
        } catch (IOException e) {
            Log.warn("[HyEssentialsX] Failed to write scoreboard asset pack: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
        }
    }

    private void mergeAssetPack(@Nonnull Path packPath,
                                @Nonnull Path tempPath,
                                @Nonnull String logoEntryName,
                                @Nonnull byte[] manifestBytes,
                                @Nonnull byte[] logoBytes) throws IOException {
        try (ZipFile zipFile = new ZipFile(packPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(
                     Files.newOutputStream(tempPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                             StandardOpenOption.WRITE))) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.equals("manifest.json") || name.equals(logoEntryName)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    continue;
                }
                ZipEntry copy = new ZipEntry(name);
                zos.putNextEntry(copy);
                try (InputStream in = zipFile.getInputStream(entry)) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
            writeZipEntry(zos, "manifest.json", manifestBytes);
            writeZipEntry(zos, logoEntryName, logoBytes);
        }
        Files.move(tempPath, packPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeAssetPack(@Nonnull Path packPath,
                                @Nonnull String logoEntryName,
                                @Nonnull byte[] manifestBytes,
                                @Nonnull byte[] logoBytes) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(
                Files.newOutputStream(packPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE))) {
            writeZipEntry(zos, "manifest.json", manifestBytes);
            writeZipEntry(zos, logoEntryName, logoBytes);
        }
    }

    private void writeZipEntry(@Nonnull ZipOutputStream zos,
                               @Nonnull String entryName,
                               @Nonnull byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(bytes);
        zos.closeEntry();
    }

    @Nonnull
    private String buildScoreboardManifest() {
        return "{\n"
                + "  \"Name\": \"" + SCOREBOARD_ASSET_PACK + "\",\n"
                + "  \"Group\": \"" + SCOREBOARD_ASSET_GROUP + "\",\n"
                + "  \"Version\": \"1.0.0\",\n"
                + "  \"Description\": \"HyEssentialsX scoreboard UI assets\",\n"
                + "  \"IncludesAssetPack\": true\n"
                + "}\n";
    }

    @Nonnull
    private Path resolveModsDirectory() {
        Path current = dataDirectory;
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && name.toString().equalsIgnoreCase("mods")) {
                return current;
            }
            current = current.getParent();
        }
        Path parent = dataDirectory.getParent();
        if (parent != null) {
            Path siblingMods = parent.resolve("mods");
            if (Files.isDirectory(siblingMods)) {
                return siblingMods;
            }
            return parent;
        }
        return dataDirectory;
    }

    @Nullable
    private byte[] readLogoBytes(@Nonnull Path filePath) {
        try {
            BufferedImage image = ImageIO.read(filePath.toFile());
            if (image == null) {
                return Files.readAllBytes(filePath);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException e) {
            Log.warn("[HyEssentialsX] Failed to read scoreboard logo: " + e.getMessage());
            return null;
        }
    }

    @Nonnull
    private String sanitizeAssetToken(@Nonnull String filename) {
        String base = filename;
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            base = filename.substring(0, dot);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(Character.toLowerCase(c));
            } else if (c == '_' || c == '-') {
                out.append('_');
            }
        }
        if (out.length() == 0) {
            return "logo";
        }
        return out.toString();
    }
}
