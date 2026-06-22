package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

public final class CommandSenderUtil {

    private CommandSenderUtil() {}

    @Nullable
    public static PlayerRef resolvePlayer(@Nonnull CommandContext context) {
        Object sender = context.sender();
        if (sender instanceof PlayerRef playerRef) {
            return playerRef;
        }
        try {
            Method method = sender.getClass().getMethod("getPlayerRef");
            Object value = method.invoke(sender);
            if (value instanceof PlayerRef playerRef) {
                return playerRef;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    public static Player resolvePlayerEntity(@Nonnull CommandContext context) {
        PlayerRef playerRef = resolvePlayer(context);
        if (playerRef == null) {
            return null;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        return store == null ? null : store.getComponent(ref, Player.getComponentType());
    }
}

