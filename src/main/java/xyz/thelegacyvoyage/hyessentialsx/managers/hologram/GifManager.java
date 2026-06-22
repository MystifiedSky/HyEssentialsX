package xyz.thelegacyvoyage.hyessentialsx.managers.hologram;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.FacingDirection;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;

public class GifManager {
   @Nonnull
   private final HologramService plugin;
   @Nonnull
   private final Path gifsFolder;
   @Nonnull
   private final ImageManager imageManager;
   @Nonnull
   private final Map<String, GifManager.GifData> gifRegistry = new ConcurrentHashMap();
   @Nonnull
   private final Map<UUID, GifManager.GifAnimationState> activeAnimations = new ConcurrentHashMap();
   @Nullable
   private ScheduledExecutorService animationScheduler;
   private static final long ANIMATION_TICK_MS = 50L;
   private static final String GIF_ASSET_PREFIX = "HyEssentialsX_Hologram_Gif_";

   public GifManager(@Nonnull HologramService plugin, @Nonnull ImageManager imageManager) {
      this.plugin = plugin;
      this.imageManager = imageManager;
      this.gifsFolder = plugin.getDataDirectory().resolve("gifs");
   }

   public void initialize() {
      try {
         if (!Files.exists(this.gifsFolder, new LinkOption[0])) {
            Files.createDirectories(this.gifsFolder);
            this.plugin.getLogger().at(Level.INFO).log("Created gifs folder at: " + String.valueOf(this.gifsFolder));
            this.createGifReadme();
         }

         this.scanGifFiles();
         this.plugin.getLogger().at(Level.INFO).log("GIF system initialized. " + this.gifRegistry.size() + " GIFs registered.");
      } catch (IOException var2) {
         this.plugin.getLogger().at(Level.WARNING).log("Failed to initialize GIF manager: " + var2.getMessage());
      }

   }

   public void start() {
      if (this.animationScheduler == null || this.animationScheduler.isShutdown()) {
         this.animationScheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "HologramService-GifAnimator");
            t.setDaemon(true);
            return t;
         });
         this.animationScheduler.scheduleAtFixedRate(this::tickAnimations, 50L, 50L, TimeUnit.MILLISECONDS);
         this.plugin.getLogger().at(Level.INFO).log("GIF animation scheduler started");
      }
   }

   public void stop() {
      if (this.animationScheduler != null) {
         this.animationScheduler.shutdown();

         try {
            if (!this.animationScheduler.awaitTermination(1L, TimeUnit.SECONDS)) {
               this.animationScheduler.shutdownNow();
            }
         } catch (InterruptedException var2) {
            this.animationScheduler.shutdownNow();
         }

         this.animationScheduler = null;
      }

      this.activeAnimations.clear();
      this.plugin.getLogger().at(Level.INFO).log("GIF animation scheduler stopped");
   }

   private void tickAnimations() {
      long currentTime = System.currentTimeMillis();
      Iterator var3 = this.activeAnimations.values().iterator();

      while(var3.hasNext()) {
         GifManager.GifAnimationState state = (GifManager.GifAnimationState)var3.next();

         try {
            GifManager.GifData gifData = (GifManager.GifData)this.gifRegistry.get(state.gifName);
            if (gifData != null && gifData.frameCount != 0) {
               long elapsed = currentTime - state.lastFrameTime;
               state.accumulatedTime += (long)((float)elapsed * state.speedMultiplier);
               state.lastFrameTime = currentTime;
               int frameDelay = gifData.frameDelays[state.currentFrame];
               if (frameDelay <= 0) {
                  frameDelay = 100;
               }

               if (state.accumulatedTime >= (long)frameDelay) {
                  state.accumulatedTime -= (long)frameDelay;
                  state.currentFrame = (state.currentFrame + 1) % gifData.frameCount;
                  this.updateEntityFrame(state, gifData);
               }
            }
         } catch (Exception var9) {
            this.plugin.getLogger().at(Level.FINE).log("Error ticking GIF animation: " + var9.getMessage());
         }
      }

   }

   private void updateEntityFrame(@Nonnull GifManager.GifAnimationState state, @Nonnull GifManager.GifData gifData) {
      try {
         if (state.frameModels == null) {
            state.frameModels = new Model[gifData.frameCount];
            int i = 0;

            while(true) {
               if (i >= gifData.frameCount) {
                  this.plugin.getLogger().at(Level.INFO).log("Cached " + gifData.frameCount + " frame models for GIF: " + state.gifName);
                  break;
               }

               String frameAssetId = gifData.frameAssetIds[i];
               if (state.billboard && state.doubleSided) {
                  frameAssetId = frameAssetId + "_Billboard_DoubleSided";
               } else if (state.billboard) {
                  frameAssetId = frameAssetId + "_Billboard";
               } else if (state.doubleSided) {
                  frameAssetId = frameAssetId + "_DoubleSided";
               }

               ModelAsset frameAsset = (ModelAsset)ModelAsset.getAssetMap().getAsset(frameAssetId);
               if (frameAsset != null) {
                  state.frameModels[i] = Model.createScaledModel(frameAsset, state.scale);
               } else {
                  this.plugin.getLogger().at(Level.WARNING).log("GIF frame asset not found: " + frameAssetId);
               }

               ++i;
            }
         }

         Model frameModel = state.frameModels[state.currentFrame];
         if (frameModel == null) {
            return;
         }

         World world = this.findWorldByUuid(state.worldId);
         if (world == null) {
            return;
         }

         world.execute(() -> {
            try {
               Store<EntityStore> store = world.getEntityStore().getStore();
               Ref<EntityStore> ref = ((EntityStore)store.getExternalData()).getRefFromUUID(state.entityId);
               if (ref != null && ref.isValid()) {
                  store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(frameModel));
               }
            } catch (Exception var5) {
            }

         });
      } catch (Exception var6) {
         this.plugin.getLogger().at(Level.FINE).log("Failed to update GIF frame: " + var6.getMessage());
      }

   }

   @Nullable
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

   public void registerAnimation(@Nonnull UUID entityId, @Nonnull UUID worldId, @Nonnull String gifName, float scale, float speedMultiplier, boolean billboard, @Nonnull FacingDirection facing) {
      this.registerAnimation(entityId, worldId, gifName, scale, speedMultiplier, billboard, false, facing);
   }

   public void registerAnimation(@Nonnull UUID entityId, @Nonnull UUID worldId, @Nonnull String gifName, float scale, float speedMultiplier, boolean billboard, boolean doubleSided, @Nonnull FacingDirection facing) {
      String normalizedName = this.normalizeGifName(gifName);
      GifManager.GifData gifData = (GifManager.GifData)this.gifRegistry.get(normalizedName);
      if (gifData == null) {
         this.plugin.getLogger().at(Level.WARNING).log("GIF not found: " + gifName);
      } else {
         GifManager.GifAnimationState state = new GifManager.GifAnimationState(entityId, worldId, normalizedName, scale, speedMultiplier, billboard, doubleSided, facing);
         this.activeAnimations.put(entityId, state);
         this.plugin.getLogger().at(Level.INFO).log("Registered GIF animation for entity " + String.valueOf(entityId) + ": " + gifName + " (frames: " + gifData.frameCount + ", speed: " + speedMultiplier + "x, doubleSided: " + doubleSided + ")");
      }
   }

   public void unregisterAnimation(@Nonnull UUID entityId) {
      this.activeAnimations.remove(entityId);
   }

   public void scanGifFiles() {
      this.gifRegistry.clear();
      if (Files.exists(this.gifsFolder, new LinkOption[0])) {
         try (Stream<Path> files = Files.list(this.gifsFolder)) {
            files.filter((p) -> !Files.isDirectory(p, new LinkOption[0]))
                 .filter((p) -> p.getFileName().toString().toLowerCase().endsWith(".gif"))
                 .forEach((gifFile) -> {
                    try {
                       this.processGifFile(gifFile);
                    } catch (Exception var6) {
                       Api var10000 = this.plugin.getLogger().at(Level.WARNING);
                       String var10001 = String.valueOf(gifFile.getFileName());
                       var10000.log("Failed to process GIF: " + var10001 + ": " + var6.getMessage());
                    }
                 });
         } catch (IOException var7) {
            this.plugin.getLogger().at(Level.WARNING).log("Failed to scan gifs folder: " + var7.getMessage());
         }

      }
   }

   private void processGifFile(@Nonnull Path gifFile) throws IOException {
      String fileName = gifFile.getFileName().toString();
      String baseName = fileName.substring(0, fileName.lastIndexOf(46));
      String gifName = this.normalizeGifName(baseName);
      this.plugin.getLogger().at(Level.INFO).log("Processing GIF: " + fileName);
      ImageReader reader = (ImageReader)ImageIO.getImageReadersByFormatName("gif").next();

      try {
         ImageInputStream stream;
         label362: {
            stream = ImageIO.createImageInputStream(gifFile.toFile());

            try {
               reader.setInput(stream);
               int frameCount = reader.getNumImages(true);
               if (frameCount == 0) {
                  this.plugin.getLogger().at(Level.WARNING).log("GIF has no frames: " + fileName);
                  break label362;
               }

               int[] frameDelays = new int[frameCount];
               String[] frameAssetIds = new String[frameCount];
               BufferedImage firstFrame = reader.read(0);
               int width = firstFrame.getWidth();
               int height = firstFrame.getHeight();

               try {
                  IIOMetadata streamMetadata = reader.getStreamMetadata();
                  if (streamMetadata != null) {
                     IIOMetadataNode root = (IIOMetadataNode)streamMetadata.getAsTree(streamMetadata.getNativeMetadataFormatName());
                     IIOMetadataNode logicalScreen = this.findNode(root, "LogicalScreenDescriptor");
                     if (logicalScreen != null) {
                        String logicalWidth = logicalScreen.getAttribute("logicalScreenWidth");
                        String logicalHeight = logicalScreen.getAttribute("logicalScreenHeight");
                        if (logicalWidth != null && !logicalWidth.isEmpty()) {
                           width = Integer.parseInt(logicalWidth);
                        }

                        if (logicalHeight != null && !logicalHeight.isEmpty()) {
                           height = Integer.parseInt(logicalHeight);
                        }
                     }
                  }
               } catch (Exception var38) {
               }

               Path framesFolder = this.imageManager.getImagesFolder().resolve("gif_" + gifName);
               if (Files.exists(framesFolder, new LinkOption[0])) {
                  Stream oldFiles = Files.list(framesFolder);

                  try {
                     Iterator var45 = oldFiles.toList().iterator();

                     while(var45.hasNext()) {
                        Path oldFile = (Path)var45.next();
                        Files.deleteIfExists(oldFile);
                     }
                  } catch (Throwable var39) {
                     if (oldFiles != null) {
                        try {
                           oldFiles.close();
                        } catch (Throwable var36) {
                           var39.addSuppressed(var36);
                        }
                     }

                     throw var39;
                  }

                  if (oldFiles != null) {
                     oldFiles.close();
                  }

                  this.plugin.getLogger().at(Level.INFO).log("Cleaned old frames for GIF: " + gifName);
               }

               Files.createDirectories(framesFolder);
               BufferedImage canvas = new BufferedImage(width, height, 2);
               Graphics2D g2d = canvas.createGraphics();
               BufferedImage previousCanvas = null;
               int i = 0;

               while(true) {
                  if (i >= frameCount) {
                     g2d.dispose();
                     boolean loaded = frameAssetIds.length > 0 && this.isModelAssetLoaded(frameAssetIds[0]);
                     GifManager.GifData gifData = new GifManager.GifData(gifName, frameCount, frameDelays, width, height, frameAssetIds, loaded);
                     this.gifRegistry.put(gifName, gifData);
                     this.plugin.getLogger().at(Level.INFO).log("Registered GIF: " + gifName + " (" + frameCount + " frames, " + width + "x" + height + ", total duration: " + gifData.getTotalDuration() + "ms)");
                     break;
                  }

                  BufferedImage frame = reader.read(i);
                  int frameX = 0;
                  int frameY = 0;
                  String disposalMethod = "none";

                  try {
                     IIOMetadata metadata = reader.getImageMetadata(i);
                     IIOMetadataNode root = (IIOMetadataNode)metadata.getAsTree(metadata.getNativeMetadataFormatName());
                     IIOMetadataNode imageDescriptor = this.findNode(root, "ImageDescriptor");
                     if (imageDescriptor != null) {
                        String x = imageDescriptor.getAttribute("imageLeftPosition");
                        String y = imageDescriptor.getAttribute("imageTopPosition");
                        if (x != null && !x.isEmpty()) {
                           frameX = Integer.parseInt(x);
                        }

                        if (y != null && !y.isEmpty()) {
                           frameY = Integer.parseInt(y);
                        }
                     }

                     IIOMetadataNode gce = this.findNode(root, "GraphicControlExtension");
                     if (gce != null) {
                        disposalMethod = gce.getAttribute("disposalMethod");
                        if (disposalMethod == null) {
                           disposalMethod = "none";
                        }
                     }
                  } catch (Exception var37) {
                  }

                  frameDelays[i] = this.getFrameDelay(reader, i);
                  if ("restoreToPrevious".equals(disposalMethod)) {
                     previousCanvas = this.copyImage(canvas);
                  }

                  g2d.drawImage(frame, frameX, frameY, (ImageObserver)null);
                  String frameFileName = gifName + "_frame_" + String.format("%03d", i) + ".png";
                  Path framePath = framesFolder.resolve(frameFileName);
                  ImageIO.write(this.copyImage(canvas), "PNG", framePath.toFile());
                  String var10002 = this.capitalize(gifName);
                  frameAssetIds[i] = "HyEssentialsX_Hologram_Gif_" + var10002 + "_Frame_" + String.format("%03d", i);
                  byte var55 = -1;
                  switch(disposalMethod.hashCode()) {
                  case -1954179604:
                     if (disposalMethod.equals("restoreToBackgroundColor")) {
                        var55 = 0;
                     }
                     break;
                  case 1612371904:
                     if (disposalMethod.equals("restoreToPrevious")) {
                        var55 = 1;
                     }
                  }

                  switch(var55) {
                  case 0:
                     g2d.setComposite(AlphaComposite.Clear);
                     g2d.fillRect(frameX, frameY, frame.getWidth(), frame.getHeight());
                     g2d.setComposite(AlphaComposite.SrcOver);
                     break;
                  case 1:
                     if (previousCanvas != null) {
                        g2d.setComposite(AlphaComposite.Src);
                        g2d.drawImage(previousCanvas, 0, 0, (ImageObserver)null);
                        g2d.setComposite(AlphaComposite.SrcOver);
                     }
                  }

                  ++i;
               }
            } catch (Throwable var40) {
               if (stream != null) {
                  try {
                     stream.close();
                  } catch (Throwable var35) {
                     var40.addSuppressed(var35);
                  }
               }

               throw var40;
            }

            if (stream != null) {
               stream.close();
            }

            return;
         }

         if (stream != null) {
            stream.close();
         }
      } finally {
         reader.dispose();
      }

   }

   @Nonnull
   private BufferedImage copyImage(@Nonnull BufferedImage source) {
      BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
      Graphics2D g = copy.createGraphics();
      g.drawImage(source, 0, 0, (ImageObserver)null);
      g.dispose();
      return copy;
   }

   private int getFrameDelay(@Nonnull ImageReader reader, int frameIndex) {
      try {
         IIOMetadata metadata = reader.getImageMetadata(frameIndex);
         String[] names = metadata.getMetadataFormatNames();
         String[] var5 = names;
         int var6 = names.length;

         for(int var7 = 0; var7 < var6; ++var7) {
            String name = var5[var7];
            IIOMetadataNode root = (IIOMetadataNode)metadata.getAsTree(name);
            IIOMetadataNode gce = this.findNode(root, "GraphicControlExtension");
            if (gce != null) {
               String delayStr = gce.getAttribute("delayTime");
               if (delayStr != null && !delayStr.isEmpty()) {
                  return Integer.parseInt(delayStr) * 10;
               }
            }
         }
      } catch (Exception var12) {
      }

      return 100;
   }

   @Nullable
   private IIOMetadataNode findNode(@Nonnull IIOMetadataNode root, @Nonnull String nodeName) {
      if (root.getNodeName().equals(nodeName)) {
         return root;
      } else {
         for(int i = 0; i < root.getLength(); ++i) {
            Node var5 = root.item(i);
            if (var5 instanceof IIOMetadataNode) {
               IIOMetadataNode child = (IIOMetadataNode)var5;
               IIOMetadataNode result = this.findNode(child, nodeName);
               if (result != null) {
                  return result;
               }
            }
         }

         return null;
      }
   }

   private boolean isModelAssetLoaded(@Nonnull String modelAssetId) {
      try {
         return ModelAsset.getAssetMap().getAsset(modelAssetId) != null;
      } catch (Exception var3) {
         return false;
      }
   }

   @Nonnull
   private String normalizeGifName(@Nonnull String name) {
      return name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
   }

   @Nonnull
   private String capitalize(@Nonnull String input) {
      if (input.isEmpty()) {
         return input;
      } else {
         StringBuilder result = new StringBuilder();
         boolean capitalizeNext = true;
         char[] var4 = input.toCharArray();
         int var5 = var4.length;

         for(int var6 = 0; var6 < var5; ++var6) {
            char c = var4[var6];
            if (c == '_') {
               result.append(c);
               capitalizeNext = true;
            } else if (capitalizeNext) {
               result.append(Character.toUpperCase(c));
               capitalizeNext = false;
            } else {
               result.append(c);
            }
         }

         return result.toString();
      }
   }

   @Nullable
   public GifManager.GifData getGifData(@Nonnull String gifName) {
      return (GifManager.GifData)this.gifRegistry.get(this.normalizeGifName(gifName));
   }

   public boolean hasGif(@Nonnull String gifName) {
      return this.gifRegistry.containsKey(this.normalizeGifName(gifName));
   }

   @Nonnull
   public Set<String> getAvailableGifs() {
      return Collections.unmodifiableSet(this.gifRegistry.keySet());
   }

   @Nonnull
   public Map<String, GifManager.GifData> getAllGifData() {
      return Collections.unmodifiableMap(this.gifRegistry);
   }

   @Nonnull
   public Path getGifsFolder() {
      return this.gifsFolder;
   }

   @Nullable
   public Model createFirstFrameModel(@Nonnull String gifName, float scale, boolean billboard) {
      return this.createFirstFrameModel(gifName, scale, billboard, false);
   }

   @Nullable
   public Model createFirstFrameModel(@Nonnull String gifName, float scale, boolean billboard, boolean doubleSided) {
      String normalizedName = this.normalizeGifName(gifName);
      GifManager.GifData gifData = (GifManager.GifData)this.gifRegistry.get(normalizedName);
      if (gifData != null && gifData.frameCount != 0) {
         String frameAssetId = gifData.frameAssetIds[0];
         if (billboard && doubleSided) {
            frameAssetId = frameAssetId + "_Billboard_DoubleSided";
         } else if (billboard) {
            frameAssetId = frameAssetId + "_Billboard";
         } else if (doubleSided) {
            frameAssetId = frameAssetId + "_DoubleSided";
         }

         try {
            ModelAsset frameAsset = (ModelAsset)ModelAsset.getAssetMap().getAsset(frameAssetId);
            if (frameAsset == null) {
               this.plugin.getLogger().at(Level.WARNING).log("GIF frame asset not loaded: " + frameAssetId + ". Run /holo reload and restart the server.");
               return null;
            } else {
               return Model.createScaledModel(frameAsset, scale);
            }
         } catch (Exception var9) {
            this.plugin.getLogger().at(Level.WARNING).log("Failed to create GIF frame model: " + var9.getMessage());
            return null;
         }
      } else {
         this.plugin.getLogger().at(Level.WARNING).log("GIF not found or has no frames: " + gifName);
         return null;
      }
   }

   public void reload() {
      this.reload(false);
   }

   public void reload(boolean liveReload) {
      this.plugin.getLogger().at(Level.INFO).log("Reloading GIF registry...");
      Map<UUID, GifManager.GifAnimationState> savedAnimations = new HashMap(this.activeAnimations);
      this.activeAnimations.clear();
      this.scanGifFiles();
      if (liveReload) {
         this.plugin.getLogger().at(Level.INFO).log("Live loading GIF frame assets...");
         this.imageManager.reload(true);
      }

      Iterator var3 = savedAnimations.values().iterator();

      while(var3.hasNext()) {
         GifManager.GifAnimationState state = (GifManager.GifAnimationState)var3.next();
         if (this.gifRegistry.containsKey(state.gifName)) {
            this.activeAnimations.put(state.entityId, state);
         }
      }

      this.plugin.getLogger().at(Level.INFO).log("GIF registry reloaded! " + this.gifRegistry.size() + " GIFs registered.");
   }

   public void liveReload() {
      this.reload(true);
   }

   private void createGifReadme() {
      Path readmePath = this.gifsFolder.resolve("README.txt");
      String readme = "HologramService - GIF Animation Support\n=====================================\n\nHOW TO USE:\n-----------\n1. Place your GIF file in this folder (e.g., animation.gif)\n2. Run: /holo reload (extracts frames and generates assets)\n3. Restart the server to load the new GIF frames\n4. Use in holograms: /holo addline <name> gif:animation\n\nFORMAT OPTIONS:\n---------------\ngif:<name>                         - default scale and speed\ngif:<name>:<scale>                 - custom scale (e.g., gif:logo:2.0)\ngif:<name>:<scale>:<speed>         - custom speed multiplier (e.g., gif:logo:1.0:0.5 for half speed)\ngif:<name>:<scale>:<speed>:<dir>   - with facing direction (n, s, e, w, etc.)\ngif:<name>:<scale>:<speed>:true    - billboard mode (faces each player)\n\nSPEED MULTIPLIER:\n- 1.0 = original GIF speed\n- 0.5 = half speed (slower)\n- 2.0 = double speed (faster)\n\nIMPORTANT NOTES:\n- GIF frames are extracted to images/gif_<name>/ folder\n- Each frame becomes a separate image asset\n- Large GIFs with many frames may impact performance\n\nRECOMMENDED SETTINGS:\n- Keep GIFs under 64 frames for best performance\n- Use reasonable dimensions (256x256 or smaller recommended)\n- Optimize your GIFs before adding them\n\nTROUBLESHOOTING:\n- If animation doesn't play after /holo reload, restart the server\n- Check logs for \"Registered GIF\" messages\n- Use /holo listgifs to see available GIFs\n";

      try {
         Files.writeString(readmePath, readme, new OpenOption[0]);
      } catch (IOException var4) {
         this.plugin.getLogger().at(Level.FINE).log("Could not create GIF README: " + var4.getMessage());
      }

   }

   @Nonnull
   public static String getAssetPrefix() {
      return "HyEssentialsX_Hologram_Gif_";
   }

   public static class GifAnimationState {
      public final UUID entityId;
      public final UUID worldId;
      public final String gifName;
      public final float scale;
      public final float speedMultiplier;
      public final boolean billboard;
      public final boolean doubleSided;
      public final FacingDirection facing;
      public int currentFrame;
      public long lastFrameTime;
      public long accumulatedTime;
      public Model[] frameModels;

      public GifAnimationState(UUID entityId, UUID worldId, String gifName, float scale, float speedMultiplier, boolean billboard, FacingDirection facing) {
         this(entityId, worldId, gifName, scale, speedMultiplier, billboard, false, facing);
      }

      public GifAnimationState(UUID entityId, UUID worldId, String gifName, float scale, float speedMultiplier, boolean billboard, boolean doubleSided, FacingDirection facing) {
         this.currentFrame = 0;
         this.accumulatedTime = 0L;
         this.entityId = entityId;
         this.worldId = worldId;
         this.gifName = gifName;
         this.scale = scale;
         this.speedMultiplier = speedMultiplier;
         this.billboard = billboard;
         this.doubleSided = doubleSided;
         this.facing = facing;
         this.lastFrameTime = System.currentTimeMillis();
         this.frameModels = null;
      }
   }

   public static class GifData {
      public final String name;
      public final int frameCount;
      public final int[] frameDelays;
      public final int width;
      public final int height;
      public final String[] frameAssetIds;
      public final boolean loaded;

      public GifData(String name, int frameCount, int[] frameDelays, int width, int height, String[] frameAssetIds, boolean loaded) {
         this.name = name;
         this.frameCount = frameCount;
         this.frameDelays = frameDelays;
         this.width = width;
         this.height = height;
         this.frameAssetIds = frameAssetIds;
         this.loaded = loaded;
      }

      public int getTotalDuration() {
         int total = 0;
         int[] var2 = this.frameDelays;
         int var3 = var2.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            int delay = var2[var4];
            total += delay;
         }

         return total;
      }
   }
}


