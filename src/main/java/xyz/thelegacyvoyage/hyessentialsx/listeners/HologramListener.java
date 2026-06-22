package xyz.thelegacyvoyage.hyessentialsx.listeners;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class HologramListener {
   @Nonnull
   private final HologramService plugin;

   public HologramListener(@Nonnull HologramService plugin) {
      this.plugin = plugin;
      this.registerEvents();
   }

   private void registerEvents() {
      this.plugin.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onPlayerAddToWorld);
      this.plugin.getLogger().at(Level.INFO).log("Player listeners registered");
   }

   public void onPlayerAddToWorld(@Nonnull AddPlayerToWorldEvent event) {
      if (!this.plugin.getHologramManager().areHologramsSpawned()) {
         this.plugin.getLogger().at(Level.INFO).log("First player joined, spawning saved holograms...");
         this.plugin.getHologramManager().spawnAllHolograms();
      } else {
         World world = event.getWorld();
         UUID worldId = world.getWorldConfig().getUuid();
         this.plugin.getHologramManager().ensureHologramsVisibleInWorld(worldId);
      }

   }
}


