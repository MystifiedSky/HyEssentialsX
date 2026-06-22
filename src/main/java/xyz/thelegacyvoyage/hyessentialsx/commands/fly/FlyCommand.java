package xyz.thelegacyvoyage.hyessentialsx.commands.fly;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.FlyManager;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;

public final class FlyCommand extends AbstractPlayerCommand {

    private static final String PERMISSION_NODE = "hyessentialsx.fly";

    private final FlyManager flyManager;

    public FlyCommand(@Nonnull FlyManager flyManager) {
        super("fly", "Toggle flight");
        this.flyManager = flyManager;
        this.setPermissionGroup(null);
        xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil.apply(this, PERMISSION_NODE);
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
            Messages.noPerm(context, "/fly");
            return;
        }

        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) {
            Messages.err(context, "Could not toggle flight.");
            return;
        }

        boolean enabled = flyManager.toggle(playerRef.getUuid());

        movementManager.applyDefaultSettings();
        MovementSettings settings = movementManager.getSettings();
        if (settings != null) settings.canFly = enabled;

        MovementSettings defaults = movementManager.getDefaultSettings();
        if (defaults != null) defaults.canFly = enabled;

        MovementStatesComponent statesComponent = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (statesComponent != null) {
            MovementStates current = statesComponent.getMovementStates();
            MovementStates updated = (current != null) ? new MovementStates(current) : new MovementStates();
            if (!enabled) {
                updated.flying = false;
                updated.jumping = false;
                updated.gliding = false;
            } else {
                updated.flying = false; // allow client to double-jump to begin flying
            }
            statesComponent.setMovementStates(updated);
            statesComponent.setSentMovementStates(updated);
        }

        if (!enabled) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                MovementStates current = (statesComponent != null) ? statesComponent.getMovementStates() : null;
                MovementStates updated = (current != null) ? new MovementStates(current) : new MovementStates();
                updated.flying = false;
                player.applyMovementStates(ref, new SavedMovementStates(false), updated, store);
            }

            Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
            if (velocity != null) {
                velocity.setY(0d);
                velocity.setClient(0d, 0d, 0d);
            }
        }

        movementManager.update(playerRef.getPacketHandler());

        if (enabled) {
            Messages.ok(context, "Flight enabled.");
        } else {
            Messages.ok(context, "Flight disabled.");
        }
    }
}



