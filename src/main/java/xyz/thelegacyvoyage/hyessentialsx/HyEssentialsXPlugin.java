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
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.RulesCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.SeenCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.WhoisCommand;
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
import xyz.thelegacyvoyage.hyessentialsx.listeners.ChatModerationListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.CleanupListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.DeathBackListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.DeathSpawnListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.FlyNoFallListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.GodHealthListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.InfiniteStaminaListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.AfkListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.PlayerDataListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.PlayerListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.PlayerVisibilityListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.TeleportWarmupListener;
import xyz.thelegacyvoyage.hyessentialsx.managers.AdminChatManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.AfkManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.BanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreecamManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.GodManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.InfiniteStaminaManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MessageManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MuteManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.AutoBroadcastManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CustomCommandManager;
import xyz.thelegacyvoyage.hyessentialsx.util.LanguageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.StorageManager;

import javax.annotation.Nonnull;

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
    private CustomCommandManager customCommandManager;
    private AutoBroadcastManager autoBroadcastManager;
    private CommandCooldownManager cooldownManager;
    private LanguageManager languageManager;


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
        if (afkManager != null) {
            afkManager.shutdown();
        }
        afkManager = new AfkManager(configManager);
        afkManager.start();
        Log.info("[HyEssentialsX] Reload complete.");
    }

    @Override
    protected void setup() {
        Log.init(LOGGER);
        Log.info("[HyEssentialsX] Setting up...");
        configManager = new ConfigManager(getDataDirectory());
        Log.info("[HyEssentialsX] Config path: " + getDataDirectory().resolve("config.json"));
        Log.info("[HyEssentialsX] AutoBroadcast present: " + configManager.hasAutoBroadcastSection());

        storage = new StorageManager(getDataDirectory(), configManager);
        languageManager = new LanguageManager(getDataDirectory(), configManager, storage);
        Messages.setLanguageManager(languageManager);
        cooldownManager = new CommandCooldownManager(configManager, storage);
        spawnManager = new SpawnManager(configManager);
        tpManager = new TPManager(configManager.getTpaRequestTimeoutSeconds() * 1000L);
        backManager = new BackManager();
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
        customCommandManager = new CustomCommandManager(getDataDirectory());
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

        Log.info("[HyEssentialsX] Started! Use /hyessentialsx help");
    }

    @Override
    protected void shutdown() {
        Log.info("[HyEssentialsX] Shutting down...");
        if (autoBroadcastManager != null) autoBroadcastManager.shutdown();
        if (afkManager != null) afkManager.shutdown();
        if (storage != null) storage.shutdown();
        instance = null;
    }

    private void registerCommands() {
        getCommandRegistry().registerCommand(new HyEssentialsXPluginCommand(storage, getDataDirectory(), languageManager));
        getCommandRegistry().registerCommand(new SpawnCommand(spawnManager, backManager, tpManager, configManager, cooldownManager));
        getCommandRegistry().registerCommand(new SetSpawnCommand(spawnManager, configManager));
        getCommandRegistry().registerCommand(new DelSpawnCommand(spawnManager, configManager));
        getCommandRegistry().registerCommand(new SetHomeCommand(homeManager, configManager));
        getCommandRegistry().registerCommand(new HomeCommand(homeManager, tpManager, configManager, cooldownManager));
        getCommandRegistry().registerCommand(new HomesCommand(homeManager, tpManager, configManager, cooldownManager));
        getCommandRegistry().registerCommand(new DelHomeCommand(homeManager, configManager));
        getCommandRegistry().registerCommand(new SetWarpCommand(warpManager, configManager));
        getCommandRegistry().registerCommand(new WarpCommand(warpManager, tpManager, configManager, cooldownManager));
        getCommandRegistry().registerCommand(new WarpsCommand(warpManager, configManager));
        getCommandRegistry().registerCommand(new DelWarpCommand(warpManager, configManager));
        getCommandRegistry().registerCommand(new KitCreateCommand(kitManager, configManager));
        getCommandRegistry().registerCommand(new KitCommand(kitManager, configManager));
        getCommandRegistry().registerCommand(new KitsCommand(kitManager, configManager));
        getCommandRegistry().registerCommand(new KitDeleteCommand(kitManager, configManager));
        getCommandRegistry().registerCommand(new MsgCommand(messageManager, configManager));
        getCommandRegistry().registerCommand(new ReplyCommand(messageManager, configManager));
        getCommandRegistry().registerCommand(new AdminChatCommand(adminChatManager, configManager));
        getCommandRegistry().registerCommand(new BroadcastCommand(configManager));
        getCommandRegistry().registerCommand(new ClearChatCommand());
        getCommandRegistry().registerCommand(new ImportHomesCommand(storage, getDataDirectory()));
        getCommandRegistry().registerCommand(new TpaCommand(tpManager, configManager, cooldownManager));
        getCommandRegistry().registerCommand(new TpaAcceptCommand(tpManager, backManager, configManager));
        getCommandRegistry().registerCommand(new TpaDenyCommand(tpManager, configManager));
        getCommandRegistry().registerCommand(new TpaCancelCommand(tpManager, configManager));
        getCommandRegistry().registerCommand(new TpaIgnoreCommand(tpManager, configManager));
        getCommandRegistry().registerCommand(new TpahereCommand(tpManager, configManager, cooldownManager));
        getCommandRegistry().registerCommand(new TpahereAllCommand(tpManager, configManager, cooldownManager));
        getCommandRegistry().registerCommand(new TphereCommand());
        getCommandRegistry().registerCommand(new BackCommand(backManager, tpManager, configManager, cooldownManager));
        getCommandRegistry().registerCommand(new FlyCommand(flyManager));
        getCommandRegistry().registerCommand(new GodCommand(godManager));
        getCommandRegistry().registerCommand(new HealCommand(cooldownManager));
        getCommandRegistry().registerCommand(new InfiniteStaminaCommand(staminaManager));
        getCommandRegistry().registerCommand(new ListCommand());
        getCommandRegistry().registerCommand(new RulesCommand(configManager));
        getCommandRegistry().registerCommand(new MotdCommand(configManager));
        getCommandRegistry().registerCommand(new NearCommand(configManager, cooldownManager));
        getCommandRegistry().registerCommand(new AfkCommand(afkManager, configManager, cooldownManager));
        getCommandRegistry().registerCommand(new WhoisCommand(storage));
        getCommandRegistry().registerCommand(new SeenCommand(storage));
        getCommandRegistry().registerCommand(new TopCommand());
        getCommandRegistry().registerCommand(new JumpToCommand(cooldownManager));
        getCommandRegistry().registerCommand(new RtpCommand(configManager, cooldownManager, tpManager));
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
        new PlayerListener(configManager, storage).register(bus);
        new PlayerDataListener(storage, banManager, messageManager, adminChatManager, freecamManager, godManager, staminaManager).register(bus);
        new ChatModerationListener(muteManager, adminChatManager, configManager).register(bus);
        new CleanupListener(tpManager, backManager, flyManager, godManager, staminaManager, vanishManager).register(bus);
        new AfkListener(afkManager).register(bus);
        Log.info("[HyEssentialsX] Listeners registered");

        new DeathBackListener(backManager).register(getEntityStoreRegistry());
        new DeathSpawnListener(spawnManager, configManager).register(getEntityStoreRegistry());
        new FlyNoFallListener(flyManager).register(getEntityStoreRegistry());
        new GodHealthListener(godManager).register(getEntityStoreRegistry());
        new InfiniteStaminaListener(staminaManager).register(getEntityStoreRegistry());
        new PlayerVisibilityListener(vanishManager).register(getEntityStoreRegistry());
        new TeleportWarmupListener(tpManager).register(getEntityStoreRegistry());
    }

    private void registerWorldHooks() {
        getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, event -> {
            // Only sync if a spawn exists; initialization will happen in /spawn as needed
            spawnManager.syncWorldSpawnProvider();
        });
    }

}
