package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.placeholder;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Hologram;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.NameplateUpdate;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.Visible;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PlaceholderUpdateManager {
   @Nonnull
   private final HologramService plugin;
   @Nonnull
   private final HologramManager hologramManager;
   private ScheduledExecutorService scheduler;
   private static final Pattern PLAYER_PLACEHOLDER_PATTERN = Pattern.compile("\\{player_[^}]+}");
   private final Map<UUID, List<Integer>> hologramsWithPlaceholders = new HashMap();
   private final Map<UUID, List<String>> originalLines = new HashMap();
   private final Map<UUID, Set<Integer>> linesWithPlayerPlaceholders = new HashMap();

   public PlaceholderUpdateManager(@Nonnull HologramService plugin, @Nonnull HologramManager hologramManager) {
      this.plugin = plugin;
      this.hologramManager = hologramManager;
   }

   public void start() {
      if (!this.plugin.getConfigManager().isHologramPlaceholdersEnabled()) {
         this.plugin.getLogger().at(Level.INFO).log("Hologram placeholders disabled in config.");
      } else if (this.scheduler == null || this.scheduler.isShutdown()) {
         this.scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread thread = new Thread(r, "HologramService-Placeholders");
            thread.setDaemon(true);
            return thread;
         });
         long intervalMs = this.plugin.getConfigManager().getHologramPlaceholderUpdateIntervalMs();
         this.scheduler.scheduleAtFixedRate(this::updatePlaceholders, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
         this.plugin.getLogger().at(Level.INFO).log("Placeholder update manager started (interval=" + intervalMs + "ms)");
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

      this.hologramsWithPlaceholders.clear();
      this.originalLines.clear();
   }

   public void registerHologram(@Nonnull Hologram hologram) {
      PlaceholderIntegration papi = this.plugin.getPlaceholderIntegration();
      List<String> lines = hologram.getLines();
      List<Integer> placeholderIndices = new ArrayList();
      Set<Integer> playerPlaceholderIndices = new HashSet();

      for(int i = 0; i < lines.size(); ++i) {
         String line = (String)lines.get(i);
         if (papi.containsPlaceholders(line)) {
            placeholderIndices.add(i);
            if (PLAYER_PLACEHOLDER_PATTERN.matcher(line).find()) {
               playerPlaceholderIndices.add(i);
            }
         }
      }

      if (!placeholderIndices.isEmpty()) {
         this.hologramsWithPlaceholders.put(hologram.getId(), placeholderIndices);
         this.originalLines.put(hologram.getId(), new ArrayList(lines));
         Api var10000;
         String var10001;
         if (!playerPlaceholderIndices.isEmpty()) {
            this.linesWithPlayerPlaceholders.put(hologram.getId(), playerPlaceholderIndices);
            var10000 = this.plugin.getLogger().at(Level.FINE);
            var10001 = hologram.getName();
            var10000.log("Registered hologram '" + var10001 + "' for placeholder updates (" + placeholderIndices.size() + " lines, " + playerPlaceholderIndices.size() + " with player placeholders)");
         } else {
            var10000 = this.plugin.getLogger().at(Level.FINE);
            var10001 = hologram.getName();
            var10000.log("Registered hologram '" + var10001 + "' for placeholder updates (" + placeholderIndices.size() + " lines)");
         }
      }

   }

   public void unregisterHologram(@Nonnull UUID hologramId) {
      this.hologramsWithPlaceholders.remove(hologramId);
      this.originalLines.remove(hologramId);
      this.linesWithPlayerPlaceholders.remove(hologramId);
   }

   private void updatePlaceholders() {
      if (this.hologramManager.areHologramsSpawned()) {
         PlaceholderIntegration papi = this.plugin.getPlaceholderIntegration();
         if (papi.isAvailable()) {
            Iterator var2 = this.hologramsWithPlaceholders.entrySet().iterator();

            while(true) {
               UUID hologramId;
               List lineIndices;
               Hologram hologram;
               List origLines;
               do {
                  do {
                     do {
                        if (!var2.hasNext()) {
                           return;
                        }

                        Entry<UUID, List<Integer>> entry = (Entry)var2.next();
                        hologramId = (UUID)entry.getKey();
                        lineIndices = (List)entry.getValue();
                        hologram = this.hologramManager.getHologram(hologramId);
                     } while(hologram == null);
                  } while(!hologram.isVisible());

                  origLines = (List)this.originalLines.get(hologramId);
               } while(origLines == null);

               List<UUID> entityIds = hologram.getLineEntityIds();
               Set<Integer> playerLines = (Set)this.linesWithPlayerPlaceholders.getOrDefault(hologramId, Collections.emptySet());
               Iterator var10 = lineIndices.iterator();

               while(var10.hasNext()) {
                  int lineIndex = (Integer)var10.next();
                  if (lineIndex < origLines.size() && lineIndex < entityIds.size()) {
                     String originalText = (String)origLines.get(lineIndex);
                     UUID entityId = (UUID)entityIds.get(lineIndex);
                     if (playerLines.contains(lineIndex)) {
                        this.updateEntityNameplatePerPlayer(hologram.getWorldId(), entityId, originalText);
                     } else {
                        String formattedText = this.hologramManager.formatText(originalText);
                        this.updateEntityNameplate(hologram.getWorldId(), entityId, formattedText);
                     }
                  }
               }
            }
         }
      }
   }

   private void updateEntityNameplate(@Nonnull UUID worldId, @Nonnull UUID entityId, @Nonnull String text) {
      try {
         World world = this.findWorldByUuid(worldId);
         if (world == null) {
            return;
         }

         world.execute(() -> {
            try {
               Store<EntityStore> store = world.getEntityStore().getStore();
               Ref<EntityStore> ref = ((EntityStore)store.getExternalData()).getRefFromUUID(entityId);
               if (ref == null || !ref.isValid()) {
                  return;
               }

                Nameplate nameplate = store.getComponent(ref, Nameplate.getComponentType());
                if (nameplate != null) {
                    nameplate.setText(text);
                }
            } catch (Exception var6) {
            }

         });
      } catch (Exception var5) {
      }

   }

   private void updateEntityNameplatePerPlayer(@Nonnull UUID worldId, @Nonnull UUID entityId, @Nonnull String originalText) {
      try {
         World world = this.findWorldByUuid(worldId);
         if (world == null) {
            return;
         }

         world.execute(() -> {
            try {
               Store<EntityStore> store = world.getEntityStore().getStore();
               Ref<EntityStore> ref = ((EntityStore)store.getExternalData()).getRefFromUUID(entityId);
               if (ref == null || !ref.isValid()) {
                  return;
               }

               Visible visible = (Visible)store.getComponent(ref, EntityModule.get().getVisibleComponentType());
               if (visible == null || visible.visibleTo.isEmpty()) {
                  return;
               }

               Iterator i$ = visible.visibleTo.entrySet().iterator();

               while(i$.hasNext()) {
                  Entry<Ref<EntityStore>, EntityViewer> viewerEntry = (Entry)i$.next();
                  Ref<EntityStore> playerRef = (Ref)viewerEntry.getKey();
                  EntityViewer viewer = (EntityViewer)viewerEntry.getValue();
                  if (playerRef.isValid()) {
                     PlaceholderUpdateManager.PlayerContext playerContext = this.getPlayerContext(store, playerRef, world);
                     String personalizedText = this.hologramManager.formatTextWithFullContext(originalText, playerContext.player, playerContext.uuid, playerContext.name, playerContext.worldName, playerContext.x, playerContext.y, playerContext.z);
                     NameplateUpdate update = new NameplateUpdate();
                     update.text = personalizedText;
                     viewer.queueUpdate(ref, update);
                  }
               }
            } catch (Exception var14) {
               this.plugin.getLogger().at(Level.FINE).log("Error updating per-player nameplate: " + var14.getMessage());
            }

         });
      } catch (Exception var5) {
      }

   }

   @Nonnull
   private PlaceholderUpdateManager.PlayerContext getPlayerContext(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef, @Nonnull World world) {
      Player player = null;
      UUID uuid = null;
      String name = "";
      String worldName = "";
      int x = 0;
      int y = 0;
      int z = 0;

      try {
         uuid = this.getPlayerUuidFromRef(store, playerRef);
         TransformComponent transform = (TransformComponent)store.getComponent(playerRef, TransformComponent.getComponentType());
         if (transform != null) {
            Vector3d pos = transform.getPosition();
            x = (int)pos.getX();
            y = (int)pos.getY();
            z = (int)pos.getZ();
         }

         worldName = world.getName();
         List<PlayerRef> players = Universe.get().getPlayers();
         Iterator var13 = players.iterator();

         while(var13.hasNext()) {
            PlayerRef pRef = (PlayerRef)var13.next();
            if (uuid != null && uuid.equals(pRef.getUuid())) {
               name = pRef.getUsername() != null ? pRef.getUsername() : "";
               if (pRef.getReference() != null && pRef.getReference().isValid()) {
                  player = this.getPlayerFromRef(world, pRef);
               }
               break;
            }
         }
      } catch (Exception var15) {
      }

      return new PlaceholderUpdateManager.PlayerContext(player, uuid, name, worldName, x, y, z);
   }

   @Nullable
   private Player getPlayerFromRef(@Nonnull World world, @Nonnull PlayerRef playerRef) {
      try {
         Ref<EntityStore> ref = playerRef.getReference();
         if (ref == null || !ref.isValid()) {
            return null;
         }
         Store<EntityStore> store = world.getEntityStore().getStore();
         return (Player)store.getComponent(ref, Player.getComponentType());
      } catch (Exception var4) {
         return null;
      }
   }

   @Nullable
   private UUID getPlayerUuidFromRef(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
      try {
         UUIDComponent uuidComp = (UUIDComponent)store.getComponent(playerRef, UUIDComponent.getComponentType());
         if (uuidComp != null) {
            return uuidComp.getUuid();
         }
      } catch (Exception var4) {
      }

      return null;
   }

   @Nonnull
   private String getPlayerNameFromUuid(@Nullable UUID playerUuid) {
      if (playerUuid == null) {
         return "";
      } else {
         try {
            List<PlayerRef> players = Universe.get().getPlayers();
            Iterator var3 = players.iterator();

            while(var3.hasNext()) {
               PlayerRef pRef = (PlayerRef)var3.next();
               if (pRef.getUuid() != null && pRef.getUuid().equals(playerUuid)) {
                  return pRef.getUsername() != null ? pRef.getUsername() : "";
               }
            }
         } catch (Exception var5) {
         }

         return "";
      }
   }

   @Nonnull
   private String getPlayerNameFromRef(@Nonnull Ref<EntityStore> playerRef) {
      try {
         List<PlayerRef> players = Universe.get().getPlayers();
         Iterator var3 = players.iterator();

         while(var3.hasNext()) {
            PlayerRef pRef = (PlayerRef)var3.next();
            if (pRef.getReference() != null && pRef.getReference().equals(playerRef)) {
               return pRef.getUsername();
            }
         }
      } catch (Exception var5) {
      }

      return "";
   }

   @Nullable
   private World findWorldByUuid(@Nonnull UUID worldId) {
      try {
         Map<String, World> worlds = Universe.get().getWorlds();
         Iterator var3 = worlds.values().iterator();

         while(var3.hasNext()) {
            World world = (World)var3.next();
            if (world.getWorldConfig() != null && worldId.equals(world.getWorldConfig().getUuid())) {
               return world;
            }
         }
      } catch (Exception var5) {
      }

      return null;
   }

   private static class PlayerContext {
      final Player player;
      final UUID uuid;
      final String name;
      final String worldName;
      final int x;
      final int y;
      final int z;

      PlayerContext(Player player, UUID uuid, String name, String worldName, int x, int y, int z) {
         this.player = player;
         this.uuid = uuid;
         this.name = name;
         this.worldName = worldName;
         this.x = x;
         this.y = y;
         this.z = z;
      }
   }
}


