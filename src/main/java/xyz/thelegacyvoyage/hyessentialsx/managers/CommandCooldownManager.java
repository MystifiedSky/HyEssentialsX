package xyz.thelegacyvoyage.hyessentialsx.managers;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandBypassUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.Map;

public final class CommandCooldownManager {

    private final ConfigManager config;
    private final StorageManager storage;

    public CommandCooldownManager(@Nonnull ConfigManager config, @Nonnull StorageManager storage) {
        this.config = config;
        this.storage = storage;
    }

    public boolean canUse(@Nonnull CommandContext context,
                          @Nonnull PlayerRef playerRef,
                          @Nonnull String key,
                          @Nonnull String commandLabel,
                          @Nonnull String bypassPermission) {
        int cooldownSeconds = config.getCooldownSeconds(key);
        if (cooldownSeconds <= 0) {
            return true;
        }
        if (CommandBypassUtil.hasCooldownBypass(context.sender(), key, bypassPermission)
                || CommandBypassUtil.hasCooldownBypass(playerRef, key, bypassPermission)) {
            return true;
        }

        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        Long lastUsed = data.getCommandCooldowns().get(key);
        if (lastUsed == null) {
            return true;
        }

        long elapsedSeconds = (System.currentTimeMillis() - lastUsed) / 1000L;
        long remaining = cooldownSeconds - elapsedSeconds;
        if (remaining <= 0) {
            return true;
        }

        Messages.warnKey(context, "cooldown.wait", Map.of(
                "time", TimeUtil.formatDurationSeconds(remaining),
                "command", commandLabel
        ));
        return false;
    }

    public boolean canUse(@Nonnull PlayerRef playerRef,
                          @Nonnull String key,
                          @Nonnull String commandLabel,
                          @Nonnull String bypassPermission) {
        int cooldownSeconds = config.getCooldownSeconds(key);
        if (cooldownSeconds <= 0) {
            return true;
        }
        if (CommandBypassUtil.hasCooldownBypass(playerRef, key, bypassPermission)) {
            return true;
        }

        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        Long lastUsed = data.getCommandCooldowns().get(key);
        if (lastUsed == null) {
            return true;
        }

        long elapsedSeconds = (System.currentTimeMillis() - lastUsed) / 1000L;
        long remaining = cooldownSeconds - elapsedSeconds;
        if (remaining <= 0) {
            return true;
        }

        Messages.sendPrefixedKey(playerRef, "cooldown.wait", Map.of(
                "time", TimeUtil.formatDurationSeconds(remaining),
                "command", commandLabel
        ));
        return false;
    }

    public void apply(@Nonnull PlayerRef playerRef, @Nonnull String key) {
        int cooldownSeconds = config.getCooldownSeconds(key);
        if (cooldownSeconds <= 0) {
            return;
        }
        PlayerDataModel data = storage.getPlayerData(playerRef.getUuid());
        data.getCommandCooldowns().put(key, System.currentTimeMillis());
        storage.savePlayerDataAsync(playerRef.getUuid(), data);
    }

    public boolean hasWarmupBypass(@Nonnull CommandContext context,
                                   @Nonnull PlayerRef playerRef,
                                   @Nonnull String key,
                                   @Nonnull String commandBypassPermission) {
        return CommandBypassUtil.hasWarmupBypass(context.sender(), key, commandBypassPermission)
                || CommandBypassUtil.hasWarmupBypass(playerRef, key, commandBypassPermission);
    }

    public boolean hasWarmupBypass(@Nonnull PlayerRef playerRef,
                                   @Nonnull String key,
                                   @Nonnull String commandBypassPermission) {
        return CommandBypassUtil.hasWarmupBypass(playerRef, key, commandBypassPermission);
    }
}

