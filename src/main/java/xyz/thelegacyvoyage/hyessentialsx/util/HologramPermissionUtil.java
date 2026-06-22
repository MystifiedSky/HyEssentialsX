package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class HologramPermissionUtil {
    public static final String PERMISSION_ADMIN = "hyessentialsx.hologram.admin";
    public static final String PERMISSION_CREATE = "hyessentialsx.hologram.create";
    public static final String PERMISSION_DELETE = "hyessentialsx.hologram.delete";
    public static final String PERMISSION_EDIT = "hyessentialsx.hologram.edit";
    public static final String PERMISSION_LIST = "hyessentialsx.hologram.list";
    public static final String PERMISSION_MOVE = "hyessentialsx.hologram.move";
    public static final String PERMISSION_RELOAD = "hyessentialsx.hologram.reload";
    public static final String PERMISSION_CLEANUP = "hyessentialsx.hologram.cleanup";

    private HologramPermissionUtil() {
    }

    public static boolean hasPermission(@Nonnull CommandSender sender) {
        return hasPermission(sender, PERMISSION_ADMIN);
    }

    public static boolean hasPermission(@Nonnull CommandSender sender, @Nonnull String permission) {
        if (CommandPermissionUtil.hasPermission(sender, permission)) {
            return true;
        }
        return !permission.equals(PERMISSION_ADMIN)
                && CommandPermissionUtil.hasPermission(sender, PERMISSION_ADMIN);
    }

    public static boolean hasPermission(@Nullable Player player) {
        return hasPermission(player, PERMISSION_ADMIN);
    }

    public static boolean hasPermission(@Nullable Player player, @Nonnull String permission) {
        PlayerRef playerRef = ServerCompatUtil.getPlayerRef(player);
        return playerRef != null && hasPermission(playerRef, permission);
    }
}

