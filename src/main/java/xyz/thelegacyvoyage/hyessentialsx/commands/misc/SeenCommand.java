package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class SeenCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.seen";

    private final StorageManager storage;
    private final RequiredArg<String> nameArg;

    public SeenCommand(@Nonnull StorageManager storage) {
        super("seen", "Shows last seen time");
        this.storage = storage;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.nameArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/seen");
            return;
        }

        String name = context.get(nameArg);
        if (name == null || name.isBlank()) {
            Messages.errKey(context, "player.name_required", java.util.Map.of());
            return;
        }

        PlayerRef online = Universe.get().getPlayerByUsername(name, NameMatching.EXACT_IGNORE_CASE);
        if (online != null) {
            Messages.send(context, Messages.tr(null, "seen.online", java.util.Map.of(
                    "player", online.getUsername()
            )));
            return;
        }

        UUID uuid = storage.resolvePlayerIdByName(name);
        if (uuid == null) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return;
        }

        PlayerDataModel data = storage.getPlayerData(uuid);
        long lastSeen = data.getLastSeenAt();
        String ago = TimeUtil.formatDurationSeconds(Math.max(0, (System.currentTimeMillis() - lastSeen) / 1000L));
        String displayName = data.getLastKnownName() != null ? data.getLastKnownName() : name;
        Messages.send(context, Messages.tr(null, "seen.last_seen", java.util.Map.of(
                "player", displayName,
                "time", ago
        )));
    }
}




