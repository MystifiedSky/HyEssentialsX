package xyz.thelegacyvoyage.hyessentialsx;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import xyz.thelegacyvoyage.hyessentialsx.commands.plugin.HyEssentialsXPluginCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.teleport.BackCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.AdminChatCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.BroadcastCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.ClearChatCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.MsgCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.ReplyCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.cheat.FlyCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.cheat.GodCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.cheat.HealCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.home.DelHomeCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.home.HomeCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.home.HomesCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.home.SetHomeCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.inventory.ClearInventoryCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.inventory.RepairCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.importer.ImportHomesCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.kit.KitCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.kit.KitCreateCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.kit.KitDeleteCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.kit.KitsCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.ListCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.MotdCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.NearCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.RankupCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.RulesCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.SeenCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.SleepPercentCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.WhoisCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.DayCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.NightCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.AfkCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.MuteCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.TempBanCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.UnbanCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.UnmuteCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.FreecamCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.VanishCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.spawn.DelSpawnCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.spawn.SetSpawnCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.spawn.SpawnCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.cheat.InfiniteStaminaCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.teleport.JumpToCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.teleport.RtpCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.tpa.TpahereAllCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.tpa.TpahereCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.tpa.TphereCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.teleport.TopCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.tpa.TpaAcceptCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.tpa.TpaCancelCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.tpa.TpaCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.tpa.TpaDenyCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.tpa.TpaIgnoreCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.warp.DelWarpCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.warp.SetWarpCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.warp.WarpCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.warp.WarpsCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.custom.CustomTextCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.economy.BalanceTopCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.economy.MoneyCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.economy.PayCommand;
import xyz.thelegacyvoyage.hyessentialsx.listeners.ChatModerationListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.CleanupListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.DeathBackListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.DeathMessageListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.DeathSpawnListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.EconomyRewardListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.FlyNoFallListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.GodHealthListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.InfiniteStaminaListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.SleepPercentListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.AfkListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.PlayerDataListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.PlayerListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.PlayerVisibilityListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.SpawnProtectionListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.TeleportWarmupListener;
import xyz.thelegacyvoyage.hyessentialsx.managers.AdminChatManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.AfkManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.BanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreecamManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.GodManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.InfiniteStaminaManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MessageManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MuteManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PaycheckManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.RankupManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SleepPercentManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.AutoBroadcastManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CustomCommandManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.LanguageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class HyEssentialsXPlugin extends JavaPlugin {

    private static HyEssentialsXPlugin instance;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private StorageManager storage;
    private SpawnManager spawnManager;
    private ConfigManager configManager;
    private TPManager tpManager;
    private BackManager backManager;
    private FlyManager flyManager;
    private GodManager godManager;
    private InfiniteStaminaManager staminaManager;
    private HomeManager homeManager;
    private WarpManager warpManager;
    private KitManager kitManager;
    private MessageManager messageManager;
    private AdminChatManager adminChatManager;
    private AfkManager afkManager;
    private MuteManager muteManager;
    private BanManager banManager;
    private FreecamManager freecamManager;
    private VanishManager vanishManager;
    private SleepPercentManager sleepPercentManager;
    private EconomyManager economyManager;
    private PaycheckManager paycheckManager;
    private PlaytimeManager playtimeManager;
    private RankupManager rankupManager;
    private CustomCommandManager customCommandManager;
    private AutoBroadcastManager autoBroadcastManager;
    private CommandCooldownManager cooldownManager;
    private LanguageManager languageManager;
    private Path dataDirectory;


    public HyEssentialsXPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static HyEssentialsXPlugin getInstance() {
        return instance;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public synchronized void reloadPlugin() {
        Log.info("[HyEssentialsX] Reloading...");
        if (configManager != null) {
            configManager.reload();
        }
        if (languageManager != null && configManager != null) {
            languageManager.reload(configManager.getLanguage());
        }
        if (customCommandManager != null) {
            customCommandManager.reload();
        }
        if (storage != null) {
            storage.reloadCaches();
        }
        if (spawnManager != null) {
            spawnManager.syncWorldSpawnProvider();
        }
        if (autoBroadcastManager != null) {
            autoBroadcastManager.shutdown();
        }
        autoBroadcastManager = new AutoBroadcastManager(configManager);
        autoBroadcastManager.start();
        if (rankupManager != null) {
            rankupManager.shutdown();
        }
        rankupManager = new RankupManager(configManager, economyManager, storage, playtimeManager);
        rankupManager.start();
        if (paycheckManager != null) {
            paycheckManager.shutdown();
        }
        paycheckManager = new PaycheckManager(configManager, economyManager, storage);
        paycheckManager.start();
        if (afkManager != null) {
            afkManager.shutdown();
        }
        afkManager = new AfkManager(configManager);
        afkManager.start();
        unregisterHyCommands();
        registerCommands();
        Log.info("[HyEssentialsX] Reload complete.");
    }

    @Override
    protected void setup() {
        Log.init(LOGGER);
        Log.info("[HyEssentialsX] Setting up...");
        dataDirectory = resolveDataDirectory();
        configManager = new ConfigManager(dataDirectory);
        Log.info("[HyEssentialsX] Config path: " + dataDirectory.resolve("config.json"));
        Log.info("[HyEssentialsX] AutoBroadcast present: " + configManager.hasAutoBroadcastSection());

        storage = new StorageManager(dataDirectory, configManager);
        languageManager = new LanguageManager(dataDirectory, configManager, storage);
        Messages.setLanguageManager(languageManager);
        cooldownManager = new CommandCooldownManager(configManager, storage);
        spawnManager = new SpawnManager(configManager);
        tpManager = new TPManager(configManager.getTpaRequestTimeoutSeconds() * 1000L);
        backManager = new BackManager(storage);
        flyManager = new FlyManager();
        godManager = new GodManager();
        staminaManager = new InfiniteStaminaManager();
        homeManager = new HomeManager(storage);
        warpManager = new WarpManager(storage);
        kitManager = new KitManager(storage);
        messageManager = new MessageManager();
        adminChatManager = new AdminChatManager();
        afkManager = new AfkManager(configManager);
        muteManager = new MuteManager(storage);
        banManager = new BanManager(storage);
        freecamManager = new FreecamManager();
        vanishManager = new VanishManager();
        sleepPercentManager = new SleepPercentManager(configManager);
        economyManager = new EconomyManager(storage, configManager);
        paycheckManager = new PaycheckManager(configManager, economyManager, storage);
        playtimeManager = new PlaytimeManager(storage);
        rankupManager = new RankupManager(configManager, economyManager, storage, playtimeManager);
        customCommandManager = new CustomCommandManager(dataDirectory);
        autoBroadcastManager = new AutoBroadcastManager(configManager);

        Log.info("[HyEssentialsX] Setup complete!");
    }


    @Override
    protected void start() {
        registerCommands();
        registerListeners();
        registerWorldHooks();

        godManager.clearAll();
        staminaManager.clearAll();
        autoBroadcastManager.start();
        afkManager.start();
        sleepPercentManager.start();
        rankupManager.start();
        paycheckManager.start();

        Log.info("[HyEssentialsX] Started! Use /hyessentialsx help");
    }

    @Override
    protected void shutdown() {
        Log.info("[HyEssentialsX] Shutting down...");
        if (autoBroadcastManager != null) autoBroadcastManager.shutdown();
        if (afkManager != null) afkManager.shutdown();
        if (sleepPercentManager != null) sleepPercentManager.shutdown();
        if (rankupManager != null) rankupManager.shutdown();
        if (paycheckManager != null) paycheckManager.shutdown();
        if (storage != null) storage.shutdown();
        instance = null;
    }

    private void registerCommands() {
        getCommandRegistry().registerCommand(new HyEssentialsXPluginCommand(storage, dataDirectory, languageManager));
        if (configManager.isSpawnEnabled()) {
            getCommandRegistry().registerCommand(new SpawnCommand(spawnManager, backManager, tpManager, configManager, cooldownManager));
            getCommandRegistry().registerCommand(new SetSpawnCommand(spawnManager, configManager));
            getCommandRegistry().registerCommand(new DelSpawnCommand(spawnManager, configManager));
        }
        if (configManager.isHomesEnabled()) {
            getCommandRegistry().registerCommand(new SetHomeCommand(homeManager, configManager));
            getCommandRegistry().registerCommand(new HomeCommand(homeManager, tpManager, configManager, cooldownManager, backManager));
            getCommandRegistry().registerCommand(new HomesCommand(homeManager, tpManager, configManager, cooldownManager, backManager));
            getCommandRegistry().registerCommand(new DelHomeCommand(homeManager, configManager));
        }
        if (configManager.isWarpsEnabled()) {
            getCommandRegistry().registerCommand(new SetWarpCommand(warpManager, configManager));
            getCommandRegistry().registerCommand(new WarpCommand(warpManager, tpManager, configManager, cooldownManager, backManager));
            getCommandRegistry().registerCommand(new WarpsCommand(warpManager, tpManager, configManager, cooldownManager, backManager));
            getCommandRegistry().registerCommand(new DelWarpCommand(warpManager, configManager));
        }
        if (configManager.isKitsEnabled()) {
            getCommandRegistry().registerCommand(new KitCreateCommand(kitManager, configManager));
            getCommandRegistry().registerCommand(new KitCommand(kitManager, configManager));
            getCommandRegistry().registerCommand(new KitsCommand(kitManager, configManager));
            getCommandRegistry().registerCommand(new KitDeleteCommand(kitManager, configManager));
        }
        if (configManager.isMsgEnabled()) {
            getCommandRegistry().registerCommand(new MsgCommand(messageManager, configManager));
            getCommandRegistry().registerCommand(new ReplyCommand(messageManager, configManager));
        }
        if (configManager.isAdminChatEnabled()) {
            getCommandRegistry().registerCommand(new AdminChatCommand(adminChatManager, configManager));
        }
        if (configManager.isBroadcastEnabled()) {
            getCommandRegistry().registerCommand(new BroadcastCommand(configManager));
        }
        getCommandRegistry().registerCommand(new ClearChatCommand());
        if (configManager.isEconomyEnabled()) {
            getCommandRegistry().registerCommand(new PayCommand(economyManager));
            getCommandRegistry().registerCommand(new MoneyCommand(economyManager, storage));
            getCommandRegistry().registerCommand(new BalanceTopCommand(economyManager, storage, configManager));
        }
        if (configManager.isHomesEnabled()) {
            getCommandRegistry().registerCommand(new ImportHomesCommand(storage, dataDirectory));
        }
        if (configManager.isTpaEnabled()) {
            getCommandRegistry().registerCommand(new TpaCommand(tpManager, configManager, cooldownManager));
            getCommandRegistry().registerCommand(new TpaAcceptCommand(tpManager, backManager, configManager));
            getCommandRegistry().registerCommand(new TpaDenyCommand(tpManager, configManager));
            getCommandRegistry().registerCommand(new TpaCancelCommand(tpManager, configManager));
            getCommandRegistry().registerCommand(new TpaIgnoreCommand(tpManager, configManager));
            getCommandRegistry().registerCommand(new TpahereCommand(tpManager, configManager, cooldownManager));
            getCommandRegistry().registerCommand(new TpahereAllCommand(tpManager, configManager, cooldownManager));
            getCommandRegistry().registerCommand(new TphereCommand(backManager));
        }
        getCommandRegistry().registerCommand(new BackCommand(backManager, tpManager, configManager, cooldownManager));
        getCommandRegistry().registerCommand(new FlyCommand(flyManager, storage));
        getCommandRegistry().registerCommand(new GodCommand(godManager));
        getCommandRegistry().registerCommand(new HealCommand(cooldownManager));
        getCommandRegistry().registerCommand(new InfiniteStaminaCommand(staminaManager));
        getCommandRegistry().registerCommand(new ListCommand());
        if (configManager.isRulesEnabled()) {
            getCommandRegistry().registerCommand(new RulesCommand(configManager));
        }
        if (configManager.isMotdEnabled()) {
            getCommandRegistry().registerCommand(new MotdCommand(configManager));
        }
        if (configManager.isNearEnabled()) {
            getCommandRegistry().registerCommand(new NearCommand(configManager, cooldownManager));
        }
        if (configManager.isAfkEnabled()) {
            getCommandRegistry().registerCommand(new AfkCommand(afkManager, configManager, cooldownManager));
        }
        getCommandRegistry().registerCommand(new SleepPercentCommand(configManager));
        getCommandRegistry().registerCommand(new DayCommand());
        getCommandRegistry().registerCommand(new NightCommand());
        if (configManager.isRankupEnabled()) {
            getCommandRegistry().registerCommand(new RankupCommand(rankupManager, economyManager));
        }
        getCommandRegistry().registerCommand(new WhoisCommand(storage));
        getCommandRegistry().registerCommand(new SeenCommand(storage));
        getCommandRegistry().registerCommand(new TopCommand(backManager));
        getCommandRegistry().registerCommand(new JumpToCommand(cooldownManager, backManager));
        if (configManager.isRtpEnabled()) {
            getCommandRegistry().registerCommand(new RtpCommand(configManager, cooldownManager, tpManager, backManager));
        }
        getCommandRegistry().registerCommand(new ClearInventoryCommand());
        getCommandRegistry().registerCommand(new RepairCommand(cooldownManager));
        getCommandRegistry().registerCommand(new FreecamCommand(freecamManager));
        getCommandRegistry().registerCommand(new VanishCommand(vanishManager));
        getCommandRegistry().registerCommand(new MuteCommand(muteManager));
        getCommandRegistry().registerCommand(new UnmuteCommand(muteManager, storage));
        getCommandRegistry().registerCommand(new TempBanCommand(banManager, storage));
        getCommandRegistry().registerCommand(new UnbanCommand(banManager, storage));
        for (var entry : customCommandManager.getCommands().values()) {
            getCommandRegistry().registerCommand(
                    new CustomTextCommand(customCommandManager, entry.getName(), entry.getPermission(), entry.getAliases())
            );
        }
        Log.info("[HyEssentialsX] Commands registered");
    }

    private void registerListeners() {
        EventRegistry bus = getEventRegistry();
        new PlayerListener(configManager, storage, vanishManager).register(bus);
        new PlayerDataListener(storage, banManager, messageManager, adminChatManager, freecamManager, godManager, staminaManager, flyManager, economyManager, playtimeManager).register(bus);
        new ChatModerationListener(muteManager, adminChatManager, configManager).register(bus);
        new CleanupListener(tpManager, backManager, flyManager, godManager, staminaManager, vanishManager).register(bus);
        new AfkListener(afkManager).register(bus);
        new SpawnProtectionListener(spawnManager, configManager).register(bus);
        new SleepPercentListener(configManager).register(bus);
        Log.info("[HyEssentialsX] Listeners registered");

        new DeathBackListener(backManager).register(getEntityStoreRegistry());
        new DeathMessageListener(configManager).register(getEntityStoreRegistry());
        new DeathSpawnListener(spawnManager, configManager).register(getEntityStoreRegistry());
        new FlyNoFallListener(flyManager).register(getEntityStoreRegistry());
        new GodHealthListener(godManager).register(getEntityStoreRegistry());
        new InfiniteStaminaListener(staminaManager).register(getEntityStoreRegistry());
        new PlayerVisibilityListener(vanishManager).register(getEntityStoreRegistry());
        new TeleportWarmupListener(tpManager).register(getEntityStoreRegistry());
        new SpawnProtectionListener(spawnManager, configManager).register(getEntityStoreRegistry());
        new EconomyRewardListener(economyManager, configManager).register(getEntityStoreRegistry());
    }

    private void registerWorldHooks() {
        getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, event -> {
            // Only sync if a spawn exists; initialization will happen in /spawn as needed
            spawnManager.syncWorldSpawnProvider();
        });
    }

    @Nonnull
    private Path resolveDataDirectory() {
        Path original = getDataDirectory();
        Path parent = original.getParent();
        if (parent == null) {
            return original;
        }
        Path desired = parent.resolve("HyEssentialsX");
        if (original.getFileName() != null
                && original.getFileName().toString().equalsIgnoreCase("HyEssentialsX")) {
            return original;
        }
        try {
            if (Files.exists(desired)) {
                if (Files.exists(original)) {
                    Log.warn("[HyEssentialsX] Both old and new data folders exist. Using " + desired);
                }
                return desired;
            }
            if (Files.exists(original)) {
                try {
                    Files.move(original, desired);
                    Log.info("[HyEssentialsX] Migrated data folder to " + desired);
                } catch (Exception moveError) {
                    Log.warn("[HyEssentialsX] Failed to move data folder. Using original: " + moveError.getMessage());
                    return original;
                }
            }
            if (!Files.exists(desired)) {
                Files.createDirectories(desired);
            }
            return desired;
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to prepare data folder. Using original: " + e.getMessage());
            return original;
        }
    }

    private void unregisterHyCommands() {
        Object manager = resolveCommandManager();
        if (manager == null) {
            return;
        }
        Map<?, ?> commands = getCommandMap(manager);
        Map<?, ?> aliases = getAliasMap(manager);
        if (commands == null) {
            return;
        }
        int removedCount = 0;
        Set<Object> removed = new HashSet<>();
        @SuppressWarnings("unchecked")
        Iterator<? extends Map.Entry<?, ?>> iterator = ((Map<?, ?>) commands).entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            Object command = entry.getValue();
            if (isHyCommand(command)) {
                removed.add(command);
                iterator.remove();
                removedCount++;
            }
        }
        if (aliases != null && !aliases.isEmpty()) {
            @SuppressWarnings("unchecked")
            Iterator<? extends Map.Entry<?, ?>> aliasIterator = ((Map<?, ?>) aliases).entrySet().iterator();
            while (aliasIterator.hasNext()) {
                Map.Entry<?, ?> entry = aliasIterator.next();
                Object value = entry.getValue();
                if (removed.contains(value) || isHyCommand(value)) {
                    aliasIterator.remove();
                }
            }
        }
        if (removedCount > 0) {
            Log.info("[HyEssentialsX] Unregistered " + removedCount + " commands");
        }
    }

    private boolean isHyCommand(Object command) {
        if (command == null) {
            return false;
        }
        String className = command.getClass().getName();
        return className.startsWith("xyz.thelegacyvoyage.hyessentialsx.");
    }

    private Object resolveCommandManager() {
        Object registry = getCommandRegistry();
        if (registry == null) {
            return null;
        }
        String name = registry.getClass().getName();
        if (name.contains("CommandManager")) {
            return registry;
        }
        Field field = findField(registry.getClass(), "commandManager", "manager");
        if (field != null) {
            try {
                field.setAccessible(true);
                Object value = field.get(registry);
                if (value != null) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            for (var method : registry.getClass().getMethods()) {
                if (method.getParameterCount() == 0 && method.getName().toLowerCase().contains("commandmanager")) {
                    Object value = method.invoke(registry);
                    if (value != null) {
                        return value;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return registry;
    }

    private Map<?, ?> getCommandMap(Object manager) {
        Field field = findField(manager.getClass(),
                "commandRegistration",
                "commandRegistrations",
                "commandMap",
                "commands",
                "registeredCommands");
        Map<?, ?> map = readMapField(manager, field);
        if (map != null) {
            return map;
        }
        return findFirstMap(manager);
    }

    private Map<?, ?> getAliasMap(Object manager) {
        Field field = findField(manager.getClass(),
                "aliases",
                "aliasMap",
                "commandAliases",
                "aliasMappings");
        Map<?, ?> map = readMapField(manager, field);
        if (map != null) {
            return map;
        }
        return null;
    }

    private Map<?, ?> readMapField(Object target, Field field) {
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            Object value = field.get(target);
            if (value instanceof Map<?, ?> map) {
                return map;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Map<?, ?> findFirstMap(Object target) {
        for (Field field : target.getClass().getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            Map<?, ?> map = readMapField(target, field);
            if (map != null) {
                return map;
            }
        }
        return null;
    }

    private Field findField(Class<?> type, String... names) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (String name : names) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

}
