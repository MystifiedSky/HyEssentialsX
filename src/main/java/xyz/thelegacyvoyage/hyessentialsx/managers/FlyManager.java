package xyz.thelegacyvoyage.hyessentialsx.managers;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class FlyManager {

    private final ConcurrentHashMap<UUID, Boolean> enabled = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> pendingApply = new ConcurrentHashMap<>();

    public boolean isEnabled(@Nonnull UUID playerId) {
        return enabled.containsKey(playerId);
    }

    public boolean setEnabled(@Nonnull UUID playerId, boolean value) {
        if (value) {
            return enabled.put(playerId, Boolean.TRUE) == null;
        }
        return enabled.remove(playerId) != null;
    }

    public boolean toggle(@Nonnull UUID playerId) {
        if (enabled.remove(playerId) != null) return false;
        enabled.put(playerId, Boolean.TRUE);
        return true;
    }

    public void clear(@Nonnull UUID playerId) {
        enabled.remove(playerId);
    }

    public void queueApply(@Nonnull UUID playerId) {
        pendingApply.put(playerId, Boolean.TRUE);
    }

    public boolean isApplyPending(@Nonnull UUID playerId) {
        return pendingApply.containsKey(playerId);
    }

    public void clearPending(@Nonnull UUID playerId) {
        pendingApply.remove(playerId);
    }

    public boolean tryApplyIfPending(@Nonnull PlayerRef target) {
        UUID playerId = target.getUuid();
        if (!isApplyPending(playerId)) return false;
        if (!applyState(target, true)) return false;
        clearPending(playerId);
        return true;
    }

    public boolean applyState(@Nonnull PlayerRef target, boolean enabled) {
        Ref<EntityStore> targetRef = target.getReference();
        if (targetRef == null) return false;
        Store<EntityStore> targetStore = targetRef.getStore();
        if (targetStore == null) return false;

        MovementManager movementManager = targetStore.getComponent(targetRef, MovementManager.getComponentType());
        if (movementManager == null) return false;

        movementManager.applyDefaultSettings();
        MovementSettings settings = movementManager.getSettings();
        if (settings != null) settings.canFly = enabled;

        MovementSettings defaults = movementManager.getDefaultSettings();
        if (defaults != null) defaults.canFly = enabled;

        MovementStatesComponent statesComponent = targetStore.getComponent(targetRef, MovementStatesComponent.getComponentType());
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
            Player player = targetStore.getComponent(targetRef, Player.getComponentType());
            if (player != null) {
                MovementStates current = (statesComponent != null) ? statesComponent.getMovementStates() : null;
                MovementStates updated = (current != null) ? new MovementStates(current) : new MovementStates();
                updated.flying = false;
                player.applyMovementStates(targetRef, new SavedMovementStates(false), updated, targetStore);
            }

            Velocity velocity = targetStore.getComponent(targetRef, Velocity.getComponentType());
            if (velocity != null) {
                velocity.setY(0d);
                velocity.setClient(0d, 0d, 0d);
            }
        }

        movementManager.update(target.getPacketHandler());
        return true;
    }
}
