package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MultipleHudBridge {

    private static final String CLASS_NAME = "com.buuz135.mhud.MultipleHUD";
    private static final Object INIT_LOCK = new Object();
    private static volatile Object instance;
    private static volatile Method setCustomHudMethod;
    private static volatile Method hideCustomHudMethod;
    private static volatile boolean hideUsesPlayerRef;
    private static final AtomicBoolean LOGGED_INIT_FAILURE = new AtomicBoolean(false);

    private MultipleHudBridge() {}

    public static boolean isAvailable() {
        ensureInitialized();
        return instance != null && setCustomHudMethod != null;
    }

    public static boolean setCustomHud(@Nonnull Player player,
                                       @Nonnull PlayerRef playerRef,
                                       @Nonnull String hudIdentifier,
                                       @Nonnull CustomUIHud hud) {
        ensureInitialized();
        if (instance == null || setCustomHudMethod == null) {
            return false;
        }
        try {
            setCustomHudMethod.invoke(instance, player, playerRef, hudIdentifier, hud);
            return true;
        } catch (Exception e) {
            Log.warn("Failed to call MultipleHUD#setCustomHud: " + e.getMessage());
            return false;
        }
    }

    public static void hideCustomHud(@Nonnull Player player,
                                     @Nonnull PlayerRef playerRef,
                                     @Nonnull String hudIdentifier) {
        ensureInitialized();
        if (instance == null || hideCustomHudMethod == null) {
            return;
        }
        try {
            if (hideUsesPlayerRef) {
                hideCustomHudMethod.invoke(instance, player, playerRef, hudIdentifier);
            } else {
                hideCustomHudMethod.invoke(instance, player, hudIdentifier);
            }
        } catch (Exception e) {
            Log.warn("Failed to call MultipleHUD#hideCustomHud: " + e.getMessage());
        }
    }

    private static void ensureInitialized() {
        if (instance != null && setCustomHudMethod != null && hideCustomHudMethod != null) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (instance != null && setCustomHudMethod != null && hideCustomHudMethod != null) {
                return;
            }
            try {
                Class<?> cls = Class.forName(CLASS_NAME);
                Method getInstance = cls.getMethod("getInstance");
                instance = getInstance.invoke(null);
                setCustomHudMethod = cls.getMethod("setCustomHud", Player.class, PlayerRef.class, String.class, CustomUIHud.class);
                try {
                    hideCustomHudMethod = cls.getMethod("hideCustomHud", Player.class, String.class);
                    hideUsesPlayerRef = false;
                } catch (NoSuchMethodException ex) {
                    hideCustomHudMethod = cls.getMethod("hideCustomHud", Player.class, PlayerRef.class, String.class);
                    hideUsesPlayerRef = true;
                }
            } catch (Exception e) {
                instance = null;
                setCustomHudMethod = null;
                hideCustomHudMethod = null;
                hideUsesPlayerRef = false;
                if (LOGGED_INIT_FAILURE.compareAndSet(false, true)) {
                    Log.warn("[HyEssentialsX] MultipleHUD bridge unavailable: " + e.getMessage());
                }
            }
        }
    }

    public static boolean canAttachToPlayer(@Nonnull Player player) {
        CustomUIHud currentHud = player.getHudManager().getCustomHud();
        return currentHud == null || isMultipleHudContainer(currentHud);
    }

    public static boolean isMultipleHudContainer(@Nullable CustomUIHud hud) {
        if (hud == null) {
            return false;
        }
        return "com.buuz135.mhud.MultipleCustomUIHud".equals(hud.getClass().getName());
    }
}
