package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import xyz.thelegacyvoyage.hyessentialsx.managers.MailManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.MailMessageModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MailCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.mail";
    private static final String SENDALL_PERMISSION = "hyessentialsx.mail.sendall";

    private final MailManager mail;
    private final StorageManager storage;
    private final ConfigManager config;

    public MailCommand(@Nonnull MailManager mail,
                       @Nonnull StorageManager storage,
                       @Nonnull ConfigManager config) {
        super("mail", "Open your mailbox");
        this.mail = mail;
        this.storage = storage;
        this.config = config;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addSubCommand(new SendSubCommand());
        this.addSubCommand(new SendAllSubCommand());
        this.addSubCommand(new ReadSubCommand());
        this.addSubCommand(new ListSubCommand());
        this.addSubCommand(new DeleteSubCommand());
        this.addSubCommand(new ClearSubCommand());
        this.addSubCommand(new ReplySubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!checkBaseAccess(context)) {
            return;
        }
        handleList(context, List.of());
    }

    private final class SendSubCommand extends CommandBase {
        private final RequiredArg<String> targetArg;
        private final RequiredArg<List<String>> messageArg;

        private SendSubCommand() {
            super("send", "Send mail to a player");
            this.targetArg = withRequiredArg("player", "Player name", ArgTypes.STRING);
            this.messageArg = withListRequiredArg("message", "Message", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!checkBaseAccess(context)) {
                return;
            }
            handleSend(context, context.get(targetArg), context.get(messageArg));
        }
    }

    private final class SendAllSubCommand extends CommandBase {
        private final RequiredArg<List<String>> messageArg;

        private SendAllSubCommand() {
            super("sendall", "Send mail to every known player");
            this.messageArg = withListRequiredArg("message", "Message", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!checkBaseAccess(context)) {
                return;
            }
            handleSendAll(context, context.get(messageArg));
        }
    }

    private final class ReadSubCommand extends CommandBase {
        private final RequiredArg<Integer> idArg;

        private ReadSubCommand() {
            super("read", "Read a mail message");
            this.idArg = withRequiredArg("id", "Inbox message id", ArgTypes.INTEGER);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!checkBaseAccess(context)) {
                return;
            }
            handleRead(context, context.get(idArg));
        }
    }

    private final class ListSubCommand extends CommandBase {
        private ListSubCommand() {
            super("list", "List mail messages");
            this.addUsageVariant(new ListOptionsVariant());
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!checkBaseAccess(context)) {
                return;
            }
            handleList(context, List.of());
        }

        private final class ListOptionsVariant extends CommandBase {
            private final RequiredArg<List<String>> optionsArg;

            private ListOptionsVariant() {
                super("List mail messages with filters or page options");
                this.optionsArg = withListRequiredArg("options", "Filter/page: inbox, sent, read, unread, page <n>", ArgTypes.STRING);
            }

            @Override
            protected boolean canGeneratePermission() {
                return false;
            }

            @Override
            protected void executeSync(@Nonnull CommandContext context) {
                if (!checkBaseAccess(context)) {
                    return;
                }
                List<String> options = context.get(optionsArg);
                handleList(context, options == null ? List.of() : options);
            }
        }
    }

    private final class DeleteSubCommand extends CommandBase {
        private final RequiredArg<String> targetArg;

        private DeleteSubCommand() {
            super("delete", "Delete an inbox message or all inbox messages");
            this.targetArg = withRequiredArg("id", "Inbox message id or all", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!checkBaseAccess(context)) {
                return;
            }
            handleDelete(context, context.get(targetArg));
        }
    }

    private final class ClearSubCommand extends CommandBase {
        private ClearSubCommand() {
            super("clear", "Clear your inbox");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!checkBaseAccess(context)) {
                return;
            }
            handleClear(context);
        }
    }

    private final class ReplySubCommand extends CommandBase {
        private final RequiredArg<Integer> idArg;
        private final RequiredArg<List<String>> messageArg;

        private ReplySubCommand() {
            super("reply", "Reply to a mail message");
            this.idArg = withRequiredArg("id", "Inbox message id", ArgTypes.INTEGER);
            this.messageArg = withListRequiredArg("message", "Message", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!checkBaseAccess(context)) {
                return;
            }
            handleReply(context, context.get(idArg), context.get(messageArg));
        }
    }

    private boolean checkBaseAccess(@Nonnull CommandContext context) {
        if (!mail.isEnabled()) {
            Messages.errKey(context, "mail.disabled", Map.of());
            return false;
        }
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/mail");
            return false;
        }
        return true;
    }

    private void handleSend(@Nonnull CommandContext context,
                            @Nullable String targetName,
                            @Nullable List<String> messageParts) {
        PlayerRef sender = requirePlayer(context);
        if (sender == null) return;

        if (targetName == null || targetName.isBlank()) {
            Messages.errKey(context, "mail.usage.send", Map.of());
            return;
        }

        PlayerRef online = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);
        UUID targetId = online != null ? online.getUuid() : storage.resolvePlayerIdByName(targetName);
        if (targetId == null) {
            Messages.errKey(context, "mail.player_not_found", Map.of());
            return;
        }

        String message = join(messageParts);
        if (message.isBlank()) {
            Messages.errKey(context, "mail.message_required", Map.of());
            return;
        }

        String targetDisplay = resolveDisplayName(online, targetId, targetName);
        MailManager.SendResult result = mail.sendMail(
                sender.getUuid(),
                sender.getUsername(),
                targetId,
                targetDisplay,
                message,
                false
        );
        reportSendResult(context, result, targetDisplay, false);
    }

    private void handleSendAll(@Nonnull CommandContext context, @Nullable List<String> messageParts) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), SENDALL_PERMISSION)) {
            Messages.noPerm(context, "/mail sendall");
            return;
        }
        String message = join(messageParts);
        if (message.isBlank()) {
            Messages.errKey(context, "mail.message_required", Map.of());
            return;
        }

        PlayerRef sender = CommandSenderUtil.resolvePlayer(context);
        UUID senderId = sender != null ? sender.getUuid() : new UUID(0L, 0L);
        String senderName = sender != null ? sender.getUsername() : resolveActorName(context);

        int sent = 0;
        for (UUID targetId : storage.listPlayerIds()) {
            PlayerRef online = Universe.get().getPlayer(targetId);
            String targetName = resolveDisplayName(online, targetId, targetId.toString());
            MailManager.SendResult result = mail.sendMail(
                    senderId,
                    senderName,
                    targetId,
                    targetName,
                    message,
                    true
            );
            if (result.getStatus() == MailManager.SendStatus.OK) {
                sent++;
            }
        }
        Messages.okKey(context, "mail.sent_all", Map.of("count", String.valueOf(sent)));
    }

    private void handleRead(@Nonnull CommandContext context, @Nullable Integer id) {
        PlayerRef player = requirePlayer(context);
        if (player == null) return;

        if (id == null || id <= 0) {
            Messages.errKey(context, "mail.usage.read", Map.of());
            return;
        }

        MailMessageModel entry = mail.markRead(player.getUuid(), id);
        if (entry == null) {
            Messages.errKey(context, "mail.read.not_found", Map.of());
            return;
        }

        String sender = entry.getSenderName() != null ? entry.getSenderName() : "Unknown";
        Messages.sendPrefixedKey(player, "mail.read.header", Map.of(
                "id", String.valueOf(entry.getId()),
                "sender", sender,
                "age", MailManager.formatAge(entry.getSentAt())
        ));
        String body = entry.getMessage() != null ? entry.getMessage() : "";
        if (!body.isBlank()) {
            Messages.sendPrefixed(player, body);
        }
    }

    private void handleList(@Nonnull CommandContext context, @Nonnull List<String> args) {
        PlayerRef player = requirePlayer(context);
        if (player == null) return;

        ListOptions options = parseListOptions(args);
        boolean sentView = "sent".equals(options.filter);

        List<MailMessageModel> entries = sentView ? mail.getSent(player.getUuid()) : mail.getInbox(player.getUuid());
        List<MailMessageModel> filtered = new ArrayList<>();
        for (MailMessageModel entry : entries) {
            if (entry == null) continue;
            if (!sentView) {
                if ("read".equals(options.filter) && !entry.isRead()) continue;
                if ("unread".equals(options.filter) && entry.isRead()) continue;
            }
            filtered.add(entry);
        }

        if (filtered.isEmpty()) {
            Messages.sendPrefixedKey(player, "mail.none", Map.of());
            return;
        }

        filtered.sort(Comparator.comparingLong(MailMessageModel::getSentAt).reversed());
        int pageSize = Math.max(1, config.getMailPageSize());
        int total = filtered.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int page = Math.max(1, Math.min(options.page, pages));

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        if (sentView) {
            Messages.sendPrefixedKey(player, "mail.list.sent_header", Map.of(
                    "page", String.valueOf(page),
                    "pages", String.valueOf(pages)
            ));
        } else {
            Messages.sendPrefixedKey(player, "mail.list.header", Map.of(
                    "page", String.valueOf(page),
                    "pages", String.valueOf(pages)
            ));
        }

        String readLabel = Messages.tr(player, "mail.status.read", Map.of());
        String unreadLabel = Messages.tr(player, "mail.status.unread", Map.of());

        for (int i = start; i < end; i++) {
            MailMessageModel entry = filtered.get(i);
            if (entry == null) continue;
            if (sentView) {
                String recipient = entry.getRecipientName() != null ? entry.getRecipientName() : "Unknown";
                Messages.sendPrefixedKey(player, "mail.list.sent_entry", Map.of(
                        "id", String.valueOf(entry.getId()),
                        "recipient", recipient,
                        "age", MailManager.formatAge(entry.getSentAt())
                ));
            } else {
                String sender = entry.getSenderName() != null ? entry.getSenderName() : "Unknown";
                String status = entry.isRead() ? readLabel : unreadLabel;
                Messages.sendPrefixedKey(player, "mail.list.entry", Map.of(
                        "id", String.valueOf(entry.getId()),
                        "sender", sender,
                        "age", MailManager.formatAge(entry.getSentAt()),
                        "status", status
                ));
            }
        }
    }

    private void handleDelete(@Nonnull CommandContext context, @Nullable String raw) {
        PlayerRef player = requirePlayer(context);
        if (player == null) return;

        if (raw == null || raw.isBlank()) {
            Messages.errKey(context, "mail.usage.delete", Map.of());
            return;
        }

        if (raw.equalsIgnoreCase("all")) {
            int cleared = mail.clearInbox(player.getUuid());
            Messages.okKey(context, "mail.deleted_all", Map.of("count", String.valueOf(cleared)));
            return;
        }

        int id = parsePositiveInt(raw);
        if (id <= 0) {
            Messages.errKey(context, "mail.usage.delete", Map.of());
            return;
        }

        if (mail.deleteInbox(player.getUuid(), id)) {
            Messages.okKey(context, "mail.deleted", Map.of());
        } else {
            Messages.errKey(context, "mail.read.not_found", Map.of());
        }
    }

    private void handleClear(@Nonnull CommandContext context) {
        PlayerRef player = requirePlayer(context);
        if (player == null) return;

        int cleared = mail.clearInbox(player.getUuid());
        Messages.okKey(context, "mail.cleared", Map.of("count", String.valueOf(cleared)));
    }

    private void handleReply(@Nonnull CommandContext context,
                             @Nullable Integer id,
                             @Nullable List<String> messageParts) {
        PlayerRef player = requirePlayer(context);
        if (player == null) return;

        if (id == null || id <= 0) {
            Messages.errKey(context, "mail.usage.reply", Map.of());
            return;
        }

        MailMessageModel entry = mail.markRead(player.getUuid(), id);
        if (entry == null) {
            Messages.errKey(context, "mail.read.not_found", Map.of());
            return;
        }

        UUID targetId = parseUuid(entry.getSenderUuid());
        if (targetId == null) {
            Messages.errKey(context, "mail.player_not_found", Map.of());
            return;
        }

        String message = join(messageParts);
        if (message.isBlank()) {
            Messages.errKey(context, "mail.message_required", Map.of());
            return;
        }

        String targetName = entry.getSenderName() != null ? entry.getSenderName() : "Unknown";
        MailManager.SendResult result = mail.sendMail(
                player.getUuid(),
                player.getUsername(),
                targetId,
                targetName,
                message,
                false
        );
        reportSendResult(context, result, targetName, true);
    }

    private void reportSendResult(@Nonnull CommandContext context,
                                  @Nonnull MailManager.SendResult result,
                                  @Nonnull String targetName,
                                  boolean reply) {
        switch (result.getStatus()) {
            case OK -> {
                if (reply) {
                    Messages.okKey(context, "mail.reply.sent", Map.of("player", targetName));
                } else {
                    Messages.okKey(context, "mail.sent", Map.of("player", targetName));
                }
            }
            case DISABLED -> Messages.errKey(context, "mail.disabled", Map.of());
            case MESSAGE_TOO_LONG -> Messages.errKey(context, "mail.message_too_long", Map.of(
                    "max", String.valueOf(config.getMailMaxMessageLength())
            ));
            case COOLDOWN -> Messages.errKey(context, "mail.spam.cooldown", Map.of(
                    "seconds", String.valueOf(result.getRetrySeconds())
            ));
            case SIMILAR -> Messages.errKey(context, "mail.spam.similar", Map.of());
        }
    }

    @Nullable
    private PlayerRef requirePlayer(@Nonnull CommandContext context) {
        PlayerRef player = CommandSenderUtil.resolvePlayer(context);
        if (player == null) {
            Messages.errKey(context, "error.player_only", Map.of());
        }
        return player;
    }

    @Nonnull
    private String resolveDisplayName(@Nullable PlayerRef online, @Nonnull UUID uuid, @Nonnull String fallback) {
        if (online != null) return online.getUsername();
        PlayerDataModel data = storage.getPlayerData(uuid);
        String name = data.getLastKnownName();
        if (name != null && !name.isBlank()) return name;
        return fallback;
    }

    @Nonnull
    private static String join(@Nullable List<String> args) {
        if (args == null || args.isEmpty()) return "";
        return String.join(" ", args).trim();
    }

    private static int parsePositiveInt(@Nullable String raw) {
        if (raw == null) return -1;
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || !trimmed.matches("\\d+")) return -1;
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Nullable
    private static UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nonnull
    private static String resolveActorName(@Nonnull CommandContext context) {
        Object sender = context.sender();
        if (sender == null) return "Console";
        if (sender instanceof PlayerRef playerRef) return playerRef.getUsername();
        try {
            java.lang.reflect.Method method = sender.getClass().getMethod("getName");
            Object value = method.invoke(sender);
            if (value instanceof String name && !name.isBlank()) return name;
        } catch (Exception ignored) {
        }
        return sender.getClass().getSimpleName();
    }

    private static final class ListOptions {
        private int page = 1;
        private String filter = "inbox";
    }

    @Nonnull
    private static ListOptions parseListOptions(@Nonnull List<String> args) {
        ListOptions options = new ListOptions();
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (token == null) continue;
            String value = token.trim();
            if (value.isEmpty()) continue;
            if (value.startsWith("--")) {
                value = value.substring(2);
            }

            String lower = value.toLowerCase();
            if (lower.equals("page") && i + 1 < args.size()) {
                int page = parsePositiveInt(args.get(++i));
                if (page > 0) options.page = page;
                continue;
            }
            if (lower.equals("filter") && i + 1 < args.size()) {
                String filter = normalizeFilter(args.get(++i));
                if (filter != null) options.filter = filter;
                continue;
            }

            if (lower.startsWith("page=") || lower.startsWith("page:")) {
                int page = parsePositiveInt(value.substring(5));
                if (page > 0) options.page = page;
                continue;
            }
            if (lower.startsWith("filter=") || lower.startsWith("filter:")) {
                String filter = normalizeFilter(value.substring(7));
                if (filter != null) options.filter = filter;
                continue;
            }

            String filter = normalizeFilter(value);
            if (filter != null) {
                options.filter = filter;
                continue;
            }

            int page = parsePositiveInt(value);
            if (page > 0) {
                options.page = page;
            }
        }
        return options;
    }

    @Nullable
    private static String normalizeFilter(@Nullable String raw) {
        if (raw == null) return null;
        String lower = raw.trim().toLowerCase();
        return switch (lower) {
            case "inbox", "sent", "read", "unread" -> lower;
            default -> null;
        };
    }
}
