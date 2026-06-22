package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public final class ServerCompatUtil {

    private ServerCompatUtil() {
    }

    @Nullable
    public static PlayerRef getPlayerRef(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref != null) {
            Store<EntityStore> store = ref.getStore();
            if (store != null) {
                PlayerRef component = store.getComponent(ref, PlayerRef.getComponentType());
                if (component != null) {
                    return component;
                }
            }
        }
        return getLegacyPlayerRef(player);
    }

    @Nullable
    public static UUID getPlayerUuid(@Nullable Player player) {
        PlayerRef playerRef = getPlayerRef(player);
        if (playerRef != null) {
            return playerRef.getUuid();
        }
        return player != null ? getUuid(player) : null;
    }

    @Nullable
    public static UUID getUuid(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        Ref<EntityStore> ref = entity.getReference();
        if (ref != null) {
            Store<EntityStore> store = ref.getStore();
            if (store != null) {
                UUIDComponent component = store.getComponent(ref, UUIDComponent.getComponentType());
                if (component != null) {
                    return component.getUuid();
                }
            }
        }
        return getLegacyUuid(entity);
    }

    @Nullable
    public static TransformComponent getTransform(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        Ref<EntityStore> ref = entity.getReference();
        if (ref != null) {
            Store<EntityStore> store = ref.getStore();
            if (store != null) {
                TransformComponent component = store.getComponent(ref, TransformComponent.getComponentType());
                if (component != null) {
                    return component;
                }
            }
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("removal")
    public static Inventory getInventory(@Nullable Player player) {
        return player != null ? player.getInventory() : null;
    }

    @Nullable
    @SuppressWarnings("removal")
    private static PlayerRef getLegacyPlayerRef(@Nonnull Player player) {
        try {
            return player.getPlayerRef();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("removal")
    private static UUID getLegacyUuid(@Nonnull Entity entity) {
        try {
            return entity.getUuid();
        } catch (Exception ignored) {
            return null;
        }
    }

}
