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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.AutoBroadcastManager;
import xyz.thelegacyvoyage.hyessentialsx.models.AnnouncementPresetModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.AnnouncementAdminUI;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class AnnouncementCommand extends AbstractPlayerCommand {

    public static final String PERMISSION_NODE = "hyessentialsx.announcement.admin";

    private final AutoBroadcastManager manager;

    public AnnouncementCommand(@Nonnull AutoBroadcastManager manager) {
        super("announcement", "Open the announcement preset editor");
        this.manager = manager;
        this.addAliases(new String[]{"announcements", "announcepreset", "announcer"});
        this.setPermissionGroups();
        CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addSubCommand(new UiSubCommand());
        this.addSubCommand(new ListSubCommand());
        this.addSubCommand(new SendSubCommand());
        this.addSubCommand(new NextSubCommand());
        this.addSubCommand(new CreateSubCommand());
        this.addSubCommand(new DeleteSubCommand());
        this.addSubCommand(new ToggleSubCommand());
        this.addSubCommand(new IntervalSubCommand());
        this.addSubCommand(new RandomSubCommand());
        this.addSubCommand(new SequentialSubCommand());
        this.addSubCommand(new SetChatSubCommand());
        this.addSubCommand(new SetPermissionSubCommand());
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
        openEditor(context, store, ref, playerRef);
    }

    private boolean canManage(@Nonnull CommandContext context, @Nonnull String command) {
        if (CommandPermissionUtil.hasPermission(context.sender(), PERMISSION_NODE)) {
            return true;
        }
        Messages.noPerm(context, command);
        return false;
    }

    private void openEditor(@Nonnull CommandContext context,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull PlayerRef playerRef) {
        if (!canManage(context, "/announcement")) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.errKey(context, "error.player_only", Map.of());
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new AnnouncementAdminUI(playerRef, manager));
        context.sendMessage(Message.raw("Announcement editor opened."));
    }

    private final class UiSubCommand extends AbstractPlayerCommand {
        private UiSubCommand() {
            super("ui", "Open the announcement preset editor");
            this.addAliases(new String[]{"gui", "editor", "edit"});
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
            openEditor(context, store, ref, playerRef);
        }
    }

    private final class ListSubCommand extends CommandBase {
        private ListSubCommand() {
            super("list", "List announcement presets");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/announcement list")) return;
            context.sendMessage(Message.raw("Announcement presets:"));
            for (AnnouncementPresetModel preset : manager.presets()) {
                context.sendMessage(Message.raw("- " + preset.getName()
                        + " [" + (preset.isEnabled() ? "enabled" : "disabled") + "]"
                        + (preset.getPermission().isBlank() ? "" : " perm=" + preset.getPermission())));
            }
        }
    }

    private final class SendSubCommand extends CommandBase {
        private final RequiredArg<String> nameArg;

        private SendSubCommand() {
            super("send", "Send an announcement preset now");
            this.addAliases(new String[]{"trigger"});
            this.nameArg = withRequiredArg("name", "Preset name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/announcement send")) return;
            String name = context.get(nameArg);
            context.sendMessage(Message.raw(manager.trigger(name)
                    ? "Announcement '" + name + "' sent."
                    : "Announcement '" + name + "' was not found."));
        }
    }

    private final class NextSubCommand extends CommandBase {
        private NextSubCommand() {
            super("next", "Send the next scheduled announcement now");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/announcement next")) return;
            context.sendMessage(Message.raw(manager.triggerNext()
                    ? "Next announcement sent."
                    : "No announcement presets are available."));
        }
    }

    private final class CreateSubCommand extends CommandBase {
        private final RequiredArg<String> nameArg;

        private CreateSubCommand() {
            super("create", "Create an announcement preset");
            this.addAliases(new String[]{"add"});
            this.nameArg = withRequiredArg("name", "Preset name", ArgTypes.STRING);
            this.addUsageVariant(new CreateMessageVariant());
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/announcement create")) return;
            createPreset(context, context.get(nameArg), "<#38BDF8>[Announcement]</#38BDF8> <#E2E8F0>Edit me.</#E2E8F0>");
        }

        private final class CreateMessageVariant extends CommandBase {
            private final RequiredArg<String> nameArg;
            private final RequiredArg<List<String>> messageArg;

            private CreateMessageVariant() {
                super("Create an announcement preset with an initial chat message");
                this.nameArg = withRequiredArg("name", "Preset name", ArgTypes.STRING);
                this.messageArg = withListRequiredArg("message", "Initial chat message", ArgTypes.STRING);
            }

            @Override
            protected boolean canGeneratePermission() {
                return false;
            }

            @Override
            protected void executeSync(@Nonnull CommandContext context) {
                if (!canManage(context, "/announcement create")) return;
                List<String> parts = context.get(messageArg);
                createPreset(context, context.get(nameArg), parts == null ? "" : String.join(" ", parts));
            }
        }

        private void createPreset(@Nonnull CommandContext context, @Nonnull String name, @Nonnull String message) {
            AnnouncementPresetModel preset = new AnnouncementPresetModel(name);
            preset.setChatMessages(List.of(message));
            manager.savePreset(preset);
            context.sendMessage(Message.raw("Created announcement preset '" + preset.getName() + "'."));
        }
    }

    private final class DeleteSubCommand extends CommandBase {
        private final RequiredArg<String> nameArg;

        private DeleteSubCommand() {
            super("delete", "Delete an announcement preset");
            this.addAliases(new String[]{"remove"});
            this.nameArg = withRequiredArg("name", "Preset name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/announcement delete")) return;
            String name = context.get(nameArg);
            context.sendMessage(Message.raw(manager.deletePreset(name)
                    ? "Deleted announcement preset '" + name + "'."
                    : "Announcement '" + name + "' was not found."));
        }
    }

    private final class ToggleSubCommand extends CommandBase {
        private final RequiredArg<String> nameArg;

        private ToggleSubCommand() {
            super("toggle", "Enable or disable an announcement preset");
            this.nameArg = withRequiredArg("name", "Preset name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/announcement toggle")) return;
            String name = context.get(nameArg);
            AnnouncementPresetModel preset = manager.getPreset(name);
            if (preset == null) {
                context.sendMessage(Message.raw("Announcement '" + name + "' was not found."));
                return;
            }
            preset.setEnabled(!preset.isEnabled());
            manager.savePreset(preset);
            context.sendMessage(Message.raw("Announcement '" + preset.getName() + "' is now "
                    + (preset.isEnabled() ? "enabled." : "disabled.")));
        }
    }

    private final class IntervalSubCommand extends CommandBase {
        private final RequiredArg<Integer> secondsArg;

        private IntervalSubCommand() {
            super("interval", "Set scheduled announcement interval seconds");
            this.secondsArg = withRequiredArg("seconds", "Seconds, minimum 30", ArgTypes.INTEGER);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/announcement interval")) return;
            int seconds = Math.max(30, context.get(secondsArg));
            manager.setIntervalSeconds(seconds);
            context.sendMessage(Message.raw("Announcement interval set to " + seconds + " seconds."));
        }
    }

    private final class RandomSubCommand extends CommandBase {
        private RandomSubCommand() {
            super("random", "Use random scheduled announcement order");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/announcement random")) return;
            manager.setRandom(true);
            context.sendMessage(Message.raw("Announcement order set to random."));
        }
    }

    private final class SequentialSubCommand extends CommandBase {
        private SequentialSubCommand() {
            super("sequential", "Use sequential scheduled announcement order");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/announcement sequential")) return;
            manager.setRandom(false);
            context.sendMessage(Message.raw("Announcement order set to sequential."));
        }
    }

    private final class SetChatSubCommand extends CommandBase {
        private final RequiredArg<String> nameArg;
        private final RequiredArg<List<String>> messageArg;

        private SetChatSubCommand() {
            super("setchat", "Replace a preset's chat message");
            this.nameArg = withRequiredArg("name", "Preset name", ArgTypes.STRING);
            this.messageArg = withListRequiredArg("message", "Chat message", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/announcement setchat")) return;
            String name = context.get(nameArg);
            AnnouncementPresetModel preset = manager.getPreset(name);
            if (preset == null) {
                context.sendMessage(Message.raw("Announcement '" + name + "' was not found."));
                return;
            }
            preset.setChatMessages(List.of(String.join(" ", context.get(messageArg))));
            manager.savePreset(preset);
            context.sendMessage(Message.raw("Updated chat message for '" + preset.getName() + "'."));
        }
    }

    private final class SetPermissionSubCommand extends CommandBase {
        private final RequiredArg<String> nameArg;
        private final RequiredArg<String> permissionArg;

        private SetPermissionSubCommand() {
            super("setpermission", "Set a preset's audience permission");
            this.addAliases(new String[]{"permission"});
            this.nameArg = withRequiredArg("name", "Preset name", ArgTypes.STRING);
            this.permissionArg = withRequiredArg("permission", "Permission node, or none", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!canManage(context, "/announcement setpermission")) return;
            String name = context.get(nameArg);
            AnnouncementPresetModel preset = manager.getPreset(name);
            if (preset == null) {
                context.sendMessage(Message.raw("Announcement '" + name + "' was not found."));
                return;
            }
            preset.setPermission(context.get(permissionArg));
            manager.savePreset(preset);
            context.sendMessage(Message.raw("Updated permission target for '" + preset.getName() + "'."));
        }
    }
}
