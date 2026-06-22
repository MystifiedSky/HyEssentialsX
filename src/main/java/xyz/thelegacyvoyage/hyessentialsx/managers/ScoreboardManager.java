package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ScoreboardManager {

    private static final int INITIAL_DELAY_MS = 500;
    private static final int MAX_PLAYER_OFFSET = 500;
    private static final int DEFAULT_WIDTH = 240;
    private static final int DEFAULT_PROFILE_PAGE_DURATION_MS = 5000;
    private static final int DEFAULT_MAX_RENDER_LINES = 50;
    private static final Pattern INLINE_ANIMATION_PATTERN =
            Pattern.compile("\\{animation:([a-zA-Z_]+)(?::([^}]+))?\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACKET_ANIMATION_PATTERN =
            Pattern.compile("\\[anim:([a-zA-Z_]+)(?::([^\\]]+))?\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_COLOR_PATTERN =
            Pattern.compile("(?i)\\{#[0-9a-f]{6}}|[&§]#[0-9a-f]{6}|[&§][0-9a-fk-or]");
    private static final int DEFAULT_CUSTOM_ANIMATION_SPEED = 1;
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
    private final Path scoreboardConfigPath;
    private final ScheduledExecutorService scheduler;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Map<UUID, ScoreboardHud> huds = new ConcurrentHashMap<>();
    private final Set<UUID> loggedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> suppressedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> defaultOffsetPreview = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PageRotationState> pageRotationStates = new ConcurrentHashMap<>();
    private final Object logoLock = new Object();
    private final Map<String, String> cachedLogoTextures = new HashMap<>();
    private final AtomicLong animationStep = new AtomicLong(0L);
    @Nullable
    private volatile AdvancedScoreboardConfig advancedConfig;
    private volatile Map<String, CustomColorAnimation> customAnimations = Map.of();
    @Nullable
    private volatile ScheduledFuture<?> tickTask;

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
        this.scoreboardConfigPath = dataDirectory.resolve("scoreboardConfig.json");
        ensureDefaultScoreboardLogo();
        this.advancedConfig = loadAdvancedScoreboardConfig();
        this.customAnimations = loadCustomAnimations();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyEssentialsX-Scoreboard");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!config.isScoreboardEnabled()) {
            cancelTickTask();
            return;
        }
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            return;
        }
        ScheduledFuture<?> existing = tickTask;
        if (existing != null && !existing.isCancelled() && !existing.isDone()) {
            return;
        }
        int interval = config.getScoreboardUpdateIntervalMs();
        tickTask = scheduler.scheduleAtFixedRate(this::tick, interval, interval, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        cancelTickTask();
        scheduler.shutdownNow();
        huds.clear();
        pageRotationStates.clear();
    }

    public void reloadConfiguration() {
        config.reload();
        advancedConfig = loadAdvancedScoreboardConfig();
        customAnimations = loadCustomAnimations();
        synchronized (logoLock) {
            cachedLogoTextures.clear();
        }
        pageRotationStates.clear();
        restartTickTask();
        refreshAll();
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
        pageRotationStates.remove(playerRef.getUuid());
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

    private void restartTickTask() {
        cancelTickTask();
        start();
    }

    private void cancelTickTask() {
        ScheduledFuture<?> current = tickTask;
        tickTask = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    private void tick() {
        if (!config.isScoreboardEnabled()) {
            return;
        }
        animationStep.incrementAndGet();
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
        AdvancedProfile profile = resolveAdvancedProfile(playerRef, world, placeholders);
        AdvancedPage activePage = resolveAdvancedPage(playerRef.getUuid(), profile);
        List<String> resolvedLines = resolveScoreboardLines(playerRef, placeholders, profile, activePage);

        int lineHeight = config.getScoreboardLineHeight();
        int lineSpacing = config.getScoreboardLineSpacing();
        int paddingTop = config.getScoreboardPaddingTop();
        int paddingBottom = config.getScoreboardPaddingBottom();
        int paddingLeft = config.getScoreboardPaddingLeft();
        int paddingRight = config.getScoreboardPaddingRight();

        ResolvedSize size = resolveSize(profile);
        int width = size.width > 0 ? size.width : config.getScoreboardWidth();
        if (width <= 0) width = DEFAULT_WIDTH;

        ResolvedLogo logo = resolveLogo(profile);
        String logoTexture = resolveLogoTexture(logo.texture);
        boolean logoVisible = logo.enabled && !logoTexture.isBlank();
        int logoWidth = Math.max(1, logo.width);
        int logoHeight = Math.max(1, logo.height);
        int logoPaddingBottom = Math.max(0, logo.paddingBottom);

        if (size.autoWidth) {
            width = resolveAutoWidth(
                    resolvedLines,
                    Math.max(8, config.getScoreboardFontSize()),
                    paddingLeft,
                    paddingRight,
                    logoVisible ? logoWidth : 0
            );
            width = clampToRange(width, size.minWidth, size.maxWidth);
        } else if (size.minWidth > 0 || size.maxWidth > 0) {
            width = clampToRange(width, size.minWidth, size.maxWidth);
        }

        int headerHeight = logoVisible ? logoHeight + logoPaddingBottom : 0;
        int linesHeight = resolvedLines.isEmpty() ? 0
                : resolvedLines.size() * lineHeight + (resolvedLines.size() - 1) * lineSpacing;

        int height = size.height > 0 ? size.height : config.getScoreboardHeight();
        if (size.autoHeight || height <= 0) {
            height = Math.max(1, paddingTop + paddingBottom + headerHeight + linesHeight);
        }
        if (size.minHeight > 0 || size.maxHeight > 0) {
            height = clampToRange(height, size.minHeight, size.maxHeight);
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
                logoWidth,
                logoHeight,
                logoPaddingBottom
        );
    }

    @Nullable
    private AdvancedProfile resolveAdvancedProfile(@Nonnull PlayerRef playerRef,
                                                   @Nonnull World world,
                                                   @Nonnull Map<String, String> placeholders) {
        AdvancedScoreboardConfig advanced = advancedConfig;
        if (advanced == null || advanced.profiles.isEmpty()) {
            return null;
        }

        String selectedProfile = null;
        String worldName = world.getName() == null ? "" : world.getName().trim().toLowerCase(Locale.ROOT);
        if (!worldName.isBlank()) {
            selectedProfile = advanced.worldProfiles.get(worldName);
        }

        for (AdvancedProfileCondition condition : advanced.conditionalProfiles) {
            if (condition.permission != null && !condition.permission.isBlank()
                    && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(playerRef, condition.permission)) {
                continue;
            }
            if (condition.condition != null && !evaluateCondition(condition.condition, placeholders, playerRef)) {
                continue;
            }
            selectedProfile = condition.profileId;
            break;
        }

        if (selectedProfile == null || selectedProfile.isBlank()) {
            selectedProfile = advanced.defaultProfile;
        }
        if (selectedProfile == null || selectedProfile.isBlank()) {
            return null;
        }
        String key = selectedProfile.trim().toLowerCase(Locale.ROOT);
        AdvancedProfile profile = advanced.profiles.get(key);
        if (profile != null) {
            return profile;
        }
        return advanced.profiles.get(advanced.defaultProfile);
    }

    @Nullable
    private AdvancedPage resolveAdvancedPage(@Nonnull UUID playerId, @Nullable AdvancedProfile profile) {
        if (profile == null || profile.pages.isEmpty()) {
            pageRotationStates.remove(playerId);
            return null;
        }
        if (profile.pages.size() == 1) {
            pageRotationStates.remove(playerId);
            return profile.pages.get(0);
        }

        long now = System.currentTimeMillis();
        PageRotationState current = pageRotationStates.get(playerId);
        if (current == null || !current.profileId.equals(profile.id) || current.pageIndex >= profile.pages.size()) {
            int firstDuration = Math.max(250, profile.pages.get(0).durationMs);
            current = new PageRotationState(profile.id, 0, now + firstDuration);
        } else if (now >= current.nextSwitchAtMs) {
            int index = current.pageIndex;
            long nextSwitch = current.nextSwitchAtMs;
            int guard = 0;
            while (now >= nextSwitch && guard < profile.pages.size() * 4) {
                index = (index + 1) % profile.pages.size();
                int duration = Math.max(250, profile.pages.get(index).durationMs);
                nextSwitch += duration;
                guard++;
            }
            current = new PageRotationState(profile.id, index, nextSwitch);
        }

        pageRotationStates.put(playerId, current);
        int resolvedIndex = Math.max(0, Math.min(current.pageIndex, profile.pages.size() - 1));
        return profile.pages.get(resolvedIndex);
    }

    @Nonnull
    private List<String> resolveScoreboardLines(@Nonnull PlayerRef playerRef,
                                                @Nonnull Map<String, String> placeholders,
                                                @Nullable AdvancedProfile profile,
                                                @Nullable AdvancedPage page) {
        List<String> lines = new ArrayList<>();
        int lineIndex = 0;
        if (page != null) {
            if (page.title != null && !page.title.isBlank()) {
                String renderedTitle = resolveRenderedLine(
                        playerRef,
                        placeholders,
                        "<center>" + page.title + "</center>",
                        lineIndex
                );
                lines.add(renderedTitle);
                lineIndex++;
            }
            for (AdvancedLineRule lineRule : page.lines) {
                String raw = resolveRawLine(playerRef, placeholders, lineRule);
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String resolved = resolveRenderedLine(playerRef, placeholders, raw, lineIndex);
                lines.add(resolved);
                lineIndex++;
                if (lines.size() >= DEFAULT_MAX_RENDER_LINES) {
                    break;
                }
            }
            if (!lines.isEmpty()) {
                return lines;
            }
        }

        for (String line : config.getScoreboardLines()) {
            if (line == null) continue;
            String resolved = resolveRenderedLine(playerRef, placeholders, line, lineIndex);
            lines.add(resolved);
            lineIndex++;
            if (lines.size() >= DEFAULT_MAX_RENDER_LINES) {
                break;
            }
        }
        return lines;
    }

    @Nullable
    private String resolveRawLine(@Nonnull PlayerRef playerRef,
                                  @Nonnull Map<String, String> placeholders,
                                  @Nonnull AdvancedLineRule rule) {
        if (rule.literalText != null) {
            return rule.literalText;
        }
        if (rule.condition == null) {
            return null;
        }
        boolean match = evaluateCondition(rule.condition, placeholders, playerRef);
        if (match) {
            return rule.thenText;
        }
        return rule.elseText;
    }

    @Nonnull
    private String resolveRenderedLine(@Nonnull PlayerRef playerRef,
                                       @Nonnull Map<String, String> placeholders,
                                       @Nonnull String raw,
                                       int lineIndex) {
        String resolved = applyPlaceholders(raw, placeholders);
        resolved = PlaceholderApiUtil.applyString(playerRef, resolved);
        resolved = resolved.replace("\n", " ").replace("\r", "");
        return applyAnimatedText(resolved, lineIndex);
    }

    @Nonnull
    private String applyAnimatedText(@Nonnull String line, int lineIndex) {
        AnimationSpec spec = extractAnimationSpec(line);
        if (spec == null) {
            return line;
        }

        String cleanText = stripInlineColorFormatting(spec.textWithoutTag);
        if (cleanText.isBlank()) {
            cleanText = " ";
        }

        String animatedColor = resolveAnimationColor(spec.type, spec.params, lineIndex);
        if (animatedColor == null || animatedColor.isBlank()) {
            return cleanText;
        }
        return "&" + animatedColor + cleanText;
    }

    @Nullable
    private AnimationSpec extractAnimationSpec(@Nonnull String line) {
        Matcher inlineMatcher = INLINE_ANIMATION_PATTERN.matcher(line);
        if (inlineMatcher.find()) {
            String type = inlineMatcher.group(1);
            String paramsRaw = inlineMatcher.group(2);
            String textWithoutTag = inlineMatcher.replaceAll("");
            return new AnimationSpec(type, splitAnimationParams(paramsRaw), textWithoutTag);
        }

        Matcher bracketMatcher = BRACKET_ANIMATION_PATTERN.matcher(line);
        if (bracketMatcher.find()) {
            String type = bracketMatcher.group(1);
            String paramsRaw = bracketMatcher.group(2);
            String textWithoutTag = bracketMatcher.replaceAll("");
            return new AnimationSpec(type, splitAnimationParams(paramsRaw), textWithoutTag);
        }
        return null;
    }

    @Nonnull
    private String[] splitAnimationParams(@Nullable String paramsRaw) {
        if (paramsRaw == null || paramsRaw.isBlank()) {
            return new String[0];
        }
        String[] split = paramsRaw.split(":");
        List<String> params = new ArrayList<>(split.length);
        for (String param : split) {
            if (param == null) {
                continue;
            }
            String trimmed = param.trim();
            if (!trimmed.isBlank()) {
                params.add(trimmed);
            }
        }
        return params.toArray(new String[0]);
    }

    @Nonnull
    private String stripInlineColorFormatting(@Nonnull String text) {
        String out = text;
        out = HEX_COLOR_PATTERN.matcher(out).replaceAll("");
        out = out.replaceAll("(?i)<#[0-9a-f]{6}>", "");
        out = out.replaceAll("(?i)</#[0-9a-f]{6}>", "");
        out = out.replaceAll("(?i)</?(bold|italic|underlined|strikethrough|obfuscated)>", "");
        return out;
    }

    @Nonnull
    private String resolveAnimationColor(@Nullable String typeRaw,
                                         @Nonnull String[] params,
                                         int lineIndex) {
        if (typeRaw == null || typeRaw.isBlank()) {
            return "#FFFFFF";
        }
        String type = typeRaw.trim().toLowerCase(Locale.ROOT);
        long step = animationStep.get();

        if ("custom".equals(type)) {
            if (params.length == 0) {
                return "#FFFFFF";
            }
            String customName = params[0].trim().toLowerCase(Locale.ROOT);
            CustomColorAnimation custom = customAnimations.get(customName);
            if (custom == null || custom.frames.isEmpty()) {
                return "#FFFFFF";
            }
            int speed = Math.max(DEFAULT_CUSTOM_ANIMATION_SPEED, custom.speed);
            int frame = (int) ((step / speed) % custom.frames.size());
            return ensureHexColor(custom.frames.get(frame), "#FFFFFF");
        }

        return switch (type) {
            case "rainbow" -> rainbowColor(step, lineIndex, params);
            case "gradient" -> gradientColor(step, lineIndex, params);
            case "pulse" -> pulseColor(step, params);
            case "shimmer" -> shimmerColor(step, lineIndex, params);
            case "breathe" -> breatheColor(step, params);
            case "glow" -> glowColor(step, params);
            case "strobe" -> strobeColor(step, params);
            default -> {
                CustomColorAnimation custom = customAnimations.get(type);
                if (custom == null || custom.frames.isEmpty()) {
                    yield "#FFFFFF";
                }
                int speed = Math.max(DEFAULT_CUSTOM_ANIMATION_SPEED, custom.speed);
                int frame = (int) ((step / speed) % custom.frames.size());
                yield ensureHexColor(custom.frames.get(frame), "#FFFFFF");
            }
        };
    }

    @Nonnull
    private String rainbowColor(long step, int lineIndex, @Nonnull String[] params) {
        boolean wave = params.length > 0 && "wave".equalsIgnoreCase(params[0]);
        float hue = wave
                ? (float) ((step * 5L + (long) lineIndex * 30L) % 360L)
                : (float) ((step * 5L) % 360L);
        return hsvToHex(hue, 1.0f, 1.0f);
    }

    @Nonnull
    private String gradientColor(long step, int lineIndex, @Nonnull String[] params) {
        String startColor = params.length >= 1 ? ensureHexColor(params[0], "#0066FF") : "#0066FF";
        String endColor = params.length >= 2 ? ensureHexColor(params[1], "#FF00FF") : "#FF00FF";
        boolean wave = params.length >= 3 && "wave".equalsIgnoreCase(params[2]);
        double progress = wave
                ? (Math.sin(step * 0.08 + lineIndex * 0.5) + 1.0) / 2.0
                : (Math.sin(step * 0.08) + 1.0) / 2.0;
        return interpolateColor(startColor, endColor, progress);
    }

    @Nonnull
    private String pulseColor(long step, @Nonnull String[] params) {
        String color1 = params.length >= 1 ? ensureHexColor(params[0], "#FFFFFF") : "#FFFFFF";
        String color2 = params.length >= 2 ? ensureHexColor(params[1], "#FF0000") : "#FF0000";
        double progress = (Math.sin(step * 0.1) + 1.0) / 2.0;
        return interpolateColor(color1, color2, progress);
    }

    @Nonnull
    private String shimmerColor(long step, int lineIndex, @Nonnull String[] params) {
        String base = params.length >= 1 ? ensureHexColor(params[0], "#FFD700") : "#FFD700";
        int[] rgb = parseHexColor(base, new int[]{255, 215, 0});
        long seed = (step / 2L) + (long) lineIndex * 1000L;
        int variation = (int) ((seed * 1103515245L + 12345L) >>> 16) % 80 - 30;
        int r = clampColor(rgb[0] + variation);
        int g = clampColor(rgb[1] + variation);
        int b = clampColor(rgb[2] + variation);
        return rgbToHex(r, g, b);
    }

    @Nonnull
    private String breatheColor(long step, @Nonnull String[] params) {
        String base = params.length >= 1 ? ensureHexColor(params[0], "#FFFFFF") : "#FFFFFF";
        int[] rgb = parseHexColor(base, new int[]{255, 255, 255});
        double brightness = 0.4 + 0.6 * ((Math.sin(step * 0.05) + 1.0) / 2.0);
        int r = clampColor((int) Math.round(rgb[0] * brightness));
        int g = clampColor((int) Math.round(rgb[1] * brightness));
        int b = clampColor((int) Math.round(rgb[2] * brightness));
        return rgbToHex(r, g, b);
    }

    @Nonnull
    private String glowColor(long step, @Nonnull String[] params) {
        String base = params.length >= 1 ? ensureHexColor(params[0], "#00AAFF") : "#00AAFF";
        int[] rgb = parseHexColor(base, new int[]{0, 170, 255});
        double glowFactor = 0.6 + 0.7 * ((Math.sin(step * 0.08) + 1.0) / 2.0);
        int r = clampColor((int) Math.round(rgb[0] * glowFactor));
        int g = clampColor((int) Math.round(rgb[1] * glowFactor));
        int b = clampColor((int) Math.round(rgb[2] * glowFactor));
        return rgbToHex(r, g, b);
    }

    @Nonnull
    private String strobeColor(long step, @Nonnull String[] params) {
        String color1 = params.length >= 1 ? ensureHexColor(params[0], "#FFFFFF") : "#FFFFFF";
        String color2 = params.length >= 2 ? ensureHexColor(params[1], "#000000") : "#000000";
        boolean first = ((step / 2L) % 2L) == 0L;
        return first ? color1 : color2;
    }

    @Nonnull
    private String ensureHexColor(@Nullable String raw, @Nonnull String def) {
        if (raw == null) {
            return def;
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            return def;
        }
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        if (trimmed.matches("(?i)#[0-9a-f]{6}")) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return def;
    }

    @Nonnull
    private String hsvToHex(float hue, float saturation, float value) {
        float c = value * saturation;
        float x = c * (1.0f - Math.abs((hue / 60.0f) % 2.0f - 1.0f));
        float m = value - c;
        float rPrime;
        float gPrime;
        float bPrime;

        if (hue < 60.0f) {
            rPrime = c;
            gPrime = x;
            bPrime = 0.0f;
        } else if (hue < 120.0f) {
            rPrime = x;
            gPrime = c;
            bPrime = 0.0f;
        } else if (hue < 180.0f) {
            rPrime = 0.0f;
            gPrime = c;
            bPrime = x;
        } else if (hue < 240.0f) {
            rPrime = 0.0f;
            gPrime = x;
            bPrime = c;
        } else if (hue < 300.0f) {
            rPrime = x;
            gPrime = 0.0f;
            bPrime = c;
        } else {
            rPrime = c;
            gPrime = 0.0f;
            bPrime = x;
        }

        int r = clampColor(Math.round((rPrime + m) * 255.0f));
        int g = clampColor(Math.round((gPrime + m) * 255.0f));
        int b = clampColor(Math.round((bPrime + m) * 255.0f));
        return rgbToHex(r, g, b);
    }

    @Nonnull
    private String interpolateColor(@Nonnull String color1, @Nonnull String color2, double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        int[] rgb1 = parseHexColor(color1, new int[]{255, 255, 255});
        int[] rgb2 = parseHexColor(color2, new int[]{255, 255, 255});
        int r = clampColor((int) Math.round(rgb1[0] + (rgb2[0] - rgb1[0]) * clamped));
        int g = clampColor((int) Math.round(rgb1[1] + (rgb2[1] - rgb1[1]) * clamped));
        int b = clampColor((int) Math.round(rgb1[2] + (rgb2[2] - rgb1[2]) * clamped));
        return rgbToHex(r, g, b);
    }

    @Nonnull
    private int[] parseHexColor(@Nonnull String hex, @Nonnull int[] def) {
        String normalized = ensureHexColor(hex, "#FFFFFF");
        try {
            int r = Integer.parseInt(normalized.substring(1, 3), 16);
            int g = Integer.parseInt(normalized.substring(3, 5), 16);
            int b = Integer.parseInt(normalized.substring(5, 7), 16);
            return new int[]{r, g, b};
        } catch (Exception ignored) {
            return def;
        }
    }

    @Nonnull
    private String rgbToHex(int r, int g, int b) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", clampColor(r), clampColor(g), clampColor(b));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Nonnull
    private Map<String, CustomColorAnimation> loadCustomAnimations() {
        Path animationsDir = dataDirectory.resolve("scoreboard").resolve("animations");
        Map<String, CustomColorAnimation> loaded = new HashMap<>();
        try {
            Files.createDirectories(animationsDir);
            ensureDefaultCustomAnimations(animationsDir);
            try (var stream = Files.list(animationsDir)) {
                stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .forEach(path -> loadCustomAnimationFile(path, loaded));
            }
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to load scoreboard custom animations: " + e.getMessage());
        }
        return loaded.isEmpty() ? Map.of() : Map.copyOf(loaded);
    }

    private void ensureDefaultCustomAnimations(@Nonnull Path animationsDir) {
        try (var stream = Files.list(animationsDir)) {
            boolean hasJson = stream.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"));
            if (hasJson) {
                return;
            }
        } catch (Exception ignored) {
            return;
        }
        writeDefaultCustomAnimation(
                animationsDir.resolve("flash.json"),
                2,
                List.of("#FFFFFF", "#FF0000", "#FFFFFF", "#FF0000")
        );
        writeDefaultCustomAnimation(
                animationsDir.resolve("ocean.json"),
                3,
                List.of("#0044AA", "#0066CC", "#0088FF", "#00AAFF", "#0088FF", "#0066CC")
        );
        writeDefaultCustomAnimation(
                animationsDir.resolve("gold.json"),
                3,
                List.of("#FFD700", "#FFEC8B", "#FFD700", "#DAA520", "#FFD700")
        );
    }

    private void writeDefaultCustomAnimation(@Nonnull Path target,
                                             int speed,
                                             @Nonnull List<String> frames) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("speed", Math.max(DEFAULT_CUSTOM_ANIMATION_SPEED, speed));
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            for (String frame : frames) {
                array.add(ensureHexColor(frame, "#FFFFFF"));
            }
            root.add("frames", array);
            Files.writeString(target, gson.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (Exception ignored) {
        }
    }

    private void loadCustomAnimationFile(@Nonnull Path path,
                                         @Nonnull Map<String, CustomColorAnimation> out) {
        try {
            String fileName = path.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String key = (dot > 0 ? fileName.substring(0, dot) : fileName).trim().toLowerCase(Locale.ROOT);
            if (key.isBlank()) {
                return;
            }

            String content = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(content, JsonObject.class);
            if (json == null) {
                return;
            }

            int speed = DEFAULT_CUSTOM_ANIMATION_SPEED;
            JsonElement speedElement = json.get("speed");
            if (speedElement != null && speedElement.isJsonPrimitive()) {
                try {
                    speed = Math.max(DEFAULT_CUSTOM_ANIMATION_SPEED, speedElement.getAsInt());
                } catch (Exception ignored) {
                    speed = DEFAULT_CUSTOM_ANIMATION_SPEED;
                }
            }

            JsonElement framesElement = json.get("frames");
            if (framesElement == null || !framesElement.isJsonArray()) {
                return;
            }
            List<String> frames = new ArrayList<>();
            for (JsonElement frameElement : framesElement.getAsJsonArray()) {
                if (frameElement == null || !frameElement.isJsonPrimitive()) {
                    continue;
                }
                String color = ensureHexColor(frameElement.getAsString(), "");
                if (!color.isBlank()) {
                    frames.add(color);
                }
            }
            if (frames.isEmpty()) {
                return;
            }
            out.put(key, new CustomColorAnimation(speed, List.copyOf(frames)));
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed loading animation file " + path.getFileName() + ": " + e.getMessage());
        }
    }

    @Nonnull
    private ResolvedLogo resolveLogo(@Nullable AdvancedProfile profile) {
        AdvancedLogo override = profile != null ? profile.logo : null;
        boolean enabled = override != null && override.enabled != null
                ? override.enabled
                : config.isScoreboardLogoEnabled();
        String texture = override != null && override.texture != null && !override.texture.isBlank()
                ? override.texture
                : config.getScoreboardLogoTexture();
        int width = override != null && override.width != null
                ? Math.max(1, override.width)
                : config.getScoreboardLogoWidth();
        int height = override != null && override.height != null
                ? Math.max(1, override.height)
                : config.getScoreboardLogoHeight();
        int paddingBottom = override != null && override.paddingBottom != null
                ? Math.max(0, override.paddingBottom)
                : config.getScoreboardLogoPaddingBottom();
        return new ResolvedLogo(enabled, texture, width, height, paddingBottom);
    }

    @Nonnull
    private ResolvedSize resolveSize(@Nullable AdvancedProfile profile) {
        AdvancedSize override = profile != null ? profile.size : null;
        int width = override != null && override.width != null ? override.width : config.getScoreboardWidth();
        int height = override != null && override.height != null ? override.height : config.getScoreboardHeight();
        boolean autoWidth = override != null && override.autoWidth != null && override.autoWidth;
        boolean autoHeight = override != null && override.autoHeight != null && override.autoHeight;
        int minWidth = override != null && override.minWidth != null ? Math.max(1, override.minWidth) : 0;
        int maxWidth = override != null && override.maxWidth != null ? Math.max(1, override.maxWidth) : 0;
        int minHeight = override != null && override.minHeight != null ? Math.max(1, override.minHeight) : 0;
        int maxHeight = override != null && override.maxHeight != null ? Math.max(1, override.maxHeight) : 0;
        return new ResolvedSize(width, height, autoWidth, autoHeight, minWidth, maxWidth, minHeight, maxHeight);
    }

    private int resolveAutoWidth(@Nonnull List<String> lines,
                                 int fontSize,
                                 int paddingLeft,
                                 int paddingRight,
                                 int logoWidth) {
        int maxTextChars = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String visible = stripFormattingForMeasure(line);
            maxTextChars = Math.max(maxTextChars, visible.length());
        }
        int charWidth = Math.max(5, Math.round(fontSize * 0.58f));
        int textWidth = maxTextChars * charWidth;
        int contentWidth = Math.max(textWidth, Math.max(0, logoWidth));
        return Math.max(80, paddingLeft + paddingRight + contentWidth);
    }

    private int clampToRange(int value, int minValue, int maxValue) {
        int out = value;
        if (minValue > 0) {
            out = Math.max(minValue, out);
        }
        if (maxValue > 0) {
            out = Math.min(maxValue, out);
        }
        return out;
    }

    @Nonnull
    private String stripFormattingForMeasure(@Nonnull String line) {
        String out = line;
        out = out.replaceAll("(?i)</?center>", "");
        out = INLINE_ANIMATION_PATTERN.matcher(out).replaceAll("");
        out = BRACKET_ANIMATION_PATTERN.matcher(out).replaceAll("");
        out = out.replaceAll("\\{#[0-9a-fA-F]{6}}", "");
        out = out.replaceAll("(?i)[&§]#[0-9a-fA-F]{6}", "");
        out = out.replaceAll("(?i)[&§][0-9a-fk-or]", "");
        out = out.replaceAll("<#[0-9a-fA-F]{6}>", "");
        out = out.replaceAll("</#[0-9a-fA-F]{6}>", "");
        out = out.replaceAll("(?i)</?(bold|italic|underlined|strikethrough|obfuscated)>", "");
        return out;
    }

    private boolean evaluateCondition(@Nonnull ConditionRule rule,
                                      @Nonnull Map<String, String> placeholders,
                                      @Nonnull PlayerRef playerRef) {
        String left = resolveConditionValue(rule.left, placeholders, playerRef);
        String right = resolveConditionValue(rule.right, placeholders, playerRef);
        return evaluateComparison(left, rule.operator, right);
    }

    @Nonnull
    private String resolveConditionValue(@Nullable String token,
                                         @Nonnull Map<String, String> placeholders,
                                         @Nonnull PlayerRef playerRef) {
        if (token == null) {
            return "";
        }
        String trimmed = token.trim();
        if (trimmed.isBlank()) {
            return "";
        }

        String stripped = trimmed;
        if (stripped.startsWith("{") && stripped.endsWith("}") && stripped.length() > 2) {
            stripped = stripped.substring(1, stripped.length() - 1);
        } else if (stripped.startsWith("%") && stripped.endsWith("%") && stripped.length() > 2) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }

        String normalizedKey = stripped.toLowerCase(Locale.ROOT);
        if (placeholders.containsKey(normalizedKey)) {
            return placeholders.get(normalizedKey);
        }

        String resolved = applyPlaceholders(trimmed, placeholders);
        resolved = PlaceholderApiUtil.applyString(playerRef, resolved);
        return resolved;
    }

    private boolean evaluateComparison(@Nonnull String left,
                                       @Nullable String operator,
                                       @Nonnull String right) {
        String op = operator == null ? "equals" : operator.trim().toLowerCase(Locale.ROOT);
        if (op.isBlank()) {
            op = "equals";
        }
        return switch (op) {
            case "equals", "eq", "==" -> left.equalsIgnoreCase(right);
            case "notequals", "not_equals", "neq", "!=" -> !left.equalsIgnoreCase(right);
            case "greaterthan", "greater_than", "gt", ">" -> compareValues(left, right) > 0;
            case "lessthan", "less_than", "lt", "<" -> compareValues(left, right) < 0;
            case "greaterthanorequals", "greater_than_or_equals", "gte", ">=" -> compareValues(left, right) >= 0;
            case "lessthanorequals", "less_than_or_equals", "lte", "<=" -> compareValues(left, right) <= 0;
            case "contains" -> left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT));
            case "startswith", "starts_with" -> left.toLowerCase(Locale.ROOT).startsWith(right.toLowerCase(Locale.ROOT));
            case "endswith", "ends_with" -> left.toLowerCase(Locale.ROOT).endsWith(right.toLowerCase(Locale.ROOT));
            default -> left.equalsIgnoreCase(right);
        };
    }

    private int compareValues(@Nonnull String left, @Nonnull String right) {
        Double leftNum = parseDoubleOrNull(left);
        Double rightNum = parseDoubleOrNull(right);
        if (leftNum != null && rightNum != null) {
            return Double.compare(leftNum, rightNum);
        }
        return left.compareToIgnoreCase(right);
    }

    @Nullable
    private Double parseDoubleOrNull(@Nonnull String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nonnull
    private AdvancedScoreboardConfig loadAdvancedScoreboardConfig() {
        try {
            if (!Files.exists(scoreboardConfigPath)) {
                return AdvancedScoreboardConfig.empty();
            }
            String content = Files.readString(scoreboardConfigPath, StandardCharsets.UTF_8);
            JsonObject rootObj = gson.fromJson(content, JsonObject.class);
            if (rootObj == null) {
                return AdvancedScoreboardConfig.empty();
            }
            JsonObject scoreboardObj = getObject(rootObj, "scoreboard");
            if (scoreboardObj == null) {
                return AdvancedScoreboardConfig.empty();
            }
            return parseAdvancedScoreboardConfig(scoreboardObj);
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to parse advanced scoreboard config: " + e.getMessage());
            return AdvancedScoreboardConfig.empty();
        }
    }

    @Nonnull
    private AdvancedScoreboardConfig parseAdvancedScoreboardConfig(@Nonnull JsonObject scoreboardObj) {
        JsonObject profilesObj = getObject(scoreboardObj, "profiles");
        if (profilesObj == null || profilesObj.entrySet().isEmpty()) {
            return AdvancedScoreboardConfig.empty();
        }

        Map<String, AdvancedProfile> profiles = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : profilesObj.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                continue;
            }
            String profileId = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (profileId.isBlank()) {
                continue;
            }
            AdvancedProfile profile = parseAdvancedProfile(profileId, entry.getValue().getAsJsonObject());
            if (profile != null) {
                profiles.put(profileId, profile);
            }
        }
        if (profiles.isEmpty()) {
            return AdvancedScoreboardConfig.empty();
        }

        String defaultProfile = readString(scoreboardObj, "defaultProfile", "default")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (defaultProfile.isBlank() || !profiles.containsKey(defaultProfile)) {
            defaultProfile = profiles.keySet().stream().findFirst().orElse("default");
        }

        Map<String, String> worldProfiles = new HashMap<>();
        JsonObject worldProfilesObj = getObject(scoreboardObj, "worldProfiles");
        if (worldProfilesObj != null) {
            for (Map.Entry<String, JsonElement> entry : worldProfilesObj.entrySet()) {
                if (entry.getValue() == null || !entry.getValue().isJsonPrimitive()) {
                    continue;
                }
                String worldName = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
                String profile = entry.getValue().getAsString().trim().toLowerCase(Locale.ROOT);
                if (worldName.isBlank() || profile.isBlank()) {
                    continue;
                }
                if (profiles.containsKey(profile)) {
                    worldProfiles.put(worldName, profile);
                }
            }
        }

        List<AdvancedProfileCondition> conditionalProfiles = new ArrayList<>();
        JsonElement conditionalElement = scoreboardObj.get("conditionalProfiles");
        if (conditionalElement != null && conditionalElement.isJsonArray()) {
            for (JsonElement element : conditionalElement.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                AdvancedProfileCondition condition = parseProfileCondition(element.getAsJsonObject(), profiles);
                if (condition != null) {
                    conditionalProfiles.add(condition);
                }
            }
        }

        return new AdvancedScoreboardConfig(defaultProfile, profiles, worldProfiles, conditionalProfiles);
    }

    @Nullable
    private AdvancedProfile parseAdvancedProfile(@Nonnull String profileId, @Nonnull JsonObject profileObj) {
        AdvancedLogo logo = parseAdvancedLogo(getObject(profileObj, "logo"));
        AdvancedSize size = parseAdvancedSize(getObject(profileObj, "size"));
        List<AdvancedPage> pages = new ArrayList<>();

        int profileDefaultDurationMs = resolvePageDurationMs(profileObj, DEFAULT_PROFILE_PAGE_DURATION_MS);
        JsonElement pagesElement = profileObj.get("pages");
        if (pagesElement != null && pagesElement.isJsonArray()) {
            for (JsonElement pageElement : pagesElement.getAsJsonArray()) {
                if (pageElement == null || !pageElement.isJsonObject()) {
                    continue;
                }
                AdvancedPage page = parseAdvancedPage(pageElement.getAsJsonObject(), profileDefaultDurationMs);
                if (page != null) {
                    pages.add(page);
                }
            }
        }

        if (pages.isEmpty()) {
            JsonElement linesElement = profileObj.get("lines");
            if (linesElement != null) {
                List<AdvancedLineRule> lines = parseAdvancedLineRules(linesElement);
                if (!lines.isEmpty()) {
                    String title = readString(profileObj, "title", "");
                    pages.add(new AdvancedPage(title, profileDefaultDurationMs, lines));
                }
            }
        }

        if (pages.isEmpty()) {
            return null;
        }
        return new AdvancedProfile(profileId, logo, size, pages);
    }

    @Nullable
    private AdvancedPage parseAdvancedPage(@Nonnull JsonObject pageObj, int fallbackDurationMs) {
        JsonElement linesElement = pageObj.get("lines");
        if (linesElement == null) {
            return null;
        }
        List<AdvancedLineRule> lines = parseAdvancedLineRules(linesElement);
        if (lines.isEmpty()) {
            return null;
        }
        String title = readString(pageObj, "title", "");
        int durationMs = resolvePageDurationMs(pageObj, fallbackDurationMs);
        return new AdvancedPage(title, durationMs, lines);
    }

    private int resolvePageDurationMs(@Nonnull JsonObject obj, int fallbackMs) {
        int durationMs = readInt(obj, "durationMs", -1);
        if (durationMs >= 0) {
            return Math.max(250, durationMs);
        }

        int seconds = readInt(obj, "durationSeconds", -1);
        if (seconds >= 0) {
            return Math.max(250, seconds * 1000);
        }

        int genericDuration = readInt(obj, "duration", -1);
        if (genericDuration >= 0) {
            // Compatibility: treat short values as seconds and larger values as milliseconds.
            if (genericDuration <= 300) {
                return Math.max(250, genericDuration * 1000);
            }
            return Math.max(250, genericDuration);
        }
        return Math.max(250, fallbackMs);
    }

    @Nonnull
    private List<AdvancedLineRule> parseAdvancedLineRules(@Nonnull JsonElement linesElement) {
        if (!linesElement.isJsonArray()) {
            return List.of();
        }

        List<AdvancedLineRule> rules = new ArrayList<>();
        for (JsonElement lineElement : linesElement.getAsJsonArray()) {
            if (lineElement == null) {
                continue;
            }
            if (lineElement.isJsonPrimitive()) {
                rules.add(AdvancedLineRule.literal(lineElement.getAsString()));
            } else if (lineElement.isJsonObject()) {
                AdvancedLineRule parsed = parseAdvancedLineRule(lineElement.getAsJsonObject());
                if (parsed != null) {
                    rules.add(parsed);
                }
            }
            if (rules.size() >= DEFAULT_MAX_RENDER_LINES) {
                break;
            }
        }
        return rules;
    }

    @Nullable
    private AdvancedLineRule parseAdvancedLineRule(@Nonnull JsonObject lineObj) {
        String literal = readString(lineObj, "text", "");
        String left = firstNonBlank(
                readString(lineObj, "condition", ""),
                readString(lineObj, "placeholder", ""),
                readString(lineObj, "left", "")
        );
        String operator = readString(lineObj, "operator", "equals");
        String right = firstNonBlank(
                readString(lineObj, "value", ""),
                readString(lineObj, "right", ""),
                readString(lineObj, "compare", "")
        );
        String thenText = firstNonBlank(
                readString(lineObj, "then", ""),
                readString(lineObj, "true", ""),
                literal
        );
        String elseText = firstNonBlank(
                readString(lineObj, "else", ""),
                readString(lineObj, "false", "")
        );

        if (left != null && !left.isBlank()) {
            ConditionRule condition = new ConditionRule(left, operator, right == null ? "" : right);
            return AdvancedLineRule.conditional(condition, thenText, elseText);
        }
        if (literal == null || literal.isBlank()) {
            return null;
        }
        return AdvancedLineRule.literal(literal);
    }

    @Nullable
    private AdvancedProfileCondition parseProfileCondition(@Nonnull JsonObject obj,
                                                           @Nonnull Map<String, AdvancedProfile> profiles) {
        String profile = readString(obj, "profile", "").trim().toLowerCase(Locale.ROOT);
        if (profile.isBlank() || !profiles.containsKey(profile)) {
            return null;
        }
        String permission = readString(obj, "permission", "").trim();
        String left = firstNonBlank(
                readString(obj, "condition", ""),
                readString(obj, "placeholder", ""),
                readString(obj, "left", "")
        );
        ConditionRule condition = null;
        if (left != null && !left.isBlank()) {
            String operator = readString(obj, "operator", "equals");
            String right = firstNonBlank(
                    readString(obj, "value", ""),
                    readString(obj, "right", ""),
                    readString(obj, "compare", "")
            );
            condition = new ConditionRule(left, operator, right == null ? "" : right);
        }
        if ((permission == null || permission.isBlank()) && condition == null) {
            return null;
        }
        return new AdvancedProfileCondition(profile, permission, condition);
    }

    @Nullable
    private AdvancedLogo parseAdvancedLogo(@Nullable JsonObject logoObj) {
        if (logoObj == null) {
            return null;
        }
        Boolean enabled = readBooleanOrNull(logoObj, "enabled");
        String texture = readString(logoObj, "texture", "");
        if (texture.isBlank()) {
            texture = null;
        }
        Integer width = readIntOrNull(logoObj, "width");
        Integer height = readIntOrNull(logoObj, "height");
        Integer paddingBottom = readIntOrNull(logoObj, "paddingBottom");
        return new AdvancedLogo(enabled, texture, width, height, paddingBottom);
    }

    @Nullable
    private AdvancedSize parseAdvancedSize(@Nullable JsonObject sizeObj) {
        if (sizeObj == null) {
            return null;
        }
        Integer width = readIntOrNull(sizeObj, "width");
        Integer height = readIntOrNull(sizeObj, "height");
        Boolean autoWidth = readBooleanOrNull(sizeObj, "autoWidth");
        Boolean autoHeight = readBooleanOrNull(sizeObj, "autoHeight");
        Integer minWidth = readIntOrNull(sizeObj, "minWidth");
        Integer maxWidth = readIntOrNull(sizeObj, "maxWidth");
        Integer minHeight = readIntOrNull(sizeObj, "minHeight");
        Integer maxHeight = readIntOrNull(sizeObj, "maxHeight");
        return new AdvancedSize(width, height, autoWidth, autoHeight, minWidth, maxWidth, minHeight, maxHeight);
    }

    @Nullable
    private JsonObject getObject(@Nonnull JsonObject obj, @Nonnull String key) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    @Nonnull
    private String readString(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull String def) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return def;
        }
        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return def;
        }
    }

    private int readInt(@Nonnull JsonObject obj, @Nonnull String key, int def) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return def;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return def;
        }
    }

    @Nullable
    private Integer readIntOrNull(@Nonnull JsonObject obj, @Nonnull String key) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private Boolean readBooleanOrNull(@Nonnull JsonObject obj, @Nonnull String key) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private String firstNonBlank(@Nullable String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isBlank()) {
                return candidate;
            }
        }
        return null;
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

        com.hypixel.hytale.math.vector.Transform transform = playerRef.getTransform();
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
            try {
                world.execute(task);
            } catch (IllegalThreadStateException ignored) {
                // World is shutting down/crashed and no longer accepts tasks.
            }
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
            String filePart = trimmed.substring(LOGO_FILE_PREFIX.length()).trim();
            if (filePart.isEmpty()) {
                Log.warn("[HyEssentialsX] Scoreboard logo file path is empty.");
                return "";
            }
            Path rawPath = Path.of(filePart);
            Path filePath = rawPath.isAbsolute() ? rawPath : dataDirectory.resolve(rawPath).normalize();
            String cacheKey = filePath.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            String cachedTexture = cachedLogoTextures.get(cacheKey);
            if (cachedTexture != null) {
                return cachedTexture;
            }
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
                String assetToken = sanitizeAssetToken(SCOREBOARD_LOGO_TOKEN);
                String assetName = SCOREBOARD_ASSET_PATH_PREFIX + assetToken + ".png";
                writeScoreboardAssetPack(assetName, logoBytes);
                ByteArrayCommonAsset asset = new ByteArrayCommonAsset(assetName, logoBytes);
                commonAssetModule.addCommonAsset(SCOREBOARD_ASSET_PACK, asset, false);
                if (Universe.get().getPlayerCount() > 0) {
                    commonAssetModule.sendAssets(List.<CommonAsset>of(asset), false);
                }
                String resolvedTexture = SCOREBOARD_TEXTURE_PATH_PREFIX + assetToken + ".png";
                cachedLogoTextures.put(cacheKey, resolvedTexture);
                Log.info("[HyEssentialsX] Loaded scoreboard logo from " + filePath + ".");
                return resolvedTexture;
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
                + "  \"ServerVersion\": \"2026.02.17-255364b8e\",\n"
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

    private static final class AnimationSpec {
        private final String type;
        private final String[] params;
        private final String textWithoutTag;

        private AnimationSpec(@Nullable String type,
                              @Nonnull String[] params,
                              @Nonnull String textWithoutTag) {
            this.type = type == null ? "" : type;
            this.params = params;
            this.textWithoutTag = textWithoutTag;
        }
    }

    private static final class CustomColorAnimation {
        private final int speed;
        private final List<String> frames;

        private CustomColorAnimation(int speed, @Nonnull List<String> frames) {
            this.speed = speed;
            this.frames = frames;
        }
    }

    private static final class AdvancedScoreboardConfig {
        private final String defaultProfile;
        private final Map<String, AdvancedProfile> profiles;
        private final Map<String, String> worldProfiles;
        private final List<AdvancedProfileCondition> conditionalProfiles;

        private AdvancedScoreboardConfig(@Nonnull String defaultProfile,
                                         @Nonnull Map<String, AdvancedProfile> profiles,
                                         @Nonnull Map<String, String> worldProfiles,
                                         @Nonnull List<AdvancedProfileCondition> conditionalProfiles) {
            this.defaultProfile = defaultProfile;
            this.profiles = profiles;
            this.worldProfiles = worldProfiles;
            this.conditionalProfiles = conditionalProfiles;
        }

        @Nonnull
        private static AdvancedScoreboardConfig empty() {
            return new AdvancedScoreboardConfig("", Map.of(), Map.of(), List.of());
        }
    }

    private static final class AdvancedProfile {
        private final String id;
        @Nullable
        private final AdvancedLogo logo;
        @Nullable
        private final AdvancedSize size;
        private final List<AdvancedPage> pages;

        private AdvancedProfile(@Nonnull String id,
                                @Nullable AdvancedLogo logo,
                                @Nullable AdvancedSize size,
                                @Nonnull List<AdvancedPage> pages) {
            this.id = id;
            this.logo = logo;
            this.size = size;
            this.pages = pages;
        }
    }

    private static final class AdvancedPage {
        private final String title;
        private final int durationMs;
        private final List<AdvancedLineRule> lines;

        private AdvancedPage(@Nonnull String title, int durationMs, @Nonnull List<AdvancedLineRule> lines) {
            this.title = title;
            this.durationMs = durationMs;
            this.lines = lines;
        }
    }

    private static final class AdvancedLineRule {
        @Nullable
        private final String literalText;
        @Nullable
        private final ConditionRule condition;
        @Nullable
        private final String thenText;
        @Nullable
        private final String elseText;

        private AdvancedLineRule(@Nullable String literalText,
                                 @Nullable ConditionRule condition,
                                 @Nullable String thenText,
                                 @Nullable String elseText) {
            this.literalText = literalText;
            this.condition = condition;
            this.thenText = thenText;
            this.elseText = elseText;
        }

        @Nonnull
        private static AdvancedLineRule literal(@Nonnull String text) {
            return new AdvancedLineRule(text, null, null, null);
        }

        @Nonnull
        private static AdvancedLineRule conditional(@Nonnull ConditionRule condition,
                                                    @Nullable String thenText,
                                                    @Nullable String elseText) {
            return new AdvancedLineRule(null, condition, thenText, elseText);
        }
    }

    private static final class ConditionRule {
        private final String left;
        private final String operator;
        private final String right;

        private ConditionRule(@Nonnull String left, @Nonnull String operator, @Nonnull String right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }
    }

    private static final class AdvancedProfileCondition {
        private final String profileId;
        @Nullable
        private final String permission;
        @Nullable
        private final ConditionRule condition;

        private AdvancedProfileCondition(@Nonnull String profileId,
                                         @Nullable String permission,
                                         @Nullable ConditionRule condition) {
            this.profileId = profileId;
            this.permission = permission;
            this.condition = condition;
        }
    }

    private static final class AdvancedLogo {
        @Nullable
        private final Boolean enabled;
        @Nullable
        private final String texture;
        @Nullable
        private final Integer width;
        @Nullable
        private final Integer height;
        @Nullable
        private final Integer paddingBottom;

        private AdvancedLogo(@Nullable Boolean enabled,
                             @Nullable String texture,
                             @Nullable Integer width,
                             @Nullable Integer height,
                             @Nullable Integer paddingBottom) {
            this.enabled = enabled;
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.paddingBottom = paddingBottom;
        }
    }

    private static final class AdvancedSize {
        @Nullable
        private final Integer width;
        @Nullable
        private final Integer height;
        @Nullable
        private final Boolean autoWidth;
        @Nullable
        private final Boolean autoHeight;
        @Nullable
        private final Integer minWidth;
        @Nullable
        private final Integer maxWidth;
        @Nullable
        private final Integer minHeight;
        @Nullable
        private final Integer maxHeight;

        private AdvancedSize(@Nullable Integer width,
                             @Nullable Integer height,
                             @Nullable Boolean autoWidth,
                             @Nullable Boolean autoHeight,
                             @Nullable Integer minWidth,
                             @Nullable Integer maxWidth,
                             @Nullable Integer minHeight,
                             @Nullable Integer maxHeight) {
            this.width = width;
            this.height = height;
            this.autoWidth = autoWidth;
            this.autoHeight = autoHeight;
            this.minWidth = minWidth;
            this.maxWidth = maxWidth;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }
    }

    private static final class PageRotationState {
        private final String profileId;
        private final int pageIndex;
        private final long nextSwitchAtMs;

        private PageRotationState(@Nonnull String profileId, int pageIndex, long nextSwitchAtMs) {
            this.profileId = profileId;
            this.pageIndex = pageIndex;
            this.nextSwitchAtMs = nextSwitchAtMs;
        }
    }

    private static final class ResolvedLogo {
        private final boolean enabled;
        private final String texture;
        private final int width;
        private final int height;
        private final int paddingBottom;

        private ResolvedLogo(boolean enabled,
                             @Nonnull String texture,
                             int width,
                             int height,
                             int paddingBottom) {
            this.enabled = enabled;
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.paddingBottom = paddingBottom;
        }
    }

    private static final class ResolvedSize {
        private final int width;
        private final int height;
        private final boolean autoWidth;
        private final boolean autoHeight;
        private final int minWidth;
        private final int maxWidth;
        private final int minHeight;
        private final int maxHeight;

        private ResolvedSize(int width,
                             int height,
                             boolean autoWidth,
                             boolean autoHeight,
                             int minWidth,
                             int maxWidth,
                             int minHeight,
                             int maxHeight) {
            this.width = width;
            this.height = height;
            this.autoWidth = autoWidth;
            this.autoHeight = autoHeight;
            this.minWidth = minWidth;
            this.maxWidth = maxWidth;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }
    }
}
