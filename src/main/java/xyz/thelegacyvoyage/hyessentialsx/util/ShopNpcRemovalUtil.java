package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopNpcInteractionRegistry;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ShopNpcRemovalUtil {

    private static final double NEARBY_RADIUS_SQ = 100.0D;
    private static final boolean DEBUG_LOGS = false;

    private ShopNpcRemovalUtil() {}

    public static void removeNearbyNpc(@Nonnull World world,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> playerRef,
                                       @Nonnull ShopModel shop) {
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            return;
        }
        Vector3d playerPos = transform.getPosition();
        List<Candidate> candidates = Collections.synchronizedList(new ArrayList<>());
        store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                TransformComponent npcTransform = store.getComponent(ref, TransformComponent.getComponentType());
                if (npcTransform == null || npcTransform.getPosition() == null) return;
                Vector3d pos = npcTransform.getPosition();
                double dx = pos.x() - playerPos.x();
                double dy = pos.y() - playerPos.y();
                double dz = pos.z() - playerPos.z();
                double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                if (distSq > NEARBY_RADIUS_SQ) return;
                boolean nameMatch = matchesShopName(store, ref, shop);
                Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
                String interactionId = interactions != null
                        ? interactions.getInteractionId(InteractionType.Use)
                        : null;
                boolean interactionMatch = interactionId != null
                        && interactionId.equalsIgnoreCase(ShopNpcInteractionRegistry.ADMIN_SHOP_ROOT_INTERACTION_ID);
                if (!interactionMatch || !nameMatch) {
                    if (DEBUG_LOGS) {
                        debug("Skip entity " + ref + " distSq=" + distSq
                                + " interaction=" + interactionId
                                + " nameplate=" + getNameplate(store, ref));
                    }
                    return;
                }
                if (DEBUG_LOGS) {
                    debug("Candidate entity " + ref + " distSq=" + distSq
                            + " interaction=" + interactionId
                            + " nameplate=" + getNameplate(store, ref));
                }
                candidates.add(new Candidate(ref, distSq));
            } catch (Exception ignored) {
            }
        });
        if (candidates.isEmpty()) {
            if (DEBUG_LOGS) {
                debug("No matching shop NPC candidates found within radius.");
            }
            return;
        }
        Candidate best = selectClosest(candidates);
        if (best != null) {
            removeCandidate(world, store, best);
        }
    }

    private static boolean matchesShopName(@Nonnull Store<EntityStore> store,
                                           @Nonnull Ref<EntityStore> ref,
                                           @Nonnull ShopModel shop) {
        String text = getNameplate(store, ref);
        if (text == null) {
            return false;
        }
        return text.equalsIgnoreCase(shop.getDisplayName()) || text.equalsIgnoreCase(shop.getName());
    }

    @Nullable
    private static String getNameplate(@Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> ref) {
        Nameplate nameplate = store.getComponent(ref, Nameplate.getComponentType());
        if (nameplate == null) {
            return null;
        }
        return nameplate.getText();
    }

    @Nullable
    private static Candidate selectClosest(@Nonnull List<Candidate> candidates) {
        return candidates.stream()
                .min(Comparator.comparingDouble(c -> c.distSq))
                .orElse(null);
    }

    private static void removeCandidate(@Nonnull World world,
                                        @Nonnull Store<EntityStore> store,
                                        @Nonnull Candidate candidate) {
        world.execute(() -> {
            try {
                if (DEBUG_LOGS) {
                    debug("Removing entity " + candidate.ref);
                }
                final boolean[] removed = {false};
                store.forEachEntityParallel(Query.any(), (index, chunk, commandBuffer) -> {
                    try {
                        Ref<EntityStore> ref = chunk.getReferenceTo(index);
                        if (ref != null && ref.equals(candidate.ref)) {
                            commandBuffer.tryRemoveEntity(ref, RemoveReason.REMOVE);
                            removed[0] = true;
                        }
                    } catch (Exception ignored) {
                    }
                });
                if (!removed[0] && DEBUG_LOGS) {
                    debug("Could not queue entity removal for " + candidate.ref);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static final class Candidate {
        private final Ref<EntityStore> ref;
        private final double distSq;

        private Candidate(@Nonnull Ref<EntityStore> ref, double distSq) {
            this.ref = ref;
            this.distSq = distSq;
        }
    }

    private static void debug(@Nonnull String message) {
        Log.info("[ShopNPC] " + message);
        System.out.println("[ShopNPC] " + message);
    }
}

