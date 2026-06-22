package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.MuteManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class UnmuteCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.mute";

    private final MuteManager muteManager;
    private final StorageManager storage;
    private final RequiredArg<String> nameArg;

    public UnmuteCommand(@Nonnull MuteManager muteManager, @Nonnull StorageManager storage) {
        super("unmute", "Unmutes a player");
        this.muteManager = muteManager;
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
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/unmute");
            return;
        }

        String name = context.get(nameArg);
        PlayerRef online = Universe.get().getPlayerByUsername(name, NameMatching.EXACT_IGNORE_CASE);
        UUID uuid = online != null ? online.getUuid() : storage.resolvePlayerIdByName(name);

        if (uuid == null) {
            Messages.errKey(context, "player.not_found", java.util.Map.of());
            return;
        }

        muteManager.unmute(uuid);
        Messages.okKey(context, "mute.unmuted", java.util.Map.of("player", name));
    }
}




