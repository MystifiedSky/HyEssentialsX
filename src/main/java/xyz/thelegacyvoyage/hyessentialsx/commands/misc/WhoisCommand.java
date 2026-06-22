package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class WhoisCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.whois";

    private final StorageManager storage;
    private final RequiredArg<String> nameArg;

    public WhoisCommand(@Nonnull StorageManager storage) {
        super("whois", "Shows player information");
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
            Messages.noPerm(context, "/whois");
            return;
        }

        String name = context.get(nameArg);
        if (name == null || name.isBlank()) {
            Messages.err(context, "Player name required.");
            return;
        }

        PlayerRef online = Universe.get().getPlayerByUsername(name, NameMatching.EXACT_IGNORE_CASE);
        UUID uuid = null;
        if (online != null) {
            uuid = online.getUuid();
        } else {
            uuid = storage.resolvePlayerIdByName(name);
        }

        if (uuid == null) {
            Messages.err(context, "Player not found.");
            return;
        }

        PlayerDataModel data = storage.getPlayerData(uuid);
        String displayName = (data.getLastKnownName() != null) ? data.getLastKnownName() : name;

        Messages.send(context, "&aPlayer: &f" + displayName);
        Messages.send(context, "&aUUID: &f" + uuid);

        if (online != null) {
            Transform t = online.getTransform();
            World world = Universe.get().getWorld(online.getWorldUuid());
            if (t != null) {
                Vector3d pos = t.getPosition();
                if (pos != null) {
                    Messages.send(context, "&aLocation: &f" + (world != null ? world.getName() : "unknown") +
                            " &7(" + (int) pos.getX() + ", " + (int) pos.getY() + ", " + (int) pos.getZ() + ")");
                }
            }
            Messages.send(context, "&aStatus: &fOnline");
        } else {
            long lastSeen = data.getLastSeenAt();
            String ago = TimeUtil.formatDurationSeconds(Math.max(0, (System.currentTimeMillis() - lastSeen) / 1000L));
            Messages.send(context, "&aStatus: &fOffline &7(" + ago + " ago)");
        }
    }
}



