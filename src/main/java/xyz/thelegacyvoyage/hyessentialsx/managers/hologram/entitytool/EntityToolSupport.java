package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.entitytool;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Hologram;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Vec3d;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class EntityToolSupport {
   @Nonnull
   private final HologramService plugin;
   @Nonnull
   private final HologramEntityTracker entityTracker;
   @Nonnull
   private final EntityToolPacketHandler packetHandler;
   @Nonnull
   private final EntityPositionSyncManager positionSyncManager;

   public EntityToolSupport(@Nonnull HologramService plugin) {
      this.plugin = plugin;
      this.entityTracker = new HologramEntityTracker(plugin);
      this.packetHandler = new EntityToolPacketHandler(plugin, this);
      this.positionSyncManager = new EntityPositionSyncManager(plugin);
   }

   public void initialize() {
      this.packetHandler.register();
      this.positionSyncManager.start();
      this.plugin.getLogger().at(Level.INFO).log("Entity tool support initialized");
   }

   public void shutdown() {
      this.positionSyncManager.stop();
      this.entityTracker.clear();
      this.plugin.getLogger().at(Level.INFO).log("Entity tool support shutdown");
   }

   @Nonnull
   public HologramEntityTracker getEntityTracker() {
      return this.entityTracker;
   }

   @Nonnull
   public EntityToolPacketHandler getPacketHandler() {
      return this.packetHandler;
   }

   @Nonnull
   public EntityPositionSyncManager getPositionSyncManager() {
      return this.positionSyncManager;
   }

   public void onEntityTransformChanged(@Nonnull UUID entityUuid, @Nonnull Vec3d newPosition) {
      Hologram hologram = this.entityTracker.getHologramForEntity(entityUuid);
      if (hologram != null) {
         int lineIndex = this.entityTracker.getLineIndexForEntity(entityUuid);
         if (lineIndex >= 0) {
            if (lineIndex == 0) {
               hologram.setPosition(newPosition);
               Api var10000 = this.plugin.getLogger().at(Level.INFO);
               String var10001 = hologram.getName();
               var10000.log("Updated hologram '" + var10001 + "' position via entity tool to " + String.valueOf(newPosition));
               this.plugin.getHologramManager().saveHolograms();
            } else {
               double yOffset = (double)(-lineIndex) * hologram.getLineSpacing();
               Vec3d basePosition = new Vec3d(newPosition.x(), newPosition.y() - yOffset, newPosition.z());
               hologram.setPosition(basePosition);
               this.plugin.getLogger().at(Level.INFO).log("Updated hologram '" + hologram.getName() + "' position via entity tool (from line " + lineIndex + ") to " + String.valueOf(basePosition));
               this.plugin.getHologramManager().saveHolograms();
            }

         }
      }
   }

   public boolean onEntityRemoved(@Nonnull UUID entityUuid) {
      Hologram hologram = this.entityTracker.getHologramForEntity(entityUuid);
      if (hologram == null) {
         return false;
      } else {
         String hologramName = hologram.getName();
         this.plugin.getHologramManager().deleteHologram(hologramName);
         this.plugin.getLogger().at(Level.INFO).log("Deleted hologram '" + hologramName + "' via entity tool");
         return true;
      }
   }

   public boolean onEntityCloned(@Nonnull UUID originalEntityUuid, @Nonnull Ref<EntityStore> clonedEntityRef, @Nonnull Store<EntityStore> store) {
      Hologram originalHologram = this.entityTracker.getHologramForEntity(originalEntityUuid);
      if (originalHologram == null) {
         return false;
      } else {
         TransformComponent transform = (TransformComponent)store.getComponent(clonedEntityRef, TransformComponent.getComponentType());
         if (transform == null) {
            return false;
         } else {
            store.removeEntity(clonedEntityRef, RemoveReason.REMOVE);
            String baseName = originalHologram.getName() + "_copy";
            String newName = baseName;

            for(int counter = 1; this.plugin.getHologramManager().hologramExists(newName); ++counter) {
               newName = baseName + counter;
            }

            Vec3d newPosition = new Vec3d(transform.getPosition().x(), transform.getPosition().y(), transform.getPosition().z());

            try {
               Hologram newHologram = this.plugin.getHologramManager().createHologram(newName, newPosition, originalHologram.getWorldId(), originalHologram.getCreatorId());
               newHologram.setLines(originalHologram.getLines());
               newHologram.setLineSpacing(originalHologram.getLineSpacing());
               newHologram.setFacingDirection(originalHologram.getFacingDirection());
               this.plugin.getHologramManager().updateHologram(newHologram);
               Api var10000 = this.plugin.getLogger().at(Level.INFO);
               String var10001 = originalHologram.getName();
               var10000.log("Cloned hologram '" + var10001 + "' to '" + newName + "' via entity tool");
               return true;
            } catch (Exception var11) {
               this.plugin.getLogger().at(Level.WARNING).log("Failed to clone hologram: " + var11.getMessage());
               return false;
            }
         }
      }
   }

   public void onEntityScaleChanged(@Nonnull UUID entityUuid, float newScale) {
      Hologram hologram = this.entityTracker.getHologramForEntity(entityUuid);
      if (hologram != null) {
         double newSpacing = 0.25D * (double)newScale;
         hologram.setLineSpacing(newSpacing);
         this.plugin.getHologramManager().updateHologram(hologram);
         Api var10000 = this.plugin.getLogger().at(Level.INFO);
         String var10001 = hologram.getName();
         var10000.log("Updated hologram '" + var10001 + "' line spacing to " + newSpacing + " via entity tool scale");
      }
   }
}


