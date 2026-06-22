package xyz.thelegacyvoyage.hyessentialsx.commands.moderation;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HiddenFromAdventurePlayers;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.VanishManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class VanishCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.vanish";
    private static final String OTHERS_PERMISSION = "hyessentialsx.vanish.others";

    private final VanishManager vanishManager;
    private final OptionalArg<PlayerRef> targetArg;

    public VanishCommand(@Nonnull VanishManager vanishManager) {
        super("vanish", "Toggle vanish");
        this.vanishManager = vanishManager;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
        this.addAliases(new String[]{"v"});
        this.targetArg = withOptionalArg("player", "Target player", ArgTypes.PLAYER_REF);
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
            Messages.noPerm(context, "/vanish");
            return;
        }

        PlayerRef target = context.provided(targetArg) ? context.get(targetArg) : playerRef;
        if (target == null) {
            Messages.errKey(context, "error.player_only", java.util.Map.of());
            return;
        }

        boolean isSelf = playerRef.getUuid().equals(target.getUuid());
        if (!isSelf && !context.sender().hasPermission(OTHERS_PERMISSION)) {
            Messages.noPerm(context, "/vanish");
            return;
        }

        boolean enabled = vanishManager.toggle(target.getUuid());

        Ref<EntityStore> targetRef = target.getReference();
        Store<EntityStore> targetStore = targetRef.getStore();
        if (enabled) {
            targetStore.addComponent(targetRef, HiddenFromAdventurePlayers.getComponentType());
        } else {
            targetStore.removeComponent(targetRef, HiddenFromAdventurePlayers.getComponentType());
        }

        if (isSelf) {
            Messages.okKey(context, enabled ? "vanish.enabled" : "vanish.disabled", java.util.Map.of());
        } else {
            Messages.okKey(context,
                    enabled ? "vanish.enabled_for" : "vanish.disabled_for",
                    java.util.Map.of("player", target.getUsername()));
            Messages.sendPrefixedKey(target,
                    enabled ? "vanish.enabled_by" : "vanish.disabled_by",
                    java.util.Map.of("player", playerRef.getUsername()));
        }
    }
}
