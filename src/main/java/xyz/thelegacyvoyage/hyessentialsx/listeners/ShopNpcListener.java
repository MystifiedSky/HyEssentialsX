package xyz.thelegacyvoyage.hyessentialsx.listeners;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.ui.ShopBrowseUI;
import xyz.thelegacyvoyage.hyessentialsx.util.Log;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.ServerCompatUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public final class ShopNpcListener {

    private final ShopManager shopManager;
    private final EconomyManager economy;
    private final ShopAdminDraftCache draftCache;
    private static final double INTERACTION_DISTANCE_SQ = 9.0D;
    private static final boolean DEBUG = true;

    public ShopNpcListener(@Nonnull ShopManager shopManager,
                           @Nonnull EconomyManager economy,
                           @Nonnull ShopAdminDraftCache draftCache) {
        this.shopManager = shopManager;
        this.economy = economy;
        this.draftCache = draftCache;
    }

    public void register(@Nonnull EventRegistry events) {
        events.registerGlobal(EventPriority.FIRST, PlayerMouseButtonEvent.class, this::acceptMouse);
        if (DEBUG) {
            Log.info("[ShopNPC] Listener registered");
        }
    }

    private void acceptMouse(@Nonnull PlayerMouseButtonEvent event) {
        if (DEBUG) {
            String targetInfo = "none";
            Entity targetEntity = event.getTargetEntity();
            if (targetEntity != null) {
                targetInfo = targetEntity.getClass().getSimpleName();
            }
            String playerName = "";
            try {
                Ref<EntityStore> ref = event.getPlayerRef();
                if (ref != null) {
                    PlayerRef pr = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
                    playerName = pr != null ? pr.getUsername() : "";
                }
            } catch (Exception ignored) {
            }
            Log.info("[ShopNPC] Mouse action=" + event.getMouseButton() + " player=" + playerName + " target=" + targetInfo);
        }
        if (event.getMouseButton() == null) return;
        if (event.getMouseButton().mouseButtonType != MouseButtonType.Right) return;
        if (event.getMouseButton().state != MouseButtonState.Pressed) return;

        Player player = event.getPlayer();
        Ref<EntityStore> playerRef = event.getPlayerRef();
        if (player == null || playerRef == null) {
            return;
        }
        if (DEBUG) {
            String targetInfo = "none";
            Entity targetEntity = event.getTargetEntity();
            if (targetEntity != null) {
                targetInfo = targetEntity.getClass().getSimpleName();
            }
            String playerName = "";
            try {
                PlayerRef ref = playerRef.getStore().getComponent(playerRef, PlayerRef.getComponentType());
                playerName = ref != null ? ref.getUsername() : "";
            } catch (Exception ignored) {
            }
            Log.info("[ShopNPC] MouseRight player=" + playerName + " target=" + targetInfo);
        }
        Store<EntityStore> store = playerRef.getStore();
        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            return;
        }
        ShopModel shop = resolveShopFromTarget(event.getTargetEntity(), playerRefComponent, player);
        if (shop == null) {
            if (DEBUG) {
                Log.info("[ShopNPC] MouseRight no shop resolved.");
            }
            return;
        }
        if (DEBUG) {
            Log.info("[ShopNPC] MouseRight resolved shop=" + shop.getName());
        }
        if (openShop(player, playerRef, store, playerRefComponent, shop)) {
            event.setCancelled(true);
        }
    }

    private boolean openShop(@Nonnull Player player,
                             @Nonnull Ref<EntityStore> playerRef,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull PlayerRef playerRefComponent,
                             @Nonnull ShopModel shop) {
        String perm = shop.getUsePermission();
        if (!perm.isBlank() && !hasPermission(player, playerRefComponent, perm)) {
            Messages.sendPrefixedKey(playerRefComponent, "shop.use.no_permission", java.util.Map.of());
            return true;
        }

        ShopBrowseUI ui = new ShopBrowseUI(playerRefComponent, shopManager, economy, shop, draftCache);
        com.hypixel.hytale.server.core.universe.world.World world = player.getWorld();
        if (world != null) {
            world.execute(() -> ui.open(player, playerRef, store));
        } else {
            ui.open(player, playerRef, store);
        }
        return true;
    }

    @Nullable
    private ShopModel resolveShopFromTarget(@Nullable Entity targetEntity,
                                            @Nonnull PlayerRef playerRefComponent,
                                            @Nonnull Player player) {
        if (targetEntity instanceof NPCEntity npc) {
            ShopModel direct = resolveShopByNpcEntity(npc);
            if (direct != null) return direct;
        }
        return resolveNearbyShop(playerRefComponent, player);
    }

    @Nullable
    private ShopModel resolveShopByNpcEntity(@Nonnull NPCEntity npcEntity) {
        java.util.UUID npcUuid = ServerCompatUtil.getUuid(npcEntity);
        if (npcUuid == null) {
            return null;
        }
        String npcId = npcUuid.toString();
        ShopModel byId = shopManager.findShopByNpcId(npcId);
        if (byId != null) {
            return byId;
        }
        ShopNpcModel byPos = resolveNpcByPosition(npcEntity);
        if (byPos == null) {
            return null;
        }
        return shopManager.getShop(byPos.getShopName());
    }

    @Nullable
    private ShopModel resolveNearbyShop(@Nonnull PlayerRef playerRefComponent, @Nonnull Player player) {
        if (player.getWorld() == null) return null;
        if (playerRefComponent.getTransform() == null
                || playerRefComponent.getTransform().getPosition() == null) {
            return null;
        }
        String worldName = player.getWorld().getName();
        var pos = playerRefComponent.getTransform().getPosition();
        List<ShopNpcModel> npcs = shopManager.listAllNpcs();
        ShopNpcModel best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (ShopNpcModel npc : npcs) {
            if (worldName != null && !worldName.equalsIgnoreCase(npc.getWorldId())) {
                continue;
            }
            var p = npc.getPosition();
            double dx = pos.getX() - (p.getX() + 0.5D);
            double dy = pos.getY() - p.getY();
            double dz = pos.getZ() - (p.getZ() + 0.5D);
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = npc;
            }
        }
        if (best == null || bestDistSq > INTERACTION_DISTANCE_SQ) {
            return null;
        }
        return shopManager.getShop(best.getShopName());
    }

    private ShopNpcModel resolveNpcByPosition(@Nonnull NPCEntity npcEntity) {
        com.hypixel.hytale.math.vector.Vector3d pos = getNpcPosition(npcEntity);
        if (pos == null) {
            return null;
        }
        String worldName = npcEntity.getWorld() != null ? npcEntity.getWorld().getName() : "";
        ShopNpcModel best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (ShopNpcModel npc : shopManager.listAllNpcs()) {
            if (!worldName.isBlank() && !npc.getWorldId().equalsIgnoreCase(worldName)) {
                continue;
            }
            com.hypixel.hytale.math.vector.Vector3i p = npc.getPosition();
            double dx = pos.getX() - (p.getX() + 0.5D);
            double dy = pos.getY() - p.getY();
            double dz = pos.getZ() - (p.getZ() + 0.5D);
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = npc;
            }
        }
        return bestDistSq <= 2.25D ? best : null;
    }

    private com.hypixel.hytale.math.vector.Vector3d getNpcPosition(@Nonnull NPCEntity npcEntity) {
        try {
            com.hypixel.hytale.math.vector.Vector3d leash = npcEntity.getLeashPoint();
            if (leash != null) return leash;
        } catch (Exception ignored) {
        }
        try {
            TransformComponent transform = ServerCompatUtil.getTransform(npcEntity);
            if (transform != null && transform.getPosition() != null) {
                return transform.getPosition();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean hasPermission(@Nonnull Player player,
                                  @Nonnull PlayerRef playerRef,
                                  @Nonnull String permission) {
        Boolean componentHas = null;
        try {
            componentHas = player.hasPermission(permission);
        } catch (Exception ignored) {
        }
        boolean moduleHas = PermissionsModule.get().hasPermission(playerRef.getUuid(), permission, false);
        if (PermissionsModule.get().getFirstPermissionProvider() == null) {
            return componentHas != null && componentHas;
        }
        if (componentHas == null) {
            return moduleHas;
        }
        return moduleHas && componentHas;
    }
}

