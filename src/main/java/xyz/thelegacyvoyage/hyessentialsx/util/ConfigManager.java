package xyz.thelegacyvoyage.hyessentialsx.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import xyz.thelegacyvoyage.hyessentialsx.models.AnnouncementPresetModel;
import xyz.thelegacyvoyage.hyessentialsx.models.CommandRuleModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlaytimeRewardModel;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnRouteGroupModel;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.models.RankupTier;
import xyz.thelegacyvoyage.hyessentialsx.util.PluginInfoUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

public final class ConfigManager {

    private static final int DEFAULT_COMMAND_COOLDOWN_SECONDS = 30;
    private static final int DEFAULT_TELEPORT_WARMUP_SECONDS = 5;
    private static final List<String> DEFAULT_SPAWN_RESPAWN_PRIORITY = List.of("bed", "setspawn", "world");
    private static final List<String> DEFAULT_SPAWN_COMMAND_ROUTE = List.of("permission", "main", "worlddefault");
    private static final List<String> DEFAULT_SPAWN_FIRST_JOIN_ROUTE = List.of("firstjoin", "group", "world", "permission", "main", "worlddefault");
    private static final List<String> DEFAULT_SPAWN_JOIN_ROUTE = List.of();
    private static final List<String> DEFAULT_SPAWN_RESPAWN_ROUTE = List.of("bed", "death", "setspawn", "worlddefault");
    private static final List<String> DEFAULT_SPAWN_DEATH_ROUTE = List.of("bed", "death", "group", "world", "setspawn", "worlddefault");
    private static final List<String> DEFAULT_SPAWN_PROTECTION_WORLDS = List.of("default");
    private static final List<String> DEFAULT_COMBAT_BLOCKED_COMMANDS = List.of("home", "spawn", "tpa", "tp", "warp");
    private static final List<String> DEFAULT_COMMAND_SPY_IGNORED_COMMANDS = List.of("login", "register", "password", "changepassword", "msg", "tell", "w", "reply", "mail", "commandspy", "cmdspy", "cspy");
    private static final List<String> DEFAULT_NICKNAME_BLACKLIST = List.of("admin", "owner", "moderator", "mod", "staff", "console", "server");
    private static final int DEFAULT_SCOREBOARD_MAX_LINES = 30;
    private static final int DEFAULT_SCOREBOARD_LINE_MAX_LENGTH = 256;
    private static final Pattern NAMED_SPAWN_PATTERN = Pattern.compile("^[a-z0-9_-]{1,32}$");

    private final Path configPath;
    private final Path economyPath;
    private final Path rewardsPath;
    private final Path rankupPath;
    private final Path chatPath;
    private final Path scoreboardPath;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private JsonObject root;

    private boolean debugMode = false;
    private boolean usePermissionsSystem = true;
    private boolean hideNoPermissionCommands = true;
    private boolean commandTreeRefreshEnabled = true;
    private boolean useWorldDefaultSpawnIfUnset = true;
    private String languageCode = "en-us";
    private boolean hologramsEnabled = true;
    private boolean hologramPlaceholdersEnabled = true;
    private int hologramPlaceholderUpdateIntervalMs = 1000;
    private int hologramMaxLines = 50;
    private int hologramMaxLineLength = 128;
    private int hologramMaxNameLength = 32;
    private double hologramDefaultLineSpacing = 0.25D;
    private boolean scoreboardEnabled = false;
    private int scoreboardUpdateIntervalMs = 2000;
    private String scoreboardAnchor = "top_right";
    private int scoreboardOffsetX = 20;
    private int scoreboardOffsetY = 20;
    private int scoreboardWidth = 240;
    private int scoreboardHeight = 0;
    private int scoreboardLineSpacing = 2;
    private int scoreboardLineHeight = 16;
    private int scoreboardFontSize = 14;
    private int scoreboardPaddingTop = 8;
    private int scoreboardPaddingBottom = 8;
    private int scoreboardPaddingLeft = 10;
    private int scoreboardPaddingRight = 10;
    private String scoreboardBackgroundColor = "#0b0f15(0.65)";
    private String scoreboardTextColor = "#f6f8ff";
    private int scoreboardMaxPlayers = 0;
    private String scoreboardBalanceFormat = "compact";
    private boolean scoreboardLogoEnabled = true;
    private String scoreboardLogoTexture = "file:scoreboard/HyEssentialsX.png";
    private int scoreboardLogoWidth = 175;
    private int scoreboardLogoHeight = 39;
    private int scoreboardLogoPaddingBottom = 6;
    private boolean scoreboardDefaultHidden = true;
    private Map<String, String> scoreboardPlaceholders = defaultScoreboardPlaceholders();
    private List<String> scoreboardLines = defaultScoreboardLines();
    private int tpaRequestTimeoutSeconds = 60;
    private int rtpMaxDistance = 5000;
    private int rtpMinDistance = 1000;
    private Map<String, String> rtpWorldOverrides = new LinkedHashMap<>();
    private final Map<String, Integer> commandCooldowns = new HashMap<>(buildDefaultCooldowns());
    private final Map<String, CommandRuleModel> commandRules = new LinkedHashMap<>();
    private int homeWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int warpWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int backWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int spawnWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int rtpWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int tpaWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;

    private boolean homesEnabled = true;
    private int homeMaxHomesPerPlayer = -1;
    private boolean warpsEnabled = true;
    private boolean warpsGuiEnabled = true;
    private boolean playerWarpsEnabled = true;
    private boolean playerWarpsGuiEnabled = true;
    private boolean playerWarpAutoApprove = true;
    private int playerWarpMaxWarpsPerPlayer = 3;
    private long playerWarpCreateCost = 0L;
    private long playerWarpVisitCost = 0L;
    private boolean kitsEnabled = true;
    private boolean kitsGuiEnabled = true;
    private boolean kitsRequirePermission = true;
    private boolean msgEnabled = true;
    private boolean nearEnabled = true;
    private boolean motdEnabled = true;
    private boolean rulesEnabled = true;
    private boolean rulesGuiEnabled = true;
    private boolean rtpEnabled = true;
    private boolean statsEnabled = true;
    private boolean statsTrackMovement = true;
    private boolean broadcastEnabled = true;
    private boolean spawnEnabled = true;
    private double flySpeedMin = 0.1;
    private double flySpeedMax = 10.0;
    private boolean timedFlightEnabled = false;
    private boolean timedFlightRequirePermission = true;
    private long flightCostPerMinute = 0L;
    private int flightDefaultMinutes = 30;
    private List<Integer> flightWarningSeconds = new ArrayList<>(List.of(60, 10));
    private boolean commandSpyEnabled = true;
    private List<String> commandSpyIgnoredCommands = new ArrayList<>(DEFAULT_COMMAND_SPY_IGNORED_COMMANDS);
    private boolean commandSpyLogToActivity = true;
    private boolean nicknamesEnabled = true;
    private int nicknameMinLength = 3;
    private int nicknameMaxLength = 16;
    private boolean nicknamePreventDuplicates = true;
    private List<String> nicknameBlacklist = new ArrayList<>(DEFAULT_NICKNAME_BLACKLIST);
    private int sleepPercentage = 100;
    private boolean sleepChatEnabled = true;
    private boolean tpaEnabled = true;
    private boolean tpaGuiEnabled = true;
    private boolean adminChatEnabled = true;
    private boolean afkEnabled = true;
    private boolean spawnProtectionEnabled = true;
    private int spawnProtectionRadius = 64;
    private boolean spawnProtectionAllowBreak = false;
    private boolean spawnProtectionAllowPlace = false;
    private boolean spawnProtectionAllowDamage = false;
    private boolean spawnProtectionAllowInteract = true;
    private List<String> spawnProtectionWorlds = new ArrayList<>(DEFAULT_SPAWN_PROTECTION_WORLDS);
    private boolean worldBorderEnabled = false;
    private int worldBorderRadius = 10000;
    private int worldBorderCenterX = 0;
    private int worldBorderCenterZ = 0;
    private int worldBorderTeleportPadding = 2;
    private boolean worldBorderExpansionEnabled = false;
    private int worldBorderExpansionAmount = 100;
    private long worldBorderExpansionIntervalSeconds = 86400L;
    private int worldBorderExpansionMaxRadius = 0;
    private long worldBorderExpansionLastRunAtMs = 0L;
    private boolean respawnInvulnerabilityEnabled = true;
    private int respawnInvulnerabilityBedSeconds = 5;
    private int respawnInvulnerabilityWorldSeconds = 5;
    private boolean respawnInvulnerabilityCancelOnAttack = true;
    private List<String> spawnRespawnPriority = DEFAULT_SPAWN_RESPAWN_PRIORITY;
    private String spawnRouteSelectionMode = "first";
    private List<String> spawnRouteCommandOrder = DEFAULT_SPAWN_COMMAND_ROUTE;
    private List<String> spawnRouteFirstJoinOrder = DEFAULT_SPAWN_FIRST_JOIN_ROUTE;
    private List<String> spawnRouteJoinOrder = DEFAULT_SPAWN_JOIN_ROUTE;
    private List<String> spawnRouteRespawnOrder = DEFAULT_SPAWN_RESPAWN_ROUTE;
    private List<String> spawnRouteDeathOrder = DEFAULT_SPAWN_DEATH_ROUTE;
    private boolean combatLogEnabled = false;
    private boolean combatLogOnlyPlayerDamage = true;
    private int combatLogTimeSeconds = 30;
    private boolean combatLogShowTitle = true;
    private boolean combatLogBlockCommands = true;
    private List<String> combatLogBlockedCommands = DEFAULT_COMBAT_BLOCKED_COMMANDS;
    private static final String COMBATLOG_PREFIX_KEY = "combatlog.prefix";
    private static final String COMBATLOG_ENTER_KEY = "combatlog.enter";
    private static final String COMBATLOG_EXIT_KEY = "combatlog.exit";
    private static final String COMBATLOG_TIME_REMAINING_KEY = "combatlog.time_remaining";
    private static final String COMBATLOG_BROADCAST_KEY = "combatlog.broadcast";
    private static final String COMBATLOG_COMMAND_BLOCKED_KEY = "combatlog.command_blocked";
    private static final String COMBATLOG_COMMAND_INFO_KEY = "combatlog.command_info";
    private static final String COMBATLOG_RELOAD_SUCCESS_KEY = "combatlog.command_reload_success";
    private static final String COMBATLOG_RELOAD_FAILED_KEY = "combatlog.command_reload_failed";
    private static final String COMBATLOG_TITLE_MAIN_KEY = "combatlog.title_main";
    private static final String COMBATLOG_TITLE_SUB_KEY = "combatlog.title_sub";
    private boolean economyEnabled = true;
    private String economyCurrencySymbol = "$";
    private int economyDecimalPlaces = 2;
    private long economyStartingBalance = 0L;
    private boolean economyHudEnabled = true;
    private boolean economyHudDefaultHidden = false;
    private String economyHudLabel = "HyCoins";
    private int economyHudUpdateIntervalMs = 1000;
    private String economyHudAnchor = "bottom_right";
    private int economyHudOffsetX = 12;
    private int economyHudOffsetY = 152;
    private int economyHudWidth = 180;
    private int economyHudHeight = 28;
    private String economyHudBackgroundColor = "#1a1e2dd8";
    private String economyHudLabelColor = "#888888";
    private String economyHudSymbolColor = "#e8b923";
    private String economyHudAmountColor = "#ffffff";
    private boolean paycheckEnabled = true;
    private long paycheckAmount = 100L;
    private double paycheckIntervalHours = 1.0;
    private boolean economyRewardsEnabled = true;
    private boolean economyBlockRewardsEnabled = true;
    private boolean economyMobRewardsEnabled = true;
    private boolean economyRewardsDebug = false;
    private boolean economyRewardsPopupEnabled = true;
    private String economyRewardsPopupStyle = "Success";
    private boolean economyBaltopGuiEnabled = true;
    private long economyMobDefaultReward = 0L;
    private Map<String, Long> economyBlockRewards = defaultBlockRewards();
    private Map<String, Long> economyBlockGroupRewards = defaultBlockGroupRewards();
    private Map<String, Long> economyMobRewards = defaultMobRewards();
    // Legacy compatibility flag. Rankup enablement now follows playtime rewards enablement.
    private boolean rankupEnabled = true;
    private int rankupConfirmTimeoutSeconds = 30;
    private boolean rankupPlaytimeEnabled = true;
    private boolean rankupCurrencyEnabled = true;
    private boolean rankupAutoEnabled = false;
    private int rankupAutoCheckSeconds = 60;
    private boolean rankupAutoUseCurrency = false;
    private List<RankupTier> rankupTiers = List.of();
    private boolean playtimeGuiEnabled = true;
    private int playtimeTopLimit = 100;
    private boolean playtimeRewardsEnabled = true;
    private boolean playtimeRewardsAutoClaim = true;
    private int playtimeRewardsCheckIntervalSeconds = 30;
    private List<PlaytimeRewardModel> playtimeRewards = defaultPlaytimeRewards();
    private String defaultKit = "";

    private boolean adminShopsEnabled = true;
    private int adminShopMaxTradeQuantity = ShopTradeQuantityUtil.DEFAULT_MAX_QUANTITY;
    private boolean playerShopsEnabled = true;
    private boolean playerShopDirectoryEnabled = true;
    private int playerShopMaxShopsPerPlayer = 1;
    private long playerShopCreationCost = 0L;
    private int playerShopChestLinkRadius = 8;
    private int playerShopMaxTradeQuantity = ShopTradeQuantityUtil.DEFAULT_MAX_QUANTITY;
    private boolean auctionHouseEnabled = true;
    private long auctionHouseMaxListingSeconds = 172800L;
    private long auctionHouseDefaultListingSeconds = 172800L;
    private int auctionHouseMaxListingsPerPlayer = 5;
    private long auctionHouseListingCost = 0L;
    private String auctionHouseNpcRole = "";

    private boolean mailEnabled = true;
    private int mailCooldownSeconds = 15;
    private double mailSimilarityThreshold = 0.9;
    private int mailSimilarityWindowSeconds = 120;
    private int mailMaxInboxSize = 100;
    private int mailMaxSentSize = 100;
    private int mailMaxAgeDays = 30;
    private int mailPageSize = 10;
    private boolean mailNotifyOnJoin = true;
    private boolean mailNotifyOnReceive = true;
    private int mailMaxMessageLength = 250;

    private double nearRadius = 50.0;
    private boolean nearShowDistance = true;

    private boolean autoBroadcastEnabled = true;
    private int autoBroadcastIntervalSeconds = 300;
    private boolean autoBroadcastRandom = false;
    private List<String> autoBroadcastMessages = List.of(
            "&c[Broadcast]&f Welcome to the server!",
            "&c[Broadcast]&f Use &e/rules&f to read the rules."
    );
    private boolean announcementsEnabled = true;
    private int announcementsIntervalSeconds = 300;
    private boolean announcementsRandom = false;
    private List<AnnouncementPresetModel> announcementPresets = defaultAnnouncementPresets();

    private boolean welcomeEnabled = true;
    private boolean welcomeBroadcastToAll = true;
    private List<String> welcomeMessages = List.of(
            "<#38BDF8>-----------------------------------------</#38BDF8>",
            "<#38BDF8><bold>Welcome to </bold></#38BDF8><#60A5FA><bold>HyEssentialsX</bold></#60A5FA>",
            "<#E2E8F0><bold>Hello, {player}!</bold></#E2E8F0>",
            "<#94A3B8>This is your first time on the server.</#94A3B8>",
            "<#E2E8F0>Use <#38BDF8>/motd</#38BDF8> and <#60A5FA>/rules</#60A5FA> to get started.</#E2E8F0>",
            "<#38BDF8>-----------------------------------------</#38BDF8>"
    );

    private boolean joinQuitEnabled = true;
    private List<String> joinMessages = List.of(
            "<#34D399><bold>+</bold></#34D399> <#E2E8F0>{player}</#E2E8F0> <#93C5FD>joined the server.</#93C5FD>"
    );
    private List<String> quitMessages = List.of(
            "<#F87171><bold>-</bold></#F87171> <#E2E8F0>{player}</#E2E8F0> <#94A3B8>left the server.</#94A3B8>"
    );
    private boolean deathMessagesEnabled = true;
    private List<String> deathMessages = List.of(
            "<#FCA5A5><bold>{player}</bold> died {cause}</#FCA5A5>"
    );
    private Map<String, String> chatGroupFormats = defaultChatGroups();
    private Map<String, String> chatGroupPrefixes = defaultChatGroupPrefixes();
    private Map<String, String> chatGroupSuffixes = defaultChatGroupSuffixes();
    private Map<String, Integer> chatGroupPriorities = defaultGroupPriorities();

    private int afkTimeoutSeconds = 300;
    private boolean afkAnnounceOnAuto = true;
    private boolean afkAnnounceOnManual = true;
    private boolean afkAnnounceOnReturn = true;
    private String afkMessage = "&e{player} is now AFK.";
    private String afkBackMessage = "&e{player} is no longer AFK.";

    private List<String> motdMessages = List.of(
            "<#38BDF8>=========================================</#38BDF8>",
            "<#38BDF8><bold>Message</bold></#38BDF8> <#60A5FA><bold>of the Day</bold></#60A5FA>",
            "<#E2E8F0>Welcome to our server, <#93C5FD>{player}</#93C5FD>.</#E2E8F0>",
            "<#94A3B8>Online now: <#38BDF8>{total_players_online}</#38BDF8> | All-time joined: <#60A5FA>{total_joined_players}</#60A5FA></#94A3B8>",
            "<#E2E8F0>Have fun and remember to read <#60A5FA>/rules</#60A5FA>.</#E2E8F0>",
            "<#A5B4FC>Discord: <url:{discord}>{discord}</url></#A5B4FC>",
            "<#38BDF8>=========================================</#38BDF8>"
    );
    private boolean motdShowOnJoin = true;
    private boolean discordEnabled = true;
    private String discordInviteUrl = "https://discord.gg/U58ax8cZZ2";
    private List<String> discordMessages = List.of(
            "<#F472B6>-----------------------------------------</#F472B6>",
            "<#C084FC><bold>Join our Discord</bold></#C084FC>",
            "<#F9A8D4>Click to join: <url:{discord}>{discord}</url></#F9A8D4>",
            "<#C084FC>-----------------------------------------</#C084FC>"
    );

    private List<String> rules = List.of(
            "&7Be respectful.",
            "&7No cheating.",
            "&7Have fun."
    );

    private boolean spawnSet = false;
    private String spawnWorld = "";
    private double spawnX = 0;
    private double spawnY = 0;
    private double spawnZ = 0;
    private float spawnYaw = 0f;
    private float spawnPitch = 0f;
    private final Map<String, SpawnModel> namedSpawns = new LinkedHashMap<>();
    private String firstJoinSpawnName = "";
    private String deathSpawnName = "";
    private final Map<String, List<String>> worldSpawnRoutes = new LinkedHashMap<>();
    private final Map<String, SpawnRouteGroupModel> groupSpawnRoutes = new LinkedHashMap<>();

    private String storageType = "sqlite";
    private String sqliteFile = "hyessentialsx.db";
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "hyessentialsx";
    private String mysqlUser = "root";
    private String mysqlPassword = "";
    private String mongoUri = "mongodb://localhost:27017";
    private String mongoDatabase = "hyessentialsx";
    private String mongoCollectionPrefix = "hex_";

    private boolean needsSplitMigration = false;

    public ConfigManager(@Nonnull Path dataFolder) {
        this.configPath = dataFolder.resolve("config.json");
        this.economyPath = dataFolder.resolve("economyConfig.json");
        this.rewardsPath = dataFolder.resolve("rewardsConfig.json");
        this.rankupPath = dataFolder.resolve("rankupConfig.json");
        this.chatPath = dataFolder.resolve("chatConfig.json");
        this.scoreboardPath = dataFolder.resolve("scoreboardConfig.json");
        ensureExists();
        load();
    }

    private void ensureExists() {
        try {
            Files.createDirectories(configPath.getParent());
        } catch (Exception e) {
            Log.error("Failed to create config folder: " + e.getMessage(), e);
            return;
        }

        boolean hasMain = Files.exists(configPath);
        boolean hasEconomy = Files.exists(economyPath);
        boolean hasRewards = Files.exists(rewardsPath);
        boolean hasChat = Files.exists(chatPath);
        boolean hasScoreboard = Files.exists(scoreboardPath);

        if (hasMain && hasEconomy && hasRewards && hasChat && hasScoreboard) return;

        if (hasMain && (!hasEconomy || !hasRewards || !hasChat || !hasScoreboard)) {
            needsSplitMigration = true;
            return;
        }

        try {
            if (!hasMain) {
                writeJson(configPath, buildDefaultMainRoot());
                Log.info("Created default config.json");
            }
            if (!hasEconomy) {
                writeJson(economyPath, buildDefaultEconomyRoot());
                Log.info("Created default economyConfig.json");
            }
            if (!hasRewards) {
                writeJson(rewardsPath, buildDefaultRewardsRoot());
                Log.info("Created default rewardsConfig.json");
            }
            if (!hasChat) {
                writeJson(chatPath, buildDefaultChatRoot());
                Log.info("Created default chatConfig.json");
            }
            if (!hasScoreboard) {
                writeJson(scoreboardPath, buildDefaultScoreboardRoot());
                Log.info("Created default scoreboardConfig.json");
            }
        } catch (Exception e) {
            Log.error("Failed to create default config files: " + e.getMessage(), e);
        }
    }

    private JsonObject buildDefaultConfig() {
        JsonObject root = new JsonObject();
        root.addProperty("version", "1.0.0");
        root.addProperty("debugMode", false);
        root.addProperty("UsePermissionsSystem", true);
        root.addProperty("HideNoPermissionCommands", true);
        root.addProperty("CommandTreeRefreshEnabled", true);

        JsonObject welcome = new JsonObject();
        welcome.addProperty("enabled", true);
        welcome.addProperty("broadcastToAll", true);
        welcome.add("messages", toArray(welcomeMessages));
        root.add("welcomeMessage", welcome);

        JsonObject joinQuit = new JsonObject();
        joinQuit.addProperty("enabled", true);
        joinQuit.add("joinMessages", toArray(joinMessages));
        joinQuit.add("quitMessages", toArray(quitMessages));
        root.add("joinAndQuit", joinQuit);

        JsonObject death = new JsonObject();
        death.addProperty("enabled", true);
        death.add("messages", toArray(deathMessages));
        root.add("deathMessages", death);

        JsonObject chat = new JsonObject();
        chat.add("groups", toChatGroupObject(chatGroupFormats));
        chat.add("groupPrefixes", toStringMapObject(chatGroupPrefixes));
        chat.add("groupSuffixes", toStringMapObject(chatGroupSuffixes));
        chat.add("groupPriorities", toPriorityObject(chatGroupPriorities));
        root.add("chat", chat);

        JsonObject spawnProtection = new JsonObject();
        spawnProtection.addProperty("enabled", true);
        spawnProtection.addProperty("radius", 64);
        spawnProtection.addProperty("allowBreak", false);
        spawnProtection.addProperty("allowPlace", false);
        spawnProtection.addProperty("allowDamage", false);
        spawnProtection.addProperty("allowInteract", true);
        root.add("spawnProtection", spawnProtection);

        JsonObject worldBorder = new JsonObject();
        worldBorder.addProperty("enabled", false);
        worldBorder.addProperty("radius", 10000);
        worldBorder.addProperty("centerX", 0);
        worldBorder.addProperty("centerZ", 0);
        worldBorder.addProperty("teleportPadding", 2);
        JsonObject worldBorderExpansion = new JsonObject();
        worldBorderExpansion.addProperty("enabled", false);
        worldBorderExpansion.addProperty("amount", 100);
        worldBorderExpansion.addProperty("intervalSeconds", 86400);
        worldBorderExpansion.addProperty("maxRadius", 0);
        worldBorderExpansion.addProperty("lastRunAtMs", 0);
        worldBorder.add("expansion", worldBorderExpansion);
        root.add("worldBorder", worldBorder);

        JsonObject respawnInvulnerability = new JsonObject();
        respawnInvulnerability.addProperty("enabled", true);
        respawnInvulnerability.addProperty("bedSeconds", 5);
        respawnInvulnerability.addProperty("worldSeconds", 5);
        respawnInvulnerability.addProperty("cancelOnAttack", true);
        root.add("respawnInvulnerability", respawnInvulnerability);

        JsonObject stats = new JsonObject();
        stats.addProperty("enabled", true);
        stats.addProperty("trackMovement", true);
        root.add("stats", stats);

        JsonObject scoreboard = new JsonObject();
        scoreboard.addProperty("enabled", scoreboardEnabled);
        scoreboard.addProperty("updateIntervalMs", scoreboardUpdateIntervalMs);
        scoreboard.addProperty("anchor", scoreboardAnchor);
        scoreboard.addProperty("offsetX", scoreboardOffsetX);
        scoreboard.addProperty("offsetY", scoreboardOffsetY);
        scoreboard.addProperty("width", scoreboardWidth);
        scoreboard.addProperty("height", scoreboardHeight);
        scoreboard.addProperty("lineSpacing", scoreboardLineSpacing);
        scoreboard.addProperty("lineHeight", scoreboardLineHeight);
        scoreboard.addProperty("fontSize", scoreboardFontSize);
        scoreboard.addProperty("backgroundColor", scoreboardBackgroundColor);
        scoreboard.addProperty("textColor", scoreboardTextColor);
        scoreboard.addProperty("maxPlayers", scoreboardMaxPlayers);
        scoreboard.addProperty("balanceFormat", scoreboardBalanceFormat);
        scoreboard.addProperty("defaultHideScoreboard", scoreboardDefaultHidden);
        JsonObject scoreboardPadding = new JsonObject();
        scoreboardPadding.addProperty("top", scoreboardPaddingTop);
        scoreboardPadding.addProperty("bottom", scoreboardPaddingBottom);
        scoreboardPadding.addProperty("left", scoreboardPaddingLeft);
        scoreboardPadding.addProperty("right", scoreboardPaddingRight);
        scoreboard.add("padding", scoreboardPadding);
        JsonObject scoreboardLogo = new JsonObject();
        scoreboardLogo.addProperty("enabled", scoreboardLogoEnabled);
        scoreboardLogo.addProperty("texture", scoreboardLogoTexture);
        scoreboardLogo.addProperty("width", scoreboardLogoWidth);
        scoreboardLogo.addProperty("height", scoreboardLogoHeight);
        scoreboardLogo.addProperty("paddingBottom", scoreboardLogoPaddingBottom);
        scoreboard.add("logo", scoreboardLogo);
        scoreboard.add("placeholders", toStringMapObject(scoreboardPlaceholders));
        scoreboard.add("lines", toArray(scoreboardLines));
        root.add("scoreboard", scoreboard);

        JsonObject economy = new JsonObject();
        economy.addProperty("enabled", true);
        economy.addProperty("currencySymbol", "$");
        economy.addProperty("decimalPlaces", 2);
        economy.addProperty("startingBalance", 0);
        economy.addProperty("baltopGui", true);
        JsonObject hud = new JsonObject();
        hud.addProperty("enabled", true);
        hud.addProperty("defaultHidden", false);
        hud.addProperty("label", "HyCoins");
        hud.addProperty("updateIntervalMs", 1000);
        hud.addProperty("anchor", "bottom_right");
        hud.addProperty("offsetX", 12);
        hud.addProperty("offsetY", 152);
        hud.addProperty("width", 180);
        hud.addProperty("height", 28);
        JsonObject hudColors = new JsonObject();
        hudColors.addProperty("background", "#1a1e2dd8");
        hudColors.addProperty("label", "#888888");
        hudColors.addProperty("symbol", "#e8b923");
        hudColors.addProperty("amount", "#ffffff");
        hud.add("colors", hudColors);
        economy.add("hud", hud);
        JsonObject paycheck = new JsonObject();
        paycheck.addProperty("enabled", true);
        paycheck.addProperty("amount", 100);
        paycheck.addProperty("intervalHours", 1.0);
        economy.add("paycheck", paycheck);
        JsonObject rewards = new JsonObject();
        rewards.addProperty("enabled", true);
        rewards.addProperty("debug", false);
        JsonObject popup = new JsonObject();
        popup.addProperty("enabled", true);
        popup.addProperty("style", "Success");
        rewards.add("popup", popup);
        JsonObject blockRewards = new JsonObject();
        blockRewards.addProperty("enabled", true);
        blockRewards.add("rewards", toMoneyMapObject(economyBlockRewards));
        blockRewards.add("groupRewards", toMoneyMapObject(economyBlockGroupRewards));
        rewards.add("blocks", blockRewards);
        JsonObject mobRewards = new JsonObject();
        mobRewards.addProperty("enabled", true);
        mobRewards.addProperty("defaultReward", 0);
        mobRewards.add("rewards", toMoneyMapObject(economyMobRewards));
        rewards.add("mobs", mobRewards);
        economy.add("rewards", rewards);
        root.add("economy", economy);

        JsonObject rankup = new JsonObject();
        rankup.addProperty("confirmTimeoutSeconds", 30);
        JsonObject requirements = new JsonObject();
        requirements.addProperty("playtimeEnabled", true);
        requirements.addProperty("currencyEnabled", true);
        rankup.add("requirements", requirements);
        JsonObject auto = new JsonObject();
        auto.addProperty("enabled", false);
        auto.addProperty("checkSeconds", 60);
        auto.addProperty("useCurrency", false);
        rankup.add("auto", auto);
        root.add("rankup", rankup);

        JsonObject playtime = new JsonObject();
        playtime.addProperty("guiEnabled", true);
        playtime.addProperty("topLimit", 100);
        JsonObject playtimeRewardSection = new JsonObject();
        playtimeRewardSection.addProperty("enabled", true);
        playtimeRewardSection.addProperty("autoClaim", true);
        playtimeRewardSection.addProperty("checkIntervalSeconds", 30);
        playtimeRewardSection.add("entries", toPlaytimeRewardsArray(defaultPlaytimeRewards()));
        playtime.add("rewards", playtimeRewardSection);
        root.add("playtime", playtime);

        JsonObject features = new JsonObject();
        features.addProperty("msg", true);
        features.addProperty("broadcast", true);
        features.addProperty("adminChat", true);
        root.add("features", features);

        JsonObject fly = new JsonObject();
        fly.addProperty("minSpeed", flySpeedMin);
        fly.addProperty("maxSpeed", flySpeedMax);
        fly.addProperty("timedEnabled", timedFlightEnabled);
        fly.addProperty("timedRequirePermission", timedFlightRequirePermission);
        fly.addProperty("costPerMinute", formatMoneyConfig(flightCostPerMinute));
        fly.addProperty("defaultMinutes", flightDefaultMinutes);
        fly.add("expiryWarningSeconds", toIntArray(flightWarningSeconds));
        root.add("fly", fly);

        JsonObject commandSpy = new JsonObject();
        commandSpy.addProperty("enabled", commandSpyEnabled);
        commandSpy.add("ignoredCommands", toArray(commandSpyIgnoredCommands));
        commandSpy.addProperty("logToActivity", commandSpyLogToActivity);
        root.add("commandSpy", commandSpy);

        JsonObject nicknames = new JsonObject();
        nicknames.addProperty("enabled", nicknamesEnabled);
        nicknames.addProperty("minLength", nicknameMinLength);
        nicknames.addProperty("maxLength", nicknameMaxLength);
        nicknames.addProperty("preventDuplicates", nicknamePreventDuplicates);
        nicknames.add("blacklist", toArray(nicknameBlacklist));
        root.add("nicknames", nicknames);

            JsonObject kits = new JsonObject();
            kits.addProperty("enabled", true);
            kits.addProperty("defaultKit", "");
            kits.addProperty("gui", true);
            kits.addProperty("requirePermission", true);
            root.add("kits", kits);

        JsonObject motd = new JsonObject();
        motd.addProperty("enabled", true);
        motd.addProperty("showOnJoin", true);
        motd.add("messages", toArray(motdMessages));
        root.add("motd", motd);

        JsonObject discord = new JsonObject();
        discord.addProperty("enabled", true);
        discord.addProperty("inviteUrl", discordInviteUrl);
        discord.add("messages", toArray(discordMessages));
        root.add("discord", discord);

        JsonObject rulesObj = new JsonObject();
        rulesObj.addProperty("enabled", true);
        rulesObj.addProperty("gui", true);
        rulesObj.add("messages", toArray(rules));
        root.add("rules", rulesObj);

        JsonObject sleep = new JsonObject();
        sleep.addProperty("percentage", sleepPercentage);
        sleep.addProperty("chatMessages", sleepChatEnabled);
        root.add("sleep", sleep);

        JsonObject afk = new JsonObject();
        afk.addProperty("enabled", true);
        afk.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        afk.addProperty("timeoutSeconds", afkTimeoutSeconds);
        afk.addProperty("announceOnAuto", afkAnnounceOnAuto);
        afk.addProperty("announceOnManual", afkAnnounceOnManual);
        afk.addProperty("announceOnReturn", afkAnnounceOnReturn);
        afk.addProperty("afkMessage", afkMessage);
        afk.addProperty("backMessage", afkBackMessage);
        root.add("afk", afk);

        JsonObject homes = new JsonObject();
        homes.addProperty("enabled", true);
        homes.addProperty("maxHomesPerPlayer", homeMaxHomesPerPlayer);
        homes.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        homes.addProperty("warmupSeconds", homeWarmupSeconds);
        root.add("homes", homes);

        JsonObject warps = new JsonObject();
        warps.addProperty("enabled", true);
        warps.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        warps.addProperty("warmupSeconds", warpWarmupSeconds);
        warps.addProperty("gui", true);
        root.add("warps", warps);

        JsonObject playerWarps = new JsonObject();
        playerWarps.addProperty("enabled", playerWarpsEnabled);
        playerWarps.addProperty("gui", playerWarpsGuiEnabled);
        playerWarps.addProperty("autoApprove", playerWarpAutoApprove);
        playerWarps.addProperty("maxWarpsPerPlayer", playerWarpMaxWarpsPerPlayer);
        playerWarps.addProperty("createCost", formatMoneyConfig(playerWarpCreateCost));
        playerWarps.addProperty("visitCost", formatMoneyConfig(playerWarpVisitCost));
        root.add("playerWarps", playerWarps);

        JsonObject back = new JsonObject();
        back.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        back.addProperty("warmupSeconds", backWarmupSeconds);
        root.add("back", back);

        JsonObject heal = new JsonObject();
        heal.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        root.add("heal", heal);

        JsonObject repair = new JsonObject();
        repair.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        root.add("repair", repair);

        JsonObject rtp = new JsonObject();
        rtp.addProperty("enabled", true);
        rtp.addProperty("radius", rtpMaxDistance);
        rtp.addProperty("minRadius", rtpMinDistance);
        rtp.addProperty("cooldownSeconds", 600);
        rtp.addProperty("warmupSeconds", rtpWarmupSeconds);
        rtp.add("worldOverrides", toStringMapObject(rtpWorldOverrides));
        root.add("rtp", rtp);

        JsonObject near = new JsonObject();
        near.addProperty("enabled", true);
        near.addProperty("radius", nearRadius);
        near.addProperty("showDistance", true);
        near.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        root.add("near", near);

        JsonObject tpa = new JsonObject();
        tpa.addProperty("enabled", true);
        tpa.addProperty("timeoutSeconds", tpaRequestTimeoutSeconds);
        tpa.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        tpa.addProperty("warmupSeconds", tpaWarmupSeconds);
        tpa.addProperty("gui", true);
        root.add("tpa", tpa);

        JsonObject autoBroadcast = new JsonObject();
        autoBroadcast.addProperty("enabled", true);
        autoBroadcast.addProperty("intervalSeconds", autoBroadcastIntervalSeconds);
        autoBroadcast.addProperty("random", autoBroadcastRandom);
        autoBroadcast.add("messages", toArray(autoBroadcastMessages));
        root.add("autoBroadcast", autoBroadcast);

        JsonObject announcements = new JsonObject();
        announcements.addProperty("enabled", announcementsEnabled);
        announcements.addProperty("intervalSeconds", announcementsIntervalSeconds);
        announcements.addProperty("random", announcementsRandom);
        announcements.add("presets", toAnnouncementArray(announcementPresets));
        root.add("announcements", announcements);

        JsonObject spawn = new JsonObject();
        spawn.addProperty("enabled", true);
        spawn.addProperty("set", false);
        spawn.addProperty("world", "");
        spawn.addProperty("x", 0.0);
        spawn.addProperty("y", 0.0);
        spawn.addProperty("z", 0.0);
        spawn.addProperty("yaw", 0.0);
        spawn.addProperty("pitch", 0.0);
        spawn.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        spawn.addProperty("warmupSeconds", spawnWarmupSeconds);
        spawn.add("respawnPriority", toArray(spawnRespawnPriority));
        spawn.add("named", toSpawnMapObject(namedSpawns));
        spawn.add("routing", toSpawnRoutingObject());
        root.add("spawn", spawn);

        root.add("commandRules", buildDefaultCommandRulesObject());

        JsonObject combatLog = new JsonObject();
        combatLog.addProperty("enabled", false);
        combatLog.addProperty("onlyPlayerDamageLog", true);
        combatLog.addProperty("combatTime", combatLogTimeSeconds);
        combatLog.addProperty("showCombatTitle", true);
        combatLog.addProperty("blockCommandsInCombat", true);
        combatLog.add("blockedCommands", toArray(combatLogBlockedCommands));
        root.add("combatLog", combatLog);

        JsonObject jumpTo = new JsonObject();
        jumpTo.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        root.add("jumpto", jumpTo);

        JsonObject storage = new JsonObject();
        storage.addProperty("type", "sqlite");
        storage.addProperty("sqliteFile", "hyessentialsx.db");
        storage.addProperty("mysqlHost", "localhost");
        storage.addProperty("mysqlPort", 3306);
        storage.addProperty("mysqlDatabase", "hyessentialsx");
        storage.addProperty("mysqlUser", "root");
        storage.addProperty("mysqlPassword", "");
        storage.addProperty("mongoUri", "mongodb://localhost:27017");
        storage.addProperty("mongoDatabase", "hyessentialsx");
        storage.addProperty("mongoCollectionPrefix", "hex_");
        root.add("storage", storage);

        JsonObject playerShops = new JsonObject();
        playerShops.addProperty("enabled", playerShopsEnabled);
        playerShops.addProperty("directoryEnabled", playerShopDirectoryEnabled);
        playerShops.addProperty("maxShopsPerPlayer", playerShopMaxShopsPerPlayer);
        playerShops.addProperty("shopCreationCost", playerShopCreationCost);
        playerShops.addProperty("chestLinkRadius", playerShopChestLinkRadius);
        playerShops.addProperty("maxTradeQuantity", playerShopMaxTradeQuantity);
        root.add("playerShops", playerShops);

        JsonObject adminShops = new JsonObject();
        adminShops.addProperty("enabled", adminShopsEnabled);
        adminShops.addProperty("maxTradeQuantity", adminShopMaxTradeQuantity);
        root.add("adminShops", adminShops);

        JsonObject auctionHouse = new JsonObject();
        auctionHouse.addProperty("enabled", auctionHouseEnabled);
        auctionHouse.addProperty("maxListingSeconds", auctionHouseMaxListingSeconds);
        auctionHouse.addProperty("defaultListingSeconds", auctionHouseDefaultListingSeconds);
        auctionHouse.addProperty("maxListingsPerPlayer", auctionHouseMaxListingsPerPlayer);
        auctionHouse.addProperty("listingCost", auctionHouseListingCost);
        auctionHouse.addProperty("npcRole", auctionHouseNpcRole);
        root.add("auctionHouse", auctionHouse);

        JsonObject mail = new JsonObject();
        mail.addProperty("enabled", mailEnabled);
        mail.addProperty("cooldownSeconds", mailCooldownSeconds);
        mail.addProperty("similarityThreshold", mailSimilarityThreshold);
        mail.addProperty("similarityWindowSeconds", mailSimilarityWindowSeconds);
        mail.addProperty("maxInboxSize", mailMaxInboxSize);
        mail.addProperty("maxSentSize", mailMaxSentSize);
        mail.addProperty("maxAgeDays", mailMaxAgeDays);
        mail.addProperty("pageSize", mailPageSize);
        mail.addProperty("notifyOnJoin", mailNotifyOnJoin);
        mail.addProperty("notifyOnReceive", mailNotifyOnReceive);
        mail.addProperty("maxMessageLength", mailMaxMessageLength);
        root.add("mail", mail);

        JsonObject general = new JsonObject();
        general.addProperty("spawnFallbackToWorldDefault", true);
        general.addProperty("language", languageCode);
        root.add("general", general);

        JsonObject holograms = new JsonObject();
        holograms.addProperty("enabled", hologramsEnabled);
        holograms.addProperty("maxLines", hologramMaxLines);
        holograms.addProperty("maxLineLength", hologramMaxLineLength);
        holograms.addProperty("maxNameLength", hologramMaxNameLength);
        holograms.addProperty("defaultLineSpacing", hologramDefaultLineSpacing);
        JsonObject hologramPlaceholders = new JsonObject();
        hologramPlaceholders.addProperty("enabled", hologramPlaceholdersEnabled);
        hologramPlaceholders.addProperty("updateIntervalMs", hologramPlaceholderUpdateIntervalMs);
        holograms.add("placeholders", hologramPlaceholders);
        root.add("holograms", holograms);

        return root;
    }

    public void load() {
        try {
            root = readCombinedRoot();
            if (root == null) {
                throw new IllegalStateException("config.json parsed to null");
            }
            boolean migrationBackupCreated = false;
            boolean migrationBackupRequired = shouldCreatePreMigrationBackup(root);
            JsonObject existingChat = getObjectOrNull(root, "chat");
            JsonObject preservedChat = existingChat != null ? existingChat.deepCopy() : null;
            JsonObject preservedBlockRewards = null;
            JsonObject preservedMobRewards = null;
            JsonObject economyObj = getObjectOrNull(root, "economy");
            if (economyObj != null) {
                JsonObject rewardsObj = getObjectOrNull(economyObj, "rewards");
                if (rewardsObj != null) {
                    JsonObject blocksObj = getObjectOrNull(rewardsObj, "blocks");
                    if (blocksObj != null && blocksObj.has("rewards") && blocksObj.get("rewards").isJsonObject()) {
                        preservedBlockRewards = blocksObj.getAsJsonObject("rewards").deepCopy();
                    }
                    JsonObject mobsObj = getObjectOrNull(rewardsObj, "mobs");
                    if (mobsObj != null && mobsObj.has("rewards") && mobsObj.get("rewards").isJsonObject()) {
                        preservedMobRewards = mobsObj.getAsJsonObject("rewards").deepCopy();
                    }
                }
            }
            boolean hadHomesEnabled = hasSectionFlag(root, "homes", "enabled");
            boolean hadWarpsEnabled = hasSectionFlag(root, "warps", "enabled");
            boolean hadKitsEnabled = hasSectionFlag(root, "kits", "enabled");
            boolean hadNearEnabled = hasSectionFlag(root, "near", "enabled");
            boolean hadMotdEnabled = hasSectionFlag(root, "motd", "enabled");
            boolean hadRtpEnabled = hasSectionFlag(root, "rtp", "enabled");
            boolean hadSpawnEnabled = hasSectionFlag(root, "spawn", "enabled");
            boolean hadTpaEnabled = hasSectionFlag(root, "tpa", "enabled");
            boolean hadAnnouncementsSection = root.has("announcements") && root.get("announcements").isJsonObject();
            JsonObject defaults = buildDefaultConfig();
            boolean changed = mergeDefaults(root, defaults);
            if (preservedChat != null) {
                root.add("chat", preservedChat);
                changed = true;
            }
            if (preservedBlockRewards != null) {
                JsonObject econ = obj(root, "economy");
                JsonObject rewards = obj(econ, "rewards");
                JsonObject blocks = obj(rewards, "blocks");
                blocks.add("rewards", preservedBlockRewards);
                changed = true;
            }
            if (preservedMobRewards != null) {
                JsonObject econ = obj(root, "economy");
                JsonObject rewards = obj(econ, "rewards");
                JsonObject mobs = obj(rewards, "mobs");
                mobs.add("rewards", preservedMobRewards);
                changed = true;
            }
            if (cleanupConfig(root)) {
                changed = true;
            }
            if (!root.has("autoBroadcast") || !root.get("autoBroadcast").isJsonObject()) {
                root.add("autoBroadcast", defaults.get("autoBroadcast"));
                changed = true;
            }
            if (!hadAnnouncementsSection) {
                JsonObject legacyAutoBroadcast = obj(root, "autoBroadcast");
                List<String> legacyMessages = list(legacyAutoBroadcast, "messages", autoBroadcastMessages);
                JsonObject announcements = new JsonObject();
                announcements.addProperty("enabled", bool(legacyAutoBroadcast, "enabled", autoBroadcastEnabled));
                announcements.addProperty("intervalSeconds", intVal(legacyAutoBroadcast, "intervalSeconds", autoBroadcastIntervalSeconds));
                announcements.addProperty("random", bool(legacyAutoBroadcast, "random", autoBroadcastRandom));
                List<AnnouncementPresetModel> migratedPresets = presetsFromAutoBroadcast(legacyMessages);
                announcements.add("presets", toAnnouncementArray(migratedPresets.isEmpty()
                        ? defaultAnnouncementPresets()
                        : migratedPresets));
                root.add("announcements", announcements);
                changed = true;
            }
            if (changed) {
                if (migrationBackupRequired && !migrationBackupCreated) {
                    backupConfigFilesBeforeMigration();
                    migrationBackupCreated = true;
                }
                try {
                    writeSplitConfigs();
                } catch (Exception e) {
                    Log.warn("Failed to persist updated config defaults: " + e.getMessage());
                }
            }

            debugMode = bool(root, "debugMode", debugMode);
            usePermissionsSystem = bool(root, "UsePermissionsSystem",
                    bool(root, "usePermissionsSystem", usePermissionsSystem));
            hideNoPermissionCommands = bool(root, "HideNoPermissionCommands",
                    bool(root, "hideNoPermissionCommands", hideNoPermissionCommands));
            commandTreeRefreshEnabled = bool(root, "CommandTreeRefreshEnabled",
                    bool(root, "commandTreeRefreshEnabled", commandTreeRefreshEnabled));
            JsonObject general = obj(root, "general");
            useWorldDefaultSpawnIfUnset = bool(general, "spawnFallbackToWorldDefault", true);
            languageCode = str(general, "language", languageCode).toLowerCase();

            JsonObject holograms = obj(root, "holograms");
            hologramsEnabled = bool(holograms, "enabled", hologramsEnabled);
            hologramMaxLines = intVal(holograms, "maxLines", hologramMaxLines);
            hologramMaxLineLength = intVal(holograms, "maxLineLength", hologramMaxLineLength);
            hologramMaxNameLength = intVal(holograms, "maxNameLength", hologramMaxNameLength);
            hologramDefaultLineSpacing = dbl(holograms, "defaultLineSpacing", hologramDefaultLineSpacing);
            JsonObject hologramPlaceholders = obj(holograms, "placeholders");
            hologramPlaceholdersEnabled = bool(hologramPlaceholders, "enabled", hologramPlaceholdersEnabled);
            hologramPlaceholderUpdateIntervalMs = intVal(hologramPlaceholders, "updateIntervalMs", hologramPlaceholderUpdateIntervalMs);

            JsonObject scoreboard = obj(root, "scoreboard");
            scoreboardEnabled = bool(scoreboard, "enabled", scoreboardEnabled);
            scoreboardUpdateIntervalMs = intVal(scoreboard, "updateIntervalMs", scoreboardUpdateIntervalMs);
            scoreboardAnchor = normalizeScoreboardAnchor(str(scoreboard, "anchor", scoreboardAnchor));
            scoreboardOffsetX = Math.max(0, intVal(scoreboard, "offsetX", scoreboardOffsetX));
            scoreboardOffsetY = Math.max(0, intVal(scoreboard, "offsetY", scoreboardOffsetY));
            scoreboardWidth = intVal(scoreboard, "width", scoreboardWidth);
            scoreboardHeight = intVal(scoreboard, "height", scoreboardHeight);
            scoreboardLineSpacing = intVal(scoreboard, "lineSpacing", scoreboardLineSpacing);
            scoreboardLineHeight = intVal(scoreboard, "lineHeight", scoreboardLineHeight);
            scoreboardFontSize = intVal(scoreboard, "fontSize", scoreboardFontSize);
            scoreboardBackgroundColor = str(scoreboard, "backgroundColor", scoreboardBackgroundColor);
            scoreboardTextColor = str(scoreboard, "textColor", scoreboardTextColor);
            scoreboardMaxPlayers = intVal(scoreboard, "maxPlayers", scoreboardMaxPlayers);
            scoreboardBalanceFormat = str(scoreboard, "balanceFormat", scoreboardBalanceFormat);
            scoreboardDefaultHidden = bool(scoreboard, "defaultHideScoreboard",
                    bool(scoreboard, "defaultHidden", scoreboardDefaultHidden));
            JsonObject scoreboardPadding = obj(scoreboard, "padding");
            scoreboardPaddingTop = intVal(scoreboardPadding, "top", scoreboardPaddingTop);
            scoreboardPaddingBottom = intVal(scoreboardPadding, "bottom", scoreboardPaddingBottom);
            scoreboardPaddingLeft = intVal(scoreboardPadding, "left", scoreboardPaddingLeft);
            scoreboardPaddingRight = intVal(scoreboardPadding, "right", scoreboardPaddingRight);
            JsonObject scoreboardLogo = obj(scoreboard, "logo");
            scoreboardLogoEnabled = bool(scoreboardLogo, "enabled", scoreboardLogoEnabled);
            scoreboardLogoTexture = str(scoreboardLogo, "texture", scoreboardLogoTexture);
            scoreboardLogoWidth = intVal(scoreboardLogo, "width", scoreboardLogoWidth);
            scoreboardLogoHeight = intVal(scoreboardLogo, "height", scoreboardLogoHeight);
            scoreboardLogoPaddingBottom = intVal(scoreboardLogo, "paddingBottom", scoreboardLogoPaddingBottom);
            scoreboardPlaceholders = readStringMap(scoreboard, "placeholders", scoreboardPlaceholders);
            scoreboardLines = sanitizeScoreboardLines(list(scoreboard, "lines", scoreboardLines));

            JsonObject tpa = obj(root, "tpa");
            tpaRequestTimeoutSeconds = intVal(tpa, "timeoutSeconds", 60);
            tpaWarmupSeconds = intVal(tpa, "warmupSeconds", tpaWarmupSeconds);
            tpaGuiEnabled = bool(tpa, "gui", tpaGuiEnabled);

            JsonObject rtp = obj(root, "rtp");
            rtpMaxDistance = intVal(rtp, "radius", 5000);
            rtpMinDistance = intVal(rtp, "minRadius", rtpMinDistance);
            rtpWarmupSeconds = intVal(rtp, "warmupSeconds", rtpWarmupSeconds);
            rtpWorldOverrides = readStringMap(rtp, "worldOverrides", rtpWorldOverrides);

            JsonObject legacyCooldowns = root.has("cooldowns") && root.get("cooldowns").isJsonObject()
                    ? root.getAsJsonObject("cooldowns")
                    : null;
            Map<String, Integer> cooldownDefaults = buildDefaultCooldowns();
            commandCooldowns.clear();
            commandCooldowns.put(CooldownKeys.BACK, readCooldown(obj(root, "back"), "cooldownSeconds", legacyCooldowns, CooldownKeys.BACK, cooldownDefaults.get(CooldownKeys.BACK)));
            commandCooldowns.put(CooldownKeys.HEAL, readCooldown(obj(root, "heal"), "cooldownSeconds", legacyCooldowns, CooldownKeys.HEAL, cooldownDefaults.get(CooldownKeys.HEAL)));
            commandCooldowns.put(CooldownKeys.REPAIR, readCooldown(obj(root, "repair"), "cooldownSeconds", legacyCooldowns, CooldownKeys.REPAIR, cooldownDefaults.get(CooldownKeys.REPAIR)));
            commandCooldowns.put(CooldownKeys.AFK, readCooldown(obj(root, "afk"), "cooldownSeconds", legacyCooldowns, CooldownKeys.AFK, cooldownDefaults.get(CooldownKeys.AFK)));
            commandCooldowns.put(CooldownKeys.NEAR, readCooldown(obj(root, "near"), "cooldownSeconds", legacyCooldowns, CooldownKeys.NEAR, cooldownDefaults.get(CooldownKeys.NEAR)));
            int tpaCooldown = readCooldown(obj(root, "tpa"), "cooldownSeconds", legacyCooldowns, CooldownKeys.TPA, cooldownDefaults.get(CooldownKeys.TPA));
            commandCooldowns.put(CooldownKeys.TPA, tpaCooldown);
            commandCooldowns.put(CooldownKeys.TPAHERE, tpaCooldown);
            commandCooldowns.put(CooldownKeys.TPAHEREALL, tpaCooldown);
            commandCooldowns.put(CooldownKeys.WARP, readCooldown(obj(root, "warps"), "cooldownSeconds", legacyCooldowns, CooldownKeys.WARP, cooldownDefaults.get(CooldownKeys.WARP)));
            commandCooldowns.put(CooldownKeys.SPAWN, readCooldown(obj(root, "spawn"), "cooldownSeconds", legacyCooldowns, CooldownKeys.SPAWN, cooldownDefaults.get(CooldownKeys.SPAWN)));
            commandCooldowns.put(CooldownKeys.RTP, readCooldown(obj(root, "rtp"), "cooldownSeconds", legacyCooldowns, CooldownKeys.RTP, cooldownDefaults.get(CooldownKeys.RTP)));
            commandCooldowns.put(CooldownKeys.JUMPTO, readCooldown(obj(root, "jumpto"), "cooldownSeconds", legacyCooldowns, CooldownKeys.JUMPTO, cooldownDefaults.get(CooldownKeys.JUMPTO)));
            commandCooldowns.put(CooldownKeys.HOME, readCooldown(obj(root, "homes"), "cooldownSeconds", legacyCooldowns, CooldownKeys.HOME, cooldownDefaults.get(CooldownKeys.HOME)));

            JsonObject homesSection = obj(root, "homes");
            homeWarmupSeconds = intVal(homesSection, "warmupSeconds", homeWarmupSeconds);
            homeMaxHomesPerPlayer = Math.max(-1, intVal(homesSection, "maxHomesPerPlayer", homeMaxHomesPerPlayer));
            JsonObject warpsSection = obj(root, "warps");
            warpWarmupSeconds = intVal(warpsSection, "warmupSeconds", warpWarmupSeconds);
            warpsGuiEnabled = bool(warpsSection, "gui", true);
            JsonObject playerWarpsSection = obj(root, "playerWarps");
            playerWarpsEnabled = bool(playerWarpsSection, "enabled", playerWarpsEnabled);
            playerWarpsGuiEnabled = bool(playerWarpsSection, "gui", playerWarpsGuiEnabled);
            playerWarpAutoApprove = bool(playerWarpsSection, "autoApprove", playerWarpAutoApprove);
            playerWarpMaxWarpsPerPlayer = Math.max(0, intVal(playerWarpsSection, "maxWarpsPerPlayer", playerWarpMaxWarpsPerPlayer));
            playerWarpCreateCost = Math.max(0L, moneyVal(playerWarpsSection, "createCost", playerWarpCreateCost));
            playerWarpVisitCost = Math.max(0L, moneyVal(playerWarpsSection, "visitCost", playerWarpVisitCost));
            JsonObject backSection = obj(root, "back");
            backWarmupSeconds = intVal(backSection, "warmupSeconds", backWarmupSeconds);

            JsonObject near = obj(root, "near");
            nearRadius = dbl(near, "radius", nearRadius);
            nearShowDistance = bool(near, "showDistance", nearShowDistance);
            JsonObject fly = obj(root, "fly");
            flySpeedMin = Math.max(0.05, dbl(fly, "minSpeed", flySpeedMin));
            flySpeedMax = Math.max(flySpeedMin, dbl(fly, "maxSpeed", flySpeedMax));
            timedFlightEnabled = bool(fly, "timedEnabled", timedFlightEnabled);
            timedFlightRequirePermission = bool(fly, "timedRequirePermission", timedFlightRequirePermission);
            flightCostPerMinute = Math.max(0L, moneyVal(fly, "costPerMinute", flightCostPerMinute));
            flightDefaultMinutes = Math.max(1, intVal(fly, "defaultMinutes", flightDefaultMinutes));
            flightWarningSeconds = sanitizePositiveInts(readIntList(fly, "expiryWarningSeconds", flightWarningSeconds));

            JsonObject commandSpy = obj(root, "commandSpy");
            commandSpyEnabled = bool(commandSpy, "enabled", commandSpyEnabled);
            commandSpyIgnoredCommands = sanitizeStringList(list(commandSpy, "ignoredCommands", commandSpyIgnoredCommands));
            commandSpyLogToActivity = bool(commandSpy, "logToActivity", commandSpyLogToActivity);

            JsonObject nicknames = obj(root, "nicknames");
            nicknamesEnabled = bool(nicknames, "enabled", nicknamesEnabled);
            nicknameMinLength = Math.max(1, intVal(nicknames, "minLength", nicknameMinLength));
            nicknameMaxLength = Math.max(nicknameMinLength, intVal(nicknames, "maxLength", nicknameMaxLength));
            nicknamePreventDuplicates = bool(nicknames, "preventDuplicates", nicknamePreventDuplicates);
            nicknameBlacklist = sanitizeStringList(list(nicknames, "blacklist", nicknameBlacklist));

            JsonObject autoBroadcast = obj(root, "autoBroadcast");
            autoBroadcastEnabled = bool(autoBroadcast, "enabled", autoBroadcastEnabled);
            autoBroadcastIntervalSeconds = intVal(autoBroadcast, "intervalSeconds", autoBroadcastIntervalSeconds);
            autoBroadcastRandom = bool(autoBroadcast, "random", autoBroadcastRandom);
            autoBroadcastMessages = list(autoBroadcast, "messages", autoBroadcastMessages);

            JsonObject announcements = obj(root, "announcements");
            announcementsEnabled = bool(announcements, "enabled", autoBroadcastEnabled);
            announcementsIntervalSeconds = intVal(announcements, "intervalSeconds", autoBroadcastIntervalSeconds);
            announcementsRandom = bool(announcements, "random", autoBroadcastRandom);
            List<AnnouncementPresetModel> fallbackPresets = presetsFromAutoBroadcast(autoBroadcastMessages);
            announcementPresets = readAnnouncementPresets(announcements, hadAnnouncementsSection
                    ? defaultAnnouncementPresets()
                    : fallbackPresets);
            if (!hadAnnouncementsSection) {
                announcementPresets = fallbackPresets.isEmpty() ? defaultAnnouncementPresets() : fallbackPresets;
                announcements.addProperty("enabled", announcementsEnabled);
                announcements.addProperty("intervalSeconds", announcementsIntervalSeconds);
                announcements.addProperty("random", announcementsRandom);
                announcements.add("presets", toAnnouncementArray(announcementPresets));
                changed = true;
            }

            JsonObject features = obj(root, "features");
            msgEnabled = bool(features, "msg", true);
            broadcastEnabled = bool(features, "broadcast", true);
            adminChatEnabled = bool(features, "adminChat", true);
            homesEnabled = bool(homesSection, "enabled", homesEnabled);
            if (!hadHomesEnabled && features.has("homes")) {
                homesEnabled = bool(features, "homes", homesEnabled);
            }
            warpsEnabled = bool(warpsSection, "enabled", warpsEnabled);
            if (!hadWarpsEnabled && features.has("warps")) {
                warpsEnabled = bool(features, "warps", warpsEnabled);
            }
            JsonObject kitsSectionEnabled = obj(root, "kits");
            kitsEnabled = bool(kitsSectionEnabled, "enabled", kitsEnabled);
            if (!hadKitsEnabled && features.has("kits")) {
                kitsEnabled = bool(features, "kits", kitsEnabled);
            }
            nearEnabled = bool(near, "enabled", nearEnabled);
            if (!hadNearEnabled && features.has("near")) {
                nearEnabled = bool(features, "near", nearEnabled);
            }
            JsonObject motdSection = obj(root, "motd");
            motdEnabled = bool(motdSection, "enabled", motdEnabled);
            if (!hadMotdEnabled && features.has("motd")) {
                motdEnabled = bool(features, "motd", motdEnabled);
            }
            JsonObject rtpSection = obj(root, "rtp");
            rtpEnabled = bool(rtpSection, "enabled", rtpEnabled);
            if (!hadRtpEnabled && features.has("rtp")) {
                rtpEnabled = bool(features, "rtp", rtpEnabled);
            }
            JsonObject spawnSection = obj(root, "spawn");
            spawnEnabled = bool(spawnSection, "enabled", spawnEnabled);
            if (!hadSpawnEnabled && features.has("spawn")) {
                spawnEnabled = bool(features, "spawn", spawnEnabled);
            }
            tpaEnabled = bool(tpa, "enabled", tpaEnabled);
            if (!hadTpaEnabled && features.has("tpa")) {
                tpaEnabled = bool(features, "tpa", tpaEnabled);
            }
            afkEnabled = bool(obj(root, "afk"), "enabled", afkEnabled);

            JsonObject welcome = obj(root, "welcomeMessage");
            welcomeEnabled = bool(welcome, "enabled", true);
            welcomeBroadcastToAll = bool(welcome, "broadcastToAll", true);
            welcomeMessages = list(welcome, "messages", welcomeMessages);

            JsonObject joinQuit = obj(root, "joinAndQuit");
            joinQuitEnabled = bool(joinQuit, "enabled", true);
            joinMessages = readStringList(joinQuit, "joinMessages", "joinMessage", joinMessages);
            quitMessages = readStringList(joinQuit, "quitMessages", "quitMessage", quitMessages);

            JsonObject death = obj(root, "deathMessages");
            deathMessagesEnabled = bool(death, "enabled", deathMessagesEnabled);
            deathMessages = list(death, "messages", deathMessages);

            JsonObject chat = obj(root, "chat");
            chatGroupFormats = readChatGroupFormats(chat, chatGroupFormats);
            chatGroupFormats = migrateStockChatGroupFormats(chatGroupFormats);
            chatGroupPrefixes = readStringMap(chat, "groupPrefixes", chatGroupPrefixes);
            chatGroupSuffixes = readStringMap(chat, "groupSuffixes", chatGroupSuffixes);
            String legacyFormat = str(chat, "format", "");
            if (!legacyFormat.isBlank() && !hasGroupFormat(chatGroupFormats, "Default")) {
                chatGroupFormats.put("Default", legacyFormat);
            }
            if (chatGroupFormats.isEmpty()) {
                chatGroupFormats = defaultChatGroups();
            }
            JsonObject chatPriorities = getObjectOrNull(chat, "groupPriorities");
            if (chatPriorities == null) {
                JsonObject legacyPriorities = obj(root, "groupPriorities");
                chatGroupPriorities = readGroupPriorities(legacyPriorities, chatGroupPriorities);
            } else {
                chatGroupPriorities = readGroupPriorities(chatPriorities, chatGroupPriorities);
            }

            JsonObject spawnProtection = obj(root, "spawnProtection");
            spawnProtectionEnabled = bool(spawnProtection, "enabled", spawnProtectionEnabled);
            spawnProtectionRadius = intVal(spawnProtection, "radius", spawnProtectionRadius);
            spawnProtectionAllowBreak = bool(spawnProtection, "allowBreak", spawnProtectionAllowBreak);
            spawnProtectionAllowPlace = bool(spawnProtection, "allowPlace", spawnProtectionAllowPlace);
            spawnProtectionAllowDamage = bool(spawnProtection, "allowDamage", spawnProtectionAllowDamage);
            spawnProtectionAllowInteract = bool(spawnProtection, "allowInteract", spawnProtectionAllowInteract);
            if (spawnProtection.has("worlds") && spawnProtection.get("worlds").isJsonArray()) {
                spawnProtectionWorlds = readSpawnProtectionWorlds(spawnProtection);
            } else {
                spawnProtectionWorlds = new ArrayList<>(List.of(resolveServerDefaultWorldName()));
                spawnProtection.add("worlds", toArray(spawnProtectionWorlds));
                changed = true;
            }

            JsonObject worldBorder = obj(root, "worldBorder");
            worldBorderEnabled = bool(worldBorder, "enabled", worldBorderEnabled);
            worldBorderRadius = Math.max(1, intVal(worldBorder, "radius", worldBorderRadius));
            worldBorderCenterX = intVal(worldBorder, "centerX", worldBorderCenterX);
            worldBorderCenterZ = intVal(worldBorder, "centerZ", worldBorderCenterZ);
            worldBorderTeleportPadding = Math.max(0, intVal(worldBorder, "teleportPadding", worldBorderTeleportPadding));
            JsonObject worldBorderExpansion = obj(worldBorder, "expansion");
            worldBorderExpansionEnabled = bool(worldBorderExpansion, "enabled", worldBorderExpansionEnabled);
            worldBorderExpansionAmount = Math.max(1, intVal(worldBorderExpansion, "amount", worldBorderExpansionAmount));
            worldBorderExpansionIntervalSeconds = Math.max(1L, longVal(worldBorderExpansion, "intervalSeconds", worldBorderExpansionIntervalSeconds));
            worldBorderExpansionMaxRadius = Math.max(0, intVal(worldBorderExpansion, "maxRadius", worldBorderExpansionMaxRadius));
            worldBorderExpansionLastRunAtMs = Math.max(0L, longVal(worldBorderExpansion, "lastRunAtMs", worldBorderExpansionLastRunAtMs));

            JsonObject respawnInvulnerability = obj(root, "respawnInvulnerability");
            respawnInvulnerabilityEnabled = bool(respawnInvulnerability, "enabled", respawnInvulnerabilityEnabled);
            respawnInvulnerabilityBedSeconds = Math.max(0, intVal(respawnInvulnerability, "bedSeconds", respawnInvulnerabilityBedSeconds));
            respawnInvulnerabilityWorldSeconds = Math.max(0, intVal(respawnInvulnerability, "worldSeconds", respawnInvulnerabilityWorldSeconds));
            respawnInvulnerabilityCancelOnAttack = bool(respawnInvulnerability, "cancelOnAttack", respawnInvulnerabilityCancelOnAttack);

            JsonObject stats = obj(root, "stats");
            statsEnabled = bool(stats, "enabled", statsEnabled);
            statsTrackMovement = bool(stats, "trackMovement", statsTrackMovement);

            JsonObject economy = obj(root, "economy");
            economyEnabled = bool(economy, "enabled", economyEnabled);
            economyCurrencySymbol = str(economy, "currencySymbol", economyCurrencySymbol);
            economyDecimalPlaces = Math.max(0, Math.min(4, intVal(economy, "decimalPlaces", economyDecimalPlaces)));
            economyStartingBalance = Math.max(0L, moneyVal(economy, "startingBalance", economyStartingBalance));
            economyBaltopGuiEnabled = bool(economy, "baltopGui", economyBaltopGuiEnabled);
            JsonObject hud = obj(economy, "hud");
            economyHudEnabled = bool(hud, "enabled", economyHudEnabled);
            economyHudDefaultHidden = bool(hud, "defaultHidden", economyHudDefaultHidden);
            economyHudLabel = str(hud, "label", economyHudLabel);
            if (economyHudLabel.isBlank()) {
                economyHudLabel = "HyCoins";
            }
            economyHudUpdateIntervalMs = Math.max(250, intVal(hud, "updateIntervalMs", economyHudUpdateIntervalMs));
            economyHudAnchor = normalizeEconomyHudAnchor(str(hud, "anchor", economyHudAnchor));
            economyHudOffsetX = Math.max(0, intVal(hud, "offsetX", economyHudOffsetX));
            economyHudOffsetY = Math.max(0, intVal(hud, "offsetY", economyHudOffsetY));
            economyHudWidth = Math.max(120, intVal(hud, "width", economyHudWidth));
            economyHudHeight = Math.max(20, intVal(hud, "height", economyHudHeight));
            JsonObject hudColors = obj(hud, "colors");
            economyHudBackgroundColor = str(hudColors, "background", economyHudBackgroundColor);
            economyHudLabelColor = str(hudColors, "label", economyHudLabelColor);
            economyHudSymbolColor = str(hudColors, "symbol", economyHudSymbolColor);
            economyHudAmountColor = str(hudColors, "amount", economyHudAmountColor);
            JsonObject paycheck = obj(economy, "paycheck");
            paycheckEnabled = bool(paycheck, "enabled", paycheckEnabled);
            paycheckAmount = Math.max(0L, moneyVal(paycheck, "amount", paycheckAmount));
            paycheckIntervalHours = dbl(paycheck, "intervalHours", paycheckIntervalHours);
            JsonObject rewards = obj(economy, "rewards");
            economyRewardsEnabled = bool(rewards, "enabled", economyRewardsEnabled);
            economyRewardsDebug = bool(rewards, "debug", economyRewardsDebug);
            JsonObject popup = obj(rewards, "popup");
            economyRewardsPopupEnabled = bool(popup, "enabled", economyRewardsPopupEnabled);
            economyRewardsPopupStyle = str(popup, "style", economyRewardsPopupStyle);
            JsonObject blockRewards = obj(rewards, "blocks");
            economyBlockRewardsEnabled = bool(blockRewards, "enabled", economyBlockRewardsEnabled);
            economyBlockRewards = normalizeKeyedMap(readMoneyMap(blockRewards, "rewards", economyBlockRewards));
            economyBlockGroupRewards = normalizeKeyedMap(readMoneyMap(blockRewards, "groupRewards", economyBlockGroupRewards));
            JsonObject mobRewards = obj(rewards, "mobs");
            economyMobRewardsEnabled = bool(mobRewards, "enabled", economyMobRewardsEnabled);
            economyMobDefaultReward = Math.max(0L, moneyVal(mobRewards, "defaultReward", economyMobDefaultReward));
            economyMobRewards = normalizeKeyedMap(readMoneyMap(mobRewards, "rewards", economyMobRewards));

            JsonObject rankup = obj(root, "rankup");
            rankupEnabled = bool(rankup, "enabled", rankupEnabled);
            rankupConfirmTimeoutSeconds = intVal(rankup, "confirmTimeoutSeconds", rankupConfirmTimeoutSeconds);
            JsonObject requirements = obj(rankup, "requirements");
            rankupPlaytimeEnabled = bool(requirements, "playtimeEnabled", rankupPlaytimeEnabled);
            rankupCurrencyEnabled = bool(requirements, "currencyEnabled", rankupCurrencyEnabled);
            JsonObject auto = obj(rankup, "auto");
            rankupAutoEnabled = bool(auto, "enabled", rankupAutoEnabled);
            rankupAutoCheckSeconds = intVal(auto, "checkSeconds", rankupAutoCheckSeconds);
            rankupAutoUseCurrency = bool(auto, "useCurrency", rankupAutoUseCurrency);
            rankupTiers = readRankupTiers(rankup);
            boolean hasLegacyRankupEntries = rankup.has("ranks")
                    && rankup.get("ranks").isJsonArray()
                    && rankup.getAsJsonArray("ranks").size() > 0;
            if (rankup.has("ranks")) {
                rankup.remove("ranks");
                changed = true;
            }

            JsonObject playtime = obj(root, "playtime");
            playtimeGuiEnabled = bool(playtime, "guiEnabled", playtimeGuiEnabled);
            playtimeTopLimit = Math.max(10, intVal(playtime, "topLimit", playtimeTopLimit));
            JsonObject playtimeRewardSection = obj(playtime, "rewards");
            playtimeRewardsEnabled = bool(playtimeRewardSection, "enabled", playtimeRewardsEnabled);
            playtimeRewardsAutoClaim = bool(playtimeRewardSection, "autoClaim", playtimeRewardsAutoClaim);
            playtimeRewardsCheckIntervalSeconds = Math.max(
                    5,
                    intVal(playtimeRewardSection, "checkIntervalSeconds", playtimeRewardsCheckIntervalSeconds)
            );
            playtimeRewards = readPlaytimeRewards(playtimeRewardSection, playtimeRewards);
            List<PlaytimeRewardModel> legacyRankupRewards = hasLegacyRankupEntries
                    ? toPlaytimeRewards(rankupTiers)
                    : List.of();
            if (!legacyRankupRewards.isEmpty()) {
                int beforeCount = playtimeRewards.size();
                playtimeRewards = mergePlaytimeRewards(playtimeRewards, legacyRankupRewards);
                rankupTiers = toRankupTiers(playtimeRewards);
                if (playtimeRewards.size() != beforeCount) {
                    changed = true;
                }
            }
            // Rankup enablement is unified under playtime rewards enablement.
            if (rankupEnabled != playtimeRewardsEnabled) {
                rankupEnabled = playtimeRewardsEnabled;
                changed = true;
            }
            List<RankupTier> unifiedRankupTiers = toRankupTiers(playtimeRewards);
            if (!unifiedRankupTiers.isEmpty()) {
                rankupTiers = unifiedRankupTiers;
            }

            JsonObject kits = obj(root, "kits");
            defaultKit = str(kits, "defaultKit", defaultKit).trim();
            kitsRequirePermission = bool(kits, "requirePermission", kitsRequirePermission);

            JsonObject motd = obj(root, "motd");
            motdShowOnJoin = bool(motd, "showOnJoin", motdShowOnJoin);
            motdMessages = list(motd, "messages", motdMessages);
            JsonObject discord = obj(root, "discord");
            discordEnabled = bool(discord, "enabled", discordEnabled);
            discordInviteUrl = str(discord, "inviteUrl", discordInviteUrl);
            discordMessages = list(discord, "messages", discordMessages);

            JsonObject rulesObj = obj(root, "rules");
            rulesEnabled = bool(rulesObj, "enabled", rulesEnabled);
            rulesGuiEnabled = bool(rulesObj, "gui", rulesGuiEnabled);
            rules = list(rulesObj, "messages", rules);

            JsonObject sleep = obj(root, "sleep");
            sleepPercentage = intVal(sleep, "percentage", sleepPercentage);
            if (sleepPercentage < 0) sleepPercentage = 0;
            if (sleepPercentage > 100) sleepPercentage = 100;
            sleepChatEnabled = bool(sleep, "chatMessages", sleepChatEnabled);
            JsonObject kitsSection = obj(root, "kits");
            kitsGuiEnabled = bool(kitsSection, "gui", kitsGuiEnabled);
            kitsRequirePermission = bool(kitsSection, "requirePermission", kitsRequirePermission);

            JsonObject afk = obj(root, "afk");
            afkEnabled = bool(afk, "enabled", afkEnabled);
            afkTimeoutSeconds = intVal(afk, "timeoutSeconds", afkTimeoutSeconds);
            afkAnnounceOnAuto = bool(afk, "announceOnAuto", afkAnnounceOnAuto);
            afkAnnounceOnManual = bool(afk, "announceOnManual", afkAnnounceOnManual);
            afkAnnounceOnReturn = bool(afk, "announceOnReturn", afkAnnounceOnReturn);
            afkMessage = str(afk, "afkMessage", afkMessage);
            afkBackMessage = str(afk, "backMessage", afkBackMessage);

            JsonObject spawn = obj(root, "spawn");
            spawnSet = bool(spawn, "set", false);
            spawnWorld = str(spawn, "world", "");
            spawnX = dbl(spawn, "x", 0.0);
            spawnY = dbl(spawn, "y", 0.0);
            spawnZ = dbl(spawn, "z", 0.0);
            spawnYaw = (float) dbl(spawn, "yaw", 0.0);
            spawnPitch = (float) dbl(spawn, "pitch", 0.0);
            spawnWarmupSeconds = intVal(spawn, "warmupSeconds", spawnWarmupSeconds);
            spawnRespawnPriority = normalizeRespawnPriority(list(spawn, "respawnPriority", spawnRespawnPriority));
            if (readSpawnRouting(spawn)) {
                changed = true;
            }
            namedSpawns.clear();
            JsonObject namedSpawnObject = getObjectOrNull(spawn, "named");
            if (namedSpawnObject == null) {
                JsonObject legacyNamedSpawns = getObjectOrNull(spawn, "namedSpawns");
                if (legacyNamedSpawns != null) {
                    namedSpawnObject = legacyNamedSpawns;
                    spawn.add("named", legacyNamedSpawns.deepCopy());
                    spawn.remove("namedSpawns");
                    changed = true;
                }
            }
            if (namedSpawnObject != null) {
                for (Map.Entry<String, JsonElement> entry : namedSpawnObject.entrySet()) {
                    String key = normalizeSpawnName(entry.getKey());
                    SpawnModel model = readSpawnModel(entry.getValue());
                    if (key == null || model == null) {
                        changed = true;
                        continue;
                    }
                    if (!entry.getKey().equals(key)) {
                        changed = true;
                    }
                    if (namedSpawns.putIfAbsent(key, model) != null) {
                        changed = true;
                    }
                }
            }

            loadCommandRules();

            JsonObject combatLog = obj(root, "combatLog");
            combatLogEnabled = bool(combatLog, "enabled", combatLogEnabled);
            combatLogOnlyPlayerDamage = bool(combatLog, "onlyPlayerDamageLog", combatLogOnlyPlayerDamage);
            combatLogTimeSeconds = intVal(combatLog, "combatTime", combatLogTimeSeconds);
            combatLogShowTitle = bool(combatLog, "showCombatTitle", combatLogShowTitle);
            combatLogBlockCommands = bool(combatLog, "blockCommandsInCombat", combatLogBlockCommands);
            combatLogBlockedCommands = normalizeCommandList(list(combatLog, "blockedCommands", combatLogBlockedCommands));

            JsonObject storage = obj(root, "storage");
            storageType = str(storage, "type", "sqlite");
            sqliteFile = str(storage, "sqliteFile", sqliteFile);
            mysqlHost = str(storage, "mysqlHost", "localhost");
            mysqlPort = intVal(storage, "mysqlPort", 3306);
            mysqlDatabase = str(storage, "mysqlDatabase", "hyessentialsx");
            mysqlUser = str(storage, "mysqlUser", "root");
            mysqlPassword = str(storage, "mysqlPassword", "");
            mongoUri = str(storage, "mongoUri", mongoUri);
            mongoDatabase = str(storage, "mongoDatabase", mongoDatabase);
            mongoCollectionPrefix = str(storage, "mongoCollectionPrefix", mongoCollectionPrefix);

            if (storage.has("mongodbUri") && !storage.has("mongoUri")) {
                mongoUri = str(storage, "mongodbUri", mongoUri);
            }
            if (storage.has("mongodbDatabase") && !storage.has("mongoDatabase")) {
                mongoDatabase = str(storage, "mongodbDatabase", mongoDatabase);
            }
            if (storage.has("mongoDb") && !storage.has("mongoDatabase")) {
                mongoDatabase = str(storage, "mongoDb", mongoDatabase);
            }

            if (storage.has("mariadbHost") && !storage.has("mysqlHost")) {
                mysqlHost = str(storage, "mariadbHost", mysqlHost);
            }
            if (storage.has("mariadbPort") && !storage.has("mysqlPort")) {
                mysqlPort = intVal(storage, "mariadbPort", mysqlPort);
            }
            if (storage.has("mariadbDatabase") && !storage.has("mysqlDatabase")) {
                mysqlDatabase = str(storage, "mariadbDatabase", mysqlDatabase);
            }
            if (storage.has("mariadbUser") && !storage.has("mysqlUser")) {
                mysqlUser = str(storage, "mariadbUser", mysqlUser);
            }
            if (storage.has("mariadbPassword") && !storage.has("mysqlPassword")) {
                mysqlPassword = str(storage, "mariadbPassword", mysqlPassword);
            }

            JsonObject playerShops = obj(root, "playerShops");
            playerShopsEnabled = bool(playerShops, "enabled", playerShopsEnabled);
            playerShopDirectoryEnabled = bool(playerShops, "directoryEnabled", playerShopDirectoryEnabled);
            playerShopMaxShopsPerPlayer = Math.max(0, intVal(playerShops, "maxShopsPerPlayer", playerShopMaxShopsPerPlayer));
            playerShopCreationCost = Math.max(0L, moneyVal(playerShops, "shopCreationCost", playerShopCreationCost));
            playerShopChestLinkRadius = Math.max(1, intVal(playerShops, "chestLinkRadius", playerShopChestLinkRadius));
            playerShopMaxTradeQuantity = Math.max(1, intVal(playerShops, "maxTradeQuantity", playerShopMaxTradeQuantity));

            JsonObject adminShops = obj(root, "adminShops");
            adminShopsEnabled = bool(adminShops, "enabled", adminShopsEnabled);
            adminShopMaxTradeQuantity = Math.max(1, intVal(adminShops, "maxTradeQuantity", adminShopMaxTradeQuantity));

            JsonObject auctionHouse = obj(root, "auctionHouse");
            auctionHouseEnabled = bool(auctionHouse, "enabled", auctionHouseEnabled);
            auctionHouseMaxListingSeconds = Math.max(60L, longVal(auctionHouse, "maxListingSeconds", auctionHouseMaxListingSeconds));
            auctionHouseDefaultListingSeconds = Math.max(60L, Math.min(
                    longVal(auctionHouse, "defaultListingSeconds", auctionHouseDefaultListingSeconds),
                    auctionHouseMaxListingSeconds
            ));
            auctionHouseMaxListingsPerPlayer = Math.max(0, intVal(auctionHouse, "maxListingsPerPlayer", auctionHouseMaxListingsPerPlayer));
            auctionHouseListingCost = Math.max(0L, moneyVal(auctionHouse, "listingCost", auctionHouseListingCost));
            auctionHouseNpcRole = str(auctionHouse, "npcRole", auctionHouseNpcRole);

            JsonObject mail = obj(root, "mail");
            mailEnabled = bool(mail, "enabled", mailEnabled);
            mailCooldownSeconds = Math.max(0, intVal(mail, "cooldownSeconds", mailCooldownSeconds));
            mailSimilarityThreshold = Math.max(0.0, dbl(mail, "similarityThreshold", mailSimilarityThreshold));
            mailSimilarityWindowSeconds = Math.max(0, intVal(mail, "similarityWindowSeconds", mailSimilarityWindowSeconds));
            mailMaxInboxSize = Math.max(0, intVal(mail, "maxInboxSize", mailMaxInboxSize));
            mailMaxSentSize = Math.max(0, intVal(mail, "maxSentSize", mailMaxSentSize));
            mailMaxAgeDays = Math.max(0, intVal(mail, "maxAgeDays", mailMaxAgeDays));
            mailPageSize = Math.max(1, intVal(mail, "pageSize", mailPageSize));
            mailNotifyOnJoin = bool(mail, "notifyOnJoin", mailNotifyOnJoin);
            mailNotifyOnReceive = bool(mail, "notifyOnReceive", mailNotifyOnReceive);
            mailMaxMessageLength = Math.max(0, intVal(mail, "maxMessageLength", mailMaxMessageLength));

            if (changed) {
                if (migrationBackupRequired && !migrationBackupCreated) {
                    backupConfigFilesBeforeMigration();
                    migrationBackupCreated = true;
                }
                applyFieldsToRoot();
                save();
                Log.info("Updated config files with new defaults.");
            }
            setVersionFromPlugin();
            if (needsSplitMigration) {
                if (!migrationBackupCreated) {
                    backupConfigFilesBeforeMigration();
                    migrationBackupCreated = true;
                }
                save();
                needsSplitMigration = false;
                Log.info("Migrated config.json into split config files.");
            }
        } catch (Exception e) {
            backupBadConfig();
            Log.warn("Failed to load config.json, using defaults: " + e.getMessage());
            root = buildDefaultConfig();
            save();
        }
    }

    public void reload() {
        load();
    }

    public boolean isUseWorldDefaultSpawnIfUnset() {
        return useWorldDefaultSpawnIfUnset;
    }

    @Nonnull
    public String getLanguage() {
        return languageCode;
    }

    public int getTpaRequestTimeoutSeconds() {
        return tpaRequestTimeoutSeconds;
    }

    public int getTpaWarmupSeconds() {
        return Math.max(0, tpaWarmupSeconds);
    }

    public boolean isTpaGuiEnabled() {
        return tpaGuiEnabled;
    }

    public int getRtpMaxDistance() {
        return rtpMaxDistance;
    }

    public int getRtpMinDistance() {
        return rtpMinDistance;
    }

    public void setRtpEnabled(boolean enabled) {
        rtpEnabled = enabled;
        save();
    }

    public void setRtpDistanceRange(int minDistance, int maxDistance) {
        rtpMinDistance = Math.max(0, minDistance);
        rtpMaxDistance = Math.max(rtpMinDistance, maxDistance);
        save();
    }

    public void setRtpWarmupSeconds(int seconds) {
        rtpWarmupSeconds = Math.max(0, seconds);
        save();
    }

    @Nullable
    public String getRtpWorldOverride(@Nonnull String worldName) {
        if (rtpWorldOverrides.isEmpty()) return null;
        return rtpWorldOverrides.get(worldName.toLowerCase(Locale.ROOT));
    }

    public int getCooldownSeconds(@Nonnull String key) {
        return getCommandRule(key).getCooldownSeconds();
    }

    public void setCooldownSeconds(@Nonnull String key, int seconds) {
        String normalized = normalizeCommandRuleKey(key);
        commandCooldowns.put(normalized, Math.max(0, seconds));
        CommandRuleModel rule = commandRules.computeIfAbsent(normalized, ignored -> defaultCommandRule(normalized));
        rule.setCooldownSeconds(seconds);
        syncLegacyCommandRuleFields(normalized, rule);
        save();
    }

    @Nonnull
    public CommandRuleModel getCommandRule(@Nonnull String key) {
        String normalized = normalizeCommandRuleKey(key);
        CommandRuleModel rule = commandRules.get(normalized);
        if (rule != null) {
            return rule;
        }
        CommandRuleModel fallback = defaultCommandRule(normalized);
        commandRules.put(normalized, fallback);
        return fallback;
    }

    public int getCommandWarmupSeconds(@Nonnull String key, int legacyWarmupSeconds) {
        CommandRuleModel rule = getCommandRule(key);
        return Math.max(0, rule.getWarmupSeconds());
    }

    public long getCommandPrice(@Nonnull String key) {
        return getCommandRule(key).getPrice();
    }

    @Nonnull
    public List<String> getCommandRuleKeys() {
        List<String> keys = new ArrayList<>(commandRules.keySet());
        Collections.sort(keys);
        return keys;
    }

    public void setCommandRule(@Nonnull String key, @Nonnull CommandRuleModel rule) {
        String normalized = normalizeCommandRuleKey(key);
        if (normalized.isBlank()) {
            return;
        }
        commandRules.put(normalized, copyCommandRule(rule));
        syncLegacyCommandRuleFields(normalized, rule);
        save();
    }

    public int getHomeWarmupSeconds() {
        return Math.max(0, homeWarmupSeconds);
    }

    public int getHomeMaxHomesPerPlayer() {
        return Math.max(-1, homeMaxHomesPerPlayer);
    }

    public int getWarpWarmupSeconds() {
        return Math.max(0, warpWarmupSeconds);
    }

    public int getBackWarmupSeconds() {
        return Math.max(0, backWarmupSeconds);
    }

    public int getSpawnWarmupSeconds() {
        return Math.max(0, spawnWarmupSeconds);
    }

    @Nonnull
    public List<String> getSpawnRespawnPriority() {
        return Collections.unmodifiableList(spawnRespawnPriority);
    }

    public boolean isCombatLogEnabled() {
        return combatLogEnabled;
    }

    public boolean isCombatLogOnlyPlayerDamage() {
        return combatLogOnlyPlayerDamage;
    }

    public int getCombatLogTimeSeconds() {
        return Math.max(0, combatLogTimeSeconds);
    }

    public boolean isCombatLogShowTitle() {
        return combatLogShowTitle;
    }

    public boolean isCombatLogBlockCommands() {
        return combatLogBlockCommands;
    }

    public void setCombatLogBlockCommands(boolean blockCommands) {
        combatLogBlockCommands = blockCommands;
        save();
    }

    @Nonnull
    public List<String> getCombatLogBlockedCommands() {
        return Collections.unmodifiableList(combatLogBlockedCommands);
    }

    @Nonnull
    public String getCombatLogPrefix() {
        return COMBATLOG_PREFIX_KEY;
    }

    @Nonnull
    public String getCombatLogEnterMessage() {
        return COMBATLOG_ENTER_KEY;
    }

    @Nonnull
    public String getCombatLogExitMessage() {
        return COMBATLOG_EXIT_KEY;
    }

    @Nonnull
    public String getCombatLogTimeRemainingMessage() {
        return COMBATLOG_TIME_REMAINING_KEY;
    }

    @Nonnull
    public String getCombatLogBroadcastMessage() {
        return COMBATLOG_BROADCAST_KEY;
    }

    @Nonnull
    public String getCombatLogCommandBlockedMessage() {
        return COMBATLOG_COMMAND_BLOCKED_KEY;
    }

    @Nonnull
    public String getCombatLogCommandInfoMessage() {
        return COMBATLOG_COMMAND_INFO_KEY;
    }

    @Nonnull
    public String getCombatLogReloadSuccessMessage() {
        return COMBATLOG_RELOAD_SUCCESS_KEY;
    }

    @Nonnull
    public String getCombatLogReloadFailedMessage() {
        return COMBATLOG_RELOAD_FAILED_KEY;
    }

    @Nonnull
    public String getCombatLogTitleMain() {
        return COMBATLOG_TITLE_MAIN_KEY;
    }

    @Nonnull
    public String getCombatLogTitleSub() {
        return COMBATLOG_TITLE_SUB_KEY;
    }

    public int getRtpWarmupSeconds() {
        return Math.max(0, rtpWarmupSeconds);
    }

    public boolean isHomesEnabled() {
        return homesEnabled;
    }

    public void setHomesEnabled(boolean enabled) {
        homesEnabled = enabled;
        save();
    }

    public void setHomeWarmupSeconds(int seconds) {
        homeWarmupSeconds = Math.max(0, seconds);
        save();
    }

    public void setHomeMaxHomesPerPlayer(int maxHomesPerPlayer) {
        homeMaxHomesPerPlayer = Math.max(-1, maxHomesPerPlayer);
        save();
    }

    public boolean isWarpsEnabled() {
        return warpsEnabled;
    }

    public boolean isWarpsGuiEnabled() {
        return warpsGuiEnabled;
    }

    public boolean isPlayerWarpsEnabled() {
        return playerWarpsEnabled;
    }

    public void setPlayerWarpsEnabled(boolean enabled) {
        playerWarpsEnabled = enabled;
        save();
    }

    public boolean isPlayerWarpsGuiEnabled() {
        return playerWarpsGuiEnabled;
    }

    public void setPlayerWarpsGuiEnabled(boolean enabled) {
        playerWarpsGuiEnabled = enabled;
        save();
    }

    public boolean isPlayerWarpAutoApprove() {
        return playerWarpAutoApprove;
    }

    public void setPlayerWarpAutoApprove(boolean autoApprove) {
        playerWarpAutoApprove = autoApprove;
        save();
    }

    public int getPlayerWarpMaxWarpsPerPlayer() {
        return Math.max(0, playerWarpMaxWarpsPerPlayer);
    }

    public void setPlayerWarpMaxWarpsPerPlayer(int maxWarpsPerPlayer) {
        playerWarpMaxWarpsPerPlayer = Math.max(0, maxWarpsPerPlayer);
        save();
    }

    public long getPlayerWarpCreateCost() {
        return Math.max(0L, playerWarpCreateCost);
    }

    public void setPlayerWarpCreateCost(long createCost) {
        playerWarpCreateCost = Math.max(0L, createCost);
        save();
    }

    public long getPlayerWarpVisitCost() {
        return Math.max(0L, playerWarpVisitCost);
    }

    public void setPlayerWarpVisitCost(long visitCost) {
        playerWarpVisitCost = Math.max(0L, visitCost);
        save();
    }

    public boolean isKitsEnabled() {
        return kitsEnabled;
    }

    public boolean isKitsGuiEnabled() {
        return kitsGuiEnabled;
    }

    public boolean isKitsRequirePermission() {
        return kitsRequirePermission;
    }

    public boolean isMsgEnabled() {
        return msgEnabled;
    }

    public boolean isNearEnabled() {
        return nearEnabled;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public boolean isUsePermissionsSystem() {
        return usePermissionsSystem;
    }

    public boolean isHideNoPermissionCommands() {
        return hideNoPermissionCommands;
    }

    public boolean isCommandTreeRefreshEnabled() {
        return commandTreeRefreshEnabled;
    }

    public boolean isHologramsEnabled() {
        return hologramsEnabled;
    }

    public boolean isHologramPlaceholdersEnabled() {
        return hologramPlaceholdersEnabled;
    }

    public int getHologramPlaceholderUpdateIntervalMs() {
        return Math.max(250, hologramPlaceholderUpdateIntervalMs);
    }

    public int getHologramMaxLines() {
        return Math.max(1, hologramMaxLines);
    }

    public int getHologramMaxLineLength() {
        return Math.max(1, hologramMaxLineLength);
    }

    public int getHologramMaxNameLength() {
        return Math.max(1, hologramMaxNameLength);
    }

    public double getHologramDefaultLineSpacing() {
        return Math.max(0.05D, hologramDefaultLineSpacing);
    }

    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public void setScoreboardEnabled(boolean enabled) {
        scoreboardEnabled = enabled;
        save();
    }

    public int getScoreboardUpdateIntervalMs() {
        return Math.max(250, scoreboardUpdateIntervalMs);
    }

    public void setScoreboardUpdateIntervalMs(int intervalMs) {
        scoreboardUpdateIntervalMs = Math.max(250, intervalMs);
        save();
    }

    @Nonnull
    public String getScoreboardAnchor() {
        return scoreboardAnchor;
    }

    public int getScoreboardOffsetX() {
        return scoreboardOffsetX;
    }

    public int getScoreboardOffsetY() {
        return scoreboardOffsetY;
    }

    public void setScoreboardOffsets(int offsetX, int offsetY) {
        scoreboardOffsetX = Math.max(0, offsetX);
        scoreboardOffsetY = Math.max(0, offsetY);
        save();
    }

    public void setScoreboardAnchor(@Nonnull String anchor) {
        scoreboardAnchor = normalizeScoreboardAnchor(anchor);
        save();
    }

    public int getScoreboardWidth() {
        return Math.max(0, scoreboardWidth);
    }

    public int getScoreboardHeight() {
        return Math.max(0, scoreboardHeight);
    }

    public int getScoreboardLineSpacing() {
        return Math.max(0, scoreboardLineSpacing);
    }

    public int getScoreboardLineHeight() {
        return Math.max(1, scoreboardLineHeight);
    }

    public int getScoreboardFontSize() {
        return Math.max(8, scoreboardFontSize);
    }

    public int getScoreboardPaddingTop() {
        return Math.max(0, scoreboardPaddingTop);
    }

    public int getScoreboardPaddingBottom() {
        return Math.max(0, scoreboardPaddingBottom);
    }

    public int getScoreboardPaddingLeft() {
        return Math.max(0, scoreboardPaddingLeft);
    }

    public int getScoreboardPaddingRight() {
        return Math.max(0, scoreboardPaddingRight);
    }

    @Nonnull
    public String getScoreboardBackgroundColor() {
        return scoreboardBackgroundColor;
    }

    @Nonnull
    public String getScoreboardTextColor() {
        return scoreboardTextColor;
    }

    public int getScoreboardMaxPlayers() {
        return Math.max(0, scoreboardMaxPlayers);
    }

    @Nonnull
    public String getScoreboardBalanceFormat() {
        return scoreboardBalanceFormat;
    }

    public boolean isScoreboardLogoEnabled() {
        return scoreboardLogoEnabled;
    }

    @Nonnull
    public String getScoreboardLogoTexture() {
        return scoreboardLogoTexture;
    }

    public int getScoreboardLogoWidth() {
        return Math.max(1, scoreboardLogoWidth);
    }

    public int getScoreboardLogoHeight() {
        return Math.max(1, scoreboardLogoHeight);
    }

    public int getScoreboardLogoPaddingBottom() {
        return Math.max(0, scoreboardLogoPaddingBottom);
    }

    public boolean isScoreboardDefaultHidden() {
        return scoreboardDefaultHidden;
    }

    public void setScoreboardDefaultHidden(boolean hidden) {
        scoreboardDefaultHidden = hidden;
        save();
    }

    public void setScoreboardSize(int width, int height) {
        scoreboardWidth = Math.max(0, width);
        scoreboardHeight = Math.max(0, height);
        save();
    }

    @Nonnull
    public Map<String, String> getScoreboardPlaceholders() {
        return Collections.unmodifiableMap(scoreboardPlaceholders);
    }

    @Nonnull
    public List<String> getScoreboardLines() {
        return Collections.unmodifiableList(scoreboardLines);
    }

    public void setScoreboardLines(@Nonnull List<String> lines) {
        scoreboardLines = sanitizeScoreboardLines(lines);
        save();
    }

    public void setScoreboardPlaceholders(@Nonnull Map<String, String> placeholders) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            String trimmed = key.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isBlank()) continue;
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            sanitized.put(trimmed, value);
        }
        scoreboardPlaceholders = sanitized.isEmpty() ? defaultScoreboardPlaceholders() : sanitized;
        save();
    }

    public boolean isMotdEnabled() {
        return motdEnabled;
    }

    public double getFlySpeedMin() {
        return Math.max(0.05, flySpeedMin);
    }

    public double getFlySpeedMax() {
        return Math.max(getFlySpeedMin(), flySpeedMax);
    }

    public boolean isTimedFlightEnabled() {
        return timedFlightEnabled;
    }

    public boolean isTimedFlightRequirePermission() {
        return timedFlightRequirePermission;
    }

    public long getFlightCostPerMinute() {
        return Math.max(0L, flightCostPerMinute);
    }

    public int getFlightDefaultMinutes() {
        return Math.max(1, flightDefaultMinutes);
    }

    @Nonnull
    public List<Integer> getFlightWarningSeconds() {
        return List.copyOf(flightWarningSeconds);
    }

    public boolean isCommandSpyEnabled() {
        return commandSpyEnabled;
    }

    @Nonnull
    public List<String> getCommandSpyIgnoredCommands() {
        return List.copyOf(commandSpyIgnoredCommands);
    }

    public boolean isCommandSpyLogToActivity() {
        return commandSpyLogToActivity;
    }

    public boolean isNicknamesEnabled() {
        return nicknamesEnabled;
    }

    public int getNicknameMinLength() {
        return Math.max(1, nicknameMinLength);
    }

    public int getNicknameMaxLength() {
        return Math.max(getNicknameMinLength(), nicknameMaxLength);
    }

    public boolean isNicknamePreventDuplicates() {
        return nicknamePreventDuplicates;
    }

    @Nonnull
    public List<String> getNicknameBlacklist() {
        return List.copyOf(nicknameBlacklist);
    }

    public boolean isDiscordEnabled() {
        return discordEnabled;
    }

    @Nonnull
    public String getDiscordInviteUrl() {
        return discordInviteUrl;
    }

    public boolean isRulesEnabled() {
        return rulesEnabled;
    }

    public boolean isRulesGuiEnabled() {
        return rulesGuiEnabled;
    }

    public int getSleepPercentage() {
        return sleepPercentage;
    }

    public boolean isSleepChatEnabled() {
        return sleepChatEnabled;
    }

    public void setSleepChatEnabled(boolean enabled) {
        sleepChatEnabled = enabled;
        save();
    }

    public boolean isRtpEnabled() {
        return rtpEnabled;
    }

    public boolean isBroadcastEnabled() {
        return broadcastEnabled;
    }

    public void setBroadcastEnabled(boolean enabled) {
        broadcastEnabled = enabled;
        save();
    }

    public boolean isSpawnEnabled() {
        return spawnEnabled;
    }

    public boolean isTpaEnabled() {
        return tpaEnabled;
    }

    public boolean isAdminChatEnabled() {
        return adminChatEnabled;
    }

    public void setAdminChatEnabled(boolean enabled) {
        adminChatEnabled = enabled;
        save();
    }

    public boolean isAfkEnabled() {
        return afkEnabled;
    }

    public double getNearRadius() {
        return nearRadius;
    }

    public boolean isNearShowDistance() {
        return nearShowDistance;
    }

    public boolean isAutoBroadcastEnabled() {
        return autoBroadcastEnabled;
    }

    public int getAutoBroadcastIntervalSeconds() {
        return autoBroadcastIntervalSeconds;
    }

    public boolean isAutoBroadcastRandom() {
        return autoBroadcastRandom;
    }

    public boolean hasAutoBroadcastSection() {
        return root != null && root.has("autoBroadcast") && root.get("autoBroadcast").isJsonObject();
    }

    @Nonnull
    public List<String> getAutoBroadcastMessages() {
        return Collections.unmodifiableList(autoBroadcastMessages);
    }

    public boolean isAnnouncementsEnabled() {
        return announcementsEnabled;
    }

    public void setAnnouncementsEnabled(boolean enabled) {
        announcementsEnabled = enabled;
        autoBroadcastEnabled = enabled;
        save();
    }

    public int getAnnouncementsIntervalSeconds() {
        return Math.max(30, announcementsIntervalSeconds);
    }

    public void setAnnouncementsIntervalSeconds(int seconds) {
        announcementsIntervalSeconds = Math.max(30, seconds);
        autoBroadcastIntervalSeconds = announcementsIntervalSeconds;
        save();
    }

    public boolean isAnnouncementsRandom() {
        return announcementsRandom;
    }

    public void setAnnouncementsRandom(boolean random) {
        announcementsRandom = random;
        autoBroadcastRandom = random;
        save();
    }

    @Nonnull
    public List<AnnouncementPresetModel> getAnnouncementPresets() {
        List<AnnouncementPresetModel> copy = new ArrayList<>();
        for (AnnouncementPresetModel preset : announcementPresets) {
            if (preset != null) {
                copy.add(preset.copy());
            }
        }
        return Collections.unmodifiableList(copy);
    }

    @Nullable
    public AnnouncementPresetModel getAnnouncementPreset(@Nonnull String name) {
        String normalized = normalizeAnnouncementName(name);
        for (AnnouncementPresetModel preset : announcementPresets) {
            if (preset != null && preset.getName().equalsIgnoreCase(normalized)) {
                return preset.copy();
            }
        }
        return null;
    }

    public void saveAnnouncementPreset(@Nonnull AnnouncementPresetModel preset) {
        AnnouncementPresetModel sanitized = sanitizeAnnouncementPreset(preset);
        String normalized = sanitized.getName();
        List<AnnouncementPresetModel> updated = new ArrayList<>();
        boolean replaced = false;
        for (AnnouncementPresetModel existing : announcementPresets) {
            if (existing == null) continue;
            if (existing.getName().equalsIgnoreCase(normalized)) {
                updated.add(sanitized);
                replaced = true;
            } else {
                updated.add(sanitizeAnnouncementPreset(existing));
            }
        }
        if (!replaced) {
            updated.add(sanitized);
        }
        announcementPresets = updated;
        syncAutoBroadcastMessagesFromAnnouncements();
        save();
    }

    public boolean deleteAnnouncementPreset(@Nonnull String name) {
        String normalized = normalizeAnnouncementName(name);
        boolean removed = false;
        List<AnnouncementPresetModel> updated = new ArrayList<>();
        for (AnnouncementPresetModel existing : announcementPresets) {
            if (existing == null) continue;
            if (existing.getName().equalsIgnoreCase(normalized)) {
                removed = true;
            } else {
                updated.add(sanitizeAnnouncementPreset(existing));
            }
        }
        if (removed) {
            announcementPresets = updated.isEmpty() ? defaultAnnouncementPresets() : updated;
            syncAutoBroadcastMessagesFromAnnouncements();
            save();
        }
        return removed;
    }

    public boolean isWelcomeEnabled() {
        return welcomeEnabled;
    }

    public boolean isWelcomeBroadcastToAll() {
        return welcomeBroadcastToAll;
    }

    @Nonnull
    public List<String> getWelcomeMessages() {
        return Collections.unmodifiableList(welcomeMessages);
    }

    public boolean isJoinQuitEnabled() {
        return joinQuitEnabled;
    }

    public boolean isDeathMessagesEnabled() {
        return deathMessagesEnabled;
    }

    public boolean isChatEnabled() {
        return true;
    }

    public boolean isChatFormatEnabled() {
        return true;
    }

    public void setChatEnabled(boolean enabled) {
        save();
    }

    public boolean isSpawnProtectionEnabled() {
        return spawnProtectionEnabled;
    }

    public int getSpawnProtectionRadius() {
        return spawnProtectionRadius;
    }

    public boolean isSpawnProtectionAllowBreak() {
        return spawnProtectionAllowBreak;
    }

    public boolean isSpawnProtectionAllowPlace() {
        return spawnProtectionAllowPlace;
    }

    public boolean isSpawnProtectionAllowDamage() {
        return spawnProtectionAllowDamage;
    }

    public boolean isSpawnProtectionAllowInteract() {
        return spawnProtectionAllowInteract;
    }

    @Nonnull
    public List<String> getSpawnProtectionWorlds() {
        return Collections.unmodifiableList(spawnProtectionWorlds);
    }

    public void seedSpawnProtectionWorldsIfMissing() {
        if (root == null) return;
        JsonObject spawnProtection = obj(root, "spawnProtection");
        if (spawnProtection.has("worlds") && spawnProtection.get("worlds").isJsonArray()) {
            return;
        }
        spawnProtectionWorlds = new ArrayList<>(List.of(resolveServerDefaultWorldName()));
        spawnProtection.add("worlds", toArray(spawnProtectionWorlds));
        save();
    }

    public boolean isWorldBorderEnabled() {
        return worldBorderEnabled;
    }

    public int getWorldBorderRadius() {
        return Math.max(1, worldBorderRadius);
    }

    public int getWorldBorderCenterX() {
        return worldBorderCenterX;
    }

    public int getWorldBorderCenterZ() {
        return worldBorderCenterZ;
    }

    public int getWorldBorderTeleportPadding() {
        return Math.max(0, worldBorderTeleportPadding);
    }

    public boolean isWorldBorderExpansionEnabled() {
        return worldBorderExpansionEnabled;
    }

    public int getWorldBorderExpansionAmount() {
        return Math.max(1, worldBorderExpansionAmount);
    }

    public long getWorldBorderExpansionIntervalSeconds() {
        return Math.max(1L, worldBorderExpansionIntervalSeconds);
    }

    public int getWorldBorderExpansionMaxRadius() {
        return Math.max(0, worldBorderExpansionMaxRadius);
    }

    public long getWorldBorderExpansionLastRunAtMs() {
        return Math.max(0L, worldBorderExpansionLastRunAtMs);
    }

    public void setWorldBorderEnabled(boolean enabled) {
        worldBorderEnabled = enabled;
        save();
    }

    public void setWorldBorderRadius(int radius) {
        worldBorderRadius = Math.max(1, radius);
        save();
    }

    public void setWorldBorderCenter(int x, int z) {
        worldBorderCenterX = x;
        worldBorderCenterZ = z;
        save();
    }

    public void setWorldBorderExpansionState(int radius, long lastRunAtMs) {
        worldBorderRadius = Math.max(1, radius);
        worldBorderExpansionLastRunAtMs = Math.max(0L, lastRunAtMs);
        save();
    }

    public boolean isRespawnInvulnerabilityEnabled() {
        return respawnInvulnerabilityEnabled;
    }

    public int getRespawnInvulnerabilityBedSeconds() {
        return Math.max(0, respawnInvulnerabilityBedSeconds);
    }

    public int getRespawnInvulnerabilityWorldSeconds() {
        return Math.max(0, respawnInvulnerabilityWorldSeconds);
    }

    public boolean isRespawnInvulnerabilityCancelOnAttack() {
        return respawnInvulnerabilityCancelOnAttack;
    }

    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    public boolean isStatsTrackMovement() {
        return statsTrackMovement;
    }

    public boolean isEconomyEnabled() {
        return economyEnabled;
    }

    @Nonnull
    public String getEconomyCurrencySymbol() {
        return economyCurrencySymbol;
    }

    public long getEconomyStartingBalance() {
        return economyStartingBalance;
    }

    public int getEconomyDecimalPlaces() {
        return Math.max(0, Math.min(4, economyDecimalPlaces));
    }

    public boolean isEconomyHudEnabled() {
        return economyHudEnabled;
    }

    public boolean isEconomyHudDefaultHidden() {
        return economyHudDefaultHidden;
    }

    @Nonnull
    public String getEconomyHudLabel() {
        return economyHudLabel;
    }

    public int getEconomyHudUpdateIntervalMs() {
        return Math.max(250, economyHudUpdateIntervalMs);
    }

    @Nonnull
    public String getEconomyHudAnchor() {
        return economyHudAnchor;
    }

    public int getEconomyHudOffsetX() {
        return Math.max(0, economyHudOffsetX);
    }

    public int getEconomyHudOffsetY() {
        return Math.max(0, economyHudOffsetY);
    }

    public int getEconomyHudWidth() {
        return Math.max(120, economyHudWidth);
    }

    public int getEconomyHudHeight() {
        return Math.max(20, economyHudHeight);
    }

    @Nonnull
    public String getEconomyHudBackgroundColor() {
        return economyHudBackgroundColor;
    }

    @Nonnull
    public String getEconomyHudLabelColor() {
        return economyHudLabelColor;
    }

    @Nonnull
    public String getEconomyHudSymbolColor() {
        return economyHudSymbolColor;
    }

    @Nonnull
    public String getEconomyHudAmountColor() {
        return economyHudAmountColor;
    }

    public void setEconomyCurrencySymbol(@Nonnull String currencySymbol) {
        String sanitized = currencySymbol.trim();
        if (sanitized.length() > 8) {
            sanitized = sanitized.substring(0, 8);
        }
        economyCurrencySymbol = sanitized;
        save();
    }

    public void setEconomyStartingBalance(long startingBalance) {
        economyStartingBalance = Math.max(0L, startingBalance);
        save();
    }

    public void setEconomyHudEnabled(boolean enabled) {
        economyHudEnabled = enabled;
        save();
    }

    public void setEconomyHudDefaultHidden(boolean hidden) {
        economyHudDefaultHidden = hidden;
        save();
    }

    public void setEconomyHudLabel(@Nonnull String label) {
        String sanitized = label.trim();
        if (sanitized.isBlank()) {
            sanitized = "HyCoins";
        }
        if (sanitized.length() > 24) {
            sanitized = sanitized.substring(0, 24);
        }
        economyHudLabel = sanitized;
        save();
    }

    public void setEconomyHudUpdateIntervalMs(int intervalMs) {
        economyHudUpdateIntervalMs = Math.max(250, intervalMs);
        save();
    }

    public void setEconomyHudAnchor(@Nonnull String anchor) {
        economyHudAnchor = normalizeEconomyHudAnchor(anchor);
        save();
    }

    public void setEconomyHudOffsets(int offsetX, int offsetY) {
        economyHudOffsetX = Math.max(0, offsetX);
        economyHudOffsetY = Math.max(0, offsetY);
        save();
    }

    public void setEconomyHudSize(int width, int height) {
        economyHudWidth = Math.max(120, width);
        economyHudHeight = Math.max(20, height);
        save();
    }

    public boolean isPaycheckEnabled() {
        return paycheckEnabled;
    }

    public long getPaycheckAmount() {
        return Math.max(0L, paycheckAmount);
    }

    public double getPaycheckIntervalHours() {
        return Math.max(0.001, paycheckIntervalHours);
    }

    public boolean isEconomyRewardsEnabled() {
        return economyRewardsEnabled;
    }

    public boolean isEconomyBlockRewardsEnabled() {
        return economyBlockRewardsEnabled;
    }

    public boolean isEconomyMobRewardsEnabled() {
        return economyMobRewardsEnabled;
    }

    public boolean isEconomyRewardsDebug() {
        return economyRewardsDebug;
    }

    public boolean isEconomyRewardsPopupEnabled() {
        return economyRewardsPopupEnabled;
    }

    @Nonnull
    public String getEconomyRewardsPopupStyle() {
        return economyRewardsPopupStyle;
    }

    public boolean isEconomyBaltopGuiEnabled() {
        return economyBaltopGuiEnabled;
    }

    public long getEconomyMobDefaultReward() {
        return economyMobDefaultReward;
    }

    @Nonnull
    public Map<String, Long> getEconomyBlockRewards() {
        return Collections.unmodifiableMap(economyBlockRewards);
    }

    @Nonnull
    public Map<String, Long> getEconomyBlockGroupRewards() {
        return Collections.unmodifiableMap(economyBlockGroupRewards);
    }

    @Nonnull
    public Map<String, Long> getEconomyMobRewards() {
        return Collections.unmodifiableMap(economyMobRewards);
    }

    public boolean isPlaytimeGuiEnabled() {
        return playtimeGuiEnabled;
    }

    public int getPlaytimeTopLimit() {
        return Math.max(10, playtimeTopLimit);
    }

    public boolean isPlaytimeRewardsEnabled() {
        return playtimeRewardsEnabled;
    }

    public boolean isPlaytimeRewardsAutoClaim() {
        return playtimeRewardsAutoClaim;
    }

    public int getPlaytimeRewardsCheckIntervalSeconds() {
        return Math.max(5, playtimeRewardsCheckIntervalSeconds);
    }

    @Nonnull
    public List<PlaytimeRewardModel> getPlaytimeRewards() {
        return List.copyOf(playtimeRewards);
    }

    public void setPlaytimeGuiEnabled(boolean enabled) {
        playtimeGuiEnabled = enabled;
        save();
    }

    public void setPlaytimeTopLimit(int limit) {
        playtimeTopLimit = Math.max(10, limit);
        save();
    }

    public void setPlaytimeRewardsEnabled(boolean enabled) {
        playtimeRewardsEnabled = enabled;
        rankupEnabled = enabled;
        save();
    }

    public void setPlaytimeRewardsAutoClaim(boolean autoClaim) {
        playtimeRewardsAutoClaim = autoClaim;
        save();
    }

    public void setPlaytimeRewardsCheckIntervalSeconds(int intervalSeconds) {
        playtimeRewardsCheckIntervalSeconds = Math.max(5, intervalSeconds);
        save();
    }

    public void setPlaytimeRewards(@Nonnull List<PlaytimeRewardModel> rewards) {
        playtimeRewards = sanitizePlaytimeRewards(rewards);
        rankupTiers = toRankupTiers(playtimeRewards);
        save();
    }

    public boolean isRankupEnabled() {
        return playtimeRewardsEnabled;
    }

    public void setRankupEnabled(boolean enabled) {
        setPlaytimeRewardsEnabled(enabled);
    }

    public int getRankupConfirmTimeoutSeconds() {
        return rankupConfirmTimeoutSeconds;
    }

    public boolean isRankupPlaytimeEnabled() {
        return rankupPlaytimeEnabled;
    }

    public void setRankupPlaytimeEnabled(boolean enabled) {
        rankupPlaytimeEnabled = enabled;
        save();
    }

    public boolean isRankupCurrencyEnabled() {
        return rankupCurrencyEnabled;
    }

    public void setRankupCurrencyEnabled(boolean enabled) {
        rankupCurrencyEnabled = enabled;
        save();
    }

    public boolean isRankupAutoEnabled() {
        return rankupAutoEnabled;
    }

    public void setRankupAutoEnabled(boolean enabled) {
        rankupAutoEnabled = enabled;
        save();
    }

    public int getRankupAutoCheckSeconds() {
        return rankupAutoCheckSeconds;
    }

    public void setRankupAutoCheckSeconds(int checkSeconds) {
        rankupAutoCheckSeconds = Math.max(1, checkSeconds);
        save();
    }

    public boolean isRankupAutoUseCurrency() {
        return rankupAutoUseCurrency;
    }

    public void setRankupAutoUseCurrency(boolean useCurrency) {
        rankupAutoUseCurrency = useCurrency;
        save();
    }

    public boolean isPlayerShopsEnabled() {
        return playerShopsEnabled;
    }

    public void setPlayerShopsEnabled(boolean enabled) {
        playerShopsEnabled = enabled;
        save();
    }

    public boolean isPlayerShopDirectoryEnabled() {
        return playerShopDirectoryEnabled;
    }

    public void setPlayerShopDirectoryEnabled(boolean enabled) {
        playerShopDirectoryEnabled = enabled;
        save();
    }

    public boolean isAdminShopsEnabled() {
        return adminShopsEnabled;
    }

    public int getAdminShopMaxTradeQuantity() {
        return adminShopMaxTradeQuantity;
    }

    public void setAdminShopMaxTradeQuantity(int maxTradeQuantity) {
        adminShopMaxTradeQuantity = Math.max(1, maxTradeQuantity);
        save();
    }

    public int getPlayerShopMaxShopsPerPlayer() {
        return playerShopMaxShopsPerPlayer;
    }

    public void setPlayerShopMaxShopsPerPlayer(int maxShopsPerPlayer) {
        playerShopMaxShopsPerPlayer = Math.max(0, maxShopsPerPlayer);
        save();
    }

    public long getPlayerShopCreationCost() {
        return playerShopCreationCost;
    }

    public void setPlayerShopCreationCost(long creationCost) {
        playerShopCreationCost = Math.max(0L, creationCost);
        save();
    }

    public int getPlayerShopChestLinkRadius() {
        return playerShopChestLinkRadius;
    }

    public void setPlayerShopChestLinkRadius(int chestLinkRadius) {
        playerShopChestLinkRadius = Math.max(0, chestLinkRadius);
        save();
    }

    public int getPlayerShopMaxTradeQuantity() {
        return playerShopMaxTradeQuantity;
    }

    public void setPlayerShopMaxTradeQuantity(int maxTradeQuantity) {
        playerShopMaxTradeQuantity = Math.max(1, maxTradeQuantity);
        save();
    }

    public boolean isAuctionHouseEnabled() {
        return auctionHouseEnabled;
    }

    public void setAuctionHouseEnabled(boolean enabled) {
        auctionHouseEnabled = enabled;
        save();
    }

    public long getAuctionHouseMaxListingSeconds() {
        return auctionHouseMaxListingSeconds;
    }

    public void setAuctionHouseMaxListingSeconds(long maxListingSeconds) {
        auctionHouseMaxListingSeconds = Math.max(60L, maxListingSeconds);
        if (auctionHouseDefaultListingSeconds > auctionHouseMaxListingSeconds) {
            auctionHouseDefaultListingSeconds = auctionHouseMaxListingSeconds;
        }
        save();
    }

    public long getAuctionHouseDefaultListingSeconds() {
        return auctionHouseDefaultListingSeconds;
    }

    public void setAuctionHouseDefaultListingSeconds(long defaultListingSeconds) {
        auctionHouseDefaultListingSeconds = Math.max(60L, Math.min(defaultListingSeconds, auctionHouseMaxListingSeconds));
        save();
    }

    public int getAuctionHouseMaxListingsPerPlayer() {
        return auctionHouseMaxListingsPerPlayer;
    }

    public void setAuctionHouseMaxListingsPerPlayer(int maxListingsPerPlayer) {
        auctionHouseMaxListingsPerPlayer = Math.max(0, maxListingsPerPlayer);
        save();
    }

    public long getAuctionHouseListingCost() {
        return auctionHouseListingCost;
    }

    public void setAuctionHouseListingCost(long listingCost) {
        auctionHouseListingCost = Math.max(0L, listingCost);
        save();
    }

    @Nonnull
    public String getAuctionHouseNpcRole() {
        return auctionHouseNpcRole == null ? "" : auctionHouseNpcRole;
    }

    public boolean isMailEnabled() {
        return mailEnabled;
    }

    public int getMailCooldownSeconds() {
        return mailCooldownSeconds;
    }

    public double getMailSimilarityThreshold() {
        return mailSimilarityThreshold;
    }

    public int getMailSimilarityWindowSeconds() {
        return mailSimilarityWindowSeconds;
    }

    public int getMailMaxInboxSize() {
        return mailMaxInboxSize;
    }

    public int getMailMaxSentSize() {
        return mailMaxSentSize;
    }

    public int getMailMaxAgeDays() {
        return mailMaxAgeDays;
    }

    public int getMailPageSize() {
        return mailPageSize;
    }

    public boolean isMailNotifyOnJoin() {
        return mailNotifyOnJoin;
    }

    public boolean isMailNotifyOnReceive() {
        return mailNotifyOnReceive;
    }

    public int getMailMaxMessageLength() {
        return mailMaxMessageLength;
    }

    @Nonnull
    public List<RankupTier> getRankupTiers() {
        List<RankupTier> unified = toRankupTiers(playtimeRewards);
        if (!unified.isEmpty()) {
            return List.copyOf(unified);
        }
        return List.copyOf(rankupTiers);
    }

    @Nonnull
    public String getDefaultKitName() {
        return defaultKit;
    }

    @Nonnull
    public String getChatFormatForGroup(@Nonnull String groupName) {
        for (Map.Entry<String, String> entry : chatGroupFormats.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(groupName)) {
                return entry.getValue();
            }
        }
        return chatGroupFormats.getOrDefault("Default", "");
    }

    @Nonnull
    public String getDefaultChatFormat() {
        return chatGroupFormats.getOrDefault("Default", "");
    }

    @Nonnull
    public String getChatFormat() {
        return getDefaultChatFormat();
    }

    @Nonnull
    public Map<String, String> getChatGroupFormats() {
        return Collections.unmodifiableMap(chatGroupFormats);
    }

    @Nonnull
    public String getChatPrefixForGroup(@Nonnull String groupName) {
        return getChatGroupText(chatGroupPrefixes, groupName);
    }

    @Nonnull
    public String getChatSuffixForGroup(@Nonnull String groupName) {
        return getChatGroupText(chatGroupSuffixes, groupName);
    }

    @Nonnull
    private String getChatGroupText(@Nonnull Map<String, String> values, @Nonnull String groupName) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(groupName)) {
                return entry.getValue();
            }
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Default")) {
                return entry.getValue();
            }
        }
        return "";
    }

    @Nonnull
    public String getHighestPriorityGroup(@Nullable java.util.Set<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) return "Default";
        String highestGroup = "Default";
        int highestPriority = Integer.MIN_VALUE;
        for (String groupName : groupNames) {
            int priority = getPriorityForGroup(groupName);
            if (priority > highestPriority) {
                highestPriority = priority;
                highestGroup = groupName;
            }
        }
        return highestGroup;
    }

    private int getPriorityForGroup(@Nonnull String groupName) {
        for (Map.Entry<String, Integer> entry : chatGroupPriorities.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(groupName)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    public int getAfkTimeoutSeconds() {
        return afkTimeoutSeconds;
    }

    public boolean isAfkAnnounceOnAuto() {
        return afkAnnounceOnAuto;
    }

    public boolean isAfkAnnounceOnManual() {
        return afkAnnounceOnManual;
    }

    public boolean isAfkAnnounceOnReturn() {
        return afkAnnounceOnReturn;
    }

    @Nonnull
    public String getAfkMessage() {
        return afkMessage;
    }

    @Nonnull
    public String getAfkBackMessage() {
        return afkBackMessage;
    }

    @Nonnull
    public List<String> getJoinMessages() {
        return Collections.unmodifiableList(joinMessages);
    }

    @Nonnull
    public List<String> getQuitMessages() {
        return Collections.unmodifiableList(quitMessages);
    }

    @Nonnull
    public List<String> getDeathMessages() {
        return Collections.unmodifiableList(deathMessages);
    }

    @Nonnull
    public List<String> getMotdMessages() {
        return Collections.unmodifiableList(motdMessages);
    }

    @Nonnull
    public List<String> getDiscordMessages() {
        return Collections.unmodifiableList(discordMessages);
    }

    public boolean isMotdShowOnJoin() {
        return motdShowOnJoin;
    }

    @Nonnull
    public List<String> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public void setSleepPercentage(int percentage) {
        if (percentage < 0) percentage = 0;
        if (percentage > 100) percentage = 100;
        this.sleepPercentage = percentage;
        save();
    }

    public boolean hasSpawn() {
        return spawnSet && spawnWorld != null && !spawnWorld.isBlank();
    }

    @Nullable
    public SpawnModel getSpawn() {
        if (!hasSpawn()) return null;
        return new SpawnModel(
                spawnWorld,
                spawnX, spawnY, spawnZ,
                spawnYaw, spawnPitch
        );
    }

    public void setSpawn(@Nonnull SpawnModel spawn) {
        spawnSet = true;
        spawnWorld = spawn.getWorldName();
        spawnX = spawn.getX();
        spawnY = spawn.getY();
        spawnZ = spawn.getZ();
        spawnYaw = spawn.getYaw();
        spawnPitch = spawn.getPitch();
        save();
    }

    public void clearSpawn() {
        spawnSet = false;
        spawnWorld = "";
        spawnX = 0;
        spawnY = 0;
        spawnZ = 0;
        spawnYaw = 0f;
        spawnPitch = 0f;
        save();
    }

    public boolean hasNamedSpawn(@Nullable String name) {
        String key = normalizeSpawnName(name);
        return key != null && namedSpawns.containsKey(key);
    }

    @Nullable
    public SpawnModel getNamedSpawn(@Nullable String name) {
        String key = normalizeSpawnName(name);
        if (key == null) return null;
        SpawnModel spawn = namedSpawns.get(key);
        return spawn == null ? null : copySpawnModel(spawn);
    }

    @Nonnull
    public Map<String, SpawnModel> getNamedSpawns() {
        Map<String, SpawnModel> out = new LinkedHashMap<>();
        for (Map.Entry<String, SpawnModel> entry : namedSpawns.entrySet()) {
            out.put(entry.getKey(), copySpawnModel(entry.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    public void setNamedSpawn(@Nonnull String name, @Nonnull SpawnModel spawn) {
        String key = normalizeSpawnName(name);
        if (key == null) {
            throw new IllegalArgumentException("Invalid spawn name: " + name);
        }
        namedSpawns.put(key, copySpawnModel(spawn));
        save();
    }

    public boolean clearNamedSpawn(@Nullable String name) {
        String key = normalizeSpawnName(name);
        if (key == null) return false;
        SpawnModel removed = namedSpawns.remove(key);
        if (removed == null) return false;
        save();
        return true;
    }

    @Nonnull
    public String getSpawnRouteSelectionMode() {
        return spawnRouteSelectionMode;
    }

    public void setSpawnRouteSelectionMode(@Nonnull String mode) {
        spawnRouteSelectionMode = normalizeSpawnSelectionMode(mode);
        save();
    }

    @Nonnull
    public List<String> getSpawnRouteOrder(@Nonnull String route) {
        return switch (normalizeSpawnRouteName(route)) {
            case "firstjoin" -> Collections.unmodifiableList(spawnRouteFirstJoinOrder);
            case "join" -> Collections.unmodifiableList(spawnRouteJoinOrder);
            case "respawn" -> Collections.unmodifiableList(spawnRouteRespawnOrder);
            case "death" -> Collections.unmodifiableList(spawnRouteDeathOrder);
            default -> Collections.unmodifiableList(spawnRouteCommandOrder);
        };
    }

    public void setSpawnRouteOrder(@Nonnull String route, @Nonnull List<String> order) {
        List<String> normalized = normalizeSpawnRouteOrder(order, defaultSpawnRouteOrder(route));
        switch (normalizeSpawnRouteName(route)) {
            case "firstjoin" -> spawnRouteFirstJoinOrder = normalized;
            case "join" -> spawnRouteJoinOrder = normalized;
            case "respawn" -> {
                spawnRouteRespawnOrder = normalized;
                spawnRespawnPriority = normalizeRespawnPriority(normalized);
            }
            case "death" -> spawnRouteDeathOrder = normalized;
            default -> spawnRouteCommandOrder = normalized;
        }
        save();
    }

    @Nonnull
    public String getFirstJoinSpawnName() {
        return firstJoinSpawnName;
    }

    public void setFirstJoinSpawnName(@Nullable String name) {
        firstJoinSpawnName = normalizeOptionalSpawnRouteName(name);
        save();
    }

    @Nonnull
    public String getDeathSpawnName() {
        return deathSpawnName;
    }

    public void setDeathSpawnName(@Nullable String name) {
        deathSpawnName = normalizeOptionalSpawnRouteName(name);
        save();
    }

    @Nonnull
    public List<String> getWorldSpawnRoute(@Nullable String worldName) {
        String key = normalizeRouteKey(worldName);
        if (key == null) return List.of();
        List<String> spawns = worldSpawnRoutes.get(key);
        return spawns == null ? List.of() : List.copyOf(spawns);
    }

    @Nonnull
    public Map<String, List<String>> getWorldSpawnRoutes() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : worldSpawnRoutes.entrySet()) {
            out.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    public void setWorldSpawnRoute(@Nonnull String worldName, @Nonnull List<String> spawns) {
        String key = normalizeRouteKey(worldName);
        if (key == null) {
            throw new IllegalArgumentException("Invalid world route: " + worldName);
        }
        List<String> normalized = normalizeSpawnNameList(spawns);
        if (normalized.isEmpty()) {
            worldSpawnRoutes.remove(key);
        } else {
            worldSpawnRoutes.put(key, normalized);
        }
        save();
    }

    @Nonnull
    public List<SpawnRouteGroupModel> getGroupSpawnRoutes() {
        List<SpawnRouteGroupModel> out = new ArrayList<>();
        for (SpawnRouteGroupModel group : groupSpawnRoutes.values()) {
            out.add(copySpawnRouteGroup(group));
        }
        out.sort(Comparator.comparingInt(SpawnRouteGroupModel::getPriority).reversed()
                .thenComparing(SpawnRouteGroupModel::getId));
        return Collections.unmodifiableList(out);
    }

    @Nullable
    public SpawnRouteGroupModel getGroupSpawnRoute(@Nullable String id) {
        String key = normalizeRouteKey(id);
        if (key == null) return null;
        SpawnRouteGroupModel group = groupSpawnRoutes.get(key);
        return group == null ? null : copySpawnRouteGroup(group);
    }

    public void setGroupSpawnRoute(@Nonnull SpawnRouteGroupModel group) {
        String key = normalizeRouteKey(group.getId());
        if (key == null) {
            throw new IllegalArgumentException("Invalid group route: " + group.getId());
        }
        groupSpawnRoutes.put(key, copySpawnRouteGroup(new SpawnRouteGroupModel(
                key,
                group.getPermission(),
                group.getPriority(),
                normalizeSpawnNameList(group.getSpawns())
        )));
        save();
    }

    public boolean clearGroupSpawnRoute(@Nullable String id) {
        String key = normalizeRouteKey(id);
        if (key == null) return false;
        SpawnRouteGroupModel removed = groupSpawnRoutes.remove(key);
        if (removed == null) return false;
        save();
        return true;
    }

    @Nonnull
    public String getStorageType() {
        return storageType;
    }

    @Nonnull
    public Path getSqliteFile(@Nonnull Path dataFolder) {
        Path file = Path.of(sqliteFile);
        if (file.isAbsolute()) return file;
        return dataFolder.resolve(file);
    }

    @Nonnull
    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    @Nonnull
    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    @Nonnull
    public String getMysqlUser() {
        return mysqlUser;
    }

    @Nonnull
    public String getMysqlPassword() {
        return mysqlPassword;
    }

    @Nonnull
    public String getMongoUri() {
        return mongoUri;
    }

    @Nonnull
    public String getMongoDatabase() {
        return mongoDatabase;
    }

    @Nonnull
    public String getMongoCollectionPrefix() {
        return mongoCollectionPrefix;
    }

    private void save() {
        try {
            applyFieldsToRoot();
            writeSplitConfigs();
        } catch (Exception e) {
            Log.warn("Failed to save config files: " + e.getMessage());
        }
    }

    private void applyFieldsToRoot() {
        if (root == null) root = buildDefaultConfig();

        root.addProperty("version", PluginInfoUtil.getVersion());
        root.addProperty("debugMode", debugMode);
        root.addProperty("UsePermissionsSystem", usePermissionsSystem);
        root.addProperty("HideNoPermissionCommands", hideNoPermissionCommands);
        root.addProperty("CommandTreeRefreshEnabled", commandTreeRefreshEnabled);
        root.remove("CommandTreeRefreshIntervalSeconds");
        root.remove("commandTreeRefreshIntervalSeconds");

        JsonObject general = obj(root, "general");
        general.addProperty("spawnFallbackToWorldDefault", useWorldDefaultSpawnIfUnset);
        general.addProperty("language", languageCode);

        JsonObject holograms = obj(root, "holograms");
        holograms.addProperty("enabled", hologramsEnabled);
        holograms.addProperty("maxLines", hologramMaxLines);
        holograms.addProperty("maxLineLength", hologramMaxLineLength);
        holograms.addProperty("maxNameLength", hologramMaxNameLength);
        holograms.addProperty("defaultLineSpacing", hologramDefaultLineSpacing);
        JsonObject hologramPlaceholders = obj(holograms, "placeholders");
        hologramPlaceholders.addProperty("enabled", hologramPlaceholdersEnabled);
        hologramPlaceholders.addProperty("updateIntervalMs", hologramPlaceholderUpdateIntervalMs);

        JsonObject scoreboard = obj(root, "scoreboard");
        scoreboard.addProperty("enabled", scoreboardEnabled);
        scoreboard.addProperty("updateIntervalMs", scoreboardUpdateIntervalMs);
        scoreboard.addProperty("anchor", scoreboardAnchor);
        scoreboard.addProperty("offsetX", scoreboardOffsetX);
        scoreboard.addProperty("offsetY", scoreboardOffsetY);
        scoreboard.addProperty("width", scoreboardWidth);
        scoreboard.addProperty("height", scoreboardHeight);
        scoreboard.addProperty("lineSpacing", scoreboardLineSpacing);
        scoreboard.addProperty("lineHeight", scoreboardLineHeight);
        scoreboard.addProperty("fontSize", scoreboardFontSize);
        scoreboard.addProperty("backgroundColor", scoreboardBackgroundColor);
        scoreboard.addProperty("textColor", scoreboardTextColor);
        scoreboard.addProperty("maxPlayers", scoreboardMaxPlayers);
        scoreboard.addProperty("balanceFormat", scoreboardBalanceFormat);
        scoreboard.addProperty("defaultHideScoreboard", scoreboardDefaultHidden);
        JsonObject scoreboardPadding = obj(scoreboard, "padding");
        scoreboardPadding.addProperty("top", scoreboardPaddingTop);
        scoreboardPadding.addProperty("bottom", scoreboardPaddingBottom);
        scoreboardPadding.addProperty("left", scoreboardPaddingLeft);
        scoreboardPadding.addProperty("right", scoreboardPaddingRight);
        JsonObject scoreboardLogo = obj(scoreboard, "logo");
        scoreboardLogo.addProperty("enabled", scoreboardLogoEnabled);
        scoreboardLogo.addProperty("texture", scoreboardLogoTexture);
        scoreboardLogo.addProperty("width", scoreboardLogoWidth);
        scoreboardLogo.addProperty("height", scoreboardLogoHeight);
        scoreboardLogo.addProperty("paddingBottom", scoreboardLogoPaddingBottom);
        scoreboard.add("placeholders", toStringMapObject(scoreboardPlaceholders));
        scoreboard.add("lines", toArray(scoreboardLines));

        JsonObject tpa = obj(root, "tpa");
        tpa.addProperty("enabled", tpaEnabled);
        tpa.addProperty("timeoutSeconds", tpaRequestTimeoutSeconds);
        tpa.addProperty("warmupSeconds", tpaWarmupSeconds);
        tpa.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.TPA));
        tpa.addProperty("gui", tpaGuiEnabled);

        JsonObject rtp = obj(root, "rtp");
        rtp.addProperty("radius", rtpMaxDistance);
        rtp.addProperty("minRadius", rtpMinDistance);
        rtp.addProperty("enabled", rtpEnabled);
        rtp.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.RTP));
        rtp.addProperty("warmupSeconds", rtpWarmupSeconds);
        rtp.add("worldOverrides", toStringMapObject(rtpWorldOverrides));
        root.remove("cooldowns");

        JsonObject near = obj(root, "near");
        near.addProperty("enabled", nearEnabled);
        near.addProperty("radius", nearRadius);
        near.addProperty("showDistance", nearShowDistance);
        near.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.NEAR));

        JsonObject fly = obj(root, "fly");
        fly.addProperty("minSpeed", flySpeedMin);
        fly.addProperty("maxSpeed", flySpeedMax);
        fly.addProperty("timedEnabled", timedFlightEnabled);
        fly.addProperty("timedRequirePermission", timedFlightRequirePermission);
        fly.addProperty("costPerMinute", formatMoneyConfig(flightCostPerMinute));
        fly.addProperty("defaultMinutes", flightDefaultMinutes);
        fly.add("expiryWarningSeconds", toIntArray(flightWarningSeconds));

        JsonObject commandSpy = obj(root, "commandSpy");
        commandSpy.addProperty("enabled", commandSpyEnabled);
        commandSpy.add("ignoredCommands", toArray(commandSpyIgnoredCommands));
        commandSpy.addProperty("logToActivity", commandSpyLogToActivity);

        JsonObject nicknames = obj(root, "nicknames");
        nicknames.addProperty("enabled", nicknamesEnabled);
        nicknames.addProperty("minLength", nicknameMinLength);
        nicknames.addProperty("maxLength", nicknameMaxLength);
        nicknames.addProperty("preventDuplicates", nicknamePreventDuplicates);
        nicknames.add("blacklist", toArray(nicknameBlacklist));

        JsonObject autoBroadcast = obj(root, "autoBroadcast");
        autoBroadcast.addProperty("enabled", autoBroadcastEnabled);
        autoBroadcast.addProperty("intervalSeconds", autoBroadcastIntervalSeconds);
        autoBroadcast.addProperty("random", autoBroadcastRandom);
        autoBroadcast.add("messages", toArray(autoBroadcastMessages));

        JsonObject announcements = obj(root, "announcements");
        announcements.addProperty("enabled", announcementsEnabled);
        announcements.addProperty("intervalSeconds", announcementsIntervalSeconds);
        announcements.addProperty("random", announcementsRandom);
        announcements.add("presets", toAnnouncementArray(announcementPresets));

        JsonObject features = obj(root, "features");
        features.addProperty("msg", msgEnabled);
        features.addProperty("broadcast", broadcastEnabled);
        features.addProperty("adminChat", adminChatEnabled);

        JsonObject welcome = obj(root, "welcomeMessage");
        welcome.addProperty("enabled", welcomeEnabled);
        welcome.addProperty("broadcastToAll", welcomeBroadcastToAll);
        welcome.add("messages", toArray(welcomeMessages));

        JsonObject joinQuit = obj(root, "joinAndQuit");
        joinQuit.addProperty("enabled", joinQuitEnabled);
        joinQuit.add("joinMessages", toArray(joinMessages));
        joinQuit.add("quitMessages", toArray(quitMessages));

        JsonObject death = obj(root, "deathMessages");
        death.addProperty("enabled", deathMessagesEnabled);
        death.add("messages", toArray(deathMessages));

        JsonObject chat = obj(root, "chat");
        chat.remove("enabled");
        chat.remove("overrideLuckPermsChatFormat");
        chat.remove("overrideLuckPerms");
        chat.add("groups", toChatGroupObject(chatGroupFormats));
        chat.add("groupPrefixes", toStringMapObject(chatGroupPrefixes));
        chat.add("groupSuffixes", toStringMapObject(chatGroupSuffixes));
        chat.add("groupPriorities", toPriorityObject(chatGroupPriorities));

        JsonObject spawnProtection = obj(root, "spawnProtection");
        spawnProtection.addProperty("enabled", spawnProtectionEnabled);
        spawnProtection.addProperty("radius", spawnProtectionRadius);
        spawnProtection.addProperty("allowBreak", spawnProtectionAllowBreak);
        spawnProtection.addProperty("allowPlace", spawnProtectionAllowPlace);
        spawnProtection.addProperty("allowDamage", spawnProtectionAllowDamage);
        spawnProtection.addProperty("allowInteract", spawnProtectionAllowInteract);
        spawnProtection.add("worlds", toArray(spawnProtectionWorlds));

        JsonObject worldBorder = obj(root, "worldBorder");
        worldBorder.addProperty("enabled", worldBorderEnabled);
        worldBorder.addProperty("radius", Math.max(1, worldBorderRadius));
        worldBorder.addProperty("centerX", worldBorderCenterX);
        worldBorder.addProperty("centerZ", worldBorderCenterZ);
        worldBorder.addProperty("teleportPadding", Math.max(0, worldBorderTeleportPadding));
        JsonObject worldBorderExpansion = obj(worldBorder, "expansion");
        worldBorderExpansion.addProperty("enabled", worldBorderExpansionEnabled);
        worldBorderExpansion.addProperty("amount", Math.max(1, worldBorderExpansionAmount));
        worldBorderExpansion.addProperty("intervalSeconds", Math.max(1L, worldBorderExpansionIntervalSeconds));
        worldBorderExpansion.addProperty("maxRadius", Math.max(0, worldBorderExpansionMaxRadius));
        worldBorderExpansion.addProperty("lastRunAtMs", Math.max(0L, worldBorderExpansionLastRunAtMs));

        JsonObject respawnInvulnerability = obj(root, "respawnInvulnerability");
        respawnInvulnerability.addProperty("enabled", respawnInvulnerabilityEnabled);
        respawnInvulnerability.addProperty("bedSeconds", Math.max(0, respawnInvulnerabilityBedSeconds));
        respawnInvulnerability.addProperty("worldSeconds", Math.max(0, respawnInvulnerabilityWorldSeconds));
        respawnInvulnerability.addProperty("cancelOnAttack", respawnInvulnerabilityCancelOnAttack);

        JsonObject stats = obj(root, "stats");
        stats.addProperty("enabled", statsEnabled);
        stats.addProperty("trackMovement", statsTrackMovement);

        JsonObject economy = obj(root, "economy");
        economy.addProperty("enabled", economyEnabled);
        economy.addProperty("currencySymbol", economyCurrencySymbol);
        economy.addProperty("decimalPlaces", Math.max(0, Math.min(4, economyDecimalPlaces)));
        economy.addProperty("startingBalance", formatMoneyConfig(economyStartingBalance));
        economy.addProperty("baltopGui", economyBaltopGuiEnabled);
        JsonObject hud = obj(economy, "hud");
        hud.addProperty("enabled", economyHudEnabled);
        hud.addProperty("defaultHidden", economyHudDefaultHidden);
        hud.addProperty("label", economyHudLabel);
        hud.addProperty("updateIntervalMs", Math.max(250, economyHudUpdateIntervalMs));
        hud.addProperty("anchor", economyHudAnchor);
        hud.addProperty("offsetX", Math.max(0, economyHudOffsetX));
        hud.addProperty("offsetY", Math.max(0, economyHudOffsetY));
        hud.addProperty("width", Math.max(120, economyHudWidth));
        hud.addProperty("height", Math.max(20, economyHudHeight));
        JsonObject hudColors = obj(hud, "colors");
        hudColors.addProperty("background", economyHudBackgroundColor);
        hudColors.addProperty("label", economyHudLabelColor);
        hudColors.addProperty("symbol", economyHudSymbolColor);
        hudColors.addProperty("amount", economyHudAmountColor);
        JsonObject paycheck = obj(economy, "paycheck");
        paycheck.addProperty("enabled", paycheckEnabled);
        paycheck.addProperty("amount", formatMoneyConfig(paycheckAmount));
        paycheck.addProperty("intervalHours", paycheckIntervalHours);
        JsonObject rewards = obj(economy, "rewards");
        rewards.addProperty("enabled", economyRewardsEnabled);
        rewards.addProperty("debug", economyRewardsDebug);
        JsonObject popup = obj(rewards, "popup");
        popup.addProperty("enabled", economyRewardsPopupEnabled);
        popup.addProperty("style", economyRewardsPopupStyle);
        JsonObject blockRewards = obj(rewards, "blocks");
        blockRewards.addProperty("enabled", economyBlockRewardsEnabled);
        blockRewards.add("rewards", toMoneyMapObject(economyBlockRewards));
        blockRewards.add("groupRewards", toMoneyMapObject(economyBlockGroupRewards));
        JsonObject mobRewards = obj(rewards, "mobs");
        mobRewards.addProperty("enabled", economyMobRewardsEnabled);
        mobRewards.addProperty("defaultReward", formatMoneyConfig(economyMobDefaultReward));
        mobRewards.add("rewards", toMoneyMapObject(economyMobRewards));

        JsonObject rankup = obj(root, "rankup");
        rankup.remove("enabled");
        rankup.addProperty("confirmTimeoutSeconds", rankupConfirmTimeoutSeconds);
        JsonObject requirements = obj(rankup, "requirements");
        requirements.addProperty("playtimeEnabled", rankupPlaytimeEnabled);
        requirements.addProperty("currencyEnabled", rankupCurrencyEnabled);
        JsonObject auto = obj(rankup, "auto");
        auto.addProperty("enabled", rankupAutoEnabled);
        auto.addProperty("checkSeconds", rankupAutoCheckSeconds);
        auto.addProperty("useCurrency", rankupAutoUseCurrency);
        rankup.remove("ranks");

        JsonObject playtime = obj(root, "playtime");
        playtime.addProperty("guiEnabled", playtimeGuiEnabled);
        playtime.addProperty("topLimit", Math.max(10, playtimeTopLimit));
        JsonObject playtimeRewardSection = obj(playtime, "rewards");
        playtimeRewardSection.addProperty("enabled", playtimeRewardsEnabled);
        playtimeRewardSection.addProperty("autoClaim", playtimeRewardsAutoClaim);
        playtimeRewardSection.addProperty("checkIntervalSeconds", Math.max(5, playtimeRewardsCheckIntervalSeconds));
        playtimeRewardSection.add("entries", toPlaytimeRewardsArray(playtimeRewards));

        JsonObject motd = obj(root, "motd");
        motd.addProperty("enabled", motdEnabled);
        motd.addProperty("showOnJoin", motdShowOnJoin);
        motd.add("messages", toArray(motdMessages));

        JsonObject discord = obj(root, "discord");
        discord.addProperty("enabled", discordEnabled);
        discord.addProperty("inviteUrl", discordInviteUrl);
        discord.add("messages", toArray(discordMessages));

        JsonObject kits = obj(root, "kits");
        kits.addProperty("enabled", kitsEnabled);
        kits.addProperty("defaultKit", defaultKit);
        kits.addProperty("gui", kitsGuiEnabled);
        kits.addProperty("requirePermission", kitsRequirePermission);

        JsonObject rulesObj = obj(root, "rules");
        rulesObj.addProperty("enabled", rulesEnabled);
        rulesObj.addProperty("gui", rulesGuiEnabled);
        rulesObj.add("messages", toArray(rules));

        JsonObject sleep = obj(root, "sleep");
        sleep.addProperty("percentage", sleepPercentage);
        sleep.addProperty("chatMessages", sleepChatEnabled);

        JsonObject afk = obj(root, "afk");
        afk.addProperty("enabled", afkEnabled);
        afk.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.AFK));
        afk.addProperty("timeoutSeconds", afkTimeoutSeconds);
        afk.addProperty("announceOnAuto", afkAnnounceOnAuto);
        afk.addProperty("announceOnManual", afkAnnounceOnManual);
        afk.addProperty("announceOnReturn", afkAnnounceOnReturn);
        afk.addProperty("afkMessage", afkMessage);
        afk.addProperty("backMessage", afkBackMessage);

        JsonObject homes = obj(root, "homes");
        homes.addProperty("enabled", homesEnabled);
        homes.addProperty("maxHomesPerPlayer", homeMaxHomesPerPlayer);
        homes.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.HOME));
        homes.addProperty("warmupSeconds", homeWarmupSeconds);

        JsonObject warps = obj(root, "warps");
        warps.addProperty("enabled", warpsEnabled);
        warps.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.WARP));
        warps.addProperty("warmupSeconds", warpWarmupSeconds);
        warps.addProperty("gui", warpsGuiEnabled);

        JsonObject playerWarps = obj(root, "playerWarps");
        playerWarps.addProperty("enabled", playerWarpsEnabled);
        playerWarps.addProperty("gui", playerWarpsGuiEnabled);
        playerWarps.addProperty("autoApprove", playerWarpAutoApprove);
        playerWarps.addProperty("maxWarpsPerPlayer", playerWarpMaxWarpsPerPlayer);
        playerWarps.addProperty("createCost", formatMoneyConfig(playerWarpCreateCost));
        playerWarps.addProperty("visitCost", formatMoneyConfig(playerWarpVisitCost));

        JsonObject back = obj(root, "back");
        back.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.BACK));
        back.addProperty("warmupSeconds", backWarmupSeconds);

        JsonObject heal = obj(root, "heal");
        heal.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.HEAL));

        JsonObject repair = obj(root, "repair");
        repair.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.REPAIR));

        JsonObject jumpTo = obj(root, "jumpto");
        jumpTo.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.JUMPTO));

        JsonObject spawn = obj(root, "spawn");
        spawn.addProperty("enabled", spawnEnabled);
        spawn.addProperty("set", spawnSet);
        spawn.addProperty("world", spawnWorld);
        spawn.addProperty("x", spawnX);
        spawn.addProperty("y", spawnY);
        spawn.addProperty("z", spawnZ);
        spawn.addProperty("yaw", spawnYaw);
        spawn.addProperty("pitch", spawnPitch);
        spawn.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.SPAWN));
        spawn.addProperty("warmupSeconds", spawnWarmupSeconds);
        spawn.add("respawnPriority", toArray(spawnRespawnPriority));
        spawn.add("named", toSpawnMapObject(namedSpawns));
        spawn.add("routing", toSpawnRoutingObject());

        root.add("commandRules", toCommandRulesObject());

        JsonObject combatLog = obj(root, "combatLog");
        combatLog.addProperty("enabled", combatLogEnabled);
        combatLog.addProperty("onlyPlayerDamageLog", combatLogOnlyPlayerDamage);
        combatLog.addProperty("combatTime", combatLogTimeSeconds);
        combatLog.addProperty("showCombatTitle", combatLogShowTitle);
        combatLog.addProperty("blockCommandsInCombat", combatLogBlockCommands);
        combatLog.add("blockedCommands", toArray(combatLogBlockedCommands));

        JsonObject storage = obj(root, "storage");
        storage.addProperty("type", storageType);
        storage.addProperty("sqliteFile", sqliteFile);
        storage.addProperty("mysqlHost", mysqlHost);
        storage.addProperty("mysqlPort", mysqlPort);
        storage.addProperty("mysqlDatabase", mysqlDatabase);
        storage.addProperty("mysqlUser", mysqlUser);
        storage.addProperty("mysqlPassword", mysqlPassword);
        storage.addProperty("mongoUri", mongoUri);
        storage.addProperty("mongoDatabase", mongoDatabase);
        storage.addProperty("mongoCollectionPrefix", mongoCollectionPrefix);

        JsonObject playerShops = obj(root, "playerShops");
        playerShops.addProperty("enabled", playerShopsEnabled);
        playerShops.addProperty("directoryEnabled", playerShopDirectoryEnabled);
        playerShops.addProperty("maxShopsPerPlayer", playerShopMaxShopsPerPlayer);
        playerShops.addProperty("shopCreationCost", formatMoneyConfig(playerShopCreationCost));
        playerShops.addProperty("chestLinkRadius", playerShopChestLinkRadius);
        playerShops.addProperty("maxTradeQuantity", playerShopMaxTradeQuantity);

        JsonObject adminShops = obj(root, "adminShops");
        adminShops.addProperty("enabled", adminShopsEnabled);
        adminShops.addProperty("maxTradeQuantity", adminShopMaxTradeQuantity);

        JsonObject auctionHouse = obj(root, "auctionHouse");
        auctionHouse.addProperty("enabled", auctionHouseEnabled);
        auctionHouse.addProperty("maxListingSeconds", auctionHouseMaxListingSeconds);
        auctionHouse.addProperty("defaultListingSeconds", auctionHouseDefaultListingSeconds);
        auctionHouse.addProperty("maxListingsPerPlayer", auctionHouseMaxListingsPerPlayer);
        auctionHouse.addProperty("listingCost", formatMoneyConfig(auctionHouseListingCost));
        auctionHouse.addProperty("npcRole", auctionHouseNpcRole);

        JsonObject mail = obj(root, "mail");
        mail.addProperty("enabled", mailEnabled);
        mail.addProperty("cooldownSeconds", mailCooldownSeconds);
        mail.addProperty("similarityThreshold", mailSimilarityThreshold);
        mail.addProperty("similarityWindowSeconds", mailSimilarityWindowSeconds);
        mail.addProperty("maxInboxSize", mailMaxInboxSize);
        mail.addProperty("maxSentSize", mailMaxSentSize);
        mail.addProperty("maxAgeDays", mailMaxAgeDays);
        mail.addProperty("pageSize", mailPageSize);
        mail.addProperty("notifyOnJoin", mailNotifyOnJoin);
        mail.addProperty("notifyOnReceive", mailNotifyOnReceive);
        mail.addProperty("maxMessageLength", mailMaxMessageLength);
    }

    @Nonnull
    private JsonObject readCombinedRoot() throws Exception {
        JsonObject main = readOrDefault(configPath, buildDefaultMainRoot());
        boolean hasEconomy = Files.exists(economyPath);
        boolean hasRewards = Files.exists(rewardsPath);
        boolean hasRankup = Files.exists(rankupPath);
        boolean hasChat = Files.exists(chatPath);
        boolean hasScoreboard = Files.exists(scoreboardPath);
        JsonObject economy = hasEconomy ? readOrDefault(economyPath, buildDefaultEconomyRoot()) : new JsonObject();
        JsonObject rewards = hasRewards ? readOrDefault(rewardsPath, buildDefaultRewardsRoot()) : new JsonObject();
        JsonObject rankup = hasRankup ? readOrDefault(rankupPath, buildDefaultRankupRoot()) : new JsonObject();
        JsonObject chat = hasChat ? readOrDefault(chatPath, buildDefaultChatRoot()) : new JsonObject();
        JsonObject scoreboard = hasScoreboard ? readOrDefault(scoreboardPath, buildDefaultScoreboardRoot()) : new JsonObject();

        JsonObject merged = main.deepCopy();
        if (hasEconomy && economy.has("economy")) {
            merged.add("economy", economy.get("economy"));
        } else if (main.has("economy")) {
            merged.add("economy", main.get("economy"));
        } else {
            JsonObject defEconomy = buildDefaultEconomyRoot();
            if (defEconomy.has("economy")) {
                merged.add("economy", defEconomy.get("economy"));
            }
        }
        JsonObject defRewards = buildDefaultRewardsRoot();
        if (hasRewards && rewards.has("playtime")) {
            merged.add("playtime", rewards.get("playtime"));
        } else if (defRewards.has("playtime")) {
            merged.add("playtime", defRewards.get("playtime"));
        }
        JsonObject defaultRankup = defRewards.has("rankup") && defRewards.get("rankup").isJsonObject()
                ? defRewards.getAsJsonObject("rankup")
                : new JsonObject();
        JsonObject rewardsRankup = hasRewards && rewards.has("rankup") && rewards.get("rankup").isJsonObject()
                ? rewards.getAsJsonObject("rankup")
                : null;
        JsonObject legacyRankup = hasRankup && rankup.has("rankup") && rankup.get("rankup").isJsonObject()
                ? rankup.getAsJsonObject("rankup")
                : null;
        JsonObject mergedRankup = mergeRankupSection(rewardsRankup, legacyRankup, defaultRankup);
        if (!mergedRankup.entrySet().isEmpty()) {
            merged.add("rankup", mergedRankup);
        }
        if (hasScoreboard && scoreboard.has("scoreboard")) {
            merged.add("scoreboard", scoreboard.get("scoreboard"));
        } else if (main.has("scoreboard")) {
            merged.add("scoreboard", main.get("scoreboard"));
        } else {
            JsonObject defScoreboard = buildDefaultScoreboardRoot();
            if (defScoreboard.has("scoreboard")) {
                merged.add("scoreboard", defScoreboard.get("scoreboard"));
            }
        }
        if (hasChat && !chat.entrySet().isEmpty()) {
            for (Map.Entry<String, JsonElement> entry : chat.entrySet()) {
                merged.add(entry.getKey(), entry.getValue());
            }
        } else {
            JsonObject defChat = buildDefaultChatRoot();
            for (Map.Entry<String, JsonElement> entry : defChat.entrySet()) {
                if (!merged.has(entry.getKey())) {
                    merged.add(entry.getKey(), entry.getValue());
                }
            }
        }
        return merged;
    }

    @Nonnull
    private JsonObject mergeRankupSection(@Nullable JsonObject rewardsRankup,
                                          @Nullable JsonObject legacyRankup,
                                          @Nonnull JsonObject defaultRankup) {
        JsonObject merged = rewardsRankup != null ? rewardsRankup.deepCopy() : defaultRankup.deepCopy();
        if (legacyRankup == null) {
            return merged;
        }

        copyLegacyRankupValue(merged, legacyRankup, defaultRankup, "confirmTimeoutSeconds");

        JsonObject mergedRequirements = obj(merged, "requirements");
        JsonObject legacyRequirements = getObjectOrNull(legacyRankup, "requirements");
        JsonObject defaultRequirements = getObjectOrNull(defaultRankup, "requirements");
        if (legacyRequirements != null) {
            JsonObject fallbackDefaults = defaultRequirements != null ? defaultRequirements : new JsonObject();
            copyLegacyRankupValue(mergedRequirements, legacyRequirements, fallbackDefaults, "playtimeEnabled");
            copyLegacyRankupValue(mergedRequirements, legacyRequirements, fallbackDefaults, "currencyEnabled");
        }

        JsonObject mergedAuto = obj(merged, "auto");
        JsonObject legacyAuto = getObjectOrNull(legacyRankup, "auto");
        JsonObject defaultAuto = getObjectOrNull(defaultRankup, "auto");
        if (legacyAuto != null) {
            JsonObject fallbackDefaults = defaultAuto != null ? defaultAuto : new JsonObject();
            copyLegacyRankupValue(mergedAuto, legacyAuto, fallbackDefaults, "enabled");
            copyLegacyRankupValue(mergedAuto, legacyAuto, fallbackDefaults, "checkSeconds");
            copyLegacyRankupValue(mergedAuto, legacyAuto, fallbackDefaults, "useCurrency");
        }

        if (legacyRankup.has("ranks") && legacyRankup.get("ranks").isJsonArray()) {
            JsonArray legacyRanks = legacyRankup.getAsJsonArray("ranks");
            boolean hasMergedRanks = merged.has("ranks")
                    && merged.get("ranks").isJsonArray()
                    && merged.getAsJsonArray("ranks").size() > 0;
            if (!hasMergedRanks && legacyRanks.size() > 0) {
                merged.add("ranks", legacyRanks.deepCopy());
            }
        }

        return merged;
    }

    private void copyLegacyRankupValue(@Nonnull JsonObject target,
                                       @Nonnull JsonObject legacy,
                                       @Nonnull JsonObject defaults,
                                       @Nonnull String key) {
        if (!legacy.has(key)) {
            return;
        }
        if (!target.has(key) || isDefaultValue(target, defaults, key)) {
            target.add(key, legacy.get(key).deepCopy());
        }
    }

    private boolean isDefaultValue(@Nonnull JsonObject target,
                                   @Nonnull JsonObject defaults,
                                   @Nonnull String key) {
        return defaults.has(key) && target.has(key) && target.get(key).equals(defaults.get(key));
    }

    @Nonnull
    private JsonObject readOrDefault(@Nonnull Path path, @Nonnull JsonObject def) {
        if (!Files.exists(path)) {
            return def;
        }
        try {
            return readJsonRoot(path);
        } catch (Exception e) {
            backupJsonFile(path);
            return def;
        }
    }

    @Nonnull
    private JsonObject readJsonRoot(@Nonnull Path path) throws Exception {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject obj = gson.fromJson(content, JsonObject.class);
        if (obj == null) {
            throw new IllegalStateException("Config parsed to null: " + path.getFileName());
        }
        return obj;
    }

    private void writeSplitConfigs() throws Exception {
        JsonObject full = root != null ? root : buildDefaultConfig();
        JsonObject main = full.deepCopy();
        main.remove("economy");
        main.remove("playtime");
        main.remove("rankup");
        main.remove("scoreboard");
        stripChatSections(main);

        JsonObject economy = buildDefaultEconomyRoot();
        if (full.has("economy")) {
            economy.add("economy", full.get("economy"));
        }
        JsonObject rewards = buildDefaultRewardsRoot();
        if (full.has("playtime")) {
            rewards.add("playtime", full.get("playtime"));
        }
        if (full.has("rankup")) {
            rewards.add("rankup", full.get("rankup"));
        }
        JsonObject chat = buildDefaultChatRoot();
        fillChatRoot(chat, full);
        JsonObject scoreboard = buildDefaultScoreboardRoot();
        if (full.has("scoreboard")) {
            scoreboard.add("scoreboard", full.get("scoreboard"));
        }

        writeJson(configPath, main);
        writeJson(economyPath, economy);
        writeJson(rewardsPath, rewards);
        writeJson(chatPath, chat);
        writeJson(scoreboardPath, scoreboard);
        try {
            if (Files.exists(rankupPath)) {
                Path backupPath = rankupPath.resolveSibling("rankupConfig.json.migrated.bak");
                Files.copy(rankupPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.deleteIfExists(rankupPath)) {
                Log.info("Removed deprecated rankupConfig.json after migration to rewardsConfig.json.");
            }
        } catch (Exception e) {
            Log.warn("Failed to remove deprecated rankupConfig.json: " + e.getMessage());
        }
    }

    private void writeJson(@Nonnull Path path, @Nonnull JsonObject data) throws Exception {
        AtomicFileUtil.writeStringAtomically(path, gson.toJson(data), StandardCharsets.UTF_8);
    }

    @Nonnull
    private JsonObject buildDefaultMainRoot() {
        JsonObject root = buildDefaultConfig();
        root.remove("economy");
        root.remove("playtime");
        root.remove("rankup");
        root.remove("scoreboard");
        stripChatSections(root);
        return root;
    }

    @Nonnull
    private JsonObject buildDefaultEconomyRoot() {
        JsonObject root = new JsonObject();
        JsonObject defaults = buildDefaultConfig();
        if (defaults.has("economy")) {
            root.add("economy", defaults.get("economy"));
        }
        return root;
    }

    @Nonnull
    private JsonObject buildDefaultRankupRoot() {
        JsonObject root = new JsonObject();
        JsonObject defaults = buildDefaultConfig();
        if (defaults.has("rankup")) {
            root.add("rankup", defaults.get("rankup"));
        }
        return root;
    }

    @Nonnull
    private JsonObject buildDefaultRewardsRoot() {
        JsonObject root = new JsonObject();
        JsonObject defaults = buildDefaultConfig();
        if (defaults.has("playtime")) {
            root.add("playtime", defaults.get("playtime"));
        }
        if (defaults.has("rankup")) {
            root.add("rankup", defaults.get("rankup"));
        }
        return root;
    }

    @Nonnull
    private JsonObject buildDefaultChatRoot() {
        JsonObject root = new JsonObject();
        JsonObject defaults = buildDefaultConfig();
        fillChatRoot(root, defaults);
        return root;
    }

    @Nonnull
    private JsonObject buildDefaultScoreboardRoot() {
        JsonObject root = new JsonObject();
        JsonObject defaults = buildDefaultConfig();
        if (defaults.has("scoreboard")) {
            root.add("scoreboard", defaults.get("scoreboard"));
        }
        return root;
    }

    private void stripChatSections(@Nonnull JsonObject root) {
        root.remove("chat");
        root.remove("welcomeMessage");
        root.remove("joinAndQuit");
        root.remove("deathMessages");
        root.remove("motd");
        root.remove("rules");
        root.remove("autoBroadcast");
    }

    private void fillChatRoot(@Nonnull JsonObject target, @Nonnull JsonObject source) {
        copySection(target, source, "chat");
        copySection(target, source, "welcomeMessage");
        copySection(target, source, "joinAndQuit");
        copySection(target, source, "deathMessages");
        copySection(target, source, "motd");
        copySection(target, source, "rules");
        copySection(target, source, "autoBroadcast");
    }

    private void copySection(@Nonnull JsonObject target, @Nonnull JsonObject source, @Nonnull String key) {
        if (source.has(key)) {
            target.add(key, source.get(key));
        }
    }

    private void backupBadConfig() {
        try {
            if (!Files.exists(configPath)) return;
            Path backupPath = configPath.resolveSibling(configPath.getFileName().toString() + ".bak");
            Files.move(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            Log.warn("Backed up invalid config.json to: " + backupPath);
        } catch (Exception e) {
            Log.warn("Failed to backup invalid config.json: " + e.getMessage());
        }
    }

    private boolean shouldCreatePreMigrationBackup(@Nonnull JsonObject combinedRoot) {
        if (needsSplitMigration) {
            return true;
        }
        if (Files.exists(rankupPath)) {
            return true;
        }
        if (hasLegacyFeatureFlags(combinedRoot)
                || combinedRoot.has("groupPriorities")
                || combinedRoot.has("placeholders")) {
            return true;
        }
        JsonObject rankup = getObjectOrNull(combinedRoot, "rankup");
        return rankup != null && (rankup.has("ranks") || rankup.has("enabled"));
    }

    private boolean hasLegacyFeatureFlags(@Nonnull JsonObject combinedRoot) {
        JsonObject features = getObjectOrNull(combinedRoot, "features");
        if (features == null) {
            return false;
        }
        return features.has("homes")
                || features.has("warps")
                || features.has("kits")
                || features.has("near")
                || features.has("motd")
                || features.has("rtp")
                || features.has("spawn")
                || features.has("tpa")
                || features.has("rules");
    }

    private void backupConfigFilesBeforeMigration() {
        try {
            Path dataFolder = configPath.getParent();
            if (dataFolder == null) {
                return;
            }
            String stamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path backupDir = dataFolder.resolve("backups").resolve("pre-migration-" + stamp);
            Files.createDirectories(backupDir);

            List<Path> paths = List.of(
                    configPath,
                    economyPath,
                    rewardsPath,
                    rankupPath,
                    chatPath,
                    scoreboardPath,
                    dataFolder.resolve("commands.yml")
            );
            int copied = 0;
            for (Path source : paths) {
                if (!Files.exists(source) || !Files.isRegularFile(source)) {
                    continue;
                }
                Path destination = backupDir.resolve(source.getFileName());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
            if (copied > 0) {
                Log.info("Created pre-migration config backup at: " + backupDir);
            } else {
                Log.warn("Pre-migration backup was requested but no config files were copied.");
            }
        } catch (Exception e) {
            Log.warn("Failed to create pre-migration config backup: " + e.getMessage());
        }
    }

    private void backupLegacyConfig() {
        try {
            if (!Files.exists(configPath)) return;
            String stamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path backupPath = configPath.resolveSibling("config.json.bak-" + stamp);
            Files.move(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            Log.info("Backed up legacy config.json to: " + backupPath);
        } catch (Exception e) {
            Log.warn("Failed to backup legacy config.json: " + e.getMessage());
        }
    }

    private void backupJsonFile(@Nonnull Path path) {
        try {
            if (!Files.exists(path)) return;
            Path backupPath = path.resolveSibling(path.getFileName().toString() + ".bak");
            Files.move(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
            Log.warn("Backed up invalid config file to: " + backupPath);
        } catch (Exception e) {
            Log.warn("Failed to backup invalid config file: " + e.getMessage());
        }
    }

    private void setVersionFromPlugin() {
        if (root == null) return;
        root.addProperty("version", PluginInfoUtil.getVersion());
        save();
    }

    private JsonObject obj(@Nonnull JsonObject parent, @Nonnull String key) {
        JsonElement el = parent.get(key);
        if (el != null && el.isJsonObject()) return el.getAsJsonObject();
        JsonObject created = new JsonObject();
        parent.add(key, created);
        return created;
    }

    private boolean hasSectionFlag(@Nonnull JsonObject parent, @Nonnull String section, @Nonnull String key) {
        JsonElement el = parent.get(section);
        if (el == null || !el.isJsonObject()) {
            return false;
        }
        return el.getAsJsonObject().has(key);
    }

    @Nullable
    private JsonObject getObjectOrNull(@Nonnull JsonObject parent, @Nonnull String key) {
        JsonElement el = parent.get(key);
        if (el != null && el.isJsonObject()) {
            return el.getAsJsonObject();
        }
        return null;
    }

    private boolean cleanupConfig(@Nonnull JsonObject root) {
        boolean changed = false;
        if (root.has("placeholders")) {
            root.remove("placeholders");
            changed = true;
        }
        JsonObject chat = getObjectOrNull(root, "chat");
        JsonObject legacyPriorities = getObjectOrNull(root, "groupPriorities");
        if (legacyPriorities != null) {
            if (chat != null && !chat.has("groupPriorities") && !legacyPriorities.entrySet().isEmpty()) {
                chat.add("groupPriorities", legacyPriorities.deepCopy());
                changed = true;
            }
            root.remove("groupPriorities");
            changed = true;
        }
        JsonObject features = getObjectOrNull(root, "features");
        if (features != null) {
            changed |= migrateFeatureFlag(features, root, "homes");
            changed |= migrateFeatureFlag(features, root, "warps");
            changed |= migrateFeatureFlag(features, root, "kits");
            changed |= migrateFeatureFlag(features, root, "near");
            changed |= migrateFeatureFlag(features, root, "motd");
            changed |= migrateFeatureFlag(features, root, "rtp");
            changed |= migrateFeatureFlag(features, root, "spawn");
            changed |= migrateFeatureFlag(features, root, "tpa");
            changed |= migrateFeatureFlag(features, root, "rules");
            if (features.entrySet().isEmpty()) {
                root.remove("features");
                changed = true;
            }
        }
        JsonObject rankup = getObjectOrNull(root, "rankup");
        if (rankup != null && rankup.has("enabled")) {
            rankup.remove("enabled");
            changed = true;
        }
        return pruneEmptyObjects(root) || changed;
    }

    private boolean migrateFeatureFlag(@Nonnull JsonObject features, @Nonnull JsonObject root, @Nonnull String key) {
        if (!features.has(key)) {
            return false;
        }
        boolean legacyValue = bool(features, key, true);
        JsonObject section = getObjectOrNull(root, key);
        if (section != null && !section.has("enabled")) {
            section.addProperty("enabled", legacyValue);
        }
        features.remove(key);
        return true;
    }

    private boolean pruneEmptyObjects(@Nonnull JsonObject root) {
        boolean changed = false;
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                JsonObject obj = value.getAsJsonObject();
                if (pruneEmptyObjects(obj)) {
                    changed = true;
                }
                if (obj.entrySet().isEmpty()) {
                    toRemove.add(entry.getKey());
                }
            }
        }
        if (!toRemove.isEmpty()) {
            for (String key : toRemove) {
                root.remove(key);
            }
            changed = true;
        }
        return changed;
    }

    @Nonnull
    private static JsonObject toPriorityObject(@Nonnull Map<String, Integer> priorities) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Integer> entry : priorities.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return obj;
    }

    @Nonnull
    private JsonObject buildDefaultCommandRulesObject() {
        JsonObject root = new JsonObject();
        JsonObject commands = new JsonObject();
        for (String key : buildDefaultCooldowns().keySet()) {
            commands.add(key, toCommandRuleObject(defaultCommandRule(key)));
        }
        root.add("commands", commands);
        return root;
    }

    private void loadCommandRules() {
        commandRules.clear();
        for (String key : buildDefaultCooldowns().keySet()) {
            commandRules.put(key, defaultCommandRule(key));
        }

        JsonObject rulesRoot = obj(root, "commandRules");
        JsonObject commands = obj(rulesRoot, "commands");
        List<String> existingKeys = new ArrayList<>(commands.keySet());
        for (String rawKey : existingKeys) {
            JsonElement value = commands.get(rawKey);
            if (value == null || !value.isJsonObject()) {
                continue;
            }
            String key = normalizeCommandRuleKey(rawKey);
            if (key.isEmpty()) {
                continue;
            }
            commandRules.put(key, readCommandRule(key, value.getAsJsonObject()));
        }
        commands.entrySet().clear();
        for (Map.Entry<String, CommandRuleModel> entry : commandRules.entrySet()) {
            commands.add(entry.getKey(), toCommandRuleObject(entry.getValue()));
        }
    }

    @Nonnull
    private CommandRuleModel defaultCommandRule(@Nonnull String key) {
        String normalized = normalizeCommandRuleKey(key);
        CommandRuleModel rule = new CommandRuleModel();
        rule.setEnabled(true);
        rule.setCooldownSeconds(commandCooldowns.getOrDefault(normalized,
                buildDefaultCooldowns().getOrDefault(normalized, DEFAULT_COMMAND_COOLDOWN_SECONDS)));
        rule.setWarmupSeconds(defaultWarmupSeconds(normalized));
        rule.setCancelWarmupOnMove(true);
        rule.setPrice(0L);
        rule.setBlacklistedWorlds(List.of());
        rule.setReductions(List.of(
                reduction("hyessentialsx." + normalized + ".cooldown.reduction.50", 50, 0, 0),
                reduction("hyessentialsx." + normalized + ".warmup.reduction.50", 0, 50, 0),
                reduction("hyessentialsx." + normalized + ".price.reduction.50", 0, 0, 50)
        ));
        return rule;
    }

    @Nonnull
    private CommandRuleModel readCommandRule(@Nonnull String key, @Nonnull JsonObject obj) {
        CommandRuleModel def = defaultCommandRule(key);
        CommandRuleModel rule = new CommandRuleModel();
        rule.setEnabled(bool(obj, "enabled", def.isEnabled()));
        rule.setCooldownSeconds(intVal(obj, "cooldownSeconds", def.getCooldownSeconds()));
        rule.setWarmupSeconds(intVal(obj, "warmupSeconds", def.getWarmupSeconds()));
        rule.setCancelWarmupOnMove(bool(obj, "cancelWarmupOnMove", def.isCancelWarmupOnMove()));
        rule.setPrice(Math.max(0L, moneyVal(obj, "price", def.getPrice())));
        rule.setBlacklistedWorlds(list(obj, "blacklistedWorlds", def.getBlacklistedWorlds()));
        rule.setReductions(readCommandRuleReductions(obj, def.getReductions()));
        return rule;
    }

    @Nonnull
    private List<CommandRuleModel.Reduction> readCommandRuleReductions(@Nonnull JsonObject obj,
                                                                        @Nonnull List<CommandRuleModel.Reduction> def) {
        JsonElement element = obj.get("reductions");
        if (element == null || !element.isJsonArray()) {
            return def;
        }
        List<CommandRuleModel.Reduction> out = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry == null || !entry.isJsonObject()) {
                continue;
            }
            JsonObject reductionObj = entry.getAsJsonObject();
            String permission = str(reductionObj, "permission", "");
            if (permission.isBlank()) {
                continue;
            }
            CommandRuleModel.Reduction reduction = new CommandRuleModel.Reduction();
            reduction.setPermission(permission);
            reduction.setCooldownReductionPercent(intVal(reductionObj, "cooldownReductionPercent", 0));
            reduction.setWarmupReductionPercent(intVal(reductionObj, "warmupReductionPercent", 0));
            reduction.setPriceReductionPercent(intVal(reductionObj, "priceReductionPercent", 0));
            if (reductionObj.has("cooldownSeconds")) {
                reduction.setCooldownSeconds(intVal(reductionObj, "cooldownSeconds", -1));
            }
            if (reductionObj.has("warmupSeconds")) {
                reduction.setWarmupSeconds(intVal(reductionObj, "warmupSeconds", -1));
            }
            if (reductionObj.has("price")) {
                reduction.setPrice(moneyVal(reductionObj, "price", -1L));
            }
            out.add(reduction);
        }
        return out;
    }

    @Nonnull
    private JsonObject toCommandRulesObject() {
        JsonObject root = new JsonObject();
        JsonObject commands = new JsonObject();
        for (Map.Entry<String, CommandRuleModel> entry : commandRules.entrySet()) {
            commands.add(entry.getKey(), toCommandRuleObject(entry.getValue()));
        }
        root.add("commands", commands);
        return root;
    }

    @Nonnull
    private JsonObject toCommandRuleObject(@Nonnull CommandRuleModel rule) {
        JsonObject obj = new JsonObject();
        obj.addProperty("enabled", rule.isEnabled());
        obj.addProperty("cooldownSeconds", rule.getCooldownSeconds());
        obj.addProperty("warmupSeconds", rule.getWarmupSeconds());
        obj.addProperty("cancelWarmupOnMove", rule.isCancelWarmupOnMove());
        obj.addProperty("price", formatMoneyConfig(rule.getPrice()));
        obj.add("blacklistedWorlds", toArray(rule.getBlacklistedWorlds()));
        JsonArray reductions = new JsonArray();
        for (CommandRuleModel.Reduction reduction : rule.getReductions()) {
            JsonObject reductionObj = new JsonObject();
            reductionObj.addProperty("permission", reduction.getPermission());
            reductionObj.addProperty("cooldownReductionPercent", reduction.getCooldownReductionPercent());
            reductionObj.addProperty("warmupReductionPercent", reduction.getWarmupReductionPercent());
            reductionObj.addProperty("priceReductionPercent", reduction.getPriceReductionPercent());
            if (reduction.getCooldownSeconds() >= 0) {
                reductionObj.addProperty("cooldownSeconds", reduction.getCooldownSeconds());
            }
            if (reduction.getWarmupSeconds() >= 0) {
                reductionObj.addProperty("warmupSeconds", reduction.getWarmupSeconds());
            }
            if (reduction.getPrice() >= 0L) {
                reductionObj.addProperty("price", formatMoneyConfig(reduction.getPrice()));
            }
            reductions.add(reductionObj);
        }
        obj.add("reductions", reductions);
        return obj;
    }

    @Nonnull
    private CommandRuleModel copyCommandRule(@Nonnull CommandRuleModel source) {
        CommandRuleModel copy = new CommandRuleModel();
        copy.setEnabled(source.isEnabled());
        copy.setCooldownSeconds(source.getCooldownSeconds());
        copy.setWarmupSeconds(source.getWarmupSeconds());
        copy.setCancelWarmupOnMove(source.isCancelWarmupOnMove());
        copy.setPrice(source.getPrice());
        copy.setBlacklistedWorlds(source.getBlacklistedWorlds());
        List<CommandRuleModel.Reduction> reductions = new ArrayList<>();
        for (CommandRuleModel.Reduction sourceReduction : source.getReductions()) {
            CommandRuleModel.Reduction reduction = new CommandRuleModel.Reduction();
            reduction.setPermission(sourceReduction.getPermission());
            reduction.setCooldownReductionPercent(sourceReduction.getCooldownReductionPercent());
            reduction.setWarmupReductionPercent(sourceReduction.getWarmupReductionPercent());
            reduction.setPriceReductionPercent(sourceReduction.getPriceReductionPercent());
            reduction.setCooldownSeconds(sourceReduction.getCooldownSeconds());
            reduction.setWarmupSeconds(sourceReduction.getWarmupSeconds());
            reduction.setPrice(sourceReduction.getPrice());
            reductions.add(reduction);
        }
        copy.setReductions(reductions);
        return copy;
    }

    private void syncLegacyCommandRuleFields(@Nonnull String key, @Nonnull CommandRuleModel rule) {
        String normalized = normalizeCommandRuleKey(key);
        commandCooldowns.put(normalized, rule.getCooldownSeconds());
        switch (normalized) {
            case CooldownKeys.HOME -> homeWarmupSeconds = rule.getWarmupSeconds();
            case CooldownKeys.WARP -> warpWarmupSeconds = rule.getWarmupSeconds();
            case CooldownKeys.BACK -> backWarmupSeconds = rule.getWarmupSeconds();
            case CooldownKeys.SPAWN -> spawnWarmupSeconds = rule.getWarmupSeconds();
            case CooldownKeys.RTP -> rtpWarmupSeconds = rule.getWarmupSeconds();
            case CooldownKeys.TPA, CooldownKeys.TPAHERE, CooldownKeys.TPAHEREALL ->
                    tpaWarmupSeconds = rule.getWarmupSeconds();
            default -> {
            }
        }
    }

    private int defaultWarmupSeconds(@Nonnull String key) {
        return switch (normalizeCommandRuleKey(key)) {
            case CooldownKeys.HOME -> homeWarmupSeconds;
            case CooldownKeys.WARP -> warpWarmupSeconds;
            case CooldownKeys.BACK -> backWarmupSeconds;
            case CooldownKeys.SPAWN -> spawnWarmupSeconds;
            case CooldownKeys.RTP -> rtpWarmupSeconds;
            case CooldownKeys.TPA, CooldownKeys.TPAHERE, CooldownKeys.TPAHEREALL -> tpaWarmupSeconds;
            default -> 0;
        };
    }

    @Nonnull
    private static CommandRuleModel.Reduction reduction(@Nonnull String permission,
                                                        int cooldownReductionPercent,
                                                        int warmupReductionPercent,
                                                        int priceReductionPercent) {
        CommandRuleModel.Reduction reduction = new CommandRuleModel.Reduction();
        reduction.setPermission(permission);
        reduction.setCooldownReductionPercent(cooldownReductionPercent);
        reduction.setWarmupReductionPercent(warmupReductionPercent);
        reduction.setPriceReductionPercent(priceReductionPercent);
        return reduction;
    }

    @Nonnull
    private static String normalizeCommandRuleKey(@Nonnull String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    @Nonnull
    private static Map<String, Integer> buildDefaultCooldowns() {
        Map<String, Integer> defaults = new HashMap<>();
        defaults.put(CooldownKeys.BACK, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.HEAL, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.REPAIR, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.MSG, 0);
        defaults.put(CooldownKeys.REPLY, 0);
        defaults.put(CooldownKeys.MAIL, 0);
        defaults.put(CooldownKeys.IGNORE, 0);
        defaults.put(CooldownKeys.PAY, 0);
        defaults.put(CooldownKeys.BALANCE, 0);
        defaults.put(CooldownKeys.BALTOP, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.RULES, 0);
        defaults.put(CooldownKeys.MOTD, 0);
        defaults.put(CooldownKeys.DISCORD, 0);
        defaults.put(CooldownKeys.LIST, 0);
        defaults.put(CooldownKeys.PLAYTIME, 0);
        defaults.put(CooldownKeys.STATS, 0);
        defaults.put(CooldownKeys.LEADERBOARD, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.RANKUP, 0);
        defaults.put(CooldownKeys.SETHOME, 0);
        defaults.put(CooldownKeys.DELHOME, 0);
        defaults.put(CooldownKeys.FLY, 0);
        defaults.put(CooldownKeys.GOD, 0);
        defaults.put(CooldownKeys.FREECAM, 0);
        defaults.put(CooldownKeys.VANISH, 0);
        defaults.put(CooldownKeys.TOP, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.BOTTOM, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.THRU, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.TPHERE, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.AFK, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.NEAR, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.TPA, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.TPAHERE, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.TPAHEREALL, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.WARP, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.SPAWN, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.RTP, 600);
        defaults.put(CooldownKeys.JUMPTO, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.HOME, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        return defaults;
    }

    private int readCooldown(@Nonnull JsonObject section,
                             @Nonnull String key,
                             @Nullable JsonObject legacyCooldowns,
                             @Nonnull String legacyKey,
                             int def) {
        JsonElement el = section.get(key);
        if (el != null && el.isJsonPrimitive()) {
            return Math.max(0, el.getAsInt());
        }
        if (legacyCooldowns != null) {
            return Math.max(0, intVal(legacyCooldowns, legacyKey, def));
        }
        return Math.max(0, def);
    }

    private boolean bool(@Nonnull JsonObject obj, @Nonnull String key, boolean def) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsBoolean() : def;
    }

    private int intVal(@Nonnull JsonObject obj, @Nonnull String key, int def) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsInt() : def;
    }

    private long longVal(@Nonnull JsonObject obj, @Nonnull String key, long def) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsLong() : def;
    }

    private long moneyVal(@Nonnull JsonObject obj, @Nonnull String key, long def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) {
            return def;
        }
        try {
            BigDecimal major = new BigDecimal(el.getAsString().trim().replace(",", ""));
            if (major.signum() < 0) {
                return 0L;
            }
            int scale = getEconomyDecimalPlaces();
            return major.setScale(scale, RoundingMode.DOWN).movePointRight(scale).longValueExact();
        } catch (Exception ignored) {
            return def;
        }
    }

    @Nonnull
    private String formatMoneyConfig(long amount) {
        int scale = getEconomyDecimalPlaces();
        long clamped = Math.max(0L, amount);
        if (scale <= 0) {
            return String.valueOf(clamped);
        }
        return BigDecimal.valueOf(clamped, scale).setScale(scale, RoundingMode.DOWN).toPlainString();
    }

    private double dbl(@Nonnull JsonObject obj, @Nonnull String key, double def) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsDouble() : def;
    }

    @Nonnull
    private String str(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull String def) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : def;
    }

    @Nonnull
    private List<String> list(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull List<String> def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonArray()) return def;
        List<String> out = new ArrayList<>();
        for (JsonElement entry : el.getAsJsonArray()) {
            if (entry.isJsonPrimitive()) out.add(entry.getAsString());
        }
        return out.isEmpty() ? def : out;
    }

    @Nonnull
    private List<String> listAllowEmpty(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull List<String> def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonArray()) return def;
        List<String> out = new ArrayList<>();
        for (JsonElement entry : el.getAsJsonArray()) {
            if (entry.isJsonPrimitive()) out.add(entry.getAsString());
        }
        return out;
    }

    @Nonnull
    private List<Integer> readIntList(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull List<Integer> def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonArray()) return def;
        List<Integer> out = new ArrayList<>();
        for (JsonElement entry : el.getAsJsonArray()) {
            if (entry == null || !entry.isJsonPrimitive()) continue;
            try {
                out.add(entry.getAsInt());
            } catch (Exception ignored) {
            }
        }
        return out.isEmpty() ? def : out;
    }

    @Nonnull
    private List<Integer> sanitizePositiveInts(@Nonnull List<Integer> values) {
        List<Integer> out = new ArrayList<>();
        for (Integer value : values) {
            if (value == null || value <= 0) continue;
            if (!out.contains(value)) {
                out.add(value);
            }
        }
        out.sort(Collections.reverseOrder());
        return out.isEmpty() ? new ArrayList<>(List.of(60, 10)) : out;
    }

    @Nonnull
    private List<String> sanitizeStringList(@Nonnull List<String> values) {
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value == null) continue;
            String cleaned = value.trim().toLowerCase(Locale.ROOT);
            if (cleaned.isBlank() || out.contains(cleaned)) continue;
            out.add(cleaned);
        }
        return out;
    }

    @Nonnull
    private List<String> readSpawnProtectionWorlds(@Nonnull JsonObject spawnProtection) {
        if (!spawnProtection.has("worlds") || !spawnProtection.get("worlds").isJsonArray()) {
            return new ArrayList<>(List.of(resolveServerDefaultWorldName()));
        }
        List<String> out = new ArrayList<>();
        for (String raw : listAllowEmpty(spawnProtection, "worlds", List.of())) {
            if (raw == null) continue;
            String worldName = raw.trim().toLowerCase(Locale.ROOT);
            if (worldName.isBlank()) continue;
            if (!out.contains(worldName)) {
                out.add(worldName);
            }
        }
        return out;
    }

    @Nonnull
    private String resolveServerDefaultWorldName() {
        try {
            HytaleServer server = HytaleServer.get();
            if (server != null) {
                Object config = server.getConfig();
                Object defaults = tryInvokeNoArgs(config, "getDefaults");
                Object world = tryInvokeNoArgs(defaults, "getWorld");
                if (world instanceof String worldName && !worldName.isBlank()) {
                    return worldName.trim();
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Map<String, World> worlds = Universe.get().getWorlds();
            if (worlds != null && !worlds.isEmpty()) {
                for (World world : worlds.values()) {
                    if (world != null && world.getName() != null && !world.getName().isBlank()) {
                        return world.getName();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return DEFAULT_SPAWN_PROTECTION_WORLDS.get(0);
    }

    @Nullable
    private Object tryInvokeNoArgs(@Nullable Object target, @Nonnull String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterCount() != 0) {
                return null;
            }
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private SpawnModel readSpawnModel(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        String world = str(obj, "world", "").trim();
        if (world.isBlank()) {
            return null;
        }
        return new SpawnModel(
                world,
                dbl(obj, "x", 0.0),
                dbl(obj, "y", 0.0),
                dbl(obj, "z", 0.0),
                (float) dbl(obj, "yaw", 0.0),
                (float) dbl(obj, "pitch", 0.0)
        );
    }

    private boolean readSpawnRouting(@Nonnull JsonObject spawn) {
        boolean changed = false;
        JsonObject routing = obj(spawn, "routing");
        spawnRouteSelectionMode = normalizeSpawnSelectionMode(str(routing, "selectionMode", spawnRouteSelectionMode));
        firstJoinSpawnName = normalizeOptionalSpawnRouteName(str(routing, "firstJoinSpawn", firstJoinSpawnName));
        deathSpawnName = normalizeOptionalSpawnRouteName(str(routing, "deathSpawn", deathSpawnName));

        JsonObject orders = obj(routing, "orders");
        spawnRouteCommandOrder = normalizeSpawnRouteOrder(list(orders, "command", spawnRouteCommandOrder), DEFAULT_SPAWN_COMMAND_ROUTE);
        spawnRouteFirstJoinOrder = normalizeSpawnRouteOrder(list(orders, "firstJoin", spawnRouteFirstJoinOrder), DEFAULT_SPAWN_FIRST_JOIN_ROUTE);
        spawnRouteJoinOrder = normalizeSpawnRouteOrder(listAllowEmpty(orders, "join", spawnRouteJoinOrder), DEFAULT_SPAWN_JOIN_ROUTE);
        List<String> respawnDefault = spawnRespawnPriority.isEmpty() ? DEFAULT_SPAWN_RESPAWN_ROUTE : spawnRespawnPriority;
        spawnRouteRespawnOrder = normalizeSpawnRouteOrder(list(orders, "respawn", respawnDefault), DEFAULT_SPAWN_RESPAWN_ROUTE);
        spawnRouteDeathOrder = normalizeSpawnRouteOrder(list(orders, "death", spawnRouteDeathOrder), DEFAULT_SPAWN_DEATH_ROUTE);

        worldSpawnRoutes.clear();
        JsonObject worlds = getObjectOrNull(routing, "worlds");
        if (worlds != null) {
            for (Map.Entry<String, JsonElement> entry : worlds.entrySet()) {
                String key = normalizeRouteKey(entry.getKey());
                List<String> names = normalizeSpawnNameList(readRouteSpawnList(entry.getValue()));
                if (key != null && !names.isEmpty()) {
                    worldSpawnRoutes.put(key, names);
                }
            }
        }

        groupSpawnRoutes.clear();
        JsonElement groupsElement = routing.get("groups");
        if (groupsElement != null && groupsElement.isJsonArray()) {
            for (JsonElement element : groupsElement.getAsJsonArray()) {
                SpawnRouteGroupModel group = readSpawnRouteGroup(element);
                if (group != null) {
                    groupSpawnRoutes.put(group.getId(), group);
                }
            }
        } else if (groupsElement != null && groupsElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : groupsElement.getAsJsonObject().entrySet()) {
                SpawnRouteGroupModel group = readSpawnRouteGroup(entry.getValue());
                if (group == null) continue;
                if (group.getId().isBlank()) {
                    group.setId(normalizeRouteKey(entry.getKey()));
                }
                if (!group.getId().isBlank()) {
                    groupSpawnRoutes.put(group.getId(), group);
                }
            }
        }
        return changed;
    }

    @Nullable
    private String normalizeSpawnName(@Nullable String rawName) {
        if (rawName == null) return null;
        String value = rawName.trim().toLowerCase(Locale.ROOT);
        if (!NAMED_SPAWN_PATTERN.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    @Nonnull
    private String normalizeOptionalSpawnRouteName(@Nullable String rawName) {
        String key = normalizeSpawnName(rawName);
        return key == null ? "" : key;
    }

    @Nullable
    private String normalizeRouteKey(@Nullable String rawName) {
        if (rawName == null) return null;
        String value = rawName.trim().toLowerCase(Locale.ROOT);
        if (!NAMED_SPAWN_PATTERN.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    @Nonnull
    private String normalizeSpawnSelectionMode(@Nullable String rawMode) {
        if (rawMode == null) return "first";
        String mode = rawMode.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "random", "nearest" -> mode;
            default -> "first";
        };
    }

    @Nonnull
    private String normalizeSpawnRouteName(@Nullable String rawRoute) {
        if (rawRoute == null) return "command";
        String route = rawRoute.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return switch (route) {
            case "first", "firstjoin", "firstlogin" -> "firstjoin";
            case "login", "normaljoin", "join" -> "join";
            case "respawn" -> "respawn";
            case "death" -> "death";
            default -> "command";
        };
    }

    @Nonnull
    private List<String> defaultSpawnRouteOrder(@Nonnull String route) {
        return switch (normalizeSpawnRouteName(route)) {
            case "firstjoin" -> DEFAULT_SPAWN_FIRST_JOIN_ROUTE;
            case "join" -> DEFAULT_SPAWN_JOIN_ROUTE;
            case "respawn" -> DEFAULT_SPAWN_RESPAWN_ROUTE;
            case "death" -> DEFAULT_SPAWN_DEATH_ROUTE;
            default -> DEFAULT_SPAWN_COMMAND_ROUTE;
        };
    }

    @Nonnull
    private List<String> normalizeSpawnRouteOrder(@Nonnull List<String> input, @Nonnull List<String> fallback) {
        if (input.isEmpty()) return fallback;
        List<String> out = new ArrayList<>();
        for (String raw : input) {
            if (raw == null) continue;
            String key = raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            if (key.isBlank()) continue;
            if ("spawn".equals(key) || "set".equals(key)) key = "setspawn";
            if ("worlddefault".equals(key) || "defaultworld".equals(key) || "worldfallback".equals(key)) key = "worlddefault";
            if ("first".equals(key) || "firstjoinspawn".equals(key)) key = "firstjoin";
            if (!List.of("bed", "firstjoin", "death", "group", "world", "permission", "main", "setspawn", "worlddefault").contains(key)) {
                continue;
            }
            if (!out.contains(key)) {
                out.add(key);
            }
        }
        return out.isEmpty() ? fallback : out;
    }

    @Nonnull
    private List<String> normalizeSpawnNameList(@Nonnull List<String> input) {
        List<String> out = new ArrayList<>();
        for (String raw : input) {
            String key = normalizeSpawnName(raw);
            if (key != null && !out.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    @Nonnull
    private List<String> readRouteSpawnList(@Nullable JsonElement element) {
        if (element == null) return List.of();
        if (element.isJsonArray()) {
            List<String> out = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                if (item != null && item.isJsonPrimitive()) {
                    out.add(item.getAsString());
                }
            }
            return out;
        }
        if (element.isJsonPrimitive()) {
            return splitRouteNames(element.getAsString());
        }
        return List.of();
    }

    @Nonnull
    private List<String> splitRouteNames(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (!value.isBlank()) {
                out.add(value);
            }
        }
        return out;
    }

    @Nullable
    private SpawnRouteGroupModel readSpawnRouteGroup(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject obj = element.getAsJsonObject();
        String id = normalizeRouteKey(str(obj, "id", ""));
        String permission = str(obj, "permission", "").trim();
        List<String> spawns = normalizeSpawnNameList(readRouteSpawnList(obj.get("spawns")));
        if (id == null || id.isBlank() || permission.isBlank() || spawns.isEmpty()) {
            return null;
        }
        return new SpawnRouteGroupModel(id, permission, intVal(obj, "priority", 0), spawns);
    }

    @Nonnull
    private SpawnModel copySpawnModel(@Nonnull SpawnModel spawn) {
        return new SpawnModel(
                spawn.getWorldName(),
                spawn.getX(),
                spawn.getY(),
                spawn.getZ(),
                spawn.getYaw(),
                spawn.getPitch()
        );
    }

    @Nonnull
    private SpawnRouteGroupModel copySpawnRouteGroup(@Nonnull SpawnRouteGroupModel group) {
        return new SpawnRouteGroupModel(
                group.getId(),
                group.getPermission(),
                group.getPriority(),
                group.getSpawns()
        );
    }

    @Nonnull
    private List<String> readStringList(@Nonnull JsonObject obj,
                                        @Nonnull String listKey,
                                        @Nonnull String legacyKey,
                                        @Nonnull List<String> def) {
        List<String> list = list(obj, listKey, def);
        if (!list.isEmpty() && list != def) return list;
        JsonElement legacy = obj.get(legacyKey);
        if (legacy != null && legacy.isJsonPrimitive()) {
            String value = legacy.getAsString();
            if (!value.isBlank()) return List.of(value);
        }
        return def;
    }

    @Nonnull
    private List<String> normalizeRespawnPriority(@Nonnull List<String> input) {
        if (input.isEmpty()) return DEFAULT_SPAWN_RESPAWN_PRIORITY;

        List<String> out = new ArrayList<>();
        for (String raw : input) {
            if (raw == null) continue;
            String key = raw.trim().toLowerCase(Locale.ROOT);
            if (key.isBlank()) continue;
            if ("spawn".equals(key) || "set".equals(key)) {
                key = "setspawn";
            }
            if (!"bed".equals(key) && !"setspawn".equals(key) && !"world".equals(key)) {
                continue;
            }
            if (!out.contains(key)) {
                out.add(key);
            }
        }
        return out.isEmpty() ? DEFAULT_SPAWN_RESPAWN_PRIORITY : out;
    }

    @Nonnull
    private List<String> normalizeCommandList(@Nonnull List<String> input) {
        if (input.isEmpty()) return DEFAULT_COMBAT_BLOCKED_COMMANDS;
        List<String> out = new ArrayList<>();
        for (String raw : input) {
            if (raw == null) continue;
            String value = raw.trim().toLowerCase(Locale.ROOT);
            while (value.startsWith("/")) {
                value = value.substring(1);
            }
            if (value.isBlank()) continue;
            if (!out.contains(value)) {
                out.add(value);
            }
        }
        return out.isEmpty() ? DEFAULT_COMBAT_BLOCKED_COMMANDS : out;
    }

    @Nonnull
    private String normalizeScoreboardAnchor(@Nullable String anchor) {
        if (anchor == null) {
            return "top_right";
        }
        String value = anchor.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return "top_right";
        }
        value = value.replace('-', '_');
        value = value.replace(' ', '_');
        return switch (value) {
            case "topright", "top_right" -> "top_right";
            case "topleft", "top_left" -> "top_left";
            case "bottomright", "bottom_right" -> "bottom_right";
            case "bottomleft", "bottom_left" -> "bottom_left";
            default -> "top_right";
        };
    }

    @Nonnull
    private String normalizeEconomyHudAnchor(@Nullable String anchor) {
        if (anchor == null) {
            return "bottom_right";
        }
        String value = anchor.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return "bottom_right";
        }
        value = value.replace('-', '_');
        value = value.replace(' ', '_');
        return switch (value) {
            case "topright", "top_right" -> "top_right";
            case "topleft", "top_left" -> "top_left";
            case "bottomleft", "bottom_left" -> "bottom_left";
            case "bottomright", "bottom_right" -> "bottom_right";
            default -> "bottom_right";
        };
    }

    @Nonnull
    private List<String> sanitizeScoreboardLines(@Nonnull List<String> input) {
        if (input.isEmpty()) {
            return defaultScoreboardLines();
        }
        List<String> lines = new ArrayList<>();
        for (String line : input) {
            if (line == null) continue;
            String cleaned = line.replace("\r", "");
            if (cleaned.length() > DEFAULT_SCOREBOARD_LINE_MAX_LENGTH) {
                cleaned = cleaned.substring(0, DEFAULT_SCOREBOARD_LINE_MAX_LENGTH);
            }
            lines.add(cleaned);
            if (lines.size() >= DEFAULT_SCOREBOARD_MAX_LINES) {
                break;
            }
        }
        return lines.isEmpty() ? defaultScoreboardLines() : lines;
    }

    @Nonnull
    private Map<String, String> readChatGroupFormats(@Nonnull JsonObject chat, @Nonnull Map<String, String> def) {
        JsonElement el = chat.get("groups");
        if (el == null) return def;
        Map<String, String> out = new LinkedHashMap<>();
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                String format = entry.getValue().getAsString().trim();
                if (!format.isBlank()) {
                    out.put(entry.getKey(), format);
                }
            }
        } else if (el.isJsonArray()) {
            for (JsonElement entry : el.getAsJsonArray()) {
                if (!entry.isJsonObject()) continue;
                JsonObject groupObj = entry.getAsJsonObject();
                String name = str(groupObj, "name", "").trim();
                if (name.isBlank()) {
                    name = str(groupObj, "group", "").trim();
                }
                if (name.isBlank()) {
                    name = str(groupObj, "permission", "").trim();
                }
                String format = str(groupObj, "format", "").trim();
                if (!name.isBlank() && !format.isBlank()) {
                    out.put(name, format);
                }
            }
        }
        return out.isEmpty() ? def : out;
    }

    @Nonnull
    private Map<String, Integer> readGroupPriorities(@Nonnull JsonObject obj, @Nonnull Map<String, Integer> def) {
        if (obj.entrySet().isEmpty()) return def;
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive()) {
                out.put(entry.getKey(), value.getAsInt());
            }
        }
        return out.isEmpty() ? def : out;
    }

    @Nonnull
    private Map<String, Long> readLongMap(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull Map<String, Long> def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonObject()) return def;
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : el.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive()) {
                try {
                    out.put(entry.getKey(), Math.max(0L, value.getAsLong()));
                } catch (Exception ignored) {
                }
            }
        }
        return out.isEmpty() ? def : out;
    }

    @Nonnull
    private Map<String, String> readStringMap(@Nonnull JsonObject obj,
                                              @Nonnull String key,
                                              @Nonnull Map<String, String> def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonObject()) return def;
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : el.getAsJsonObject().entrySet()) {
            if (entry.getValue() == null || !entry.getValue().isJsonPrimitive()) continue;
            String mapKey = entry.getKey() != null ? entry.getKey().trim() : "";
            if (mapKey.isBlank()) continue;
            String value = entry.getValue().getAsString().trim();
            if (value.isBlank()) continue;
            out.put(mapKey.toLowerCase(Locale.ROOT), value);
        }
        return out.isEmpty() ? def : out;
    }

    @Nonnull
    private Map<String, Long> normalizeKeyedMap(@Nonnull Map<String, Long> input) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            String trimmed = key.trim();
            if (trimmed.isBlank()) continue;
            out.put(trimmed.toLowerCase(), entry.getValue());
        }
        return out;
    }

    private boolean hasGroupFormat(@Nonnull Map<String, String> groups, @Nonnull String groupName) {
        for (String key : groups.keySet()) {
            if (key.equalsIgnoreCase(groupName)) return true;
        }
        return false;
    }

    @Nonnull
    private Map<String, String> migrateStockChatGroupFormats(@Nonnull Map<String, String> groups) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, String> legacy = legacyDefaultChatGroups();
        String modern = "{prefix}&f{player}{suffix}&7: &f{message}";
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            String groupName = entry.getKey();
            String format = entry.getValue();
            String legacyFormat = getIgnoreCase(legacy, groupName);
            if (legacyFormat != null && legacyFormat.equals(format)) {
                out.put(groupName, modern);
            } else {
                out.put(groupName, format);
            }
        }
        return out;
    }

    @Nullable
    private String getIgnoreCase(@Nonnull Map<String, String> values, @Nonnull String key) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Nonnull
    private JsonArray toArray(@Nonnull List<String> values) {
        JsonArray arr = new JsonArray();
        for (String value : values) arr.add(value);
        return arr;
    }

    @Nonnull
    private JsonArray toIntArray(@Nonnull List<Integer> values) {
        JsonArray arr = new JsonArray();
        for (Integer value : values) {
            if (value != null) {
                arr.add(value);
            }
        }
        return arr;
    }

    @Nonnull
    private JsonObject toLongMapObject(@Nonnull Map<String, Long> values) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return obj;
    }

    @Nonnull
    private JsonObject toMoneyMapObject(@Nonnull Map<String, Long> values) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            obj.addProperty(entry.getKey(), formatMoneyConfig(entry.getValue()));
        }
        return obj;
    }

    @Nonnull
    private Map<String, Long> readMoneyMap(@Nonnull JsonObject obj, @Nonnull String key, @Nonnull Map<String, Long> def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonObject()) return def;
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : el.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive()) {
                try {
                    BigDecimal major = new BigDecimal(value.getAsString().trim().replace(",", ""));
                    int scale = getEconomyDecimalPlaces();
                    out.put(entry.getKey(), Math.max(0L,
                            major.setScale(scale, RoundingMode.DOWN).movePointRight(scale).longValueExact()));
                } catch (Exception ignored) {
                }
            }
        }
        return out.isEmpty() ? def : out;
    }

    @Nonnull
    private JsonObject toSpawnMapObject(@Nonnull Map<String, SpawnModel> values) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, SpawnModel> entry : values.entrySet()) {
            SpawnModel spawn = entry.getValue();
            if (spawn == null || spawn.getWorldName().isBlank()) {
                continue;
            }
            JsonObject spawnObj = new JsonObject();
            spawnObj.addProperty("world", spawn.getWorldName());
            spawnObj.addProperty("x", spawn.getX());
            spawnObj.addProperty("y", spawn.getY());
            spawnObj.addProperty("z", spawn.getZ());
            spawnObj.addProperty("yaw", spawn.getYaw());
            spawnObj.addProperty("pitch", spawn.getPitch());
            obj.add(entry.getKey(), spawnObj);
        }
        return obj;
    }

    @Nonnull
    private JsonObject toSpawnRoutingObject() {
        JsonObject routing = new JsonObject();
        routing.addProperty("selectionMode", spawnRouteSelectionMode);
        routing.addProperty("firstJoinSpawn", firstJoinSpawnName);
        routing.addProperty("deathSpawn", deathSpawnName);

        JsonObject orders = new JsonObject();
        orders.add("command", toArray(spawnRouteCommandOrder));
        orders.add("firstJoin", toArray(spawnRouteFirstJoinOrder));
        orders.add("join", toArray(spawnRouteJoinOrder));
        orders.add("respawn", toArray(spawnRouteRespawnOrder));
        orders.add("death", toArray(spawnRouteDeathOrder));
        routing.add("orders", orders);

        JsonObject worlds = new JsonObject();
        for (Map.Entry<String, List<String>> entry : worldSpawnRoutes.entrySet()) {
            worlds.add(entry.getKey(), toArray(entry.getValue()));
        }
        routing.add("worlds", worlds);

        JsonArray groups = new JsonArray();
        for (SpawnRouteGroupModel group : getGroupSpawnRoutes()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", group.getId());
            obj.addProperty("permission", group.getPermission());
            obj.addProperty("priority", group.getPriority());
            obj.add("spawns", toArray(group.getSpawns()));
            groups.add(obj);
        }
        routing.add("groups", groups);
        return routing;
    }

    @Nonnull
    private JsonObject toStringMapObject(@Nonnull Map<String, String> values) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return obj;
    }

    @Nonnull
    private JsonArray toAnnouncementArray(@Nonnull List<AnnouncementPresetModel> presets) {
        JsonArray arr = new JsonArray();
        for (AnnouncementPresetModel preset : presets) {
            if (preset == null) continue;
            arr.add(toAnnouncementObject(sanitizeAnnouncementPreset(preset)));
        }
        return arr;
    }

    @Nonnull
    private JsonObject toAnnouncementObject(@Nonnull AnnouncementPresetModel preset) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", preset.getName());
        obj.addProperty("enabled", preset.isEnabled());
        obj.addProperty("permission", preset.getPermission());
        obj.add("chatMessages", toArray(preset.getChatMessages()));

        AnnouncementPresetModel.NotificationAction notification = preset.getNotification();
        if (notification != null && (!notification.getTitle().isBlank() || !notification.getMessage().isBlank())) {
            JsonObject notificationObj = new JsonObject();
            notificationObj.addProperty("title", notification.getTitle());
            notificationObj.addProperty("message", notification.getMessage());
            notificationObj.addProperty("icon", notification.getIcon());
            notificationObj.addProperty("style", notification.getStyle());
            obj.add("notification", notificationObj);
        }

        AnnouncementPresetModel.TitleAction title = preset.getTitle();
        if (title != null && (!title.getPrimary().isBlank() || !title.getSecondary().isBlank())) {
            JsonObject titleObj = new JsonObject();
            titleObj.addProperty("primary", title.getPrimary());
            titleObj.addProperty("secondary", title.getSecondary());
            titleObj.addProperty("major", title.isMajor());
            obj.add("title", titleObj);
        }

        AnnouncementPresetModel.SoundAction sound = preset.getSound();
        if (sound != null && sound.getSoundEventIndex() >= 0) {
            JsonObject soundObj = new JsonObject();
            soundObj.addProperty("soundEventIndex", sound.getSoundEventIndex());
            soundObj.addProperty("category", sound.getCategory());
            soundObj.addProperty("volume", sound.getVolume());
            soundObj.addProperty("pitch", sound.getPitch());
            obj.add("sound", soundObj);
        }

        AnnouncementPresetModel.ParticleAction particle = preset.getParticle();
        if (particle != null && !particle.getParticleSystemId().isBlank()) {
            JsonObject particleObj = new JsonObject();
            particleObj.addProperty("particleSystemId", particle.getParticleSystemId());
            particleObj.addProperty("scale", particle.getScale());
            particleObj.addProperty("color", particle.getColor());
            obj.add("particle", particleObj);
        }

        obj.add("serverCommands", toArray(preset.getServerCommands()));
        obj.add("playerCommands", toArray(preset.getPlayerCommands()));
        return obj;
    }

    @Nonnull
    private List<AnnouncementPresetModel> readAnnouncementPresets(@Nonnull JsonObject announcements,
                                                                  @Nonnull List<AnnouncementPresetModel> def) {
        JsonElement element = announcements.get("presets");
        if (element == null || !element.isJsonArray()) {
            element = announcements.get("announcements");
        }
        if (element == null || !element.isJsonArray()) {
            return sanitizeAnnouncementPresets(def);
        }
        List<AnnouncementPresetModel> out = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry == null || !entry.isJsonObject()) continue;
            AnnouncementPresetModel preset = readAnnouncementPreset(entry.getAsJsonObject());
            if (preset != null) {
                out.add(preset);
            }
        }
        return out.isEmpty() ? sanitizeAnnouncementPresets(def) : out;
    }

    @Nullable
    private AnnouncementPresetModel readAnnouncementPreset(@Nonnull JsonObject obj) {
        String name = str(obj, "name", "").trim();
        if (name.isBlank()) return null;
        AnnouncementPresetModel preset = new AnnouncementPresetModel(name);
        preset.setEnabled(bool(obj, "enabled", true));
        preset.setPermission(str(obj, "permission", ""));
        preset.setChatMessages(readStringList(obj, "chatMessages", "chatMessage", List.of()));

        JsonObject notificationObj = getObjectOrNull(obj, "notification");
        if (notificationObj == null) notificationObj = getObjectOrNull(obj, "notifyMessage");
        if (notificationObj != null) {
            AnnouncementPresetModel.NotificationAction notification = new AnnouncementPresetModel.NotificationAction();
            notification.setTitle(str(notificationObj, "title", ""));
            notification.setMessage(str(notificationObj, "message", ""));
            notification.setIcon(str(notificationObj, "icon", ""));
            notification.setStyle(str(notificationObj, "style", "Default"));
            preset.setNotification(notification);
        }

        JsonObject titleObj = getObjectOrNull(obj, "title");
        if (titleObj == null) titleObj = getObjectOrNull(obj, "titleMessage");
        if (titleObj != null) {
            AnnouncementPresetModel.TitleAction title = new AnnouncementPresetModel.TitleAction();
            title.setPrimary(str(titleObj, "primary", ""));
            title.setSecondary(str(titleObj, "secondary", ""));
            title.setMajor(bool(titleObj, "major", false));
            preset.setTitle(title);
        }

        JsonObject soundObj = getObjectOrNull(obj, "sound");
        if (soundObj != null) {
            AnnouncementPresetModel.SoundAction sound = new AnnouncementPresetModel.SoundAction();
            sound.setSoundEventIndex(intVal(soundObj, "soundEventIndex", -1));
            sound.setCategory(str(soundObj, "category", "Music"));
            sound.setVolume((float) dbl(soundObj, "volume", 1.0));
            sound.setPitch((float) dbl(soundObj, "pitch", 1.0));
            preset.setSound(sound);
        }

        JsonObject particleObj = getObjectOrNull(obj, "particle");
        if (particleObj != null) {
            AnnouncementPresetModel.ParticleAction particle = new AnnouncementPresetModel.ParticleAction();
            particle.setParticleSystemId(str(particleObj, "particleSystemId", ""));
            particle.setScale((float) dbl(particleObj, "scale", 1.0));
            particle.setColor(str(particleObj, "color", ""));
            preset.setParticle(particle);
        }

        preset.setServerCommands(readStringList(obj, "serverCommands", "runCommandAsServer", List.of()));
        preset.setPlayerCommands(readStringList(obj, "playerCommands", "runCommandAsPlayer", List.of()));
        return sanitizeAnnouncementPreset(preset);
    }

    @Nonnull
    private List<AnnouncementPresetModel> sanitizeAnnouncementPresets(@Nonnull List<AnnouncementPresetModel> presets) {
        List<AnnouncementPresetModel> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (AnnouncementPresetModel preset : presets) {
            if (preset == null) continue;
            AnnouncementPresetModel sanitized = sanitizeAnnouncementPreset(preset);
            String key = sanitized.getName().toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                out.add(sanitized);
            }
        }
        return out.isEmpty() ? defaultAnnouncementPresets() : out;
    }

    @Nonnull
    private AnnouncementPresetModel sanitizeAnnouncementPreset(@Nonnull AnnouncementPresetModel preset) {
        AnnouncementPresetModel copy = preset.copy();
        copy.setName(copy.getName());
        copy.setPermission(copy.getPermission());
        copy.setChatMessages(copy.getChatMessages());
        copy.setServerCommands(copy.getServerCommands());
        copy.setPlayerCommands(copy.getPlayerCommands());
        return copy;
    }

    @Nonnull
    private List<AnnouncementPresetModel> presetsFromAutoBroadcast(@Nonnull List<String> messages) {
        List<AnnouncementPresetModel> presets = new ArrayList<>();
        int index = 1;
        for (String message : messages) {
            if (message == null || message.isBlank()) continue;
            AnnouncementPresetModel preset = new AnnouncementPresetModel("legacy_" + index++);
            preset.setChatMessages(List.of(message));
            presets.add(preset);
        }
        return presets;
    }

    private void syncAutoBroadcastMessagesFromAnnouncements() {
        List<String> messages = new ArrayList<>();
        for (AnnouncementPresetModel preset : announcementPresets) {
            if (preset == null || preset.getChatMessages().isEmpty()) continue;
            messages.add(preset.getChatMessages().get(0));
        }
        if (!messages.isEmpty()) {
            autoBroadcastMessages = messages;
        }
    }

    @Nonnull
    private static String normalizeAnnouncementName(@Nonnull String name) {
        return new AnnouncementPresetModel(name).getName();
    }

    @Nonnull
    private JsonObject toChatGroupObject(@Nonnull Map<String, String> groups) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return obj;
    }

    @Nonnull
    private JsonArray toRankupArray(@Nonnull List<RankupTier> tiers) {
        JsonArray arr = new JsonArray();
        for (RankupTier tier : tiers) {
            JsonObject obj = new JsonObject();
            obj.addProperty("rank", tier.getRank());
            obj.addProperty("playtimeHours", tier.getPlaytimeHours());
            obj.addProperty("cost", tier.getCost());
            obj.add("commands", toArray(tier.getCommands()));
            arr.add(obj);
        }
        return arr;
    }

    @Nonnull
    private JsonArray toPlaytimeRewardsArray(@Nonnull List<PlaytimeRewardModel> rewards) {
        JsonArray arr = new JsonArray();
        for (PlaytimeRewardModel reward : sanitizePlaytimeRewards(rewards)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", reward.getId());
            obj.addProperty("requiredSeconds", reward.getRequiredSeconds());
            obj.addProperty("requiredCost", reward.getRequiredCost());
            obj.addProperty("rank", reward.getRank());
            obj.addProperty("autoClaim", reward.isAutoClaim());
            obj.add("commands", toArray(reward.getCommands()));
            obj.addProperty("broadcastMessage", reward.getBroadcastMessage());
            arr.add(obj);
        }
        return arr;
    }

    @Nonnull
    private List<PlaytimeRewardModel> readPlaytimeRewards(@Nonnull JsonObject rewardSection,
                                                          @Nonnull List<PlaytimeRewardModel> def) {
        JsonElement entriesElement = rewardSection.get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            entriesElement = rewardSection.get("list");
        }
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            return sanitizePlaytimeRewards(def);
        }
        List<PlaytimeRewardModel> out = new ArrayList<>();
        for (JsonElement element : entriesElement.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            PlaytimeRewardModel reward = readPlaytimeRewardEntry(element.getAsJsonObject());
            if (reward != null) {
                out.add(reward);
            }
        }
        if (out.isEmpty()) {
            return sanitizePlaytimeRewards(def);
        }
        return sanitizePlaytimeRewards(out);
    }

    @Nullable
    private PlaytimeRewardModel readPlaytimeRewardEntry(@Nonnull JsonObject obj) {
        String id = str(obj, "id", "").trim();
        if (id.isBlank()) {
            id = str(obj, "name", "").trim();
        }
        if (id.isBlank()) {
            return null;
        }

        long requiredSeconds = parseRewardSeconds(obj);
        if (requiredSeconds < 0L) {
            return null;
        }

        List<String> commands = new ArrayList<>();
        for (String cmd : list(obj, "commands", List.of())) {
            if (cmd == null) continue;
            String cleaned = cmd.trim();
            if (!cleaned.isBlank()) {
                commands.add(cleaned);
            }
        }
        if (commands.isEmpty() && obj.has("command") && obj.get("command").isJsonPrimitive()) {
            String single = obj.get("command").getAsString().trim();
            if (!single.isBlank()) {
                commands.add(single);
            }
        }

        String broadcast = str(obj, "broadcastMessage", "").trim();
        if (broadcast.isBlank()) {
            broadcast = str(obj, "broadcast", "").trim();
        }

        long requiredCost = Math.max(
                0L,
                longVal(obj, "requiredCost",
                        longVal(obj, "cost", longVal(obj, "price", 0L)))
        );
        String rank = str(obj, "rank", "").trim();
        if (rank.isBlank()) {
            rank = str(obj, "targetRank", "").trim();
        }
        if (rank.isBlank()) {
            rank = str(obj, "group", "").trim();
        }
        boolean autoClaim = bool(obj, "autoClaim", rank.isBlank());

        return new PlaytimeRewardModel(id, requiredSeconds, requiredCost, rank, autoClaim, commands, broadcast);
    }

    private long parseRewardSeconds(@Nonnull JsonObject obj) {
        if (obj.has("requiredSeconds") && obj.get("requiredSeconds").isJsonPrimitive()) {
            return Math.max(0L, obj.get("requiredSeconds").getAsLong());
        }
        if (obj.has("timeRequirementSeconds") && obj.get("timeRequirementSeconds").isJsonPrimitive()) {
            return Math.max(0L, obj.get("timeRequirementSeconds").getAsLong());
        }
        if (obj.has("playtimeSeconds") && obj.get("playtimeSeconds").isJsonPrimitive()) {
            return Math.max(0L, obj.get("playtimeSeconds").getAsLong());
        }
        if (obj.has("requiredMinutes") && obj.get("requiredMinutes").isJsonPrimitive()) {
            return Math.max(0L, obj.get("requiredMinutes").getAsLong() * 60L);
        }
        if (obj.has("requiredHours") && obj.get("requiredHours").isJsonPrimitive()) {
            return Math.max(0L, Math.round(obj.get("requiredHours").getAsDouble() * 3600.0));
        }
        if (obj.has("requiredTime") && obj.get("requiredTime").isJsonPrimitive()) {
            return TimeUtil.parseDurationSeconds(obj.get("requiredTime").getAsString());
        }
        if (obj.has("time") && obj.get("time").isJsonPrimitive()) {
            return TimeUtil.parseDurationSeconds(obj.get("time").getAsString());
        }
        if (obj.has("timeRequirement") && obj.get("timeRequirement").isJsonPrimitive()) {
            try {
                long raw = obj.get("timeRequirement").getAsLong();
                if (raw < 0L) return -1L;
                // Compatibility: some plugins store milliseconds.
                if (raw > 315360000L) {
                    return Math.max(0L, raw / 1000L);
                }
                return raw;
            } catch (Exception ignored) {
                long parsed = TimeUtil.parseDurationSeconds(obj.get("timeRequirement").getAsString());
                return parsed;
            }
        }
        return -1L;
    }

    @Nonnull
    private List<PlaytimeRewardModel> sanitizePlaytimeRewards(@Nonnull List<PlaytimeRewardModel> input) {
        List<PlaytimeRewardModel> out = new ArrayList<>();
        for (PlaytimeRewardModel reward : input) {
            if (reward == null) continue;
            String id = reward.getId().trim();
            if (id.isBlank()) continue;

            boolean duplicate = false;
            for (PlaytimeRewardModel existing : out) {
                if (existing.getId().equalsIgnoreCase(id)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;

            long required = Math.max(0L, reward.getRequiredSeconds());
            long requiredCost = Math.max(0L, reward.getRequiredCost());
            String rank = reward.getRank().trim();
            boolean autoClaim = reward.isAutoClaim();
            List<String> commands = new ArrayList<>();
            for (String command : reward.getCommands()) {
                if (command == null) continue;
                String cleaned = command.trim();
                if (!cleaned.isBlank()) {
                    commands.add(cleaned);
                }
            }
            String broadcast = reward.getBroadcastMessage().trim();
            out.add(new PlaytimeRewardModel(id, required, requiredCost, rank, autoClaim, commands, broadcast));
        }
        return out;
    }

    @Nonnull
    private List<PlaytimeRewardModel> toPlaytimeRewards(@Nonnull List<RankupTier> tiers) {
        List<PlaytimeRewardModel> out = new ArrayList<>();
        for (RankupTier tier : tiers) {
            if (tier == null) {
                continue;
            }
            String rank = tier.getRank().trim();
            if (rank.isBlank()) {
                continue;
            }
            String id = "rankup_" + rank.toLowerCase(Locale.ROOT);
            out.add(new PlaytimeRewardModel(
                    id,
                    tier.getPlaytimeSeconds(),
                    tier.getCost(),
                    rank,
                    false,
                    tier.getCommands(),
                    ""
            ));
        }
        return sanitizePlaytimeRewards(out);
    }

    @Nonnull
    private List<PlaytimeRewardModel> mergePlaytimeRewards(@Nonnull List<PlaytimeRewardModel> existing,
                                                           @Nonnull List<PlaytimeRewardModel> additions) {
        List<PlaytimeRewardModel> merged = new ArrayList<>(sanitizePlaytimeRewards(existing));
        for (PlaytimeRewardModel candidate : sanitizePlaytimeRewards(additions)) {
            boolean present = false;
            for (PlaytimeRewardModel current : merged) {
                if (current.getId().equalsIgnoreCase(candidate.getId())) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                merged.add(candidate);
            }
        }
        return sanitizePlaytimeRewards(merged);
    }

    @Nonnull
    private List<RankupTier> toRankupTiers(@Nonnull List<PlaytimeRewardModel> rewards) {
        List<RankupTier> tiers = new ArrayList<>();
        for (PlaytimeRewardModel reward : sanitizePlaytimeRewards(rewards)) {
            String rank = reward.getRank().trim();
            if (rank.isBlank()) {
                continue;
            }
            tiers.add(new RankupTier(
                    rank,
                    reward.getRequiredSeconds(),
                    reward.getRequiredCost(),
                    reward.getCommands().isEmpty() ? defaultRankupCommands() : reward.getCommands()
            ));
        }
        tiers.sort(Comparator.comparingLong(RankupTier::getPlaytimeSeconds)
                .thenComparingLong(RankupTier::getCost)
                .thenComparing(t -> t.getRank().toLowerCase(Locale.ROOT)));
        return tiers;
    }

    @Nonnull
    private List<RankupTier> readRankupTiers(@Nonnull JsonObject rankup) {
        boolean hasRanks = rankup.has("ranks") && rankup.get("ranks").isJsonArray();
        if (!hasRanks) {
            return List.of();
        }
        JsonArray arr = rankup.getAsJsonArray("ranks");
        List<RankupTier> tiers = new ArrayList<>();
        for (JsonElement element : arr) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String rank = str(obj, "rank", "").trim();
            if (rank.isBlank()) {
                rank = str(obj, "id", "").trim();
            }
            if (rank.isBlank()) continue;
            double playtimeHours = 0.0;
            if (obj.has("playtimeHours")) {
                try {
                    playtimeHours = obj.get("playtimeHours").getAsDouble();
                } catch (Exception ignored) {
                }
            }
            long playtimeSeconds = Math.max(0L, Math.round(playtimeHours * 3600.0));
            long cost = Math.max(0L, moneyVal(obj, "cost", 0L));
            List<String> commands = list(obj, "commands", defaultRankupCommands());
            tiers.add(new RankupTier(rank, playtimeSeconds, cost, commands));
        }
        if (tiers.isEmpty()) return List.of();
        return tiers;
    }

    private boolean mergeDefaults(@Nonnull JsonObject target, @Nonnull JsonObject defaults) {
        boolean changed = false;
        for (String key : defaults.keySet()) {
            JsonElement defVal = defaults.get(key);
            if (!target.has(key)) {
                target.add(key, defVal);
                changed = true;
                continue;
            }
            JsonElement curVal = target.get(key);
            if (defVal.isJsonObject() && curVal.isJsonObject()) {
                if (mergeDefaults(curVal.getAsJsonObject(), defVal.getAsJsonObject())) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    @Nonnull
    private static Map<String, String> defaultChatGroups() {
        Map<String, String> groups = new LinkedHashMap<>();
        groups.put("Default", "{prefix}&f{player}{suffix}&7: &f{message}");
        groups.put("Member", "{prefix}&f{player}{suffix}&7: &f{message}");
        groups.put("VIP", "{prefix}&f{player}{suffix}&7: &f{message}");
        groups.put("Moderator", "{prefix}&f{player}{suffix}&7: &f{message}");
        groups.put("Admin", "{prefix}&f{player}{suffix}&7: &f{message}");
        groups.put("OP", "{prefix}&f{player}{suffix}&7: &f{message}");
        return groups;
    }

    @Nonnull
    private static Map<String, String> legacyDefaultChatGroups() {
        Map<String, String> groups = new LinkedHashMap<>();
        groups.put("Default", "&7[{group}] &f{player}&7: &f{message}");
        groups.put("Member", "&#5EEAD4[{group}] &f{player}&7: &f{message}");
        groups.put("VIP", "&#FBBF24[{group}] &f{player}&7: &f{message}");
        groups.put("Moderator", "&#34D399[{group}] &f{player}&7: &f{message}");
        groups.put("Admin", "&#F87171[{group}] &f{player}&7: &f{message}");
        groups.put("OP", "&#FB7185[{group}] &f{player}&7: &f{message}");
        return groups;
    }

    @Nonnull
    private static Map<String, String> defaultChatGroupPrefixes() {
        Map<String, String> prefixes = new LinkedHashMap<>();
        prefixes.put("Default", "&7[Default] ");
        prefixes.put("Member", "&#5EEAD4[Member] ");
        prefixes.put("VIP", "&#FBBF24[VIP] ");
        prefixes.put("Moderator", "&#34D399[Moderator] ");
        prefixes.put("Admin", "&#F87171[Admin] ");
        prefixes.put("OP", "&#FB7185[OP] ");
        return prefixes;
    }

    @Nonnull
    private static Map<String, String> defaultChatGroupSuffixes() {
        return new LinkedHashMap<>();
    }

    @Nonnull
    private static Map<String, Integer> defaultGroupPriorities() {
        Map<String, Integer> priorities = new LinkedHashMap<>();
        priorities.put("Default", 0);
        priorities.put("Member", 1);
        priorities.put("VIP", 10);
        priorities.put("Moderator", 50);
        priorities.put("Admin", 90);
        priorities.put("OP", 100);
        return priorities;
    }

    @Nonnull
    private static Map<String, String> defaultScoreboardPlaceholders() {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("server_name", "The Legacy Voyage");
        placeholders.put("discord", "https://discord.gg/########");
        placeholders.put("website", "https://www.example.com/");
        return placeholders;
    }

    @Nonnull
    private static List<String> defaultScoreboardLines() {
        return List.of(
                "&4Server: &6{server_name}",
                "&2World: &6{world}",
                "&cPlayers: &6{player_count}&c/&6{max_players}",
                "",
                "&dName: &6{player}",
                "&dRank: &6{rank}",
                "&dBalance: &6{balance}",
                "",
                "&2Coords: &6{coords}",
                "&cTPS: &6{tps}",
                "",
                "&9Discord: &6{discord}",
                "&8Website: &6{website}"
        );
    }

    @Nonnull
    private static List<AnnouncementPresetModel> defaultAnnouncementPresets() {
        AnnouncementPresetModel tip = new AnnouncementPresetModel("server_tip");
        tip.setEnabled(true);
        tip.setChatMessages(List.of(
                "<#38BDF8><bold>[Tip]</bold></#38BDF8> <#E2E8F0>Use <#FACC15>/rules</#FACC15>, <#FACC15>/shop</#FACC15>, and <#FACC15>/ah</#FACC15> to explore the server.</#E2E8F0>"
        ));

        AnnouncementPresetModel event = new AnnouncementPresetModel("event_reminder");
        event.setEnabled(false);
        event.setChatMessages(List.of(
                "<#FACC15><bold>[Event]</bold></#FACC15> <#E2E8F0>A server event is starting soon.</#E2E8F0>"
        ));
        AnnouncementPresetModel.TitleAction title = new AnnouncementPresetModel.TitleAction();
        title.setPrimary("Server Event");
        title.setSecondary("Starting soon");
        title.setMajor(true);
        event.setTitle(title);
        AnnouncementPresetModel.SoundAction sound = new AnnouncementPresetModel.SoundAction();
        sound.setSoundEventIndex(24);
        sound.setCategory("Music");
        sound.setVolume(1.0f);
        sound.setPitch(1.0f);
        event.setSound(sound);

        AnnouncementPresetModel vip = new AnnouncementPresetModel("vip_notice");
        vip.setEnabled(false);
        vip.setPermission("hyessentialsx.announcement.vip");
        vip.setChatMessages(List.of(
                "<#FDE68A><bold>[VIP]</bold></#FDE68A> <#E2E8F0>Thanks for supporting the server, {player}.</#E2E8F0>"
        ));
        AnnouncementPresetModel.NotificationAction notification = new AnnouncementPresetModel.NotificationAction();
        notification.setTitle("VIP Notice");
        notification.setMessage("Thanks for supporting the server.");
        notification.setStyle("Success");
        vip.setNotification(notification);

        return List.of(tip, event, vip);
    }

    @Nonnull
    private static Map<String, Long> defaultBlockRewards() {
        Map<String, Long> rewards = new LinkedHashMap<>();
        rewards.put("oak_log", 1L);
        rewards.put("spruce_log", 1L);
        rewards.put("birch_log", 1L);
        rewards.put("jungle_log", 1L);
        rewards.put("acacia_log", 1L);
        rewards.put("dark_oak_log", 1L);
        rewards.put("coal_ore", 2L);
        rewards.put("copper_ore", 3L);
        rewards.put("iron_ore", 4L);
        rewards.put("gold_ore", 6L);
        rewards.put("diamond_ore", 12L);
        rewards.put("emerald_ore", 15L);
        return rewards;
    }

    @Nonnull
    private static Map<String, Long> defaultBlockGroupRewards() {
        Map<String, Long> rewards = new LinkedHashMap<>();
        rewards.put("wood", 1L);
        rewards.put("ore", 5L);
        return rewards;
    }

    @Nonnull
    private static Map<String, Long> defaultMobRewards() {
        Map<String, Long> rewards = new LinkedHashMap<>();
        rewards.put("goblin", 2L);
        rewards.put("goblin_duke", 25L);
        rewards.put("goblin_duke_large", 40L);
        rewards.put("skeleton_fighter", 6L);
        rewards.put("scarak_broodmother", 50L);
        rewards.put("wraith_lantern", 12L);
        rewards.put("bear_grizzly", 8L);
        return rewards;
    }

    @Nonnull
    private static List<PlaytimeRewardModel> defaultPlaytimeRewards() {
        return List.of();
    }

    @Nonnull
    private static List<String> defaultRankupCommands() {
        return List.of(
                "/lp user {player} parent set {rank}",
                "/broadcast &#F59E0B[Rankup] &f{player} &7ranked up to {rank}"
        );
    }

    @Nonnull
    private static List<RankupTier> defaultRankupTiers() {
        List<RankupTier> tiers = new ArrayList<>();
        tiers.add(new RankupTier(
                "member",
                Math.round(1.0 * 3600.0),
                1000L,
                defaultRankupCommands()
        ));
        return tiers;
    }
}

