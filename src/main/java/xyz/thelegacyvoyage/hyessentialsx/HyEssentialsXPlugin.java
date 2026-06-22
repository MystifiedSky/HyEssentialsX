package xyz.thelegacyvoyage.hyessentialsx;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import xyz.thelegacyvoyage.hyessentialsx.commands.plugin.HyEssentialsXPluginCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.teleport.BackCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.AdminChatCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.BroadcastCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.ClearChatCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.IgnoreCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.MailCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.MsgCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.ReplyCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.SocialSpyCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.chat.UnignoreCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.combat.CombatLogCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.cheat.FlyCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.cheat.FlySpeedCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.cheat.GodCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.cheat.HealCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.home.DelHomeCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.home.HomeCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.home.HomesCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.home.SetHomeCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.inventory.ClearInventoryCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.inventory.InvSeeCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.inventory.MoreCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.inventory.RepairCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.inventory.TrashCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.importer.ImportHomesCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.kit.KitCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.kit.KitCreateCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.kit.KitDeleteCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.kit.KitEditCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.kit.KitEditOrderCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.kit.KitsCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.ListCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.DiscordCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.MotdCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.NearCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.PlaytimeCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.RankupCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.RulesCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.SeenCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.SleepPercentCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.WhoisCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.DayCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.NightCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.misc.AfkCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.MuteCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.BanCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.IpBanCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.IpHistoryCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.BanListCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.TempBanCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.UnipBanCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.UnbanCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.UnmuteCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.FreecamCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.FreezeCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.UnfreezeCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.moderation.VanishCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.spawn.DelSpawnCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.spawn.SetSpawnCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.spawn.SpawnCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.cheat.InfiniteStaminaCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.teleport.JumpToCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.teleport.RtpCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.teleport.ThruCommand;
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
import xyz.thelegacyvoyage.hyessentialsx.commands.economy.EcoAdminCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.economy.EcoGuiCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.economy.MoneyCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.economy.PayCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.shop.ShopCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.shop.PlayerShopCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.hologram.HologramCommand;
import xyz.thelegacyvoyage.hyessentialsx.commands.scoreboard.ScoreboardCommand;
import xyz.thelegacyvoyage.hyessentialsx.listeners.ChatModerationListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.CleanupListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.CombatLogListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.DeathBackListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.DeathMessageListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.DeathSpawnListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.EconomyHudListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.EconomyRewardListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.FlyNoFallListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.GodHealthListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.InfiniteStaminaListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.FreezeListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.IpBanListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.SleepPercentListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.AfkListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.PlayerDataListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.PlayerListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.PlayerVisibilityListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.RespawnTeleportListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.ScoreboardListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.SpawnProtectionListener;
import xyz.thelegacyvoyage.hyessentialsx.listeners.TeleportWarmupListener;
import xyz.thelegacyvoyage.hyessentialsx.managers.AdminChatManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.AfkManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.BanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.BackManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CombatLogManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyAuditManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyHudManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreecamManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreezeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.GodManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.HomeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.InfiniteStaminaManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.IpBanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.IgnoreManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MessageManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MailManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MuteManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PaycheckManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeRewardManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.RankupManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SleepPercentManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SocialSpyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ScoreboardManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopNpcFixTask;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopNpcInteractionRegistry;
import xyz.thelegacyvoyage.hyessentialsx.managers.TPManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.placeholders.HyEssentialsXPlaceholderExpansion;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.AutoBroadcastManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CustomCommandManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.LanguageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ServerVersion;
import xyz.thelegacyvoyage.hyessentialsx.util.VaultUnlockedIntegration;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.api.DefaultHyEssentialsXApi;
import xyz.thelegacyvoyage.hyessentialsx.api.HyEssentialsXApiProvider;

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
    private SocialSpyManager socialSpyManager;
    private IgnoreManager ignoreManager;
    private AdminChatManager adminChatManager;
    private MailManager mailManager;
    private AfkManager afkManager;
    private MuteManager muteManager;
    private BanManager banManager;
    private IpBanManager ipBanManager;
    private CombatLogManager combatLogManager;
    private FreecamManager freecamManager;
    private FreezeManager freezeManager;
    private VanishManager vanishManager;
    private SleepPercentManager sleepPercentManager;
    private EconomyManager economyManager;
    private EconomyAuditManager economyAuditManager;
    private EconomyHudManager economyHudManager;
    private PaycheckManager paycheckManager;
    private PlaytimeManager playtimeManager;
    private PlaytimeRewardManager playtimeRewardManager;
    private ScoreboardManager scoreboardManager;
    private RankupManager rankupManager;
    private CustomCommandManager customCommandManager;
    private AutoBroadcastManager autoBroadcastManager;
    private CommandCooldownManager cooldownManager;
    private LanguageManager languageManager;
    private ShopManager shopManager;
    private ShopNpcFixTask shopNpcFixTask;
    private xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache shopAdminDraftCache;
    private HologramService hologramService;
    private HyEssentialsXPlaceholderExpansion placeholderExpansion;
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
        paycheckManager = new PaycheckManager(configManager, economyManager, storage, playtimeManager);
        paycheckManager.start();
        if (playtimeRewardManager != null) {
            playtimeRewardManager.shutdown();
        }
        playtimeRewardManager = new PlaytimeRewardManager(configManager, playtimeManager, storage);
        playtimeRewardManager.start();
        if (afkManager != null) {
            afkManager.shutdown();
        }
        afkManager = new AfkManager(configManager);
        afkManager.start();
        if (scoreboardManager != null) {
            scoreboardManager.shutdown();
        }
        scoreboardManager = new ScoreboardManager(configManager, storage, economyManager, playtimeManager, dataDirectory);
        scoreboardManager.start();
        scoreboardManager.refreshAll();
        if (economyHudManager != null) {
            economyHudManager.start();
            economyHudManager.refreshAll();
        }
        unregisterHyCommands();
        registerCommands();
        if (combatLogManager != null && configManager != null) {
            combatLogManager.setConfig(configManager);
            if (configManager.isCombatLogEnabled()) {
                combatLogManager.wrapBlockedCommands(getCommandRegistry());
            } else {
                combatLogManager.unwrapBlockedCommands(getCommandRegistry());
            }
        }
        if (freezeManager != null) {
            freezeManager.wrapAllCommands(getCommandRegistry());
        }
        if (shopNpcFixTask != null) {
            shopNpcFixTask.start();
        }
        registerPlaceholderExpansion();
        VaultUnlockedIntegration.refresh();
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
        Log.info("[HyEssentialsX] Packet API: write(" + ServerVersion.packetWriteSignature() + ")"
                + " | ToClientPacket=" + ServerVersion.hasToClientPacket()
                + " | runtime=" + ServerVersion.runtimeVersionHint());

        storage = new StorageManager(dataDirectory, configManager);
        languageManager = new LanguageManager(dataDirectory, configManager, storage);
        Messages.setLanguageManager(languageManager);
        Messages.setConfigManager(configManager);
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
        socialSpyManager = new SocialSpyManager();
        ignoreManager = new IgnoreManager(storage);
        adminChatManager = new AdminChatManager();
        mailManager = new MailManager(storage, configManager);
        afkManager = new AfkManager(configManager);
        muteManager = new MuteManager(storage);
        banManager = new BanManager(storage);
        ipBanManager = new IpBanManager(storage);
        combatLogManager = new CombatLogManager(configManager);
        freecamManager = new FreecamManager();
        freezeManager = new FreezeManager(storage);
        vanishManager = new VanishManager();
        sleepPercentManager = new SleepPercentManager(configManager);
        economyManager = new EconomyManager(storage, configManager);
        economyAuditManager = new EconomyAuditManager(dataDirectory);
        economyHudManager = new EconomyHudManager(configManager, storage, economyManager);
        playtimeManager = new PlaytimeManager(storage);
        playtimeRewardManager = new PlaytimeRewardManager(configManager, playtimeManager, storage);
        paycheckManager = new PaycheckManager(configManager, economyManager, storage, playtimeManager);
        scoreboardManager = new ScoreboardManager(configManager, storage, economyManager, playtimeManager, dataDirectory);
        rankupManager = new RankupManager(configManager, economyManager, storage, playtimeManager);
        customCommandManager = new CustomCommandManager(dataDirectory);
        autoBroadcastManager = new AutoBroadcastManager(configManager);
        shopManager = new ShopManager(storage);
        shopNpcFixTask = new ShopNpcFixTask(shopManager);
        shopAdminDraftCache = new xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache();
        ShopNpcInteractionRegistry.register(this, shopManager, economyManager, configManager, shopAdminDraftCache);
        HyEssentialsXApiProvider.register(new DefaultHyEssentialsXApi(economyManager, playtimeManager, shopManager));
        hologramService = new HologramService(this, dataDirectory, configManager);
        VaultUnlockedIntegration.configure(economyManager, storage);

        Log.info("[HyEssentialsX] Setup complete!");
    }


    @Override
    protected void start() {
        if (combatLogManager != null) {
            combatLogManager.setShutdownInProgress(false);
        }
        if (hologramService != null && configManager != null && configManager.isHologramsEnabled()) {
            hologramService.start();
        }
        registerCommands();
        if (freezeManager != null) {
            freezeManager.wrapAllCommands(getCommandRegistry());
        }
        registerListeners();
        registerWorldHooks();
        if (shopNpcFixTask != null) {
            shopNpcFixTask.start();
        }
        godManager.clearAll();
        staminaManager.clearAll();
        autoBroadcastManager.start();
        afkManager.start();
        sleepPercentManager.start();
        rankupManager.start();
        paycheckManager.start();
        if (playtimeRewardManager != null) {
            playtimeRewardManager.start();
        }
        if (scoreboardManager != null) {
            scoreboardManager.start();
            scoreboardManager.refreshAll();
        }
        if (economyHudManager != null) {
            economyHudManager.start();
            economyHudManager.refreshAll();
        }
        registerPlaceholderExpansion();
        VaultUnlockedIntegration.refresh();

        Log.info("[HyEssentialsX] Started! Use /hyessentialsx help");
    }

    @Override
    protected void shutdown() {
        Log.info("[HyEssentialsX] Shutting down...");
        if (combatLogManager != null) {
            combatLogManager.setShutdownInProgress(true);
            combatLogManager.unwrapBlockedCommands(getCommandRegistry());
        }
        if (freezeManager != null) {
            freezeManager.unwrapAllCommands(getCommandRegistry());
        }
        if (autoBroadcastManager != null) autoBroadcastManager.shutdown();
        if (afkManager != null) afkManager.shutdown();
        if (sleepPercentManager != null) sleepPercentManager.shutdown();
        if (rankupManager != null) rankupManager.shutdown();
        if (paycheckManager != null) paycheckManager.shutdown();
        if (playtimeRewardManager != null) playtimeRewardManager.shutdown();
        if (scoreboardManager != null) scoreboardManager.shutdown();
        if (economyHudManager != null) economyHudManager.shutdown();
        if (economyAuditManager != null) economyAuditManager.shutdown();
        if (shopNpcFixTask != null) shopNpcFixTask.stop();
        unregisterPlaceholderExpansion();
        if (hologramService != null) {
            hologramService.shutdown();
        }
        VaultUnlockedIntegration.unregister();
        if (storage != null) storage.shutdown();
        HyEssentialsXApiProvider.clear();
        instance = null;
    }

    private void registerPlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!isPlaceholderApiAvailable()) {
            return;
        }
        try {
            HyEssentialsXPlaceholderExpansion expansion = new HyEssentialsXPlaceholderExpansion(
                    this,
                    economyManager,
                    playtimeManager,
                    storage,
                    homeManager,
                    mailManager
            );
            if (expansion.register()) {
                placeholderExpansion = expansion;
                Log.info("[HyEssentialsX] PlaceholderAPI expansion registered.");
            }
        } catch (Throwable t) {
            Log.warn("[HyEssentialsX] Failed to register PlaceholderAPI expansion: " + t.getMessage());
        }
    }

    private void unregisterPlaceholderExpansion() {
        if (placeholderExpansion == null) {
            return;
        }
        try {
            placeholderExpansion.unregister();
        } catch (Throwable ignored) {
        } finally {
            placeholderExpansion = null;
        }
    }

    private boolean isPlaceholderApiAvailable() {
        try {
            Class.forName("at.helpch.placeholderapi.expansion.PlaceholderExpansion");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void registerCommands() {
        var registry = getCommandRegistry();
        java.util.function.Consumer<AbstractCommand> reg = cmd -> {
            AbstractCommand toRegister = cmd;
            if (combatLogManager != null) {
                toRegister = combatLogManager.wrapIfBlocked(cmd);
            }
            registry.registerCommand(toRegister);
        };
        reg.accept(new HyEssentialsXPluginCommand(storage, spawnManager, dataDirectory, languageManager));
        if (configManager.isCombatLogEnabled()) {
            reg.accept(new CombatLogCommand(combatLogManager, configManager));
        }
        if (configManager.isSpawnEnabled()) {
            reg.accept(new SpawnCommand(spawnManager, backManager, tpManager, configManager, cooldownManager));
            reg.accept(new SetSpawnCommand(spawnManager, configManager));
            reg.accept(new DelSpawnCommand(spawnManager, configManager));
        }
        if (configManager.isHomesEnabled()) {
            reg.accept(new SetHomeCommand(homeManager, configManager));
            reg.accept(new HomeCommand(homeManager, tpManager, configManager, cooldownManager, backManager, storage));
            reg.accept(new HomesCommand(homeManager, tpManager, configManager, cooldownManager, backManager));
            reg.accept(new DelHomeCommand(homeManager, configManager));
        }
        if (configManager.isWarpsEnabled()) {
            reg.accept(new SetWarpCommand(warpManager, configManager));
            reg.accept(new WarpCommand(warpManager, tpManager, configManager, cooldownManager, backManager));
            reg.accept(new WarpsCommand(warpManager, tpManager, configManager, cooldownManager, backManager));
            reg.accept(new DelWarpCommand(warpManager, configManager));
        }
        if (configManager.isKitsEnabled()) {
            reg.accept(new KitCreateCommand(kitManager, configManager));
            reg.accept(new KitEditCommand(kitManager, configManager));
            reg.accept(new KitEditOrderCommand(kitManager, configManager));
            reg.accept(new KitCommand(kitManager, configManager));
            reg.accept(new KitsCommand(kitManager, configManager));
            reg.accept(new KitDeleteCommand(kitManager, configManager));
        }
        if (configManager.isMsgEnabled()) {
            reg.accept(new MsgCommand(messageManager, ignoreManager, socialSpyManager, configManager));
            reg.accept(new ReplyCommand(messageManager, ignoreManager, socialSpyManager, configManager));
            reg.accept(new SocialSpyCommand(socialSpyManager));
            reg.accept(new IgnoreCommand(ignoreManager));
            reg.accept(new UnignoreCommand(ignoreManager));
        }
        if (configManager.isAdminChatEnabled()) {
            reg.accept(new AdminChatCommand(adminChatManager, configManager));
        }
        if (configManager.isBroadcastEnabled()) {
            reg.accept(new BroadcastCommand(configManager));
        }
        reg.accept(new ClearChatCommand());
        reg.accept(new MailCommand(mailManager, storage, configManager));
        if (configManager.isEconomyEnabled()) {
            reg.accept(new PayCommand(economyManager, economyAuditManager));
            reg.accept(new MoneyCommand(economyManager, storage, economyAuditManager));
            reg.accept(new BalanceTopCommand(economyManager, storage, configManager));
            if (economyHudManager != null) {
                reg.accept(new EcoGuiCommand(economyManager, economyHudManager, configManager));
                reg.accept(new EcoAdminCommand(economyManager, storage, configManager, economyHudManager, economyAuditManager));
            }
        }
        if (configManager.isHomesEnabled()) {
            reg.accept(new ImportHomesCommand(storage, dataDirectory));
        }
        if (configManager.isTpaEnabled()) {
            reg.accept(new TpaCommand(tpManager, configManager, cooldownManager));
            reg.accept(new TpaAcceptCommand(tpManager, backManager, configManager));
            reg.accept(new TpaDenyCommand(tpManager, configManager));
            reg.accept(new TpaCancelCommand(tpManager, configManager));
            reg.accept(new TpaIgnoreCommand(tpManager, configManager));
            reg.accept(new TpahereCommand(tpManager, configManager, cooldownManager));
            reg.accept(new TpahereAllCommand(tpManager, configManager, cooldownManager));
            reg.accept(new TphereCommand(backManager));
        }
        reg.accept(new BackCommand(backManager, tpManager, configManager, cooldownManager));
        reg.accept(new FlyCommand(flyManager, storage));
        reg.accept(new FlySpeedCommand(flyManager, storage, configManager));
        reg.accept(new GodCommand(godManager));
        reg.accept(new HealCommand(cooldownManager));
        reg.accept(new InfiniteStaminaCommand(staminaManager));
        reg.accept(new ListCommand());
        if (configManager.isRulesEnabled()) {
            reg.accept(new RulesCommand(configManager));
        }
        if (configManager.isMotdEnabled()) {
            reg.accept(new MotdCommand(configManager, storage));
        }
        if (configManager.isDiscordEnabled()) {
            reg.accept(new DiscordCommand(configManager));
        }
        if (configManager.isNearEnabled()) {
            reg.accept(new NearCommand(configManager, cooldownManager));
        }
        if (configManager.isAfkEnabled()) {
            reg.accept(new AfkCommand(afkManager, configManager, cooldownManager));
        }
        reg.accept(new SleepPercentCommand(configManager));
        reg.accept(new DayCommand());
        reg.accept(new NightCommand());
        reg.accept(new RankupCommand(rankupManager, economyManager, playtimeManager, playtimeRewardManager, storage, configManager));
        reg.accept(new PlaytimeCommand(playtimeManager, playtimeRewardManager, rankupManager, storage, configManager));
        if (scoreboardManager != null && configManager.isScoreboardEnabled()) {
            reg.accept(new ScoreboardCommand(scoreboardManager, configManager));
        }
        reg.accept(new WhoisCommand(storage));
        reg.accept(new IpHistoryCommand(storage));
        reg.accept(new SeenCommand(storage));
        reg.accept(new TopCommand(backManager));
        reg.accept(new JumpToCommand(cooldownManager, backManager));
        reg.accept(new ThruCommand(backManager));
        if (configManager.isRtpEnabled()) {
            reg.accept(new RtpCommand(configManager, cooldownManager, tpManager, backManager));
        }
        if (configManager.isAdminShopsEnabled()) {
            reg.accept(new ShopCommand(shopManager, economyManager, shopAdminDraftCache));
        }
        if (configManager.isPlayerShopsEnabled()) {
            reg.accept(new PlayerShopCommand(shopManager, economyManager, shopAdminDraftCache, configManager, storage));
        }
        reg.accept(new ClearInventoryCommand());
        reg.accept(new InvSeeCommand());
        reg.accept(new MoreCommand());
        reg.accept(new RepairCommand(cooldownManager));
        reg.accept(new TrashCommand());
        reg.accept(new FreecamCommand(freecamManager));
        reg.accept(new FreezeCommand(freezeManager));
        reg.accept(new UnfreezeCommand(freezeManager));
        reg.accept(new VanishCommand(vanishManager));
        reg.accept(new MuteCommand(muteManager));
        reg.accept(new UnmuteCommand(muteManager, storage));
        reg.accept(new BanCommand(banManager, storage));
        reg.accept(new TempBanCommand(banManager, storage));
        reg.accept(new BanListCommand(banManager, ipBanManager, storage));
        reg.accept(new UnbanCommand(banManager, storage));
        reg.accept(new IpBanCommand(ipBanManager, storage));
        reg.accept(new UnipBanCommand(ipBanManager, storage));
        if (hologramService != null && configManager != null && configManager.isHologramsEnabled()) {
            reg.accept(new HologramCommand(hologramService));
        }
        for (var entry : customCommandManager.getCommands().values()) {
            reg.accept(
                    new CustomTextCommand(customCommandManager, configManager, entry.getName(), entry.getPermission(), entry.getAliases())
            );
        }
        Log.info("[HyEssentialsX] Commands registered");
    }

    private void registerListeners() {
        EventRegistry bus = getEventRegistry();
        new PlayerListener(configManager, storage, vanishManager, mailManager).register(bus);
        new PlayerDataListener(
                storage,
                banManager,
                messageManager,
                socialSpyManager,
                adminChatManager,
                freecamManager,
                godManager,
                staminaManager,
                flyManager,
                economyManager,
                playtimeManager,
                configManager,
                kitManager
        ).register(bus);
        if (scoreboardManager != null && configManager.isScoreboardEnabled()) {
            new ScoreboardListener(scoreboardManager).register(bus);
        }
        if (economyHudManager != null && configManager.isEconomyEnabled()) {
            new EconomyHudListener(economyHudManager).register(bus);
        }
        new ChatModerationListener(muteManager, adminChatManager, configManager).register(bus);
        new IpBanListener(ipBanManager, storage).register(bus);
        new CleanupListener(tpManager, backManager, flyManager, godManager, staminaManager, vanishManager).register(bus);
        new FreezeListener(freezeManager).register(bus);
        new AfkListener(afkManager).register(bus);
        new CombatLogListener(combatLogManager, configManager).register(bus);
        new SpawnProtectionListener(spawnManager, configManager).register(bus);
        RespawnTeleportListener respawnTeleportListener = new RespawnTeleportListener(spawnManager);
        respawnTeleportListener.register(bus);
        new SleepPercentListener(configManager).register(bus);
        Log.info("[HyEssentialsX] Listeners registered");

        new DeathBackListener(backManager).register(getEntityStoreRegistry());
        new DeathMessageListener(configManager).register(getEntityStoreRegistry());
        new DeathSpawnListener(spawnManager, configManager).register(getEntityStoreRegistry());
        respawnTeleportListener.register(getEntityStoreRegistry());
        new FlyNoFallListener(flyManager).register(getEntityStoreRegistry());
        new GodHealthListener(godManager).register(getEntityStoreRegistry());
        new InfiniteStaminaListener(staminaManager).register(getEntityStoreRegistry());
        new PlayerVisibilityListener(vanishManager).register(getEntityStoreRegistry());
        new TeleportWarmupListener(tpManager).register(getEntityStoreRegistry());
        new SpawnProtectionListener(spawnManager, configManager).register(getEntityStoreRegistry());
        new EconomyRewardListener(economyManager, configManager).register(getEntityStoreRegistry());
        new CombatLogListener(combatLogManager, configManager).register(getEntityStoreRegistry());
        new FreezeListener(freezeManager).register(getEntityStoreRegistry());
    }

    private void registerWorldHooks() {
        getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, event -> {
            // Only sync if a spawn exists; initialization will happen in /spawn as needed
            spawnManager.syncWorldSpawnProvider();
            if (combatLogManager != null && configManager != null && configManager.isCombatLogEnabled()) {
                combatLogManager.wrapBlockedCommands(getCommandRegistry());
            }
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
        try {
            Class<?> cls = Class.forName("com.hypixel.hytale.server.core.command.system.CommandManager");
            java.lang.reflect.Method get = cls.getMethod("get");
            Object manager = get.invoke(null);
            if (manager != null) {
                return manager;
            }
        } catch (Exception ignored) {
        }
        try {
            Class<?> cls = Class.forName("com.hypixel.hytale.server.core.command.CommandManager");
            java.lang.reflect.Method get = cls.getMethod("get");
            return get.invoke(null);
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

