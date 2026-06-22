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
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public final class ReplyCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.msg";

    private final MessageManager messages;
    private final ConfigManager config;
    private final RequiredArg<List<String>> msgArg;

    public ReplyCommand(@Nonnull MessageManager messages, @Nonnull ConfigManager config) {
        super("r", "Replies to last message");
        this.messages = messages;
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"reply"});
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
            Messages.noPerm(context, "/r");
            return;
        }
        if (!config.isMsgEnabled()) {
            Messages.err(context, "Private messaging is disabled.");
            return;
        }

        UUID last = messages.getLastPartner(playerRef.getUuid());
        if (last == null) {
            Messages.err(context, "Nobody to reply to.");
            return;
        }

        PlayerRef target = Universe.get().getPlayer(last);
        if (target == null) {
            Messages.err(context, "Player is no longer online.");
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



