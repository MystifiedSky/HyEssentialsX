package xyz.thelegacyvoyage.hyessentialsx.commands.misc;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.NicknameManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandSenderUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public final class NickCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.nick";
    private static final String OTHER_PERMISSION = "hyessentialsx.nick.other";

    private final NicknameManager nicknames;
    private final ConfigManager config;

    public NickCommand(@Nonnull NicknameManager nicknames, @Nonnull ConfigManager config) {
        super("nick", "Set or clear nicknames");
        this.nicknames = nicknames;
        this.config = config;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"nickname", "enick"});
        this.addUsageVariant(new SelfNickVariant());
        this.addUsageVariant(new OtherNickVariant());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/nick");
            return;
        }
        if (!config.isNicknamesEnabled()) {
            Messages.errKey(context, "nick.disabled", Map.of());
            return;
        }
        Messages.errKey(context, "nick.usage", Map.of());
    }

    private void setSelf(@Nonnull CommandContext context, @Nonnull String nickname) {
        if (!canUseNick(context, "/nick")) {
            return;
        }
        PlayerRef actor = CommandSenderUtil.resolvePlayer(context);
        if (actor == null) {
            Messages.errKey(context, "error.player_only", Map.of());
            return;
        }
        applyNickname(context, actor, actor, nickname);
    }

    private void setOther(@Nonnull CommandContext context, @Nonnull PlayerRef target, @Nonnull String nickname) {
        if (!canUseNick(context, "/nick <player> <nickname>")) {
            return;
        }
        if (!CommandPermissionUtil.hasPermission(context.sender(), OTHER_PERMISSION)) {
            Messages.noPerm(context, "/nick <player> <nickname>");
            return;
        }
        PlayerRef actor = CommandSenderUtil.resolvePlayer(context);
        applyNickname(context, actor, target, nickname);
    }

    private boolean canUseNick(@Nonnull CommandContext context, @Nonnull String command) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, command);
            return false;
        }
        if (!config.isNicknamesEnabled()) {
            Messages.errKey(context, "nick.disabled", Map.of());
            return false;
        }
        return true;
    }

    private void applyNickname(@Nonnull CommandContext context,
                               @Nullable PlayerRef actor,
                               @Nonnull PlayerRef target,
                               @Nonnull String nickname) {
        if (isClear(nickname)) {
            nicknames.clearNickname(target);
            if (actor != null && actor.getUuid().equals(target.getUuid())) {
                Messages.okKey(context, "nick.cleared", Map.of());
            } else {
                Messages.okKey(context, "nick.cleared_other", Map.of("player", target.getUsername()));
                Messages.sendPrefixedKey(target, "nick.cleared_by", Map.of());
            }
            return;
        }

        NicknameManager.Result result = nicknames.setNickname(actor != null ? actor : target, target, nickname);
        if (!result.success()) {
            Messages.errKey(context, result.reasonKey(), Map.of(
                    "min", String.valueOf(config.getNicknameMinLength()),
                    "max", String.valueOf(config.getNicknameMaxLength())
            ));
            return;
        }
        String applied = result.nickname() == null ? nickname : result.nickname();
        if (actor != null && actor.getUuid().equals(target.getUuid())) {
            Messages.okKey(context, "nick.set", Map.of("nick", applied));
        } else {
            Messages.okKey(context, "nick.set_other", Map.of("player", target.getUsername(), "nick", applied));
            Messages.sendPrefixedKey(target, "nick.set_by", Map.of("nick", applied));
        }
    }

    private boolean isClear(@Nonnull String value) {
        String lower = value.trim().toLowerCase();
        return lower.equals("off") || lower.equals("clear") || lower.equals("reset") || lower.equals("none");
    }

    private final class SelfNickVariant extends CommandBase {
        private final RequiredArg<String> nicknameArg;

        private SelfNickVariant() {
            super("Set or clear your nickname");
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, PERMISSION_NODE);
            this.nicknameArg = withRequiredArg("nickname", "Nickname, or off", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            setSelf(context, context.get(nicknameArg));
        }
    }

    private final class OtherNickVariant extends CommandBase {
        private final RequiredArg<PlayerRef> targetArg;
        private final RequiredArg<String> nicknameArg;

        private OtherNickVariant() {
            super("Set or clear another player's nickname");
            this.setPermissionGroups();
            CommandPermissionUtil.apply(this, OTHER_PERMISSION);
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
            this.nicknameArg = withRequiredArg("nickname", "Nickname, or off", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            PlayerRef target = context.get(targetArg);
            if (target == null) {
                Messages.errKey(context, "player.not_found", Map.of());
                return;
            }
            setOther(context, target, context.get(nicknameArg));
        }
    }
}
