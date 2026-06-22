package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;

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
}

