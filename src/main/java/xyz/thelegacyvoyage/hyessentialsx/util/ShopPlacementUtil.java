package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("removal")
public final class ShopPlacementUtil {

    private static final Object WARN_LOCK = new Object();
    private static volatile boolean warned = false;
    private static final boolean DEBUG_LOGS = false;
    private static final Object DEBUG_LOCK = new Object();

    private ShopPlacementUtil() {}

    public static boolean canPlaceShop(@Nullable PlayerRef playerRef,
                                       @Nullable World world,
                                       @Nullable Store<EntityStore> store,
                                       @Nullable Ref<EntityStore> ref,
                                       @Nullable Vector3d position) {
        if (playerRef == null || world == null || store == null || ref == null || position == null) {
            if (DEBUG_LOGS) {
                debug("Shop placement check skipped (null input).");
            }
            return true;
        }
        if (store.isInThread()) {
            return checkInWorldThread(playerRef, world, store, ref, position);
        }
        if (DEBUG_LOGS) {
            debug("Shop placement check scheduled on world thread.");
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        world.execute(() -> future.complete(checkInWorldThread(playerRef, world, store, ref, position)));
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            warnOnce("Shop placement check timed out; allowing placement. " + e.getMessage());
            return SimpleClaimsUtil.canPlaceShop(playerRef, world, position);
        }
    }

    private static boolean checkInWorldThread(@Nullable PlayerRef playerRef,
                                              @Nullable World world,
                                              @Nullable Store<EntityStore> store,
                                              @Nullable Ref<EntityStore> ref,
                                              @Nullable Vector3d position) {
        if (playerRef == null || world == null || store == null || ref == null || position == null) {
            return true;
        }
        Vector3i target = new Vector3i(
                (int) Math.floor(position.getX()),
                (int) Math.floor(position.getY()),
                (int) Math.floor(position.getZ())
        );
        ItemStack inHand = ItemStack.EMPTY;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            Inventory inventory = player.getInventory();
            if (inventory != null) {
                ItemStack held = inventory.getItemInHand();
                if (held != null) {
                    inHand = held;
                }
            }
        }

        boolean cancelled = false;
        try {
            PlaceBlockEvent event = new PlaceBlockEvent(inHand, target, RotationTuple.NONE);
            store.invoke(ref, event);
            cancelled = event.isCancelled();
        } catch (Exception e) {
            warnOnce("Shop placement check failed; falling back to claim API. " + e.getMessage());
        }

        if (DEBUG_LOGS) {
            String name = playerRef.getUsername();
            String playerLabel = name == null ? playerRef.getUuid().toString() : name;
            debug("Shop placement check: player=" + playerLabel
                    + " world=" + world.getName()
                    + " pos=" + target.getX() + "," + target.getY() + "," + target.getZ()
                    + " eventCancelled=" + cancelled);
        }
        if (cancelled) {
            return false;
        }
        boolean claimAllowed = SimpleClaimsUtil.canPlaceShop(playerRef, world, position);
        if (DEBUG_LOGS) {
            debug("Shop placement fallback (SimpleClaims): allowed=" + claimAllowed);
        }
        return claimAllowed;
    }

    private static void warnOnce(String message) {
        if (warned) return;
        synchronized (WARN_LOCK) {
            if (warned) return;
            warned = true;
            Log.warn("[HyEssentialsX] " + message);
            System.out.println("[HyEssentialsX] " + message);
        }
    }

    private static void debug(String message) {
        synchronized (DEBUG_LOCK) {
            Log.info("[HyEssentialsX] " + message);
            System.out.println("[HyEssentialsX] " + message);
        }
    }
}

