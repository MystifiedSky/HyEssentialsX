package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.entitytool;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Hologram;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class HologramEntityTracker {
   @Nonnull
   private final HologramService plugin;
   @Nonnull
   private final Map<UUID, Hologram> entityToHologram;
   @Nonnull
   private final Map<UUID, Integer> entityToLineIndex;
   @Nonnull
   private final Map<Integer, UUID> networkIdToEntity;

   public HologramEntityTracker(@Nonnull HologramService plugin) {
      this.plugin = plugin;
      this.entityToHologram = new ConcurrentHashMap();
      this.entityToLineIndex = new ConcurrentHashMap();
      this.networkIdToEntity = new ConcurrentHashMap();
   }

   public void registerEntity(@Nonnull UUID entityUuid, @Nonnull Hologram hologram, int lineIndex) {
      this.entityToHologram.put(entityUuid, hologram);
      this.entityToLineIndex.put(entityUuid, lineIndex);
   }

   public void registerNetworkId(int networkId, @Nonnull UUID entityUuid) {
      this.networkIdToEntity.put(networkId, entityUuid);
   }

   public void unregisterEntity(@Nonnull UUID entityUuid) {
      this.entityToHologram.remove(entityUuid);
      this.entityToLineIndex.remove(entityUuid);
      this.networkIdToEntity.entrySet().removeIf((entry) -> {
         return ((UUID)entry.getValue()).equals(entityUuid);
      });
   }

   public void unregisterHologram(@Nonnull Hologram hologram) {
      Iterator var2 = hologram.getLineEntityIds().iterator();

      while(var2.hasNext()) {
         UUID entityUuid = (UUID)var2.next();
         this.unregisterEntity(entityUuid);
      }

   }

   @Nullable
   public Hologram getHologramForEntity(@Nonnull UUID entityUuid) {
      return (Hologram)this.entityToHologram.get(entityUuid);
   }

   public int getLineIndexForEntity(@Nonnull UUID entityUuid) {
      Integer index = (Integer)this.entityToLineIndex.get(entityUuid);
      return index != null ? index : -1;
   }

   @Nullable
   public UUID getEntityFromNetworkId(int networkId) {
      return (UUID)this.networkIdToEntity.get(networkId);
   }

   public boolean isHologramEntity(@Nonnull UUID entityUuid) {
      return this.entityToHologram.containsKey(entityUuid);
   }

   public boolean isHologramNetworkId(int networkId) {
      UUID entityUuid = (UUID)this.networkIdToEntity.get(networkId);
      return entityUuid != null && this.entityToHologram.containsKey(entityUuid);
   }

   public void clear() {
      this.entityToHologram.clear();
      this.entityToLineIndex.clear();
      this.networkIdToEntity.clear();
   }

   public int getTrackedCount() {
      return this.entityToHologram.size();
   }
}


