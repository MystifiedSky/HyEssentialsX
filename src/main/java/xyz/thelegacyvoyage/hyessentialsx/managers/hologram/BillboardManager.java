package xyz.thelegacyvoyage.hyessentialsx.managers.hologram;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.BillboardConfig;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Vec3d;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.ComponentUpdateType;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.Visible;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class BillboardManager {
   @Nonnull
   private final HologramService plugin;
   @Nonnull
   private final BillboardConfig config;
   @Nonnull
   private final Map<UUID, BillboardManager.BillboardData> billboardEntities;
   private ScheduledExecutorService scheduler;

   public BillboardManager(@Nonnull HologramService plugin) {
      this.plugin = plugin;
      this.config = new BillboardConfig(plugin);
      this.billboardEntities = new ConcurrentHashMap();
      this.config.load();
   }

   @Nonnull
   public BillboardConfig getConfig() {
      return this.config;
   }

   public void start() {
      if (!this.config.isEnabled()) {
         this.plugin.getLogger().at(Level.INFO).log("Billboard rotation is disabled in config");
      } else if (this.scheduler == null || this.scheduler.isShutdown()) {
         this.scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread thread = new Thread(r, "HologramService-Billboard");
            thread.setDaemon(true);
            return thread;
         });
         long interval = this.config.getUpdateIntervalMs();
         this.scheduler.scheduleAtFixedRate(this::updateBillboards, interval, interval, TimeUnit.MILLISECONDS);
         this.plugin.getLogger().at(Level.INFO).log("Billboard manager started (per-player mode, interval=" + interval + "ms)");
      }
   }

   public void stop() {
      if (this.scheduler != null) {
         this.scheduler.shutdown();

         try {
            if (!this.scheduler.awaitTermination(1L, TimeUnit.SECONDS)) {
               this.scheduler.shutdownNow();
            }
         } catch (InterruptedException var2) {
            this.scheduler.shutdownNow();
            Thread.currentThread().interrupt();
         }

         this.scheduler = null;
      }

      this.billboardEntities.clear();
      this.plugin.getLogger().at(Level.INFO).log("Billboard manager stopped");
   }

   public void registerBillboard(@Nonnull UUID entityId, @Nonnull UUID worldId, @Nonnull Vec3d position) {
      this.registerBillboard(entityId, worldId, position, -1.0F, 0.0F);
   }

   public void registerBillboard(@Nonnull UUID entityId, @Nonnull UUID worldId, @Nonnull Vec3d position, float customDistance) {
      this.registerBillboard(entityId, worldId, position, customDistance, 0.0F);
   }

   public void registerBillboard(@Nonnull UUID entityId, @Nonnull UUID worldId, @Nonnull Vec3d position, float customDistance, float defaultYaw) {
      this.billboardEntities.put(entityId, new BillboardManager.BillboardData(entityId, worldId, position, customDistance, defaultYaw));
      String distInfo = customDistance > 0.0F ? ", distance: " + customDistance + " blocks" : ", using config distance";
      String dirInfo = defaultYaw != 0.0F ? ", default yaw: " + String.format("%.2f", Math.toDegrees((double)defaultYaw)) + "Â°" : "";
      this.plugin.getLogger().at(Level.INFO).log("Registered billboard entity: " + String.valueOf(entityId) + distInfo + dirInfo);
   }

   public void unregisterBillboard(@Nonnull UUID entityId) {
      this.billboardEntities.remove(entityId);
   }

   public void unregisterAll(@Nonnull Set<UUID> entityIds) {
      Iterator var2 = entityIds.iterator();

      while(var2.hasNext()) {
         UUID id = (UUID)var2.next();
         this.billboardEntities.remove(id);
      }

   }

   public void clear() {
      this.billboardEntities.clear();
   }

   private void updateBillboards() {
      if (!this.billboardEntities.isEmpty()) {
         try {
            Map<UUID, List<BillboardManager.BillboardData>> billboardsByWorld = new ConcurrentHashMap();
            Iterator var2 = this.billboardEntities.values().iterator();

            while(var2.hasNext()) {
               BillboardManager.BillboardData data = (BillboardManager.BillboardData)var2.next();
               ((List)billboardsByWorld.computeIfAbsent(data.worldId, (k) -> {
                  return new ArrayList();
               })).add(data);
            }

            var2 = billboardsByWorld.entrySet().iterator();

            while(var2.hasNext()) {
               Entry<UUID, List<BillboardManager.BillboardData>> entry = (Entry)var2.next();
               UUID worldId = (UUID)entry.getKey();
               List<BillboardManager.BillboardData> billboards = (List)entry.getValue();
               World world = this.findWorldByUuid(worldId);
               if (world != null) {
                  world.execute(() -> {
                     try {
                        this.sendPerPlayerBillboardUpdates(world, billboards);
                     } catch (Exception var4) {
                        this.plugin.getLogger().at(Level.WARNING).log("Error updating billboards: " + var4.getMessage());
                     }

                  });
               }
            }
         } catch (Exception var7) {
            this.plugin.getLogger().at(Level.WARNING).log("Billboard update error: " + var7.getMessage());
         }

      }
   }

   private void sendPerPlayerBillboardUpdates(@Nonnull World world, @Nonnull List<BillboardManager.BillboardData> billboards) {
      Store<EntityStore> store = world.getEntityStore().getStore();
      Iterator var4 = billboards.iterator();

      while(var4.hasNext()) {
         BillboardManager.BillboardData billboard = (BillboardManager.BillboardData)var4.next();

         try {
            Ref<EntityStore> billboardRef = ((EntityStore)store.getExternalData()).getRefFromUUID(billboard.entityId);
            if (billboardRef != null && billboardRef.isValid()) {
               TransformComponent billboardTransform = (TransformComponent)store.getComponent(billboardRef, TransformComponent.getComponentType());
               if (billboardTransform != null) {
                  Vector3d billboardPos = billboardTransform.getPosition();
                  Visible visible = (Visible)store.getComponent(billboardRef, EntityModule.get().getVisibleComponentType());
                  if (visible != null && !visible.visibleTo.isEmpty()) {
                     Iterator var10 = visible.visibleTo.entrySet().iterator();

                     while(var10.hasNext()) {
                        Entry<Ref<EntityStore>, EntityViewer> viewerEntry = (Entry)var10.next();
                        Ref<EntityStore> playerRef = (Ref)viewerEntry.getKey();
                        EntityViewer viewer = (EntityViewer)viewerEntry.getValue();
                        if (playerRef.isValid()) {
                           TransformComponent playerTransform = (TransformComponent)store.getComponent(playerRef, TransformComponent.getComponentType());
                           if (playerTransform != null) {
                              Vector3d playerPos = playerTransform.getPosition();
                              double distSq = billboardPos.distanceSquaredTo(playerPos);
                              double maxDist = this.config.getMaxTrackingDistance();
                              double minDist = billboard.hasCustomDistance() ? (double)billboard.customTrackingDistance : this.config.getMinTrackingDistance();
                              if (!(distSq > maxDist * maxDist)) {
                                 float yaw;
                                 if (distSq <= minDist * minDist) {
                                    double dx = playerPos.getX() - billboardPos.getX();
                                    double dz = playerPos.getZ() - billboardPos.getZ();
                                    yaw = (float)Math.atan2(-dx, -dz);
                                 } else {
                                    yaw = billboard.defaultYaw;
                                 }

                                 ModelTransform transform = new ModelTransform();
                                 transform.position = new Position(billboardPos.getX(), billboardPos.getY(), billboardPos.getZ());
                                 transform.bodyOrientation = new Direction(yaw, 0.0F, 0.0F);
                                 transform.lookOrientation = new Direction(yaw, 0.0F, 0.0F);
                                 ComponentUpdate update = new ComponentUpdate();
                                 update.type = ComponentUpdateType.Transform;
                                 update.transform = transform;
                                 viewer.queueUpdate(billboardRef, update);
                              }
                           }
                        }
                     }

                     billboard.position = new Vec3d(billboardPos.getX(), billboardPos.getY(), billboardPos.getZ());
                  }
               }
            }
         } catch (Exception var27) {
            Api var10000 = this.plugin.getLogger().at(Level.FINE);
            String var10001 = String.valueOf(billboard.entityId);
            var10000.log("Could not update billboard " + var10001 + ": " + var27.getMessage());
         }
      }

   }

   private World findWorldByUuid(@Nonnull UUID worldId) {
      Iterator var2 = Universe.get().getWorlds().values().iterator();

      World world;
      do {
         if (!var2.hasNext()) {
            return null;
         }

         world = (World)var2.next();
      } while(!world.getWorldConfig().getUuid().equals(worldId));

      return world;
   }

   public int getBillboardCount() {
      return this.billboardEntities.size();
   }

   private static class BillboardData {
      final UUID entityId;
      final UUID worldId;
      Vec3d position;
      final float customTrackingDistance;
      final float defaultYaw;

      BillboardData(UUID entityId, UUID worldId, Vec3d position, float customTrackingDistance, float defaultYaw) {
         this.entityId = entityId;
         this.worldId = worldId;
         this.position = position;
         this.customTrackingDistance = customTrackingDistance;
         this.defaultYaw = defaultYaw;
      }

      boolean hasCustomDistance() {
         return this.customTrackingDistance > 0.0F;
      }
   }
}


