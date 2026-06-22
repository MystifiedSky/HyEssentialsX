package xyz.thelegacyvoyage.hyessentialsx.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xyz.thelegacyvoyage.hyessentialsx.models.SpawnModel;
import xyz.thelegacyvoyage.hyessentialsx.util.PluginInfoUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigManager {

    private static final int DEFAULT_COMMAND_COOLDOWN_SECONDS = 30;
    private static final int DEFAULT_TELEPORT_WARMUP_SECONDS = 5;

    private final Path configPath;
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
    private final Map<String, Integer> commandCooldowns = new HashMap<>(buildDefaultCooldowns());
    private int homeWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int warpWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int backWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int spawnWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int rtpWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;
    private int tpaWarmupSeconds = DEFAULT_TELEPORT_WARMUP_SECONDS;

    private boolean homesEnabled = true;
    private boolean warpsEnabled = true;
    private boolean kitsEnabled = true;
    private boolean msgEnabled = true;
    private boolean nearEnabled = true;
    private boolean motdEnabled = true;
    private boolean rulesEnabled = true;
    private boolean rtpEnabled = true;
    private boolean broadcastEnabled = true;
    private boolean spawnEnabled = true;
    private boolean tpaEnabled = true;
    private boolean adminChatEnabled = true;
    private boolean afkEnabled = true;

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

    public ConfigManager(@Nonnull Path dataFolder) {
        this.configPath = dataFolder.resolve("config.json");
        ensureExists();
        load();
    }

    private void ensureExists() {
        if (Files.exists(configPath)) return;
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject defaults = buildDefaultConfig();
            Files.writeString(configPath, gson.toJson(defaults), StandardCharsets.UTF_8);
            Log.info("Created default config.json");
        } catch (Exception e) {
            Log.error("Failed to create config.json: " + e.getMessage(), e);
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

        JsonObject features = new JsonObject();
        features.addProperty("homes", true);
        features.addProperty("warps", true);
        features.addProperty("kits", true);
        features.addProperty("msg", true);
        features.addProperty("near", true);
        features.addProperty("motd", true);
        features.addProperty("rules", true);
        features.addProperty("rtp", true);
        features.addProperty("broadcast", true);
        features.addProperty("spawn", true);
        features.addProperty("tpa", true);
        features.addProperty("adminChat", true);
        root.add("features", features);

        JsonObject motd = new JsonObject();
        motd.addProperty("enabled", true);
        motd.addProperty("showOnJoin", true);
        motd.add("messages", toArray(motdMessages));
        root.add("motd", motd);

        JsonObject rulesObj = new JsonObject();
        rulesObj.addProperty("enabled", true);
        rulesObj.add("messages", toArray(rules));
        root.add("rules", rulesObj);

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
        homes.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        homes.addProperty("warmupSeconds", homeWarmupSeconds);
        root.add("homes", homes);

        JsonObject warps = new JsonObject();
        warps.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        warps.addProperty("warmupSeconds", warpWarmupSeconds);
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
        root.add("rtp", rtp);

        JsonObject near = new JsonObject();
        near.addProperty("enabled", true);
        near.addProperty("radius", nearRadius);
        near.addProperty("showDistance", true);
        near.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        root.add("near", near);

        JsonObject tpa = new JsonObject();
        tpa.addProperty("timeoutSeconds", tpaRequestTimeoutSeconds);
        tpa.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        tpa.addProperty("warmupSeconds", tpaWarmupSeconds);
        root.add("tpa", tpa);

        JsonObject autoBroadcast = new JsonObject();
        autoBroadcast.addProperty("enabled", true);
        autoBroadcast.addProperty("intervalSeconds", autoBroadcastIntervalSeconds);
        autoBroadcast.addProperty("random", autoBroadcastRandom);
        autoBroadcast.add("messages", toArray(autoBroadcastMessages));
        root.add("autoBroadcast", autoBroadcast);

        JsonObject spawn = new JsonObject();
        spawn.addProperty("set", false);
        spawn.addProperty("world", "");
        spawn.addProperty("x", 0.0);
        spawn.addProperty("y", 0.0);
        spawn.addProperty("z", 0.0);
        spawn.addProperty("yaw", 0.0);
        spawn.addProperty("pitch", 0.0);
        spawn.addProperty("cooldownSeconds", DEFAULT_COMMAND_COOLDOWN_SECONDS);
        spawn.addProperty("warmupSeconds", spawnWarmupSeconds);
        root.add("spawn", spawn);

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

        JsonObject general = new JsonObject();
        general.addProperty("spawnFallbackToWorldDefault", true);
        general.addProperty("language", languageCode);
        root.add("general", general);

        return root;
    }

    public void load() {
        try {
            String content = Files.readString(configPath, StandardCharsets.UTF_8);
            root = gson.fromJson(content, JsonObject.class);
            if (root == null) root = buildDefaultConfig();
            JsonObject defaults = buildDefaultConfig();
            boolean changed = mergeDefaults(root, defaults);
            if (!root.has("autoBroadcast") || !root.get("autoBroadcast").isJsonObject()) {
                root.add("autoBroadcast", defaults.get("autoBroadcast"));
                changed = true;
            }

            JsonObject general = obj(root, "general");
            useWorldDefaultSpawnIfUnset = bool(general, "spawnFallbackToWorldDefault", true);
            languageCode = str(general, "language", languageCode).toLowerCase();

            JsonObject tpa = obj(root, "tpa");
            tpaRequestTimeoutSeconds = intVal(tpa, "timeoutSeconds", 60);
            tpaWarmupSeconds = intVal(tpa, "warmupSeconds", tpaWarmupSeconds);

            JsonObject rtp = obj(root, "rtp");
            rtpMaxDistance = intVal(rtp, "radius", 5000);
            rtpMinDistance = intVal(rtp, "minRadius", rtpMinDistance);
            rtpWarmupSeconds = intVal(rtp, "warmupSeconds", rtpWarmupSeconds);

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
            homesEnabled = bool(features, "homes", true);
            warpsEnabled = bool(features, "warps", true);
            kitsEnabled = bool(features, "kits", true);
            msgEnabled = bool(features, "msg", true);
            nearEnabled = bool(features, "near", true);
            motdEnabled = bool(features, "motd", true);
            rulesEnabled = bool(features, "rules", true);
            rtpEnabled = bool(features, "rtp", true);
            broadcastEnabled = bool(features, "broadcast", true);
            spawnEnabled = bool(features, "spawn", true);
            tpaEnabled = bool(features, "tpa", true);
            adminChatEnabled = bool(features, "adminChat", true);
            if (!features.has("near")) {
                nearEnabled = bool(near, "enabled", nearEnabled);
            }
            if (!features.has("rtp")) {
                rtpEnabled = bool(obj(root, "rtp"), "enabled", rtpEnabled);
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

            JsonObject motd = obj(root, "motd");
            motdEnabled = bool(motd, "enabled", motdEnabled);
            motdShowOnJoin = bool(motd, "showOnJoin", motdShowOnJoin);
            motdMessages = list(motd, "messages", motdMessages);

            JsonObject rulesObj = obj(root, "rules");
            rulesEnabled = bool(rulesObj, "enabled", rulesEnabled);
            rules = list(rulesObj, "messages", rules);

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

            if (changed) {
                applyFieldsToRoot();
                save();
                Log.info("Updated config.json with new defaults at: " + configPath);
            }
            setVersionFromPlugin();
        } catch (Exception e) {
            Log.warn("Failed to load config.json, using defaults: " + e.getMessage());
            root = buildDefaultConfig();
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

    public int getRtpMaxDistance() {
        return rtpMaxDistance;
    }

    public int getRtpMinDistance() {
        return rtpMinDistance;
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

    public int getRtpWarmupSeconds() {
        return Math.max(0, rtpWarmupSeconds);
    }

    public boolean isHomesEnabled() {
        return homesEnabled;
    }

    public boolean isWarpsEnabled() {
        return warpsEnabled;
    }

    public boolean isKitsEnabled() {
        return kitsEnabled;
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
            Files.writeString(configPath, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.warn("Failed to save config.json: " + e.getMessage());
        }
    }

    private void applyFieldsToRoot() {
        if (root == null) root = buildDefaultConfig();

        root.addProperty("version", PluginInfoUtil.getVersion());

        JsonObject general = obj(root, "general");
        general.addProperty("spawnFallbackToWorldDefault", useWorldDefaultSpawnIfUnset);
        general.addProperty("language", languageCode);

        JsonObject tpa = obj(root, "tpa");
        tpa.addProperty("timeoutSeconds", tpaRequestTimeoutSeconds);
        tpa.addProperty("warmupSeconds", tpaWarmupSeconds);
        tpa.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.TPA));

        JsonObject rtp = obj(root, "rtp");
        rtp.addProperty("radius", rtpMaxDistance);
        rtp.addProperty("minRadius", rtpMinDistance);
        rtp.addProperty("enabled", rtpEnabled);
        rtp.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.RTP));
        rtp.addProperty("warmupSeconds", rtpWarmupSeconds);
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
        features.addProperty("homes", homesEnabled);
        features.addProperty("warps", warpsEnabled);
        features.addProperty("kits", kitsEnabled);
        features.addProperty("msg", msgEnabled);
        features.addProperty("near", nearEnabled);
        features.addProperty("motd", motdEnabled);
        features.addProperty("rules", rulesEnabled);
        features.addProperty("rtp", rtpEnabled);
        features.addProperty("broadcast", broadcastEnabled);
        features.addProperty("spawn", spawnEnabled);
        features.addProperty("tpa", tpaEnabled);
        features.addProperty("adminChat", adminChatEnabled);

        JsonObject welcome = obj(root, "welcomeMessage");
        welcome.addProperty("enabled", welcomeEnabled);
        welcome.addProperty("broadcastToAll", welcomeBroadcastToAll);
        welcome.add("messages", toArray(welcomeMessages));

        JsonObject joinQuit = obj(root, "joinAndQuit");
        joinQuit.addProperty("enabled", joinQuitEnabled);
        joinQuit.add("joinMessages", toArray(joinMessages));
        joinQuit.add("quitMessages", toArray(quitMessages));

        JsonObject motd = obj(root, "motd");
        motd.addProperty("enabled", motdEnabled);
        motd.addProperty("showOnJoin", motdShowOnJoin);
        motd.add("messages", toArray(motdMessages));

        JsonObject rulesObj = obj(root, "rules");
        rulesObj.addProperty("enabled", rulesEnabled);
        rulesObj.add("messages", toArray(rules));

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
        homes.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.HOME));
        homes.addProperty("warmupSeconds", homeWarmupSeconds);

        JsonObject warps = obj(root, "warps");
        warps.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.WARP));
        warps.addProperty("warmupSeconds", warpWarmupSeconds);

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
        spawn.addProperty("set", spawnSet);
        spawn.addProperty("world", spawnWorld);
        spawn.addProperty("x", spawnX);
        spawn.addProperty("y", spawnY);
        spawn.addProperty("z", spawnZ);
        spawn.addProperty("yaw", spawnYaw);
        spawn.addProperty("pitch", spawnPitch);
        spawn.addProperty("cooldownSeconds", getCooldownSeconds(CooldownKeys.SPAWN));
        spawn.addProperty("warmupSeconds", spawnWarmupSeconds);

        JsonObject storage = obj(root, "storage");
        storage.addProperty("type", storageType);
        storage.addProperty("sqliteFile", sqliteFile);
        storage.addProperty("mysqlHost", mysqlHost);
        storage.addProperty("mysqlPort", mysqlPort);
        storage.addProperty("mysqlDatabase", mysqlDatabase);
        storage.addProperty("mysqlUser", mysqlUser);
        storage.addProperty("mysqlPassword", mysqlPassword);
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
    private JsonArray toArray(@Nonnull List<String> values) {
        JsonArray arr = new JsonArray();
        for (String value : values) arr.add(value);
        return arr;
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
}
