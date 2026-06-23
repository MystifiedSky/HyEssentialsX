package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.StaffActionUtil;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public final class ClearWarningsCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.clearwarnings";

    private final StorageManager storage;
    private final RequiredArg<String> playerArg;

    public ClearWarningsCommand(@Nonnull StorageManager storage) {
        super("clearwarnings", "Clear player warnings");
        this.storage = storage;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"clearwarns"});
        this.playerArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/clearwarnings");
            return;
        }

        String name = context.get(playerArg);
        PlayerRef online = Universe.get().getPlayerByUsername(name, NameMatching.EXACT_IGNORE_CASE);
        UUID uuid = online != null ? online.getUuid() : storage.resolvePlayerIdByName(name);
        if (uuid == null) {
            Messages.errKey(context, "player.not_found", Map.of());
            return;
        }

        PlayerDataModel data = storage.getPlayerData(uuid);
        String displayName = online != null ? online.getUsername()
                : data.getLastKnownName() == null || data.getLastKnownName().isBlank() ? name : data.getLastKnownName();
        int count = storage.clearWarnings(uuid);
        String actor = StaffActionUtil.resolveActorName(context);
        StaffActionUtil.log(storage, actor, "clearwarnings", uuid, displayName, String.valueOf(count));
        Messages.okKey(context, "warnings.cleared", Map.of(
                "player", displayName,
                "count", String.valueOf(count)
        ));
    }
}
