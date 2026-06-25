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
import xyz.thelegacyvoyage.hyessentialsx.models.WarningModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WarningsCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.warnings";

    private final StorageManager storage;
    private final RequiredArg<String> playerArg;

    public WarningsCommand(@Nonnull StorageManager storage) {
        super("warnings", "Show player warnings");
        this.storage = storage;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.playerArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/warnings");
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
        List<WarningModel> warnings = new ArrayList<>(data.getWarnings());
        warnings.sort(Comparator.comparingLong(WarningModel::getCreatedAt).reversed());

        context.sendMessage(Messages.m(Messages.tr(null, "warnings.header", Map.of(
                "player", displayName,
                "count", String.valueOf(warnings.size())
        ))));
        if (warnings.isEmpty()) {
            Messages.errKey(context, "warnings.none", Map.of());
            return;
        }

        int index = 1;
        for (WarningModel warning : warnings) {
            String ago = TimeUtil.formatDurationSeconds(Math.max(0L, (System.currentTimeMillis() - warning.getCreatedAt()) / 1000L));
            String expires = warning.getExpiresAt() <= 0L ? "permanent" : TimeUtil.formatRemaining(warning.getExpiresAt());
            context.sendMessage(Messages.m(Messages.tr(null, "warnings.entry", Map.of(
                    "index", String.valueOf(index),
                    "reason", warning.getReason() == null || warning.getReason().isBlank()
                            ? Messages.tr(null, "reason.none", Map.of())
                            : warning.getReason(),
                    "actor", warning.getIssuer() == null || warning.getIssuer().isBlank()
                            ? Messages.tr(null, "actor.unknown", Map.of())
                            : warning.getIssuer(),
                    "age", ago,
                    "expires", expires,
                    "state", warning.isActive() ? "active" : "inactive"
            ))));
            index++;
        }
    }
}
