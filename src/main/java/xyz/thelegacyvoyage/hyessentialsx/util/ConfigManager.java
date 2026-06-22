package xyz.thelegacyvoyage.hyessentialsx.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.models.RankupTier;
import xyz.thelegacyvoyage.hyessentialsx.util.PluginInfoUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigManager {

    private static final int DEFAULT_COMMAND_COOLDOWN_SECONDS = 30;
    private static final int DEFAULT_TELEPORT_WARMUP_SECONDS = 5;
    private static final List<String> DEFAULT_SPAWN_RESPAWN_PRIORITY = List.of("bed", "setspawn", "world");
    private static final List<String> DEFAULT_COMBAT_BLOCKED_COMMANDS = List.of("home", "spawn", "tpa", "tp", "warp");

    private final Path configPath;
    private final Path economyPath;
    private final Path rankupPath;
    private final Path chatPath;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private JsonObject root;

    private boolean useWorldDefaultSpawnIfUnset = true;
    private String languageCode = "en-us";
    private int tpaRequestTimeoutSeconds = 60;
    private int rtpMaxDistance = 5000;
    private int rtpMinDistance = 1000;
    private Map<String, String> rtpWorldOverrides = new LinkedHashMap<>();
    private final Map<String, Integer> commandCooldowns = new HashMap<>(buildDefaultCooldowns());
    private int homeWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int warpWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int backWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int spawnWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int rtpWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int tpaWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;

    private boolean homesEnabled = true;
    private boolean warpsEnabled = true;
    private boolean warpsGuiEnabled = true;
    private boolean kitsEnabled = true;
    private boolean kitsGuiEnabled = true;
    private boolean kitsRequirePermission = true;
    private boolean msgEnabled = true;
    private boolean nearEnabled = true;
    private boolean motdEnabled = true;
    private boolean rulesEnabled = true;
    private boolean rulesGuiEnabled = true;
    private boolean rtpEnabled = true;
    private boolean broadcastEnabled = true;
    private boolean spawnEnabled = true;
    private int sleepPercentage = 100;
    private boolean sleepChatEnabled = true;
    private boolean tpaEnabled = true;
    private boolean tpaGuiEnabled = true;
    private boolean adminChatEnabled = true;
    private boolean afkEnabled = true;
    private boolean chatEnabled = false;
    private boolean chatOverrideLuckPerms = false;
    private boolean spawnProtectionEnabled = true;
    private int spawnProtectionRadius = 64;
    private boolean spawnProtectionAllowBreak = false;
    private boolean spawnProtectionAllowPlace = false;
    private boolean spawnProtectionAllowDamage = false;
    private boolean spawnProtectionAllowInteract = true;
    private List<String> spawnRespawnPriority = DEFAULT_SPAWN_RESPAWN_PRIORITY;
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
    private long economyStartingBalance = 0L;
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
    private boolean rankupEnabled = false;
    private int rankupConfirmTimeoutSeconds = 30;
    private boolean rankupPlaytimeEnabled = true;
    private boolean rankupCurrencyEnabled = true;
    private boolean rankupAutoEnabled = false;
    private int rankupAutoCheckSeconds = 60;
    private boolean rankupAutoUseCurrency = false;
    private List<RankupTier> rankupTiers = defaultRankupTiers();
    private String defaultKit = "";

    private boolean playerShopsEnabled = true;
    private int playerShopMaxShopsPerPlayer = 1;
    private long playerShopCreationCost = 0L;
    private int playerShopChestLinkRadius = 8;

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

    private boolean welcomeEnabled = true;
    private boolean welcomeBroadcastToAll = true;
    private List<String> welcomeMessages = List.of(
            "<#6EE7FF>------------------------------</#6EE7FF>",
            "<#A78BFA><bold>Welcome, {player}!</bold></#A78BFA>",
            "<#93C5FD>This is your first time joining.</#93C5FD>",
            "<#5EEAD4>We hope you enjoy your stay!</#5EEAD4>",
            "<#6EE7FF>------------------------------</#6EE7FF>"
    );

    private boolean joinQuitEnabled = true;
    private List<String> joinMessages = List.of(
            "<#34D399><bold>+ </bold></#34D399><#93C5FD><bold>{player} joined the server.</bold></#93C5FD>"
    );
    private List<String> quitMessages = List.of(
            "<#FCA5A5><bold>- </bold></#FCA5A5><#93C5FD><bold>{player} left the server.</bold></#93C5FD>"
    );
    private boolean deathMessagesEnabled = true;
    private List<String> deathMessages = List.of(
            "<#FCA5A5><bold>{player}</bold> died {cause}</#FCA5A5>"
    );
    private Map<String, String> chatGroupFormats = defaultChatGroups();
    private Map<String, Integer> chatGroupPriorities = defaultGroupPriorities();

    private int afkTimeoutSeconds = 300;
    private boolean afkAnnounceOnAuto = true;
    private boolean afkAnnounceOnManual = true;
    private boolean afkAnnounceOnReturn = true;
    private String afkMessage = "&e{player} is now AFK.";
    private String afkBackMessage = "&e{player} is no longer AFK.";

    private List<String> motdMessages = List.of(
            "<#6EE7FF>------------------------------</#6EE7FF>",
            "<#F9A8D4><bold>Message of the Day</bold></#F9A8D4>",
            "<#E2E8F0>Welcome to our server!</#E2E8F0>",
            "<#E2E8F0>Have fun and follow /rules.</#E2E8F0>",
            "<#A7F3D0>Visit us on Discord: <url:https://discord.gg/U58ax8cZZ2>https://discord.gg/U58ax8cZZ2</url></#A7F3D0>",
            "<#6EE7FF>------------------------------</#6EE7FF>"
    );
    private boolean motdShowOnJoin = true;

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

    private String storageType = "sqlite";
    private String sqliteFile = "hyessentialsx.db";
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "hyessentialsx";
    private String mysqlUser = "root";
    private String mysqlPassword = "";

    private boolean needsSplitMigration = false;

    public ConfigManager(@Nonnull Path dataFolder) {
        this.configPath = dataFolder.resolve("config.json");
        this.economyPath = dataFolder.resolve("economyConfig.json");
        this.rankupPath = dataFolder.resolve("rankupConfig.json");
        this.chatPath = dataFolder.resolve("chatConfig.json");
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
        boolean hasRankup = Files.exists(rankupPath);
        boolean hasChat = Files.exists(chatPath);

        if (hasMain && hasEconomy && hasRankup && hasChat) return;

        if (hasMain && (!hasEconomy || !hasRankup || !hasChat)) {
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
            if (!hasRankup) {
                writeJson(rankupPath, buildDefaultRankupRoot());
                Log.info("Created default rankupConfig.json");
            }
            if (!hasChat) {
                writeJson(chatPath, buildDefaultChatRoot());
                Log.info("Created default chatConfig.json");
            }
        } catch (Exception e) {
            Log.error("Failed to create default config files: " + e.getMessage(), e);
        }
    }

    private JsonObject buildDefaultConfig() {
        JsonObject root = new JsonObject();
        root.addProperty("version", "1.0.0");
        root.addProperty("debugMode", false);

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
        chat.addProperty("enabled", false);
        chat.addProperty("overrideLuckPermsChatFormat", false);
        chat.add("groups", toChatGroupObject(chatGroupFormats));
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

        JsonObject economy = new JsonObject();
        economy.addProperty("enabled", true);
        economy.addProperty("currencySymbol", "$");
        economy.addProperty("startingBalance", 0);
        economy.addProperty("baltopGui", true);
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
        blockRewards.add("rewards", toLongMapObject(economyBlockRewards));
        blockRewards.add("groupRewards", toLongMapObject(economyBlockGroupRewards));
        rewards.add("blocks", blockRewards);
        JsonObject mobRewards = new JsonObject();
        mobRewards.addProperty("enabled", true);
        mobRewards.addProperty("defaultReward", 0);
        mobRewards.add("rewards", toLongMapObject(economyMobRewards));
        rewards.add("mobs", mobRewards);
        economy.add("rewards", rewards);
        root.add("economy", economy);

        JsonObject rankup = new JsonObject();
        rankup.addProperty("enabled", false);
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
        rankup.add("ranks", toRankupArray(rankupTiers));
        root.add("rankup", rankup);

        JsonObject features = new JsonObject();
        features.addProperty("msg", true);
        features.addProperty("broadcast", true);
        features.addProperty("adminChat", true);
        root.add("features", features);

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
        homes.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        homes.addProperty("warmupSeconds", homeWarmupSeconds);
        root.add("homes", homes);

        JsonObject warps = new JsonObject();
        warps.addProperty("enabled", true);
        warps.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        warps.addProperty("warmupSeconds", warpWarmupSeconds);
        warps.addProperty("gui", true);
        root.add("warps", warps);

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
        root.add("spawn", spawn);

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
        root.add("storage", storage);

        JsonObject playerShops = new JsonObject();
        playerShops.addProperty("enabled", playerShopsEnabled);
        playerShops.addProperty("maxShopsPerPlayer", playerShopMaxShopsPerPlayer);
        playerShops.addProperty("shopCreationCost", playerShopCreationCost);
        playerShops.addProperty("chestLinkRadius", playerShopChestLinkRadius);
        root.add("playerShops", playerShops);

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

        return root;
    }

    public void load() {
        try {
            root = readCombinedRoot();
            if (root == null) {
                throw new IllegalStateException("config.json parsed to null");
            }
            JsonObject existingChat = getObjectOrNull(root, "chat");
            JsonObject preservedChat = existingChat != null ? existingChat.deepCopy() : null;
            JsonObject preservedBlockRewards = null;
            JsonObject preservedMobRewards = null;
            JsonArray preservedRankupRanks = null;
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
            JsonObject rankupObj = getObjectOrNull(root, "rankup");
            if (rankupObj != null && rankupObj.has("ranks") && rankupObj.get("ranks").isJsonArray()) {
                preservedRankupRanks = rankupObj.getAsJsonArray("ranks").deepCopy();
            }
            boolean hadHomesEnabled = hasSectionFlag(root, "homes", "enabled");
            boolean hadWarpsEnabled = hasSectionFlag(root, "warps", "enabled");
            boolean hadKitsEnabled = hasSectionFlag(root, "kits", "enabled");
            boolean hadNearEnabled = hasSectionFlag(root, "near", "enabled");
            boolean hadMotdEnabled = hasSectionFlag(root, "motd", "enabled");
            boolean hadRtpEnabled = hasSectionFlag(root, "rtp", "enabled");
            boolean hadSpawnEnabled = hasSectionFlag(root, "spawn", "enabled");
            boolean hadTpaEnabled = hasSectionFlag(root, "tpa", "enabled");
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
            if (preservedRankupRanks != null) {
                JsonObject rankup = obj(root, "rankup");
                rankup.add("ranks", preservedRankupRanks);
                changed = true;
            }
            if (cleanupConfig(root)) {
                changed = true;
            }
            if (!root.has("autoBroadcast") || !root.get("autoBroadcast").isJsonObject()) {
                root.add("autoBroadcast", defaults.get("autoBroadcast"));
                changed = true;
            }
            if (changed) {
                try {
                    Files.writeString(configPath, gson.toJson(root), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    Log.warn("Failed to persist updated config defaults: " + e.getMessage());
                }
            }

            JsonObject general = obj(root, "general");
            useWorldDefaultSpawnIfUnset = bool(general, "spawnFallbackToWorldDefault", true);
            languageCode = str(general, "language", languageCode).toLowerCase();

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
            JsonObject warpsSection = obj(root, "warps");
            warpWarmupSeconds = intVal(warpsSection, "warmupSeconds", warpWarmupSeconds);
            warpsGuiEnabled = bool(warpsSection, "gui", true);
            JsonObject backSection = obj(root, "back");
            backWarmupSeconds = intVal(backSection, "warmupSeconds", backWarmupSeconds);

            JsonObject near = obj(root, "near");
            nearRadius = dbl(near, "radius", nearRadius);
            nearShowDistance = bool(near, "showDistance", nearShowDistance);

            JsonObject autoBroadcast = obj(root, "autoBroadcast");
            autoBroadcastEnabled = bool(autoBroadcast, "enabled", autoBroadcastEnabled);
            autoBroadcastIntervalSeconds = intVal(autoBroadcast, "intervalSeconds", autoBroadcastIntervalSeconds);
            autoBroadcastRandom = bool(autoBroadcast, "random", autoBroadcastRandom);
            autoBroadcastMessages = list(autoBroadcast, "messages", autoBroadcastMessages);

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
            chatEnabled = bool(chat, "enabled", chatEnabled);
            if (chat.has("overrideLuckPermsChatFormat")) {
                chatOverrideLuckPerms = bool(chat, "overrideLuckPermsChatFormat", chatOverrideLuckPerms);
            } else {
                chatOverrideLuckPerms = bool(chat, "overrideLuckPerms", chatOverrideLuckPerms);
            }
            chatGroupFormats = readChatGroupFormats(chat, chatGroupFormats);
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

            JsonObject economy = obj(root, "economy");
            economyEnabled = bool(economy, "enabled", economyEnabled);
            economyCurrencySymbol = str(economy, "currencySymbol", economyCurrencySymbol);
            economyStartingBalance = Math.max(0L, longVal(economy, "startingBalance", economyStartingBalance));
            economyBaltopGuiEnabled = bool(economy, "baltopGui", economyBaltopGuiEnabled);
            JsonObject paycheck = obj(economy, "paycheck");
            paycheckEnabled = bool(paycheck, "enabled", paycheckEnabled);
            paycheckAmount = Math.max(0L, longVal(paycheck, "amount", paycheckAmount));
            paycheckIntervalHours = dbl(paycheck, "intervalHours", paycheckIntervalHours);
            JsonObject rewards = obj(economy, "rewards");
            economyRewardsEnabled = bool(rewards, "enabled", economyRewardsEnabled);
            economyRewardsDebug = bool(rewards, "debug", economyRewardsDebug);
            JsonObject popup = obj(rewards, "popup");
            economyRewardsPopupEnabled = bool(popup, "enabled", economyRewardsPopupEnabled);
            economyRewardsPopupStyle = str(popup, "style", economyRewardsPopupStyle);
            JsonObject blockRewards = obj(rewards, "blocks");
            economyBlockRewardsEnabled = bool(blockRewards, "enabled", economyBlockRewardsEnabled);
            economyBlockRewards = normalizeKeyedMap(readLongMap(blockRewards, "rewards", economyBlockRewards));
            economyBlockGroupRewards = normalizeKeyedMap(readLongMap(blockRewards, "groupRewards", economyBlockGroupRewards));
            JsonObject mobRewards = obj(rewards, "mobs");
            economyMobRewardsEnabled = bool(mobRewards, "enabled", economyMobRewardsEnabled);
            economyMobDefaultReward = Math.max(0L, longVal(mobRewards, "defaultReward", economyMobDefaultReward));
            economyMobRewards = normalizeKeyedMap(readLongMap(mobRewards, "rewards", economyMobRewards));

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

            JsonObject kits = obj(root, "kits");
            defaultKit = str(kits, "defaultKit", defaultKit).trim();
            kitsRequirePermission = bool(kits, "requirePermission", kitsRequirePermission);

            JsonObject motd = obj(root, "motd");
            motdShowOnJoin = bool(motd, "showOnJoin", motdShowOnJoin);
            motdMessages = list(motd, "messages", motdMessages);

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
            playerShopMaxShopsPerPlayer = Math.max(0, intVal(playerShops, "maxShopsPerPlayer", playerShopMaxShopsPerPlayer));
            playerShopCreationCost = Math.max(0L, longVal(playerShops, "shopCreationCost", playerShopCreationCost));
            playerShopChestLinkRadius = Math.max(1, intVal(playerShops, "chestLinkRadius", playerShopChestLinkRadius));

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
                applyFieldsToRoot();
                save();
                Log.info("Updated config files with new defaults.");
            }
            setVersionFromPlugin();
            if (needsSplitMigration) {
                backupLegacyConfig();
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

    @Nullable
    public String getRtpWorldOverride(@Nonnull String worldName) {
        if (rtpWorldOverrides.isEmpty()) return null;
        return rtpWorldOverrides.get(worldName.toLowerCase(Locale.ROOT));
    }

    public int getCooldownSeconds(@Nonnull String key) {
        Integer value = commandCooldowns.get(key);
        return value != null ? value : DEFAULT_COMMAND_COOLDOWN_SECONDS;
    }

    public int getHomeWarmupSeconds() {
        return Math.max(0, homeWarmupSeconds);
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

    public boolean isWarpsEnabled() {
        return warpsEnabled;
    }

    public boolean isWarpsGuiEnabled() {
        return warpsGuiEnabled;
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

    public boolean isMotdEnabled() {
        return motdEnabled;
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

    public boolean isRtpEnabled() {
        return rtpEnabled;
    }

    public boolean isBroadcastEnabled() {
        return broadcastEnabled;
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
        return chatEnabled;
    }

    public boolean isChatFormatEnabled() {
        return chatEnabled;
    }

    public boolean isOverrideLuckPermsChatFormat() {
        return chatOverrideLuckPerms;
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

    public boolean isRankupEnabled() {
        return rankupEnabled;
    }

    public int getRankupConfirmTimeoutSeconds() {
        return rankupConfirmTimeoutSeconds;
    }

    public boolean isRankupPlaytimeEnabled() {
        return rankupPlaytimeEnabled;
    }

    public boolean isRankupCurrencyEnabled() {
        return rankupCurrencyEnabled;
    }

    public boolean isRankupAutoEnabled() {
        return rankupAutoEnabled;
    }

    public int getRankupAutoCheckSeconds() {
        return rankupAutoCheckSeconds;
    }

    public boolean isRankupAutoUseCurrency() {
        return rankupAutoUseCurrency;
    }

    public boolean isPlayerShopsEnabled() {
        return playerShopsEnabled;
    }

    public int getPlayerShopMaxShopsPerPlayer() {
        return playerShopMaxShopsPerPlayer;
    }

    public long getPlayerShopCreationCost() {
        return playerShopCreationCost;
    }

    public int getPlayerShopChestLinkRadius() {
        return playerShopChestLinkRadius;
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

        JsonObject general = obj(root, "general");
        general.addProperty("spawnFallbackToWorldDefault", useWorldDefaultSpawnIfUnset);
        general.addProperty("language", languageCode);

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

        JsonObject autoBroadcast = obj(root, "autoBroadcast");
        autoBroadcast.addProperty("enabled", autoBroadcastEnabled);
        autoBroadcast.addProperty("intervalSeconds", autoBroadcastIntervalSeconds);
        autoBroadcast.addProperty("random", autoBroadcastRandom);
        autoBroadcast.add("messages", toArray(autoBroadcastMessages));

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
        chat.addProperty("enabled", chatEnabled);
        chat.addProperty("overrideLuckPermsChatFormat", chatOverrideLuckPerms);
        chat.remove("overrideLuckPerms");
        chat.add("groups", toChatGroupObject(chatGroupFormats));
        chat.add("groupPriorities", toPriorityObject(chatGroupPriorities));

        JsonObject spawnProtection = obj(root, "spawnProtection");
        spawnProtection.addProperty("enabled", spawnProtectionEnabled);
        spawnProtection.addProperty("radius", spawnProtectionRadius);
        spawnProtection.addProperty("allowBreak", spawnProtectionAllowBreak);
        spawnProtection.addProperty("allowPlace", spawnProtectionAllowPlace);
        spawnProtection.addProperty("allowDamage", spawnProtectionAllowDamage);
        spawnProtection.addProperty("allowInteract", spawnProtectionAllowInteract);

        JsonObject economy = obj(root, "economy");
        economy.addProperty("enabled", economyEnabled);
        economy.addProperty("currencySymbol", economyCurrencySymbol);
        economy.addProperty("startingBalance", Math.max(0L, economyStartingBalance));
        economy.addProperty("baltopGui", economyBaltopGuiEnabled);
        JsonObject paycheck = obj(economy, "paycheck");
        paycheck.addProperty("enabled", paycheckEnabled);
        paycheck.addProperty("amount", Math.max(0L, paycheckAmount));
        paycheck.addProperty("intervalHours", paycheckIntervalHours);
        JsonObject rewards = obj(economy, "rewards");
        rewards.addProperty("enabled", economyRewardsEnabled);
        rewards.addProperty("debug", economyRewardsDebug);
        JsonObject popup = obj(rewards, "popup");
        popup.addProperty("enabled", economyRewardsPopupEnabled);
        popup.addProperty("style", economyRewardsPopupStyle);
        JsonObject blockRewards = obj(rewards, "blocks");
        blockRewards.addProperty("enabled", economyBlockRewardsEnabled);
        blockRewards.add("rewards", toLongMapObject(economyBlockRewards));
        blockRewards.add("groupRewards", toLongMapObject(economyBlockGroupRewards));
        JsonObject mobRewards = obj(rewards, "mobs");
        mobRewards.addProperty("enabled", economyMobRewardsEnabled);
        mobRewards.addProperty("defaultReward", Math.max(0L, economyMobDefaultReward));
        mobRewards.add("rewards", toLongMapObject(economyMobRewards));

        JsonObject rankup = obj(root, "rankup");
        rankup.addProperty("enabled", rankupEnabled);
        rankup.addProperty("confirmTimeoutSeconds", rankupConfirmTimeoutSeconds);
        JsonObject requirements = obj(rankup, "requirements");
        requirements.addProperty("playtimeEnabled", rankupPlaytimeEnabled);
        requirements.addProperty("currencyEnabled", rankupCurrencyEnabled);
        JsonObject auto = obj(rankup, "auto");
        auto.addProperty("enabled", rankupAutoEnabled);
        auto.addProperty("checkSeconds", rankupAutoCheckSeconds);
        auto.addProperty("useCurrency", rankupAutoUseCurrency);
        rankup.add("ranks", toRankupArray(rankupTiers));

        JsonObject motd = obj(root, "motd");
        motd.addProperty("enabled", motdEnabled);
        motd.addProperty("showOnJoin", motdShowOnJoin);
        motd.add("messages", toArray(motdMessages));

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
        homes.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.HOME));
        homes.addProperty("warmupSeconds", homeWarmupSeconds);

        JsonObject warps = obj(root, "warps");
        warps.addProperty("enabled", warpsEnabled);
        warps.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.WARP));
        warps.addProperty("warmupSeconds", warpWarmupSeconds);
        warps.addProperty("gui", warpsGuiEnabled);

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

        JsonObject playerShops = obj(root, "playerShops");
        playerShops.addProperty("enabled", playerShopsEnabled);
        playerShops.addProperty("maxShopsPerPlayer", playerShopMaxShopsPerPlayer);
        playerShops.addProperty("shopCreationCost", Math.max(0L, playerShopCreationCost));
        playerShops.addProperty("chestLinkRadius", playerShopChestLinkRadius);

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
        boolean hasRankup = Files.exists(rankupPath);
        boolean hasChat = Files.exists(chatPath);
        JsonObject economy = hasEconomy ? readOrDefault(economyPath, buildDefaultEconomyRoot()) : new JsonObject();
        JsonObject rankup = hasRankup ? readOrDefault(rankupPath, buildDefaultRankupRoot()) : new JsonObject();
        JsonObject chat = hasChat ? readOrDefault(chatPath, buildDefaultChatRoot()) : new JsonObject();

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
        if (hasRankup && rankup.has("rankup")) {
            merged.add("rankup", rankup.get("rankup"));
        } else if (main.has("rankup")) {
            merged.add("rankup", main.get("rankup"));
        } else {
            JsonObject defRankup = buildDefaultRankupRoot();
            if (defRankup.has("rankup")) {
                merged.add("rankup", defRankup.get("rankup"));
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
        main.remove("rankup");
        stripChatSections(main);

        JsonObject economy = buildDefaultEconomyRoot();
        if (full.has("economy")) {
            economy.add("economy", full.get("economy"));
        }
        JsonObject rankup = buildDefaultRankupRoot();
        if (full.has("rankup")) {
            rankup.add("rankup", full.get("rankup"));
        }
        JsonObject chat = buildDefaultChatRoot();
        fillChatRoot(chat, full);

        writeJson(configPath, main);
        writeJson(economyPath, economy);
        writeJson(rankupPath, rankup);
        writeJson(chatPath, chat);
    }

    private void writeJson(@Nonnull Path path, @Nonnull JsonObject data) throws Exception {
        Files.writeString(path, gson.toJson(data), StandardCharsets.UTF_8);
    }

    @Nonnull
    private JsonObject buildDefaultMainRoot() {
        JsonObject root = buildDefaultConfig();
        root.remove("economy");
        root.remove("rankup");
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
    private JsonObject buildDefaultChatRoot() {
        JsonObject root = new JsonObject();
        JsonObject defaults = buildDefaultConfig();
        fillChatRoot(root, defaults);
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
    private static Map<String, Integer> buildDefaultCooldowns() {
        Map<String, Integer> defaults = new HashMap<>();
        defaults.put(CooldownKeys.BACK, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.HEAL, DEFAULT_COMMAND_COOLDOWN_SECONDS);
        defaults.put(CooldownKeys.REPAIR, DEFAULT_COMMAND_COOLDOWN_SECONDS);
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
    private JsonArray toArray(@Nonnull List<String> values) {
        JsonArray arr = new JsonArray();
        for (String value : values) arr.add(value);
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
    private JsonObject toStringMapObject(@Nonnull Map<String, String> values) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return obj;
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
    private List<RankupTier> readRankupTiers(@Nonnull JsonObject rankup) {
        boolean hasRanks = rankup.has("ranks") && rankup.get("ranks").isJsonArray();
        if (!hasRanks) {
            return defaultRankupTiers();
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
            long cost = Math.max(0L, longVal(obj, "cost", 0L));
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
        groups.put("Default", "&7[{group}] &f{player}&7: &f{message}");
        groups.put("Member", "&#5EEAD4[{group}] &f{player}&7: &f{message}");
        groups.put("VIP", "&#FBBF24[{group}] &f{player}&7: &f{message}");
        groups.put("Moderator", "&#34D399[{group}] &f{player}&7: &f{message}");
        groups.put("Admin", "&#F87171[{group}] &f{player}&7: &f{message}");
        groups.put("OP", "&#FB7185[{group}] &f{player}&7: &f{message}");
        return groups;
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
