package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.PlaceholderApiUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class MotdCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.motd";
    private static final String BYPASS_PERMISSION = "hyessentialsx.motd.bypass";

    private final ConfigManager config;
    private final StorageManager storage;
    private final CommandCooldownManager cooldowns;

    public MotdCommand(@Nonnull ConfigManager config,
                       @Nonnull StorageManager storage,
                       @Nonnull CommandCooldownManager cooldowns) {
        super("motd", "Shows Message of the Day");
        this.config = config;
        this.storage = storage;
        this.cooldowns = cooldowns;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/motd");
            return;
        }
        if (!config.isMotdEnabled()) {
            Messages.errKey(context, "motd.disabled", Map.of());
            return;
        }

        PlayerRef player = CommandSenderUtil.resolvePlayer(context);
        if (player != null) {
            if (!cooldowns.canUse(context, player, CooldownKeys.MOTD, "/motd", BYPASS_PERMISSION)) {
                return;
            }
            if (!cooldowns.apply(player, CooldownKeys.MOTD)) {
                return;
            }
        }
        String playerName = player != null ? player.getUsername() : Messages.tr(null, "actor.console", Map.of());
        Map<String, String> placeholders = buildPlaceholders(playerName, player);
        for (String line : config.getMotdMessages()) {
            context.sendMessage(PlaceholderApiUtil.apply(player, applyPlaceholders(line, placeholders)));
        }
    }

    private Map<String, String> buildPlaceholders(@Nonnull String playerName, @Nullable PlayerRef playerRef) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", playerName);
        placeholders.put("total_players_online", String.valueOf(Universe.get().getPlayers().size()));
        placeholders.put("discord", config.getDiscordInviteUrl());
        java.util.Set<java.util.UUID> ids = storage.listPlayerIds();
        int total = ids.size();
        if (playerRef != null && !ids.contains(playerRef.getUuid())) {
            total += 1;
        }
        placeholders.put("total_joined_players", String.valueOf(total));
        return placeholders;
    }

    private String applyPlaceholders(@Nonnull String line, @Nonnull Map<String, String> placeholders) {
        String out = line;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            out = out.replace("{" + key + "}", value);
            out = out.replace("%" + key + "%", value);
        }
        return out;
    }
}




