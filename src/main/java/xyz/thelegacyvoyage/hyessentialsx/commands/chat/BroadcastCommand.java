package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.AutoBroadcastManager;
import xyz.thelegacyvoyage.hyessentialsx.ui.AnnouncementAdminUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class BroadcastCommand extends CommandBase {

    private static final String PERMISSION_NODE = "hyessentialsx.broadcast";

    private final ConfigManager config;
    private final AutoBroadcastManager autoBroadcastManager;

    public BroadcastCommand(@Nonnull ConfigManager config, @Nonnull AutoBroadcastManager autoBroadcastManager) {
        super("broadcast", "Broadcasts message");
        this.config = config;
        this.autoBroadcastManager = autoBroadcastManager;
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"bc", "alert", "bcast"});
        this.addSubCommand(new UiSubCommand());
        this.addUsageVariant(new BroadcastMessageCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/broadcast");
            return;
        }
        Messages.errKey(context, "broadcast.message_required", Map.of());
    }

    private void sendBroadcast(@Nonnull CommandContext context, @Nonnull String message) {
        if (!CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            Messages.noPerm(context, "/broadcast");
            return;
        }
        if (!config.isBroadcastEnabled()) {
            Messages.errKey(context, "broadcast.disabled", Map.of());
            return;
        }
        if (message.isBlank()) {
            Messages.errKey(context, "broadcast.message_required", Map.of());
            return;
        }

        Message msg = Messages.m(Messages.tr(null, "broadcast.format", Map.of("message", message)));
        Universe.get().sendMessage(msg);
    }

    private final class BroadcastMessageCommand extends CommandBase {
        private final RequiredArg<List<String>> messageArg;

        private BroadcastMessageCommand() {
            super("Broadcast a message");
            this.messageArg = withListRequiredArg("message", "Message to broadcast", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            List<String> parts = context.get(messageArg);
            sendBroadcast(context, parts == null ? "" : String.join(" ", parts));
        }
    }

    private final class UiSubCommand extends AbstractPlayerCommand {
        private UiSubCommand() {
            super("ui", "Open the broadcast and announcement editor");
            this.addAliases(new String[]{"gui", "editor", "announcements"});
            CommandPermissionUtil.apply(this, AnnouncementCommand.PERMISSION_NODE);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            if (!CommandPermissionUtil.hasPermission(context.sender(), AnnouncementCommand.PERMISSION_NODE)) {
                Messages.noPerm(context, "/broadcast ui");
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Messages.errKey(context, "error.player_only", Map.of());
                return;
            }
            player.getPageManager().openCustomPage(ref, store, new AnnouncementAdminUI(playerRef, autoBroadcastManager));
            context.sendMessage(Message.raw("Broadcast editor opened."));
        }
    }
}




