package xyz.thelegacyvoyage.hyessentialsx.managers.hologram;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.event.EventRegistry;
import xyz.thelegacyvoyage.hyessentialsx.HyEssentialsXPlugin;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation.AnimationRegistry;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation.HologramAnimationManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation.HologramAnimationSystem;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.entitytool.EntityToolSupport;
import xyz.thelegacyvoyage.hyessentialsx.listeners.HologramListener;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.placeholder.PlaceholderIntegration;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.logging.Level;

public final class HologramService {

    private final HyEssentialsXPlugin plugin;
    private final Path dataDirectory;
    private final ConfigManager configManager;

    private HologramManager hologramManager;
    private PlaceholderIntegration placeholderIntegration;
    private EntityToolSupport entityToolSupport;
    private AnimationRegistry animationRegistry;
    private HologramAnimationManager animationManager;

    public HologramService(@Nonnull HyEssentialsXPlugin plugin,
                           @Nonnull Path dataDirectory,
                           @Nonnull ConfigManager configManager) {
        this.plugin = plugin;
        this.dataDirectory = dataDirectory.resolve("holograms");
        this.configManager = configManager;
    }

    public void start() {
        if (!configManager.isHologramsEnabled()) {
            getLogger().at(Level.INFO).log("[HyEssentialsX] Holograms are disabled in config.");
            return;
        }
        ensureDataDirectory();
        placeholderIntegration = new PlaceholderIntegration(this);
        animationRegistry = new AnimationRegistry();
        animationManager = new HologramAnimationManager();
        HologramAnimationSystem animationSystem = new HologramAnimationSystem(animationManager);
        plugin.getEntityStoreRegistry().registerSystem(animationSystem);

        hologramManager = new HologramManager(this);
        entityToolSupport = new EntityToolSupport(this);
        entityToolSupport.initialize();
        hologramManager.loadHolograms();

        registerEvents();

        getLogger().at(Level.INFO).log("[HyEssentialsX] Hologram system started.");
    }

    public void shutdown() {
        if (animationManager != null) {
            animationManager.clearAll();
        }
        if (entityToolSupport != null) {
            entityToolSupport.shutdown();
        }
        if (hologramManager != null) {
            hologramManager.saveHolograms();
            hologramManager.removeAllHolograms();
        }
        getLogger().at(Level.INFO).log("[HyEssentialsX] Hologram system stopped.");
    }

    private void registerEvents() {
        new HologramListener(this);
    }

    private void ensureDataDirectory() {
        try {
            java.nio.file.Files.createDirectories(dataDirectory);
        } catch (Exception e) {
            Log.warn("[HyEssentialsX] Failed to create holograms data directory: " + e.getMessage());
        }
    }

    @Nonnull
    public Path getDataDirectory() {
        return dataDirectory;
    }

    @Nonnull
    public HytaleLogger getLogger() {
        return plugin.getLogger();
    }

    @Nonnull
    public EventRegistry getEventRegistry() {
        return plugin.getEventRegistry();
    }

    @Nonnull
    public HologramManager getHologramManager() {
        return hologramManager;
    }

    @Nonnull
    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Nonnull
    public PlaceholderIntegration getPlaceholderIntegration() {
        return placeholderIntegration;
    }

    @Nonnull
    public EntityToolSupport getEntityToolSupport() {
        return entityToolSupport;
    }

    @Nonnull
    public AnimationRegistry getAnimationRegistry() {
        return animationRegistry;
    }

    @Nonnull
    public HologramAnimationManager getAnimationManager() {
        return animationManager;
    }
}

