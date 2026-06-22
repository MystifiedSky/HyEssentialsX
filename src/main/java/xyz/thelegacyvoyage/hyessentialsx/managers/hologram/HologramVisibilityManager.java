package xyz.thelegacyvoyage.hyessentialsx.managers.hologram;

import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Hologram;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Vec3d;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class HologramVisibilityManager {
   @Nonnull
   private final HologramService plugin;
   @Nonnull
   private final HologramManager hologramManager;
   private ScheduledExecutorService scheduler;
   private static final long UPDATE_INTERVAL_MS = 1000L;
   private boolean infiniteVisibilityEnabled = true;
   private final Map<UUID, Long> pendingRespawns = new ConcurrentHashMap();
   private int tickCounter = 0;

   public HologramVisibilityManager(@Nonnull HologramService plugin, @Nonnull HologramManager hologramManager) {
      this.plugin = plugin;
      this.hologramManager = hologramManager;
   }

   public void start() {
      if (!this.infiniteVisibilityEnabled) {
         this.plugin.getLogger().at(Level.INFO).log("Hologram infinite visibility is disabled");
      } else if (this.scheduler == null || this.scheduler.isShutdown()) {
         this.scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread thread = new Thread(r, "HologramService-Visibility");
            thread.setDaemon(true);
            return thread;
         });
         this.scheduler.scheduleAtFixedRate(this::tick, 1000L, 1000L, TimeUnit.MILLISECONDS);
         this.plugin.getLogger().at(Level.INFO).log("Hologram visibility manager started (interval=1000ms)");
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

      this.pendingRespawns.clear();
      this.plugin.getLogger().at(Level.INFO).log("Hologram visibility manager stopped");
   }

   public void setInfiniteVisibilityEnabled(boolean enabled) {
      this.infiniteVisibilityEnabled = enabled;
      if (!enabled || this.scheduler != null && !this.scheduler.isShutdown()) {
         if (!enabled && this.scheduler != null) {
            this.stop();
         }
      } else {
         this.start();
      }

   }

   public boolean isInfiniteVisibilityEnabled() {
      return this.infiniteVisibilityEnabled;
   }

   private void tick() {
      if (this.hologramManager.areHologramsSpawned()) {
         ++this.tickCounter;

         try {
            this.updateVisibility();
            if (this.tickCounter % 2 == 0) {
               this.checkAndRespawnInvalidHolograms();
            }
         } catch (Exception var2) {
         }

      }
   }

   private void updateVisibility() {
      try {
         Map<UUID, List<UUID>> hologramsByWorld = new HashMap();
         Iterator var2 = this.hologramManager.getAllHolograms().iterator();

         while(var2.hasNext()) {
            Hologram hologram = (Hologram)var2.next();
            if (hologram.isVisible()) {
               List<UUID> entityIds = hologram.getLineEntityIds();
               if (!entityIds.isEmpty()) {
                  ((List)hologramsByWorld.computeIfAbsent(hologram.getWorldId(), (k) -> {
                     return new ArrayList();
                  })).addAll(entityIds);
               }
            }
         }

         var2 = hologramsByWorld.entrySet().iterator();

         while(var2.hasNext()) {
            Entry<UUID, List<UUID>> entry = (Entry)var2.next();
            UUID worldId = (UUID)entry.getKey();
            List<UUID> entityIds = (List)entry.getValue();
            World world = this.findWorldByUuid(worldId);
            if (world != null) {
               world.execute(() -> {
                  try {
                     this.injectVisibilityForWorld(world, entityIds);
                  } catch (Exception var4) {
                  }

               });
            }
         }
      } catch (Exception var7) {
      }

   }

   private void checkAndRespawnInvalidHolograms() {
      try {
         Map<UUID, List<Hologram>> hologramsByWorld = new HashMap();
         Iterator var2 = this.hologramManager.getAllHolograms().iterator();

         while(var2.hasNext()) {
            Hologram hologram = (Hologram)var2.next();
            if (hologram.isVisible()) {
               ((List)hologramsByWorld.computeIfAbsent(hologram.getWorldId(), (k) -> {
                  return new ArrayList();
               })).add(hologram);
            }
         }

         var2 = hologramsByWorld.entrySet().iterator();

         while(var2.hasNext()) {
            Entry<UUID, List<Hologram>> entry = (Entry)var2.next();
            UUID worldId = (UUID)entry.getKey();
            List<Hologram> holograms = (List)entry.getValue();
            World world = this.findWorldByUuid(worldId);
            if (world != null) {
               world.execute(() -> {
                  try {
                     this.checkHologramsInWorld(world, holograms);
                  } catch (Exception var4) {
                  }

               });
            }
         }
      } catch (Exception var7) {
      }

   }

   private void checkHologramsInWorld(@Nonnull World world, @Nonnull List<Hologram> holograms) {
      Store<EntityStore> store = world.getEntityStore().getStore();
      Iterator var4 = holograms.iterator();

      while(var4.hasNext()) {
         Hologram hologram = (Hologram)var4.next();

         try {
            List<UUID> entityIds = hologram.getLineEntityIds();
            boolean hasInvalidEntities = false;
            if (entityIds.isEmpty() && !hologram.getLines().isEmpty()) {
               hasInvalidEntities = true;
            } else {
               label70: {
                  Iterator var8 = entityIds.iterator();

                  Ref ref;
                  do {
                     if (!var8.hasNext()) {
                        break label70;
                     }

                     UUID entityId = (UUID)var8.next();
                     ref = ((EntityStore)store.getExternalData()).getRefFromUUID(entityId);
                  } while(ref != null && ref.isValid());

                  hasInvalidEntities = true;
               }
            }

            if (hasInvalidEntities) {
               Vec3d pos = hologram.getPosition();
               int chunkX = MathUtil.floor(pos.x()) >> 5;
               int chunkZ = MathUtil.floor(pos.z()) >> 5;
               long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
               Ref<?> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
               if (chunkRef != null && chunkRef.isValid()) {
                  Long lastRespawn = (Long)this.pendingRespawns.get(hologram.getId());
                  long now = System.currentTimeMillis();
                  if (lastRespawn == null || now - lastRespawn > 15000L) {
                     this.pendingRespawns.put(hologram.getId(), now);
                     this.plugin.getLogger().at(Level.FINE).log("Respawning hologram '" + hologram.getName() + "' (chunk reloaded at " + chunkX + ", " + chunkZ + ")");
                     this.hologramManager.respawnHologram(hologram);
                  }
               }
            } else {
               this.pendingRespawns.remove(hologram.getId());
            }
         } catch (Exception var17) {
         }
      }

   }

   private void injectVisibilityForWorld(@Nonnull World world, @Nonnull List<UUID> entityIds) {
      Store<EntityStore> store = world.getEntityStore().getStore();
      List<Ref<EntityStore>> hologramRefs = new ArrayList();
      Iterator var5 = entityIds.iterator();

      while(var5.hasNext()) {
         UUID entityId = (UUID)var5.next();
         Ref<EntityStore> ref = ((EntityStore)store.getExternalData()).getRefFromUUID(entityId);
         if (ref != null && ref.isValid()) {
            hologramRefs.add(ref);
         }
      }

      if (!hologramRefs.isEmpty()) {
         store.forEachEntityParallel((index, archetypeChunk, commandBuffer) -> {
            try {
               if (!archetypeChunk.getArchetype().contains(EntityViewer.getComponentType())) {
                  return;
               }

               EntityViewer viewer = (EntityViewer)archetypeChunk.getComponent(index, EntityViewer.getComponentType());
               if (viewer != null && viewer.visible != null) {
                  Iterator i$ = hologramRefs.iterator();

                  while(i$.hasNext()) {
                     Ref<EntityStore> hologramRef = (Ref)i$.next();
                     if (hologramRef.isValid()) {
                        viewer.visible.add(hologramRef);
                     }
                  }
               }
            } catch (Exception var7) {
            }

         });
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
}


