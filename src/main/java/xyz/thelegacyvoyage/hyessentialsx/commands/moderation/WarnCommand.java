package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarningEscalationManager;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarningEscalationRuleModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarningModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.StaffActionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WarnCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.warn";

    private final StorageManager storage;
    private final WarningEscalationManager escalationManager;
    private final RequiredArg<String> playerArg;

    public WarnCommand(@Nonnull StorageManager storage, @Nonnull WarningEscalationManager escalationManager) {
        super("warn", "Warn a player");
        this.storage = storage;
        this.escalationManager = escalationManager;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.playerArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
        this.addUsageVariant(new WarnReasonCommand());
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

        warnPlayer(context, name, uuid, online, List.of());
    }

    private void warnPlayer(@Nonnull CommandContext context,
                            @Nonnull String name,
                            @Nonnull UUID uuid,
                            PlayerRef online,
                            @Nonnull List<String> reasonParts) {
        PlayerDataModel data = storage.getPlayerData(uuid);
        String displayName = online != null ? online.getUsername()
                : data.getLastKnownName() == null || data.getLastKnownName().isBlank() ? name : data.getLastKnownName();
        String reason = Messages.tr(null, "reason.none", Map.of());
        String actor = StaffActionUtil.resolveActorName(context);
        long expiresAt = parseExpiry(reasonParts);
        if (expiresAt < 0L) {
            Messages.errKey(context, "warnrules.invalid_duration", Map.of());
            return;
        }
        String joined = joinReason(reasonParts).trim();
        if (!joined.isBlank()) {
            reason = joined;
        }
        addWarning(context, uuid, displayName, actor, reason, expiresAt, online);
    }

    private void addWarning(@Nonnull CommandContext context,
                            @Nonnull UUID uuid,
                            @Nonnull String displayName,
                            @Nonnull String actor,
                            @Nonnull String reason,
                            long expiresAt,
                            PlayerRef online) {
        WarningModel warning = new WarningModel(UUID.randomUUID().toString(), displayName, actor, reason,
                System.currentTimeMillis(), expiresAt);
        storage.addWarning(uuid, warning);
        StaffActionUtil.log(storage, actor, "warn", uuid, displayName, reason);
        WarningEscalationRuleModel escalated = escalationManager.evaluate(uuid, displayName, actor);

        Messages.okKey(context, "warn.success", Map.of(
                "player", displayName,
                "count", String.valueOf(storage.countActiveWarnings(uuid))
        ));
        if (escalated != null) {
            Messages.okKey(context, "warn.escalated", Map.of(
                    "player", displayName,
                    "rule", escalated.getName(),
                    "action", escalated.getAction()
            ));
        }
        if (online != null) {
            Messages.sendPrefixedKey(online, "warn.target", Map.of("reason", reason));
        }
    }

    private long parseExpiry(@Nonnull List<String> parts) {
        if (parts.isEmpty()) return 0L;
        String first = parts.get(0);
        String normalized = first.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.startsWith("expire:") && !normalized.startsWith("expires:")) return 0L;
        int colon = first.indexOf(':');
        String raw = colon >= 0 ? first.substring(colon + 1) : "";
        long seconds = parseDuration(raw);
        if (seconds < 0L) return -1L;
        return seconds <= 0L ? 0L : System.currentTimeMillis() + seconds * 1000L;
    }

    @Nonnull
    private String joinReason(@Nonnull List<String> parts) {
        if (parts.isEmpty()) return "";
        String first = parts.get(0);
        String normalized = first.toLowerCase(java.util.Locale.ROOT);
        if ((normalized.startsWith("expire:") || normalized.startsWith("expires:")) && parts.size() > 1) {
            return String.join(" ", parts.subList(1, parts.size()));
        }
        return String.join(" ", parts);
    }

    private long parseDuration(@Nonnull String raw) {
        if (raw.isBlank() || "0".equals(raw.trim()) || "none".equalsIgnoreCase(raw.trim())
                || "permanent".equalsIgnoreCase(raw.trim())) {
            return 0L;
        }
        return TimeUtil.parseDurationSeconds(raw);
    }

    private final class WarnReasonCommand extends CommandBase {
        private final RequiredArg<String> playerArg;
        private final RequiredArg<List<String>> reasonArg;

        private WarnReasonCommand() {
            super("Warn a player with a reason");
            this.playerArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
            this.reasonArg = withListRequiredArg("reason", "Reason", ArgTypes.STRING);
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
            List<String> parts = context.get(reasonArg);
            warnPlayer(context, name, uuid, online, parts == null ? List.of() : parts);
        }
    }
}
