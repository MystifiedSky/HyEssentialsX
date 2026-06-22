package xyz.thelegacyvoyage.hyessentialsx.util;

import org.joml.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Predicate;

public final class SimpleClaimsUtil {

    private static final String CLAIM_MANAGER_CLASS = "com.buuz135.simpleclaims.claim.ClaimManager";

    private static final Object INIT_LOCK = new Object();
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static volatile Object claimManager = null;
    private static volatile Method isAllowedMethod = null;
    private static volatile boolean warned = false;

    private static final Predicate<Object> PLACE_CHECK = party -> {
        if (party == null) return true;
        try {
            Method method = party.getClass().getMethod("isBlockPlaceEnabled");
            Object result = method.invoke(party);
            return Boolean.TRUE.equals(result);
        } catch (Exception ignored) {
            return true;
        }
    };

    private SimpleClaimsUtil() {}

    public static boolean canPlaceShop(@Nullable PlayerRef playerRef,
                                       @Nullable World world,
                                       @Nullable Vector3d position) {
        if (playerRef == null || world == null || position == null) {
            return true;
        }
        int blockX = (int) Math.floor(position.x());
        int blockZ = (int) Math.floor(position.z());
        return canPlaceShop(playerRef.getUuid(), world.getName(), blockX, blockZ);
    }

    public static boolean canPlaceShop(@Nullable UUID playerUuid,
                                       @Nullable String worldName,
                                       int blockX,
                                       int blockZ) {
        if (playerUuid == null || worldName == null) {
            return true;
        }
        if (!ensureInitialized()) {
            return true;
        }
        try {
            Object result = isAllowedMethod.invoke(claimManager, playerUuid, worldName, blockX, blockZ, PLACE_CHECK);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            warnOnce("SimpleClaims check failed: " + e.getMessage());
            return true;
        }
    }

    private static boolean ensureInitialized() {
        if (initialized) {
            return available;
        }
        synchronized (INIT_LOCK) {
            if (initialized) {
                return available;
            }
            try {
                Class<?> managerClass = Class.forName(CLAIM_MANAGER_CLASS);
                Method getInstance = managerClass.getMethod("getInstance");
                claimManager = getInstance.invoke(null);
                isAllowedMethod = managerClass.getMethod(
                        "isAllowedToInteract",
                        UUID.class,
                        String.class,
                        int.class,
                        int.class,
                        Predicate.class
                );
                available = true;
            } catch (ClassNotFoundException e) {
                available = false;
            } catch (Exception e) {
                available = false;
                warnOnce("Failed to initialize SimpleClaims integration: " + e.getMessage());
            } finally {
                initialized = true;
            }
        }
        return available;
    }

    private static void warnOnce(@Nonnull String message) {
        if (warned) return;
        warned = true;
        Log.warn("[HyEssentialsX] " + message);
    }
}

