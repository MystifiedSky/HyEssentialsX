package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.entitytool;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Hologram;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Vec3d;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.packets.buildertools.EntityToolAction;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Iterator;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class EntityToolPacketHandler {
   @Nonnull
   private final HologramService plugin;
   @Nonnull
   private final EntityToolSupport entityToolSupport;

   public EntityToolPacketHandler(@Nonnull HologramService plugin, @Nonnull EntityToolSupport entityToolSupport) {
      this.plugin = plugin;
      this.entityToolSupport = entityToolSupport;
   }

   public void register() {
      this.plugin.getLogger().at(Level.INFO).log("Entity tool packet handler registered");
   }

   public boolean handleEntityAction(@Nonnull Player player, int entityNetworkId, @Nonnull EntityToolAction action, @Nonnull World world, @Nonnull Store<EntityStore> store) {
      Ref<EntityStore> entityRef = world.getEntityStore().getRefFromNetworkId(entityNetworkId);
      if (entityRef != null && entityRef.isValid()) {
         UUIDComponent uuidComponent = (UUIDComponent)store.getComponent(entityRef, UUIDComponent.getComponentType());
         if (uuidComponent == null) {
            return false;
         } else {
            UUID entityUuid = uuidComponent.getUuid();
            HologramEntityTracker tracker = this.entityToolSupport.getEntityTracker();
            if (!tracker.isHologramEntity(entityUuid)) {
               return false;
            } else {
               switch(action) {
               case Remove:
                  return this.entityToolSupport.onEntityRemoved(entityUuid);
               case Clone:
                  this.plugin.getLogger().at(Level.INFO).log("Clone action on hologram entity - will create new hologram");
                  return false;
               case Freeze:
                  this.plugin.getLogger().at(Level.INFO).log("Freeze action on hologram entity - ignored (already static)");
                  return true;
               default:
                  return false;
               }
            }
         }
      } else {
         return false;
      }
   }

   public boolean handleEntityTransform(@Nonnull Player player, int entityNetworkId, @Nonnull ModelTransform transform, @Nonnull World world, @Nonnull Store<EntityStore> store) {
      Ref<EntityStore> entityRef = world.getEntityStore().getRefFromNetworkId(entityNetworkId);
      if (entityRef != null && entityRef.isValid()) {
         UUIDComponent uuidComponent = (UUIDComponent)store.getComponent(entityRef, UUIDComponent.getComponentType());
         if (uuidComponent == null) {
            return false;
         } else {
            UUID entityUuid = uuidComponent.getUuid();
            HologramEntityTracker tracker = this.entityToolSupport.getEntityTracker();
            if (!tracker.isHologramEntity(entityUuid)) {
               return false;
            } else {
               if (transform.position != null) {
                  Vec3d newPosition = new Vec3d(transform.position.x, transform.position.y, transform.position.z);
                  this.entityToolSupport.onEntityTransformChanged(entityUuid, newPosition);
                  Hologram hologram = tracker.getHologramForEntity(entityUuid);
                  if (hologram != null) {
                     int movedLineIndex = tracker.getLineIndexForEntity(entityUuid);
                     this.updateOtherHologramLines(hologram, movedLineIndex, world, store);
                  }
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   public boolean handleEntityScale(@Nonnull Player player, int entityNetworkId, float scale, @Nonnull World world, @Nonnull Store<EntityStore> store) {
      Ref<EntityStore> entityRef = world.getEntityStore().getRefFromNetworkId(entityNetworkId);
      if (entityRef != null && entityRef.isValid()) {
         UUIDComponent uuidComponent = (UUIDComponent)store.getComponent(entityRef, UUIDComponent.getComponentType());
         if (uuidComponent == null) {
            return false;
         } else {
            UUID entityUuid = uuidComponent.getUuid();
            HologramEntityTracker tracker = this.entityToolSupport.getEntityTracker();
            if (!tracker.isHologramEntity(entityUuid)) {
               return false;
            } else {
               this.entityToolSupport.onEntityScaleChanged(entityUuid, scale);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private void updateOtherHologramLines(@Nonnull Hologram hologram, int movedLineIndex, @Nonnull World world, @Nonnull Store<EntityStore> store) {
      Vec3d basePosition = hologram.getPosition();
      double lineSpacing = hologram.getLineSpacing();
      int lineIndex = 0;
      Iterator var9 = hologram.getLineEntityIds().iterator();

      while(true) {
         while(var9.hasNext()) {
            UUID entityUuid = (UUID)var9.next();
            if (lineIndex == movedLineIndex) {
               ++lineIndex;
            } else {
               Ref<EntityStore> entityRef = ((EntityStore)store.getExternalData()).getRefFromUUID(entityUuid);
               if (entityRef != null && entityRef.isValid()) {
                  double yOffset = (double)(-lineIndex) * lineSpacing;
                  Vec3d correctPosition = new Vec3d(basePosition.x(), basePosition.y() + yOffset, basePosition.z());
                  TransformComponent transform = (TransformComponent)store.getComponent(entityRef, TransformComponent.getComponentType());
                  if (transform != null) {
                     transform.getPosition().assign(correctPosition.x(), correctPosition.y(), correctPosition.z());
                     transform.markChunkDirty(store);
                  }

                  ++lineIndex;
               } else {
                  ++lineIndex;
               }
            }
         }

         return;
      }
   }
}


