package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public final class PlaytimeCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.playtime";
    private static final String OTHERS_PERMISSION = "hyessentialsx.playtime.other";

    private final PlaytimeManager playtime;
    private final StorageManager storage;

    public PlaytimeCommand(@Nonnull PlaytimeManager playtime, @Nonnull StorageManager storage) {
        super("playtime", "Shows your playtime");
        this.playtime = playtime;
        this.storage = storage;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"pt"});
        this.setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/playtime");
            return;
        }
        java.util.List<String> args = CommandInputUtil.getArgs(context);
        if (args.isEmpty()) {
            long seconds = playtime.getPlaytimeSeconds(playerRef.getUuid());
            String formatted = TimeUtil.formatDurationSeconds(seconds);
            Messages.sendPrefixedKey(playerRef, "playtime.self", Map.of("time", formatted));
            return;
        }

        if (!context.sender().hasPermission(OTHERS_PERMISSION)) {
            Messages.noPerm(context, "/playtime <player>");
            return;
        }

        String targetName = args.get(0);
        if (targetName == null || targetName.isBlank()) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        PlayerRef online = Universe.get().getPlayerByUsername(targetName, com.hypixel.hytale.server.core.NameMatching.EXACT_IGNORE_CASE);
        UUID uuid = online != null ? online.getUuid() : storage.resolvePlayerIdByName(targetName);
        if (uuid == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        String displayName = resolveDisplayName(online, uuid, targetName);
        long seconds = playtime.getPlaytimeSeconds(uuid);
        String formatted = TimeUtil.formatDurationSeconds(seconds);
        Messages.okKey(context, "playtime.other", Map.of("player", displayName, "time", formatted));
    }

    @Nonnull
    private String resolveDisplayName(PlayerRef online, @Nonnull UUID uuid, @Nonnull String fallback) {
        if (online != null) return online.getUsername();
        PlayerDataModel data = storage.getPlayerData(uuid);
        String name = data.getLastKnownName();
        if (name != null && !name.isBlank()) return name;
        return fallback;
    }
}
