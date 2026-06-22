package xyz.thelegacyvoyage.hyessentialsx.managers.hologram;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class BillboardConfig {
   private final HologramService plugin;
   private final Path configFile;
   private long updateIntervalMs = 50L;
   private double maxTrackingDistance = 64.0D;
   private double minTrackingDistance = 32.0D;
   private boolean enabled = true;

   public BillboardConfig(@Nonnull HologramService plugin) {
      this.plugin = plugin;
      this.configFile = plugin.getDataDirectory().resolve("billboard.properties");
   }

   public void load() {
      try {
         Files.createDirectories(this.plugin.getDataDirectory());
         if (Files.exists(this.configFile, new LinkOption[0])) {
            Properties props = new Properties();
            InputStream in = Files.newInputStream(this.configFile);

            try {
               props.load(in);
            } catch (Throwable var6) {
               if (in != null) {
                  try {
                     in.close();
                  } catch (Throwable var5) {
                     var6.addSuppressed(var5);
                  }
               }

               throw var6;
            }

            if (in != null) {
               in.close();
            }

            this.updateIntervalMs = this.parseLong(props, "update_interval_ms", 50L);
            this.maxTrackingDistance = this.parseDouble(props, "max_tracking_distance", 64.0D);
            this.minTrackingDistance = this.parseDouble(props, "min_tracking_distance", 32.0D);
            this.enabled = this.parseBoolean(props, "enabled", true);
            this.plugin.getLogger().at(Level.INFO).log("Loaded billboard config: interval=" + this.updateIntervalMs + "ms, min_dist=" + this.minTrackingDistance + ", max_dist=" + this.maxTrackingDistance);
         } else {
            this.save();
            this.plugin.getLogger().at(Level.INFO).log("Created default billboard.properties config file");
         }
      } catch (IOException var7) {
         this.plugin.getLogger().at(Level.WARNING).log("Failed to load billboard config: " + var7.getMessage());
      }

   }

   public void save() {
      try {
         Files.createDirectories(this.plugin.getDataDirectory());
         OutputStream out = Files.newOutputStream(this.configFile);

         try {
            PrintWriter writer = new PrintWriter(out);
            writer.println("# HologramService Billboard Configuration");
            writer.println("# ");
            writer.println("# enabled: Enable/disable billboard rotation feature (true/false)");
            writer.println("# update_interval_ms: How often to update billboard rotation in milliseconds");
            writer.println("#   Lower = smoother but more CPU/network. Recommended: 50-100");
            writer.println("# min_tracking_distance: Distance (blocks) at which billboard starts rotating to face player");
            writer.println("#   Players further than this will see the billboard at its default rotation");
            writer.println("# max_tracking_distance: Maximum distance (blocks) to track players (optimization)");
            writer.println("");
            writer.println("enabled=" + this.enabled);
            writer.println("update_interval_ms=" + this.updateIntervalMs);
            writer.println("min_tracking_distance=" + this.minTrackingDistance);
            writer.println("max_tracking_distance=" + this.maxTrackingDistance);
            writer.flush();
         } catch (Throwable var5) {
            if (out != null) {
               try {
                  out.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (out != null) {
            out.close();
         }
      } catch (IOException var6) {
         this.plugin.getLogger().at(Level.WARNING).log("Failed to save billboard config: " + var6.getMessage());
      }

   }

   public void reload() {
      this.load();
   }

   public long getUpdateIntervalMs() {
      return this.updateIntervalMs;
   }

   public double getMaxTrackingDistance() {
      return this.maxTrackingDistance;
   }

   public double getMinTrackingDistance() {
      return this.minTrackingDistance;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   private long parseLong(Properties props, String key, long defaultValue) {
      try {
         String value = props.getProperty(key);
         if (value != null) {
            return Long.parseLong(value.trim());
         }
      } catch (NumberFormatException var6) {
      }

      return defaultValue;
   }

   private double parseDouble(Properties props, String key, double defaultValue) {
      try {
         String value = props.getProperty(key);
         if (value != null) {
            return Double.parseDouble(value.trim());
         }
      } catch (NumberFormatException var6) {
      }

      return defaultValue;
   }

   private boolean parseBoolean(Properties props, String key, boolean defaultValue) {
      String value = props.getProperty(key);
      if (value != null) {
         value = value.trim().toLowerCase();
         if (value.equals("true") || value.equals("yes") || value.equals("1")) {
            return true;
         }

         if (value.equals("false") || value.equals("no") || value.equals("0")) {
            return false;
         }
      }

      return defaultValue;
   }
}


