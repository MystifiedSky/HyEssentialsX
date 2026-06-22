package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.MessageManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.IgnoreManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SocialSpyManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class MsgCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.msg";

    private final MessageManager messages;
    private final IgnoreManager ignoreManager;
    private final SocialSpyManager socialSpyManager;
    private final ConfigManager config;
    private final RequiredArg<PlayerRef> targetArg;

    public MsgCommand(@Nonnull MessageManager messages,
                      @Nonnull IgnoreManager ignoreManager,
                      @Nonnull SocialSpyManager socialSpyManager,
                      @Nonnull ConfigManager config) {
        super("msg", "Sends a private message");
        this.messages = messages;
        this.ignoreManager = ignoreManager;
        this.socialSpyManager = socialSpyManager;
        this.config = config;
        this.setPermissionGroup(null);
        this.setAllowsExtraArguments(true);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"w", "m", "t", "pm", "tell", "whisper", "sendmessage"});
        this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
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
            Messages.noPerm(context, "/msg");
            return;
        }
        if (!config.isMsgEnabled()) {
            Messages.errKey(context, "msg.disabled", Map.of());
            return;
        }

        PlayerRef target = context.get(targetArg);
        if (target == null) {
            Messages.errKey(context, "msg.player_not_found", Map.of());
            return;
        }

        List<String> parts = xyz.thelegacyvoyage.hyessentialsx.util.CommandInputUtil.getArgs(context);
        if (!parts.isEmpty()) {
            parts = parts.subList(1, parts.size());
        }
        String message = String.join(" ", parts);
        if (message.isBlank()) {
            Messages.errKey(context, "msg.message_required", Map.of());
            return;
        }
        if (ignoreManager.isIgnoring(target.getUuid(), playerRef.getUuid())
                && !xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.hasPermission(context.sender(), "hyessentialsx.msg.ignore.bypass")) {
            Messages.errKey(context, "msg.target_ignoring", Map.of("player", target.getUsername()));
            return;
        }

        Messages.send(playerRef, Messages.tr(playerRef, "msg.format_sender",
                Map.of("player", target.getUsername(), "message", message)));
        Messages.send(target, Messages.tr(target, "msg.format_receiver",
                Map.of("player", playerRef.getUsername(), "message", message)));
        messages.setLastPartner(playerRef.getUuid(), target.getUuid());
        socialSpyManager.notifyPrivateMessage(playerRef, target, message);
    }
}




