package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.models.StaffActivityEntryModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

public final class StaffActionUtil {

    private StaffActionUtil() {
    }

    @Nonnull
    public static String resolveActorName(@Nonnull CommandContext context) {
        Object sender = context.sender();
        if (sender == null) return Messages.tr(null, "actor.console", Map.of());
        if (sender instanceof PlayerRef playerRef) return playerRef.getUsername();
        try {
            Method method = sender.getClass().getMethod("getName");
            Object value = method.invoke(sender);
            if (value instanceof String name && !name.isBlank()) return name;
        } catch (Exception ignored) {
        }
        return sender.getClass().getSimpleName();
    }

    public static void log(@Nonnull StorageManager storage,
                           @Nonnull String actor,
                           @Nonnull String action,
                           @Nullable UUID targetUuid,
                           @Nullable String targetName,
                           @Nullable String detail) {
        if (targetUuid != null) {
            storage.addStaffCase(targetUuid, action, actor, detail);
        }
        storage.addStaffActivity(new StaffActivityEntryModel(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                actor,
                action,
                targetUuid == null ? null : targetUuid.toString(),
                targetName,
                detail
        ));
    }
}
