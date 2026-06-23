package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarningModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.StaffActionUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WarnCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.warn";

    private final StorageManager storage;
    private final RequiredArg<String> playerArg;
    private final OptionalArg<List<String>> reasonArg;

    public WarnCommand(@Nonnull StorageManager storage) {
        super("warn", "Warn a player");
        this.storage = storage;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.playerArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
        this.reasonArg = withListOptionalArg("reason", "Reason", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/warn");
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
        String reason = Messages.tr(null, "reason.none", Map.of());
        if (context.provided(reasonArg)) {
            List<String> parts = context.get(reasonArg);
            String joined = String.join(" ", parts).trim();
            if (!joined.isBlank()) {
                reason = joined;
            }
        }

        String actor = StaffActionUtil.resolveActorName(context);
        WarningModel warning = new WarningModel(UUID.randomUUID().toString(), displayName, actor, reason,
                System.currentTimeMillis(), 0L);
        storage.addWarning(uuid, warning);
        StaffActionUtil.log(storage, actor, "warn", uuid, displayName, reason);

        Messages.okKey(context, "warn.success", Map.of(
                "player", displayName,
                "count", String.valueOf(storage.countActiveWarnings(uuid))
        ));
        if (online != null) {
            Messages.sendPrefixedKey(online, "warn.target", Map.of("reason", reason));
        }
    }
}
