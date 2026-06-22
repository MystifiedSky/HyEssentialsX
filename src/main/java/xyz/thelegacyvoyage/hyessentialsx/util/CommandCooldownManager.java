package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;

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
        if (context.sender().hasPermission(bypassPermission)) {
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
        if (PermissionsModule.get().hasPermission(playerRef.getUuid(), bypassPermission)) {
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
}
