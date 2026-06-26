package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.MessageManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.IgnoreManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SocialSpyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.CommandCooldownManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.NicknameManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.CooldownKeys;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReplyCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.msg";
    private static final String BYPASS_PERMISSION = "hyessentialsx.reply.bypass";

    private final MessageManager messages;
    private final IgnoreManager ignoreManager;
    private final SocialSpyManager socialSpyManager;
    private final ConfigManager config;
    private final CommandCooldownManager cooldowns;
    private final NicknameManager nicknames;
    private final RequiredArg<List<String>> messageArg;

    public ReplyCommand(@Nonnull MessageManager messages,
                        @Nonnull IgnoreManager ignoreManager,
                        @Nonnull SocialSpyManager socialSpyManager,
                        @Nonnull ConfigManager config,
                        @Nonnull CommandCooldownManager cooldowns,
                        @Nonnull NicknameManager nicknames) {
        super("r", "Replies to last message");
        this.messages = messages;
        this.ignoreManager = ignoreManager;
        this.socialSpyManager = socialSpyManager;
        this.config = config;
        this.cooldowns = cooldowns;
        this.nicknames = nicknames;
        this.setPermissionGroups();
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"reply"});
        this.messageArg = withListRequiredArg("message", "Message", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        if (!xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/r");
            return;
        }
        if (!config.isMsgEnabled()) {
            Messages.errKey(context, "msg.disabled", Map.of());
            return;
        }
        if (!cooldowns.canUse(context, playerRef, CooldownKeys.REPLY, "/reply", BYPASS_PERMISSION, world)) {
            return;
        }

        UUID last = messages.getLastPartner(playerRef.getUuid());
        if (last == null) {
            Messages.errKey(context, "msg.no_reply", Map.of());
            return;
        }

        PlayerRef target = Universe.get().getPlayer(last);
        if (target == null) {
            Messages.errKey(context, "msg.target_offline", Map.of());
            return;
        }

        String message = String.join(" ", context.get(messageArg));
        if (message.isBlank()) {
            Messages.errKey(context, "msg.message_required", Map.of());
            return;
        }
        if (ignoreManager.isIgnoring(target.getUuid(), playerRef.getUuid())
                && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), "hyessentialsx.msg.ignore.bypass")) {
            Messages.errKey(context, "msg.target_ignoring", Map.of("player", target.getUsername()));
            return;
        }
        if (!cooldowns.apply(playerRef, CooldownKeys.REPLY)) {
            return;
        }

        Messages.send(playerRef, Messages.tr(playerRef, "msg.format_sender",
                Map.of("player", nicknames.displayName(target), "message", message)));
        Messages.send(target, Messages.tr(target, "msg.format_receiver",
                Map.of("player", nicknames.displayName(playerRef), "message", message)));
        messages.setLastPartner(playerRef.getUuid(), target.getUuid());
        socialSpyManager.notifyPrivateMessage(playerRef, target, message);
    }
}




