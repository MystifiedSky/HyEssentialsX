package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarningEscalationManager;
import xyz.thelegacyvoyage.hyessentialsx.models.WarningEscalationRuleModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WarnRulesCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.warnrules";

    private final WarningEscalationManager escalationManager;

    public WarnRulesCommand(@Nonnull WarningEscalationManager escalationManager) {
        super("warnrules", "Manage warning escalation rules");
        this.escalationManager = escalationManager;
        this.setPermissionGroups();
        this.addAliases(new String[]{"warningrules", "escalations", "warnescalations"});
        this.addSubCommand(new ListSubCommand());
        this.addSubCommand(new SetSubCommand());
        this.addSubCommand(new EnableSubCommand(true));
        this.addSubCommand(new EnableSubCommand(false));
        this.addSubCommand(new DeleteSubCommand());
        this.addSubCommand(new ResetSubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        listRules(context);
    }

    private final class ListSubCommand extends CommandBase {
        private ListSubCommand() {
            super("list", "List warning escalation rules");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            listRules(context);
        }
    }

    private final class SetSubCommand extends CommandBase {
        private final RequiredArg<String> idArg;
        private final RequiredArg<Integer> thresholdArg;
        private final RequiredArg<String> actionArg;
        private final RequiredArg<String> durationArg;
        private final RequiredArg<String> windowArg;

        private SetSubCommand() {
            super("set", "Create or update a warning escalation rule");
            this.idArg = withRequiredArg("id", "Rule ID", ArgTypes.STRING);
            this.thresholdArg = withRequiredArg("warnings", "Warning threshold", ArgTypes.INTEGER);
            this.actionArg = withRequiredArg("action", "MUTE, TEMPBAN, BAN, or COMMAND", ArgTypes.STRING);
            this.durationArg = withRequiredArg("duration", "Punishment duration, or 0 for permanent/none", ArgTypes.STRING);
            this.windowArg = withRequiredArg("window", "Warning window, or 0 for all active warnings", ArgTypes.STRING);
            this.addUsageVariant(new SetDetailVariant());
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/warnrules set")) return;

            String id = context.get(idArg).trim();
            int threshold = context.get(thresholdArg);
            String action = context.get(actionArg).trim().toUpperCase(Locale.ROOT);
            long duration = parseDuration(context.get(durationArg));
            long window = parseDuration(context.get(windowArg));
            if (id.isBlank() || !id.matches("[a-zA-Z0-9_-]{2,48}")) {
                Messages.errKey(context, "warnrules.invalid_id", Map.of());
                return;
            }
            if (threshold <= 0) {
                Messages.errKey(context, "warnrules.invalid_threshold", Map.of());
                return;
            }
            if (!isAction(action)) {
                Messages.errKey(context, "warnrules.invalid_action", Map.of());
                return;
            }
            if (duration < 0L || window < 0L) {
                Messages.errKey(context, "warnrules.invalid_duration", Map.of());
                return;
            }

            saveRule(context, id, threshold, action, duration, window, "");
        }

        private void saveRule(@Nonnull CommandContext context,
                              @Nonnull String id,
                              int threshold,
                              @Nonnull String action,
                              long duration,
                              long window,
                              @Nonnull String detail) {
            WarningEscalationRuleModel existing = escalationManager.getRule(id);
            WarningEscalationRuleModel rule = existing == null
                    ? new WarningEscalationRuleModel(id, id, threshold, action, duration, window, detail, "COMMAND".equals(action) ? detail : "")
                    : existing;
            rule.setThreshold(threshold);
            rule.setAction(action);
            rule.setDurationSeconds(duration);
            rule.setWindowSeconds(window);
            if ("COMMAND".equals(action)) {
                rule.setCommand(detail);
                rule.setReason("");
            } else {
                rule.setReason(detail);
                rule.setCommand("");
            }
            rule.setEnabled(true);
            escalationManager.saveRule(rule);
            Messages.okKey(context, "warnrules.saved", Map.of("id", rule.getId()));
        }

        private final class SetDetailVariant extends CommandBase {
            private final RequiredArg<String> idArg;
            private final RequiredArg<Integer> thresholdArg;
            private final RequiredArg<String> actionArg;
            private final RequiredArg<String> durationArg;
            private final RequiredArg<String> windowArg;
            private final RequiredArg<List<String>> detailArg;

            private SetDetailVariant() {
                super("Create or update a warning escalation rule with detail");
                this.idArg = withRequiredArg("id", "Rule ID", ArgTypes.STRING);
                this.thresholdArg = withRequiredArg("warnings", "Warning threshold", ArgTypes.INTEGER);
                this.actionArg = withRequiredArg("action", "MUTE, TEMPBAN, BAN, or COMMAND", ArgTypes.STRING);
                this.durationArg = withRequiredArg("duration", "Punishment duration, or 0 for permanent/none", ArgTypes.STRING);
                this.windowArg = withRequiredArg("window", "Warning window, or 0 for all active warnings", ArgTypes.STRING);
                this.detailArg = withListRequiredArg("detail", "Reason or command text", ArgTypes.STRING);
            }

            @Override
            protected boolean canGeneratePermission() {
                return false;
            }

            @Override
            protected void executeSync(@Nonnull CommandContext context) {
                if (!canManage(context, "/warnrules set")) return;

                String id = context.get(idArg).trim();
                int threshold = context.get(thresholdArg);
                String action = context.get(actionArg).trim().toUpperCase(Locale.ROOT);
                long duration = parseDuration(context.get(durationArg));
                long window = parseDuration(context.get(windowArg));
                if (id.isBlank() || !id.matches("[a-zA-Z0-9_-]{2,48}")) {
                    Messages.errKey(context, "warnrules.invalid_id", Map.of());
                    return;
                }
                if (threshold <= 0) {
                    Messages.errKey(context, "warnrules.invalid_threshold", Map.of());
                    return;
                }
                if (!isAction(action)) {
                    Messages.errKey(context, "warnrules.invalid_action", Map.of());
                    return;
                }
                if (duration < 0L || window < 0L) {
                    Messages.errKey(context, "warnrules.invalid_duration", Map.of());
                    return;
                }
                List<String> parts = context.get(detailArg);
                saveRule(context, id, threshold, action, duration, window, parts == null ? "" : String.join(" ", parts).trim());
            }
        }
    }

    private final class EnableSubCommand extends CommandBase {
        private final RequiredArg<String> idArg;
        private final boolean enabled;

        private EnableSubCommand(boolean enabled) {
            super(enabled ? "enable" : "disable", (enabled ? "Enable" : "Disable") + " a warning escalation rule");
            this.enabled = enabled;
            this.idArg = withRequiredArg("id", "Rule ID", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, enabled ? "/warnrules enable" : "/warnrules disable")) return;
            String id = context.get(idArg);
            if (escalationManager.getRule(id) == null) {
                Messages.errKey(context, "warnrules.not_found", Map.of("id", id));
                return;
            }
            escalationManager.setRuleEnabled(id, enabled);
            Messages.okKey(context, enabled ? "warnrules.enabled" : "warnrules.disabled", Map.of("id", id));
        }
    }

    private final class DeleteSubCommand extends CommandBase {
        private final RequiredArg<String> idArg;

        private DeleteSubCommand() {
            super("delete", "Delete a warning escalation rule");
            this.idArg = withRequiredArg("id", "Rule ID", ArgTypes.STRING);
            this.addAliases(new String[]{"remove"});
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/warnrules delete")) return;
            String id = context.get(idArg);
            if (!escalationManager.deleteRule(id)) {
                Messages.errKey(context, "warnrules.not_found", Map.of("id", id));
                return;
            }
            Messages.okKey(context, "warnrules.deleted", Map.of("id", id));
        }
    }

    private final class ResetSubCommand extends CommandBase {
        private ResetSubCommand() {
            super("reset", "Restore default warning escalation rules");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/warnrules reset")) return;
            escalationManager.resetDefaultRules();
            Messages.okKey(context, "warnrules.reset", Map.of());
        }
    }

    private void listRules(@Nonnull CommandContext context) {
        if (!canManage(context, "/warnrules")) return;
        List<WarningEscalationRuleModel> rules = escalationManager.listRules();
        if (rules.isEmpty()) {
            Messages.warnKey(context, "warnrules.none", Map.of());
            return;
        }
        Messages.sendKey(context, "warnrules.header", Map.of("count", String.valueOf(rules.size())));
        for (WarningEscalationRuleModel rule : rules) {
            Messages.sendKey(context, "warnrules.entry", Map.of(
                    "id", rule.getId(),
                    "state", rule.isEnabled() ? "on" : "off",
                    "threshold", String.valueOf(rule.getThreshold()),
                    "action", rule.getAction(),
                    "duration", rule.getDurationSeconds() <= 0L ? "0" : TimeUtil.formatDurationSeconds(rule.getDurationSeconds()),
                    "window", rule.getWindowSeconds() <= 0L ? "0" : TimeUtil.formatDurationSeconds(rule.getWindowSeconds()),
                    "detail", detail(rule)
            ));
        }
    }

    private boolean canManage(@Nonnull CommandContext context, @Nonnull String command) {
        if (CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)
                || CommandPermissionUtil.hasPermission(context.sender(), "hyessentialsx.admin")) {
            return true;
        }
        Messages.noPerm(context, command);
        return false;
    }

    private long parseDuration(@Nonnull String raw) {
        if ("0".equals(raw.trim()) || "none".equalsIgnoreCase(raw.trim()) || "permanent".equalsIgnoreCase(raw.trim())) {
            return 0L;
        }
        return TimeUtil.parseDurationSeconds(raw);
    }

    private boolean isAction(@Nonnull String action) {
        return action.equals("MUTE") || action.equals("TEMPBAN") || action.equals("BAN") || action.equals("COMMAND");
    }

    @Nonnull
    private String detail(@Nonnull WarningEscalationRuleModel rule) {
        if ("COMMAND".equals(rule.getAction())) {
            return rule.getCommand().isBlank() ? "-" : rule.getCommand();
        }
        return rule.getReason().isBlank() ? "-" : rule.getReason();
    }
}
