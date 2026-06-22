package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.entitytool;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Hologram;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Vec3d;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class EntityPositionSyncManager {
   @Nonnull
   private final HologramService plugin;
   private ScheduledExecutorService scheduler;
   private volatile boolean running = false;
   private static final long SYNC_INTERVAL_MS = 50L;
   private static final double MOVEMENT_THRESHOLD = 0.1D;
   private static final long SYNC_COOLDOWN_MS = 500L;
   private final Map<UUID, Long> lastSyncTime = new ConcurrentHashMap();
   private final Map<UUID, Vec3d> lastKnownPosition = new ConcurrentHashMap();
   private final Map<UUID, Long> positionChangeTime = new ConcurrentHashMap();
   private static final long POSITION_STABLE_MS = 100L;
   private volatile boolean isSyncing = false;

   public EntityPositionSyncManager(@Nonnull HologramService plugin) {
      this.plugin = plugin;
   }

   public void start() {
      if (!this.running) {
         this.running = true;
         this.scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "HologramService-PositionSync");
            t.setDaemon(true);
            return t;
         });
         this.scheduler.scheduleAtFixedRate(this::syncPositions, 50L, 50L, TimeUnit.MILLISECONDS);
         this.plugin.getLogger().at(Level.INFO).log("Entity position sync manager started");
      }
   }

   public void stop() {
      this.running = false;
      if (this.scheduler != null) {
         this.scheduler.shutdown();

         try {
            if (!this.scheduler.awaitTermination(2L, TimeUnit.SECONDS)) {
               this.scheduler.shutdownNow();
            }
         } catch (InterruptedException var2) {
            this.scheduler.shutdownNow();
            Thread.currentThread().interrupt();
         }

         this.scheduler = null;
      }

      this.plugin.getLogger().at(Level.INFO).log("Entity position sync manager stopped");
   }

   private void syncPositions() {
      if (this.running && !this.isSyncing) {
         this.isSyncing = true;

         try {
            Collection<Hologram> holograms = this.plugin.getHologramManager().getAllHolograms();
            Iterator var2 = holograms.iterator();

            while(true) {
               Hologram hologram;
               Long lastSync;
               do {
                  if (!var2.hasNext()) {
                     return;
                  }

                  hologram = (Hologram)var2.next();
                  lastSync = (Long)this.lastSyncTime.get(hologram.getId());
               } while(lastSync != null && System.currentTimeMillis() - lastSync < 500L);

               this.checkAndSyncHologramPosition(hologram);
            }
         } catch (Exception var8) {
         } finally {
            this.isSyncing = false;
         }

      }
   }

   private void checkAndSyncHologramPosition(@Nonnull Hologram hologram) {
      List<UUID> entityIds = hologram.getLineEntityIds();
      if (!entityIds.isEmpty()) {
         UUID firstEntityId = (UUID)entityIds.get(0);
         World world = this.findWorldByUuid(hologram.getWorldId());
         if (world != null) {
            world.execute(() -> {
               try {
                  Store<EntityStore> store = world.getEntityStore().getStore();
                  Ref<EntityStore> entityRef = ((EntityStore)store.getExternalData()).getRefFromUUID(firstEntityId);
                  if (entityRef == null || !entityRef.isValid()) {
                     return;
                  }

                  TransformComponent transform = (TransformComponent)store.getComponent(entityRef, TransformComponent.getComponentType());
                  if (transform == null) {
                     return;
                  }

                  double entityX = transform.getPosition().getX();
                  double entityY = transform.getPosition().getY();
                  double entityZ = transform.getPosition().getZ();
                  Vec3d currentPos = new Vec3d(entityX, entityY, entityZ);
                  Vec3d lastPos = (Vec3d)this.lastKnownPosition.get(firstEntityId);
                  if (lastPos == null) {
                     this.lastKnownPosition.put(firstEntityId, currentPos);
                     this.positionChangeTime.put(firstEntityId, System.currentTimeMillis());
                     return;
                  }

                  double moveDx = Math.abs(entityX - lastPos.x());
                  double dx = Math.abs(entityY - lastPos.y());
                  double dy = Math.abs(entityZ - lastPos.z());
                  if (moveDx > 0.001D || dx > 0.001D || dy > 0.001D) {
                     this.lastKnownPosition.put(firstEntityId, currentPos);
                     this.positionChangeTime.put(firstEntityId, System.currentTimeMillis());
                     return;
                  }

                  Long changeTime = (Long)this.positionChangeTime.get(firstEntityId);
                  if (changeTime != null && System.currentTimeMillis() - changeTime < 100L) {
                     return;
                  }

                  Vec3d storedPos = hologram.getPosition();
                  dx = entityX - storedPos.x();
                  dy = entityY - storedPos.y();
                  double dz = entityZ - storedPos.z();
                  if (Math.abs(dx) > 0.1D || Math.abs(dy) > 0.1D || Math.abs(dz) > 0.1D) {
                     Vec3d newPosition = new Vec3d(entityX, entityY, entityZ);
                     hologram.setPosition(newPosition);
                     this.updateAllLinePositions(hologram, store);
                     this.lastSyncTime.put(hologram.getId(), System.currentTimeMillis());
                     this.lastKnownPosition.put(firstEntityId, currentPos);
                     this.plugin.getHologramManager().saveHolograms();
                     Api var10000 = this.plugin.getLogger().at(Level.INFO);
                     String var10001 = hologram.getName();
                     var10000.log("Synced hologram '" + var10001 + "' position to " + String.format("%.2f, %.2f, %.2f", entityX, entityY, entityZ));
                  }
               } catch (Exception var24) {
               }

            });
         }
      }
   }

   private void updateAllLinePositions(@Nonnull Hologram hologram, @Nonnull Store<EntityStore> store) {
      List<UUID> entityIds = hologram.getLineEntityIds();
      Vec3d basePosition = hologram.getPosition();
      double lineSpacing = hologram.getLineSpacing();

      for(int i = 1; i < entityIds.size(); ++i) {
         UUID entityId = (UUID)entityIds.get(i);
         Ref<EntityStore> entityRef = ((EntityStore)store.getExternalData()).getRefFromUUID(entityId);
         if (entityRef != null && entityRef.isValid()) {
            TransformComponent transform = (TransformComponent)store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform != null) {
               double yOffset = (double)(-i) * lineSpacing;
               transform.getPosition().assign(basePosition.x(), basePosition.y() + yOffset, basePosition.z());
               transform.markChunkDirty(store);
            }
         }
      }

   }

   public void clearTrackingForHologram(@Nonnull Hologram hologram) {
      Iterator var2 = hologram.getLineEntityIds().iterator();

      while(var2.hasNext()) {
         UUID entityId = (UUID)var2.next();
         this.lastKnownPosition.remove(entityId);
         this.positionChangeTime.remove(entityId);
      }

      this.lastSyncTime.remove(hologram.getId());
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
}


