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
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;

public final class MsgCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.msg";

    private final MessageManager messages;
    private final ConfigManager config;
    private final RequiredArg<PlayerRef> targetArg;
    private final RequiredArg<List<String>> msgArg;

    public MsgCommand(@Nonnull MessageManager messages, @Nonnull ConfigManager config) {
        super("msg", "Sends a private message");
        this.messages = messages;
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"w", "m", "t", "pm", "tell", "whisper"});
        this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
        this.msgArg = withListRequiredArg("message", "Message", ArgTypes.STRING);
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
        if (!context.sender().hasPermission(PERMISSION_NODE)) {
            Messages.noPerm(context, "/msg");
            return;
        }
        if (!config.isMsgEnabled()) {
            Messages.err(context, "Private messaging is disabled.");
            return;
        }

        PlayerRef target = context.get(targetArg);
        if (target == null) {
            Messages.err(context, "Player not found.");
            return;
        }

        List<String> parts = context.get(msgArg);
        String message = String.join(" ", parts);
        if (message.isBlank()) {
            Messages.err(context, "Message required.");
            return;
        }

        Messages.send(playerRef, "&7[&fMe &7-> &f" + target.getUsername() + "&7] &f" + message);
        Messages.send(target, "&7[&f" + playerRef.getUsername() + " &7-> &fMe&7] &f" + message);
        messages.setLastPartner(playerRef.getUuid(), target.getUuid());
    }
}



