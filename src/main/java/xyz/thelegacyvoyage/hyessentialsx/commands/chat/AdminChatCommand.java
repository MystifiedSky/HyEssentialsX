package xyz.thelegacyvoyage.hyessentialsx.commands.chat;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.AdminChatManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AdminChatCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.adminchat";

    private final AdminChatManager adminChatManager;
    private final ConfigManager config;
    private final OptionalArg<List<String>> msgArg;

    public AdminChatCommand(@Nonnull AdminChatManager adminChatManager, @Nonnull ConfigManager config) {
        super("adminchat", "Sends admin message");
        this.adminChatManager = adminChatManager;
        this.config = config;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"a"});
        this.msgArg = withListOptionalArg("message", "Message", ArgTypes.STRING);
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
            Messages.noPerm(context, "/adminchat");
            return;
        }
        if (!config.isAdminChatEnabled()) {
            Messages.errKey(context, "adminchat.disabled", Map.of());
            return;
        }

        if (context.provided(msgArg)) {
            List<String> parts = context.get(msgArg);
            String message = String.join(" ", parts);
            if (message.isBlank()) {
                Messages.errKey(context, "adminchat.message_required", Map.of());
                return;
            }
            sendAdminMessage(playerRef, message);
            return;
        }

        boolean enabled = adminChatManager.toggle(playerRef.getUuid());
        Messages.okKey(context, enabled ? "adminchat.enabled" : "adminchat.disabled_toggle", Map.of());
    }

    private void sendAdminMessage(@Nonnull PlayerRef sender, @Nonnull String message) {
        List<PlayerRef> targets = new ArrayList<>();
        for (PlayerRef ref : Universe.get().getPlayers()) {
            if (hasAdminChatPermission(ref)) {
                targets.add(ref);
            }
        }

        for (PlayerRef target : targets) {
            String formatted = Messages.tr(target, "adminchat.format",
                    Map.of("player", sender.getUsername(), "message", message));
            Messages.send(target, formatted);
        }
    }

    private boolean hasAdminChatPermission(@Nonnull PlayerRef ref) {
        Boolean componentHas = null;
        try {
            Ref<EntityStore> reference = ref.getReference();
            Store<EntityStore> store = reference.getStore();
            if (store != null) {
                Player playerComponent = store.getComponent(reference, Player.getComponentType());
                if (playerComponent != null) {
                    componentHas = playerComponent.hasPermission(PERMISSION_NODE);
                }
            }
        } catch (Exception ignored) {
        }
        boolean moduleHas = PermissionsModule.get().hasPermission(ref.getUuid(), PERMISSION_NODE, false);
        if (PermissionsModule.get().getFirstPermissionProvider() == null) {
            return componentHas != null && componentHas;
        }
        if (componentHas == null) {
            return moduleHas;
        }
        return moduleHas && componentHas;
    }
}



