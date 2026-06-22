package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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
        List<Candidate> rawCandidates = Collections.synchronizedList(new ArrayList<>());
        store.forEachEntityParallel(Query.any(), (index, chunk, commandBuffer) -> {
            try {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                if (store.getComponent(ref, Player.getComponentType()) != null) {
                    return;
                }
                TransformComponent npcTransform = store.getComponent(ref, TransformComponent.getComponentType());
                if (npcTransform == null || npcTransform.getPosition() == null) return;
                Vector3d pos = npcTransform.getPosition();
                double dx = pos.getX() - playerPos.getX();
                double dy = pos.getY() - playerPos.getY();
                double dz = pos.getZ() - playerPos.getZ();
                double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                if (distSq > NEARBY_RADIUS_SQ) return;
                rawCandidates.add(new Candidate(ref, distSq, false));
                boolean nameMatch = matchesShopName(store, ref, shop);
                Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
                String interactionId = interactions != null
                        ? interactions.getInteractionId(InteractionType.Use)
                        : null;
                boolean interactionMatch = interactionId != null
                        && interactionId.equalsIgnoreCase(ShopNpcInteractionRegistry.ADMIN_SHOP_ROOT_INTERACTION_ID);
                if (!interactionMatch && !nameMatch) {
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
                            + " nameplate=" + getNameplate(store, ref)
                            + " nameMatch=" + nameMatch);
                }
                candidates.add(new Candidate(ref, distSq, nameMatch));
            } catch (Exception ignored) {
            }
        });
        if (candidates.isEmpty()) {
            if (DEBUG_LOGS) {
                debug("No shop NPC candidates found within radius. rawCount=" + rawCandidates.size());
            }
            if (!rawCandidates.isEmpty()) {
                Candidate fallback = selectClosest(rawCandidates);
                if (fallback != null) {
                    if (DEBUG_LOGS) {
                        debug("Falling back to closest NPC within radius.");
                    }
                    removeCandidate(world, store, fallback);
                }
            }
            return;
        }
        Candidate best = selectBest(candidates);
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
    private static Candidate selectBest(@Nonnull List<Candidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing((Candidate c) -> !c.nameMatch)
                        .thenComparingDouble(c -> c.distSq))
                .findFirst()
                .orElse(null);
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
                store.removeEntity(candidate.ref, RemoveReason.REMOVE);
            } catch (Exception ignored) {
            }
        });
    }

    private static final class Candidate {
        private final Ref<EntityStore> ref;
        private final double distSq;
        private final boolean nameMatch;

        private Candidate(@Nonnull Ref<EntityStore> ref, double distSq, boolean nameMatch) {
            this.ref = ref;
            this.distSq = distSq;
            this.nameMatch = nameMatch;
        }
    }

    private static void debug(@Nonnull String message) {
        Log.info("[ShopNPC] " + message);
        System.out.println("[ShopNPC] " + message);
    }
}
