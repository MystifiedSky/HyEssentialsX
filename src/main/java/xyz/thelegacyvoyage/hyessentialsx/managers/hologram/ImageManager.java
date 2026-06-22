package xyz.thelegacyvoyage.hyessentialsx.managers.hologram;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import com.hypixel.hytale.assetstore.AssetStore;
import xyz.thelegacyvoyage.hyessentialsx.util.hologram.ByteArrayCommonAsset;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.universe.Universe;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import xyz.thelegacyvoyage.hyessentialsx.util.PluginInfoUtil;

public class ImageManager {
   @Nonnull
   private final HologramService plugin;
   @Nonnull
   private final Path imagesFolder;
   @Nonnull
   private final Path pluginDataFolder;
   @Nonnull
   private final Path modsFolder;
   @Nonnull
   private final Map<String, ImageManager.ImageData> imageRegistry = new ConcurrentHashMap();
   private static final String ASSET_PREFIX = "HyEssentialsX_Hologram_Image_";
   private static final String ASSETS_ZIP_NAME = "HyEssentialsX_Assets.zip";
   private static final String ASSET_PACK_NAME = "HyEssentialsX_Assets";
   private static final String SCOREBOARD_ASSET_PREFIX = "Common/UI/Custom/Textures/HyEssentialsX/Scoreboard/";

   public ImageManager(@Nonnull HologramService plugin) {
      this.plugin = plugin;
      this.pluginDataFolder = plugin.getDataDirectory();
      this.imagesFolder = this.pluginDataFolder.resolve("images");
      this.modsFolder = this.findModsFolder(this.pluginDataFolder);
   }

   @Nonnull
   private Path findModsFolder(@Nonnull Path pluginDataDir) {
      Path parent = pluginDataDir.getParent();
      if (parent == null) {
         return pluginDataDir;
      }
      Path grandParent = parent.getParent();
      return grandParent != null ? grandParent : parent;
   }

   public void initialize() {
      try {
         this.plugin.getLogger().at(Level.INFO).log("Images folder: " + String.valueOf(this.imagesFolder.toAbsolutePath()));
         this.plugin.getLogger().at(Level.INFO).log("Mods folder: " + String.valueOf(this.modsFolder.toAbsolutePath()));
         this.plugin.getLogger().at(Level.INFO).log("Assets zip location: " + String.valueOf(this.modsFolder.resolve(ASSETS_ZIP_NAME)));
         if (!Files.exists(this.imagesFolder, new LinkOption[0])) {
            Files.createDirectories(this.imagesFolder);
            this.plugin.getLogger().at(Level.INFO).log("Created images folder at: " + String.valueOf(this.imagesFolder));
            this.createReadme();
         }

         List<ImageManager.ImageFileInfo> imageFiles = this.scanImageFiles();
         Iterator var2 = imageFiles.iterator();

         while(var2.hasNext()) {
            ImageManager.ImageFileInfo info = (ImageManager.ImageFileInfo)var2.next();
            boolean isLoaded = this.isModelAssetLoaded(info.modelAssetId);
            this.imageRegistry.put(info.imageName, new ImageManager.ImageData(info.imageName, info.modelAssetId, info.texturePath, info.width, info.height, isLoaded));
         }

         if (this.isModelAssetLoaded("HyEssentialsX_Hologram_Billboard")) {
            this.plugin.getLogger().at(Level.INFO).log("Base billboard ModelAsset is loaded - asset pack is working!");
         } else {
            this.plugin.getLogger().at(Level.WARNING).log("Base billboard ModelAsset NOT loaded - run /holo reload and restart server!");
         }

         Path assetsZip = this.modsFolder.resolve(ASSETS_ZIP_NAME);
         if (!Files.exists(assetsZip, new LinkOption[0]) && !imageFiles.isEmpty()) {
            this.plugin.getLogger().at(Level.INFO).log("Generating initial assets zip...");
            this.generateAssetsZip();
         }

         Api var10000 = this.plugin.getLogger().at(Level.INFO);
         int var10001 = this.imageRegistry.size();
         var10000.log("Image system initialized. " + var10001 + " images registered, " + this.imageRegistry.values().stream().filter((i) -> {
            return i.loaded;
         }).count() + " loaded.");
      } catch (IOException var5) {
         this.plugin.getLogger().at(Level.WARNING).log("Failed to initialize image manager: " + var5.getMessage());
      }

   }

   private List<ImageManager.ImageFileInfo> scanImageFiles() {
      List<ImageManager.ImageFileInfo> result = new ArrayList<>();
      if (!Files.exists(this.imagesFolder, new LinkOption[0])) {
         return result;
      }

      try (Stream<Path> files = Files.list(this.imagesFolder)) {
         files.filter((p) -> !Files.isDirectory(p, new LinkOption[0]))
              .filter((p) -> {
                 String name = p.getFileName().toString().toLowerCase();
                 return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
              })
              .forEach((p) -> {
                 ImageManager.ImageFileInfo info = this.processImageFile(p, ASSET_PREFIX);
                 if (info != null) {
                    result.add(info);
                 }
              });
      } catch (IOException var7) {
         this.plugin.getLogger().at(Level.WARNING).log("Failed to scan images folder: " + var7.getMessage());
      }

      try (Stream<Path> dirs = Files.list(this.imagesFolder)) {
         dirs.filter((p) -> Files.isDirectory(p, new LinkOption[0]))
             .filter((p) -> p.getFileName().toString().startsWith("gif_"))
             .forEach((gifDir) -> {
                try (Stream<Path> frames = Files.list(gifDir)) {
                   frames.filter((p) -> !Files.isDirectory(p, new LinkOption[0]))
                         .filter((p) -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                         .sorted()
                         .forEach((frameFile) -> {
                            ImageManager.ImageFileInfo info = this.processImageFile(frameFile, GifManager.getAssetPrefix());
                            if (info != null) {
                               result.add(info);
                            }
                         });
                } catch (IOException var8) {
                   this.plugin.getLogger().at(Level.WARNING).log("Failed to scan gif folder: " + var8.getMessage());
                }
             });
      } catch (IOException var6) {
         this.plugin.getLogger().at(Level.WARNING).log("Failed to scan gif directories: " + var6.getMessage());
      }

      return result;
   }

   @Nullable
   private ImageManager.ImageFileInfo processImageFile(@Nonnull Path imageFile, @Nonnull String assetPrefix) {
      try {
         String fileName = imageFile.getFileName().toString();
         String baseName = fileName.substring(0, fileName.lastIndexOf(46));
         String imageName = baseName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
         String modelAssetId = assetPrefix + this.capitalize(imageName);
         String textureFileName = modelAssetId + ".png";
         String texturePath = "Characters/HologramService/" + textureFileName;
         BufferedImage image = ImageIO.read(imageFile.toFile());
         if (image == null) {
            this.plugin.getLogger().at(Level.WARNING).log("Could not read image: " + fileName);
            return null;
         } else {
            return new ImageManager.ImageFileInfo(imageFile, imageName, modelAssetId, texturePath, image.getWidth(), image.getHeight());
         }
      } catch (IOException var10) {
         Api var10000 = this.plugin.getLogger().at(Level.WARNING);
         String var10001 = String.valueOf(imageFile.getFileName());
         var10000.log("Failed to process image: " + var10001 + ": " + var10.getMessage());
         return null;
      }
   }

   public void generateAssetsZip() {
      Path assetsZip = this.modsFolder.resolve(ASSETS_ZIP_NAME);
      this.plugin.getLogger().at(Level.INFO).log("=== Generating " + ASSETS_ZIP_NAME + " ===");
      this.plugin.getLogger().at(Level.INFO).log("Output location: " + String.valueOf(assetsZip.toAbsolutePath()));

      try {
         List<ImageManager.ImageFileInfo> imageFiles = this.scanImageFiles();
         ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(assetsZip.toFile()));

         try {
            this.addResourceToZip(zos, "Common/Characters/HyEssentialsX_Hologram_Billboard.blockymodel", "Common/Characters/HyEssentialsX_Hologram_Billboard.blockymodel");
            this.addResourceToZip(zos, "Common/Characters/HyEssentialsX_Hologram_Billboard.png", "Common/Characters/HyEssentialsX_Hologram_Billboard.png");
            this.addResourceToZip(zos, "Server/Models/HyEssentialsX_Hologram_Billboard.json", "Server/Models/HyEssentialsX_Hologram_Billboard.json");
            Iterator var4 = imageFiles.iterator();

            while(true) {
               if (!var4.hasNext()) {
                  this.addScoreboardTexturesToZip(zos);
                  String manifest = this.createManifestJson();
                  this.addStringToZip(zos, manifest, "manifest.json");
                  break;
               }

               ImageManager.ImageFileInfo info = (ImageManager.ImageFileInfo)var4.next();
               String textureZipPath = "Common/Characters/HologramService/" + info.modelAssetId + ".png";
               this.addImageToZip(zos, info.path, textureZipPath);
               this.plugin.getLogger().at(Level.FINE).log("Added texture: " + textureZipPath);
               String blockyModelJson = this.createBlockyModelJsonString(info.width, info.height, false, false);
               String blockyModelZipPath = "Common/Characters/HologramService/" + info.modelAssetId + ".blockymodel";
               this.addStringToZip(zos, blockyModelJson, blockyModelZipPath);
               this.plugin.getLogger().at(Level.FINE).log("Added blockymodel: " + blockyModelZipPath);
               String billboardBlockyModelJson = this.createBlockyModelJsonString(info.width, info.height, true, false);
               String billboardBlockyModelZipPath = "Common/Characters/HologramService/" + info.modelAssetId + "_Billboard.blockymodel";
               this.addStringToZip(zos, billboardBlockyModelJson, billboardBlockyModelZipPath);
               this.plugin.getLogger().at(Level.FINE).log("Added billboard blockymodel: " + billboardBlockyModelZipPath);
               String dsBlockyModelJson = this.createBlockyModelJsonString(info.width, info.height, false, true);
               String dsBlockyModelZipPath = "Common/Characters/HologramService/" + info.modelAssetId + "_DoubleSided.blockymodel";
               this.addStringToZip(zos, dsBlockyModelJson, dsBlockyModelZipPath);
               this.plugin.getLogger().at(Level.FINE).log("Added doublesided blockymodel: " + dsBlockyModelZipPath);
               String billboardDsBlockyModelJson = this.createBlockyModelJsonString(info.width, info.height, true, true);
               String billboardDsBlockyModelZipPath = "Common/Characters/HologramService/" + info.modelAssetId + "_Billboard_DoubleSided.blockymodel";
               this.addStringToZip(zos, billboardDsBlockyModelJson, billboardDsBlockyModelZipPath);
               this.plugin.getLogger().at(Level.FINE).log("Added billboard+doublesided blockymodel: " + billboardDsBlockyModelZipPath);
               String modelAssetJson = this.createModelAssetJsonString(info.modelAssetId, info.texturePath, info.width, info.height, false, false);
               String modelAssetZipPath = "Server/Models/" + info.modelAssetId + ".json";
               this.addStringToZip(zos, modelAssetJson, modelAssetZipPath);
               this.plugin.getLogger().at(Level.FINE).log("Added model asset: " + modelAssetZipPath);
               String billboardModelAssetJson = this.createModelAssetJsonString(info.modelAssetId + "_Billboard", info.texturePath, info.width, info.height, true, false);
               String billboardModelAssetZipPath = "Server/Models/" + info.modelAssetId + "_Billboard.json";
               this.addStringToZip(zos, billboardModelAssetJson, billboardModelAssetZipPath);
               this.plugin.getLogger().at(Level.FINE).log("Added billboard model asset: " + billboardModelAssetZipPath);
               String dsModelAssetJson = this.createModelAssetJsonString(info.modelAssetId + "_DoubleSided", info.texturePath, info.width, info.height, false, true);
               String dsModelAssetZipPath = "Server/Models/" + info.modelAssetId + "_DoubleSided.json";
               this.addStringToZip(zos, dsModelAssetJson, dsModelAssetZipPath);
               this.plugin.getLogger().at(Level.FINE).log("Added doublesided model asset: " + dsModelAssetZipPath);
               String billboardDsModelAssetJson = this.createModelAssetJsonString(info.modelAssetId + "_Billboard_DoubleSided", info.texturePath, info.width, info.height, true, true);
               String billboardDsModelAssetZipPath = "Server/Models/" + info.modelAssetId + "_Billboard_DoubleSided.json";
               this.addStringToZip(zos, billboardDsModelAssetJson, billboardDsModelAssetZipPath);
               this.plugin.getLogger().at(Level.FINE).log("Added billboard+doublesided model asset: " + billboardDsModelAssetZipPath);
            }
         } catch (Throwable var24) {
            try {
               zos.close();
            } catch (Throwable var23) {
               var24.addSuppressed(var23);
            }

            throw var24;
         }

         zos.close();
         this.plugin.getLogger().at(Level.INFO).log("=== Successfully created " + ASSETS_ZIP_NAME + " ===");
         this.plugin.getLogger().at(Level.INFO).log("Contains " + imageFiles.size() + " custom image(s) (with billboard variants)");
      } catch (IOException var25) {
         this.plugin.getLogger().at(Level.SEVERE).log("Failed to generate assets zip: " + var25.getMessage());
         var25.printStackTrace();
      }

   }

   private void addResourceToZip(ZipOutputStream zos, String resourcePath, String zipPath) throws IOException {
      InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourcePath);

      label43: {
         try {
            if (is == null) {
               this.plugin.getLogger().at(Level.WARNING).log("Resource not found in JAR: " + resourcePath);
               break label43;
            }

            zos.putNextEntry(new ZipEntry(zipPath));
            is.transferTo(zos);
            zos.closeEntry();
            this.plugin.getLogger().at(Level.FINE).log("Added from JAR: " + zipPath);
         } catch (Throwable var8) {
            if (is != null) {
               try {
                  is.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }
            }

            throw var8;
         }

         if (is != null) {
            is.close();
         }

         return;
      }

      if (is != null) {
         is.close();
      }

   }

   private void addFileToZip(ZipOutputStream zos, Path file, String zipPath) throws IOException {
      zos.putNextEntry(new ZipEntry(zipPath));
      Files.copy(file, zos);
      zos.closeEntry();
   }

   @Nullable
   private byte[] readImageAsPng(@Nonnull Path file) {
      try {
         BufferedImage image = ImageIO.read(file.toFile());
         if (image == null) {
            return null;
         }
         ByteArrayOutputStream output = new ByteArrayOutputStream();
         ImageIO.write(image, "png", output);
         return output.toByteArray();
      } catch (IOException var4) {
         this.plugin.getLogger().at(Level.WARNING).log("Failed to convert image to PNG: " + file.getFileName() + ": " + var4.getMessage());
         return null;
      }
   }

   private void addImageToZip(ZipOutputStream zos, Path file, String zipPath) throws IOException {
      byte[] pngBytes = this.readImageAsPng(file);
      if (pngBytes != null) {
         zos.putNextEntry(new ZipEntry(zipPath));
         zos.write(pngBytes);
         zos.closeEntry();
      } else {
         this.addFileToZip(zos, file, zipPath);
      }
   }

   private void addStringToZip(ZipOutputStream zos, String content, String zipPath) throws IOException {
      zos.putNextEntry(new ZipEntry(zipPath));
      zos.write(content.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
   }

   private void addScoreboardTexturesToZip(ZipOutputStream zos) {
      Path scoreboardFolder = this.resolveScoreboardFolder();
      if (scoreboardFolder == null || !Files.exists(scoreboardFolder, new LinkOption[0])) {
         return;
      }

      try (Stream<Path> files = Files.list(scoreboardFolder)) {
         files.filter((p) -> !Files.isDirectory(p, new LinkOption[0]))
              .filter((p) -> {
                 String name = p.getFileName().toString().toLowerCase();
                 return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
              })
              .forEach((p) -> {
                 try {
                    String token = this.sanitizeScoreboardToken(p.getFileName().toString());
                    if (token.isEmpty()) {
                       token = "logo";
                    }
                    String zipPath = SCOREBOARD_ASSET_PREFIX + token + ".png";
                    byte[] pngBytes = this.readImageAsPng(p);
                    if (pngBytes != null) {
                       zos.putNextEntry(new ZipEntry(zipPath));
                       zos.write(pngBytes);
                       zos.closeEntry();
                    } else {
                       this.plugin.getLogger().at(Level.WARNING).log("Failed to convert scoreboard image to PNG: " + p.getFileName());
                    }
                 } catch (Exception var6) {
                    this.plugin.getLogger().at(Level.WARNING).log("Failed to add scoreboard texture: " + p.getFileName() + ": " + var6.getMessage());
                 }
              });
      } catch (IOException var4) {
         this.plugin.getLogger().at(Level.WARNING).log("Failed to scan scoreboard textures: " + var4.getMessage());
      }
   }

   @Nullable
   private Path resolveScoreboardFolder() {
      Path parent = this.pluginDataFolder.getParent();
      return parent != null ? parent.resolve("scoreboard") : null;
   }

   @Nonnull
   private String sanitizeScoreboardToken(@Nonnull String filename) {
      String base = filename;
      int dot = filename.lastIndexOf('.');
      if (dot > 0) {
         base = filename.substring(0, dot);
      }
      StringBuilder out = new StringBuilder();
      for(int i = 0; i < base.length(); ++i) {
         char c = base.charAt(i);
         if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
            out.append(Character.toLowerCase(c));
         } else if (c == '_' || c == '-') {
            out.append('_');
         }
      }
      return out.toString();
   }

   private String createModelAssetJsonString(String modelAssetId, String texturePath, int width, int height, boolean billboard) {
      return this.createModelAssetJsonString(modelAssetId, texturePath, width, height, billboard, false);
   }

   private String createModelAssetJsonString(String modelAssetId, String texturePath, int width, int height, boolean billboard, boolean doubleSided) {
      double aspectRatio = (double)width / (double)height;
      double normalizedWidth;
      double normalizedHeight;
      if (width >= height) {
         normalizedWidth = 1.0D;
         normalizedHeight = 1.0D / aspectRatio;
      } else {
         normalizedHeight = 1.0D;
         normalizedWidth = aspectRatio;
      }

      double halfWidth = normalizedWidth / 2.0D;
      String baseAssetId = modelAssetId.replace("_Billboard_DoubleSided", "").replace("_Billboard", "").replace("_DoubleSided", "");
      String blockymodelSuffix = "";
      if (billboard && doubleSided) {
         blockymodelSuffix = "_Billboard_DoubleSided";
      } else if (billboard) {
         blockymodelSuffix = "_Billboard";
      } else if (doubleSided) {
         blockymodelSuffix = "_DoubleSided";
      }

      String imageModelPath = "Characters/HologramService/" + baseAssetId + blockymodelSuffix + ".blockymodel";
      return String.format(Locale.US, "{\n  \"Model\": \"%s\",\n  \"Texture\": \"%s\",\n  \"MinScale\": 0.01,\n  \"MaxScale\": 100.0,\n  \"EyeHeight\": 0.0,\n  \"CrouchOffset\": 0.0,\n  \"HitBox\": {\n    \"Min\": { \"X\": %.4f, \"Y\": 0.0, \"Z\": -0.01 },\n    \"Max\": { \"X\": %.4f, \"Y\": %.4f, \"Z\": 0.01 }\n  }\n}\n", imageModelPath, texturePath, -halfWidth, halfWidth, normalizedHeight);
   }

   private String createBlockyModelJsonString(int width, int height) {
      return this.createBlockyModelJsonString(width, height, false);
   }

   private String createBlockyModelJsonString(int width, int height, boolean billboard) {
      return this.createBlockyModelJsonString(width, height, billboard, false);
   }

   private String createBlockyModelJsonString(int width, int height, boolean billboard, boolean doubleSided) {
      double offsetX = 0.0D;
      double offsetY = 0.0D;
      String lodValue = billboard ? "billboard" : "auto";
      return doubleSided ? String.format(Locale.US, "{\n  \"nodes\": [\n    {\n      \"id\": \"1\",\n      \"name\": \"Front\",\n      \"position\": {\"x\": 0, \"y\": 0, \"z\": 0},\n      \"orientation\": {\"x\": 0, \"y\": 0, \"z\": 0, \"w\": 1},\n      \"shape\": {\n        \"type\": \"quad\",\n        \"offset\": {\"x\": %.1f, \"y\": %.1f, \"z\": 0.001},\n        \"stretch\": {\"x\": 1, \"y\": 1, \"z\": 1},\n        \"settings\": {\n          \"size\": {\"x\": %d, \"y\": %d},\n          \"normal\": \"+Z\"\n        },\n        \"visible\": true,\n        \"doubleSided\": false,\n        \"shadingMode\": \"flat\",\n        \"unwrapMode\": \"custom\",\n        \"textureLayout\": {\n          \"front\": {\n            \"offset\": {\"x\": 0, \"y\": 0},\n            \"mirror\": {\"x\": false, \"y\": false},\n            \"angle\": 0\n          }\n        }\n      }\n    },\n    {\n      \"id\": \"2\",\n      \"name\": \"Back\",\n      \"position\": {\"x\": 0, \"y\": 0, \"z\": 0},\n      \"orientation\": {\"x\": 0, \"y\": 0, \"z\": 0, \"w\": 1},\n      \"shape\": {\n        \"type\": \"quad\",\n        \"offset\": {\"x\": %.1f, \"y\": %.1f, \"z\": -0.001},\n        \"stretch\": {\"x\": 1, \"y\": 1, \"z\": 1},\n        \"settings\": {\n          \"size\": {\"x\": %d, \"y\": %d},\n          \"normal\": \"-Z\"\n        },\n        \"visible\": true,\n        \"doubleSided\": false,\n        \"shadingMode\": \"flat\",\n        \"unwrapMode\": \"custom\",\n        \"textureLayout\": {\n          \"front\": {\n            \"offset\": {\"x\": 0, \"y\": 0},\n            \"mirror\": {\"x\": false, \"y\": false},\n            \"angle\": 0\n          }\n        }\n      }\n    }\n  ],\n  \"format\": \"character\",\n  \"lod\": \"%s\"\n}\n", offsetX, offsetY, width, height, offsetX, offsetY, width, height, lodValue) : String.format(Locale.US, "{\n  \"nodes\": [\n    {\n      \"id\": \"1\",\n      \"name\": \"Billboard\",\n      \"position\": {\"x\": 0, \"y\": 0, \"z\": 0},\n      \"orientation\": {\"x\": 0, \"y\": 0, \"z\": 0, \"w\": 1},\n      \"shape\": {\n        \"type\": \"quad\",\n        \"offset\": {\"x\": %.1f, \"y\": %.1f, \"z\": 0},\n        \"stretch\": {\"x\": 1, \"y\": 1, \"z\": 1},\n        \"settings\": {\n          \"size\": {\"x\": %d, \"y\": %d},\n          \"normal\": \"+Z\"\n        },\n        \"visible\": true,\n        \"doubleSided\": false,\n        \"shadingMode\": \"flat\",\n        \"unwrapMode\": \"custom\",\n        \"textureLayout\": {\n          \"front\": {\n            \"offset\": {\"x\": 0, \"y\": 0},\n            \"mirror\": {\"x\": false, \"y\": false},\n            \"angle\": 0\n          }\n        }\n      }\n    }\n  ],\n  \"format\": \"character\",\n  \"lod\": \"%s\"\n}\n", offsetX, offsetY, width, height, lodValue);
   }

   private String createManifestJson() {
      return "{\n  \"Name\": \"" + ASSET_PACK_NAME + "\",\n  \"Group\": \"xyz.thelegacyvoyage.hyessentialsx.assets\",\n  \"Version\": \"" + PluginInfoUtil.getVersion() + "\",\n  \"Description\": \"HyEssentialsX combined assets\",\n  \"ServerVersion\": \"" + PluginInfoUtil.getServerVersion() + "\",\n  \"IncludesAssetPack\": true\n}\n";
   }

   private boolean isModelAssetLoaded(@Nonnull String modelAssetId) {
      try {
         ModelAsset asset = (ModelAsset)ModelAsset.getAssetMap().getAsset(modelAssetId);
         return asset != null;
      } catch (Exception var3) {
         return false;
      }
   }

   @Nullable
   public Model createImageModel(@Nonnull String imageName, float scale) {
      return this.createImageModel(imageName, scale, false, false);
   }

   @Nullable
   public Model createImageModel(@Nonnull String imageName, float scale, boolean billboard) {
      return this.createImageModel(imageName, scale, billboard, false);
   }

   @Nullable
   public Model createImageModel(@Nonnull String imageName, float scale, boolean billboard, boolean doubleSided) {
      String normalizedName = imageName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
      ImageManager.ImageData imageData = (ImageManager.ImageData)this.imageRegistry.get(normalizedName);
      if (imageData == null) {
         this.plugin.getLogger().at(Level.WARNING).log("Image not found: " + imageName + ". Make sure to place the PNG in the images folder and run /holo reload.");
         return null;
      } else {
         String modelAssetId = imageData.modelAssetId;
         if (billboard && doubleSided) {
            modelAssetId = imageData.modelAssetId + "_Billboard_DoubleSided";
         } else if (billboard) {
            modelAssetId = imageData.modelAssetId + "_Billboard";
         } else if (doubleSided) {
            modelAssetId = imageData.modelAssetId + "_DoubleSided";
         }

         if (!this.isModelAssetLoaded(modelAssetId)) {
            String fallbackId = null;
            if (billboard && doubleSided && this.isModelAssetLoaded(imageData.modelAssetId + "_Billboard")) {
               fallbackId = imageData.modelAssetId + "_Billboard";
               this.plugin.getLogger().at(Level.WARNING).log("Billboard+DoubleSided variant for '" + imageName + "' not loaded. Using billboard variant.");
            } else if (billboard && this.isModelAssetLoaded(imageData.modelAssetId)) {
               fallbackId = imageData.modelAssetId;
               this.plugin.getLogger().at(Level.WARNING).log("Billboard variant for '" + imageName + "' not loaded. Using regular model.");
            } else if (doubleSided && this.isModelAssetLoaded(imageData.modelAssetId)) {
               fallbackId = imageData.modelAssetId;
               this.plugin.getLogger().at(Level.WARNING).log("DoubleSided variant for '" + imageName + "' not loaded. Using regular model.");
            } else if (this.isModelAssetLoaded(imageData.modelAssetId)) {
               fallbackId = imageData.modelAssetId;
            }

            if (fallbackId == null) {
               this.plugin.getLogger().at(Level.WARNING).log("Image '" + imageName + "' is registered but not yet loaded. Run /holo reload and restart the server.");
               return null;
            }

            modelAssetId = fallbackId;
         }

         try {
            ModelAsset modelAsset = (ModelAsset)ModelAsset.getAssetMap().getAsset(modelAssetId);
            if (modelAsset == null) {
               this.plugin.getLogger().at(Level.WARNING).log("ModelAsset not found: " + modelAssetId);
               return null;
            } else {
               return Model.createScaledModel(modelAsset, scale);
            }
         } catch (Exception var9) {
            this.plugin.getLogger().at(Level.WARNING).log("Failed to create image model for " + imageName + ": " + var9.getMessage());
            return null;
         }
      }
   }

   @Nullable
   public ImageManager.ImageData getImageData(@Nonnull String imageName) {
      return (ImageManager.ImageData)this.imageRegistry.get(imageName.toLowerCase().replaceAll("[^a-z0-9_]", "_"));
   }

   public boolean hasImage(@Nonnull String imageName) {
      return this.imageRegistry.containsKey(imageName.toLowerCase().replaceAll("[^a-z0-9_]", "_"));
   }

   public boolean isImageLoaded(@Nonnull String imageName) {
      String normalizedName = imageName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
      ImageManager.ImageData imageData = (ImageManager.ImageData)this.imageRegistry.get(normalizedName);
      return imageData == null ? false : this.isModelAssetLoaded(imageData.modelAssetId);
   }

   @Nonnull
   public Set<String> getAvailableImages() {
      Set<String> images = new HashSet();
      Iterator var2 = this.imageRegistry.entrySet().iterator();

      while(var2.hasNext()) {
         Entry<String, ImageManager.ImageData> entry = (Entry)var2.next();
         if (((ImageManager.ImageData)entry.getValue()).modelAssetId.startsWith("HyEssentialsX_Hologram_Image_")) {
            images.add((String)entry.getKey());
         }
      }

      return Collections.unmodifiableSet(images);
   }

   private void createReadme() {
      Path readmePath = this.imagesFolder.resolve("README.txt");
      String readme = "HologramService - Image Display\n=============================\n\nHOW TO USE:\n-----------\n1. Place your PNG image in this folder (e.g., logo.png)\n2. Run: /holo reload (this generates HyEssentialsX_Assets.zip in the mods folder)\n3. RESTART THE SERVER (required for Hytale to load new images)\n4. Use in holograms: /holo addline <name> image:logo\n   Or with scale: /holo addline <name> image:logo:2.0\n\nIMPORTANT:\n- Running /holo reload generates HyEssentialsX_Assets.zip in the mods folder\n- A server restart is REQUIRED after that for images to load!\n- The zip contains all textures and model definitions needed by Hytale\n\nSUPPORTED FORMATS:\n- PNG (recommended - supports transparency)\n- JPG/JPEG\n\nIMAGE NAMING:\n- Use lowercase letters, numbers, and underscores\n- Example: my_logo.png -> use as image:my_logo\n\nFILE STRUCTURE GENERATED:\n- mods/HyEssentialsX_Assets.zip\n  - Common/Characters/HologramService/*.png (your textures)\n  - Common/Characters/HyEssentialsX_Hologram_Billboard.blockymodel (base model)\n  - Server/Models/*.json (model asset definitions)\n\nTROUBLESHOOTING:\n- If image doesn't appear, make sure you:\n  1. Ran /holo reload\n  2. Restarted the server\n- Use /holo listimages to see available images\n- Check logs for \"Successfully created HyEssentialsX_Assets.zip\"\n";

      try {
         Files.writeString(readmePath, readme, new OpenOption[0]);
      } catch (IOException var4) {
         this.plugin.getLogger().at(Level.FINE).log("Could not create README: " + var4.getMessage());
      }

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

   public void reload() {
      this.reload(false);
   }

   public void reload(boolean liveReload) {
      this.plugin.getLogger().at(Level.INFO).log("Reloading image registry...");
      this.imageRegistry.clear();
      List<ImageManager.ImageFileInfo> imageFiles = this.scanImageFiles();
      Iterator var3 = imageFiles.iterator();

      while(var3.hasNext()) {
         ImageManager.ImageFileInfo info = (ImageManager.ImageFileInfo)var3.next();
         boolean isLoaded = this.isModelAssetLoaded(info.modelAssetId);
         this.imageRegistry.put(info.imageName, new ImageManager.ImageData(info.imageName, info.modelAssetId, info.texturePath, info.width, info.height, isLoaded));
      }

      this.generateAssetsZip();
      if (liveReload) {
         int loaded = this.loadAssetsLive(imageFiles);
         this.plugin.getLogger().at(Level.INFO).log("Live loaded " + loaded + " image assets!");
         Iterator var8 = imageFiles.iterator();

         while(var8.hasNext()) {
            ImageManager.ImageFileInfo info = (ImageManager.ImageFileInfo)var8.next();
            boolean isLoaded = this.isModelAssetLoaded(info.modelAssetId);
            if (isLoaded) {
               this.imageRegistry.put(info.imageName, new ImageManager.ImageData(info.imageName, info.modelAssetId, info.texturePath, info.width, info.height, true));
            }
         }
      }

      Api var10000 = this.plugin.getLogger().at(Level.INFO);
      int var10001 = this.imageRegistry.size();
      var10000.log("Image registry reloaded! " + var10001 + " images registered, " + this.imageRegistry.values().stream().filter((i) -> {
         return i.loaded;
      }).count() + " currently loaded.");
   }

   private int loadAssetsLive(@Nonnull List<ImageManager.ImageFileInfo> imageFiles) {
      int loadedCount = 0;
      String packName = ASSET_PACK_NAME;

      try {
         CommonAssetModule commonAssetModule = CommonAssetModule.get();
         if (commonAssetModule == null) {
            this.plugin.getLogger().at(Level.WARNING).log("CommonAssetModule not available for live reload");
            return 0;
         }

         AssetStore<String, ModelAsset, DefaultAssetMap<String, ModelAsset>> modelAssetStore = ModelAsset.getAssetStore();
         List<ModelAsset> modelAssetsToLoad = new ArrayList();
         List<CommonAsset> assetsToSend = new ArrayList();
         this.plugin.getLogger().at(Level.INFO).log("=== Starting Live Asset Load ===");
         Iterator var8 = imageFiles.iterator();

         byte[] baseTextureBytes;
         Api var10000;
         while(var8.hasNext()) {
            ImageManager.ImageFileInfo info = (ImageManager.ImageFileInfo)var8.next();

            try {
               baseTextureBytes = this.readImageAsPng(info.path);
               if (baseTextureBytes == null) {
                  baseTextureBytes = Files.readAllBytes(info.path);
               }
               String textureName = "Characters/HologramService/" + info.modelAssetId + ".png";
               ByteArrayCommonAsset textureAsset = new ByteArrayCommonAsset(textureName, baseTextureBytes);
               commonAssetModule.addCommonAsset(packName, textureAsset, false);
               assetsToSend.add(textureAsset);
               this.plugin.getLogger().at(Level.FINE).log("Live loaded texture: " + textureName);
               String blockyModelJson = this.createBlockyModelJsonString(info.width, info.height, false);
               byte[] blockyModelBytes = blockyModelJson.getBytes(StandardCharsets.UTF_8);
               String blockyModelName = "Characters/HologramService/" + info.modelAssetId + ".blockymodel";
               ByteArrayCommonAsset blockyModelAsset = new ByteArrayCommonAsset(blockyModelName, blockyModelBytes);
               commonAssetModule.addCommonAsset(packName, blockyModelAsset, false);
               assetsToSend.add(blockyModelAsset);
               this.plugin.getLogger().at(Level.FINE).log("Live loaded blockymodel: " + blockyModelName);
               String billboardBlockyModelJson = this.createBlockyModelJsonString(info.width, info.height, true);
               byte[] billboardBlockyModelBytes = billboardBlockyModelJson.getBytes(StandardCharsets.UTF_8);
               String billboardBlockyModelName = "Characters/HologramService/" + info.modelAssetId + "_Billboard.blockymodel";
               ByteArrayCommonAsset billboardBlockyModelAsset = new ByteArrayCommonAsset(billboardBlockyModelName, billboardBlockyModelBytes);
               commonAssetModule.addCommonAsset(packName, billboardBlockyModelAsset, false);
               assetsToSend.add(billboardBlockyModelAsset);
               this.plugin.getLogger().at(Level.FINE).log("Live loaded billboard blockymodel: " + billboardBlockyModelName);
               ModelAsset regularModelAsset = this.createModelAsset(info.modelAssetId, info.texturePath, info.width, info.height, false);
               if (regularModelAsset != null) {
                  modelAssetsToLoad.add(regularModelAsset);
                  this.plugin.getLogger().at(Level.FINE).log("Prepared ModelAsset: " + info.modelAssetId);
               }

               ModelAsset billboardModelAsset = this.createModelAsset(info.modelAssetId + "_Billboard", info.texturePath, info.width, info.height, true);
               if (billboardModelAsset != null) {
                  modelAssetsToLoad.add(billboardModelAsset);
                  this.plugin.getLogger().at(Level.FINE).log("Prepared ModelAsset: " + info.modelAssetId + "_Billboard");
               }

               ++loadedCount;
            } catch (Exception var26) {
               var10000 = this.plugin.getLogger().at(Level.WARNING);
               String var10001 = info.imageName;
               var10000.log("Failed to live load image " + var10001 + ": " + var26.getMessage());
            }
         }

         try {
            InputStream baseModelStream = this.getClass().getClassLoader().getResourceAsStream("Common/Characters/HyEssentialsX_Hologram_Billboard.blockymodel");
            if (baseModelStream != null) {
               byte[] baseModelBytes = baseModelStream.readAllBytes();
               baseModelStream.close();
               ByteArrayCommonAsset baseModelAsset = new ByteArrayCommonAsset("Characters/HyEssentialsX_Hologram_Billboard.blockymodel", baseModelBytes);
               commonAssetModule.addCommonAsset(packName, baseModelAsset, false);
               assetsToSend.add(baseModelAsset);
               this.plugin.getLogger().at(Level.FINE).log("Live loaded base billboard model");
            }

            InputStream baseTextureStream = this.getClass().getClassLoader().getResourceAsStream("Common/Characters/HyEssentialsX_Hologram_Billboard.png");
            if (baseTextureStream != null) {
               baseTextureBytes = baseTextureStream.readAllBytes();
               baseTextureStream.close();
               ByteArrayCommonAsset baseTextureAsset = new ByteArrayCommonAsset("Characters/HyEssentialsX_Hologram_Billboard.png", baseTextureBytes);
               commonAssetModule.addCommonAsset(packName, baseTextureAsset, false);
               assetsToSend.add(baseTextureAsset);
               this.plugin.getLogger().at(Level.FINE).log("Live loaded base billboard texture");
            }
         } catch (Exception var25) {
            this.plugin.getLogger().at(Level.WARNING).log("Failed to live load base assets: " + var25.getMessage());
         }

         if (!modelAssetsToLoad.isEmpty()) {
            try {
               modelAssetStore.loadAssets(packName, modelAssetsToLoad);
               this.plugin.getLogger().at(Level.INFO).log("Loaded " + modelAssetsToLoad.size() + " ModelAssets into asset store");
            } catch (Exception var24) {
               this.plugin.getLogger().at(Level.WARNING).log("Failed to load ModelAssets: " + var24.getMessage());
            }
         }

         if (!assetsToSend.isEmpty() && Universe.get().getPlayerCount() > 0) {
            try {
               var10000 = this.plugin.getLogger().at(Level.INFO);
               int var33 = assetsToSend.size();
               var10000.log("Sending " + var33 + " assets to " + Universe.get().getPlayerCount() + " players...");
               commonAssetModule.sendAssets(assetsToSend, false);
               this.plugin.getLogger().at(Level.INFO).log("Sent all HologramService assets (without global rebuild)");
            } catch (Exception var23) {
               this.plugin.getLogger().at(Level.WARNING).log("Failed to send assets: " + var23.getMessage());
            }
         }

         this.plugin.getLogger().at(Level.INFO).log("=== Live Asset Load Complete ===");
      } catch (Exception var27) {
         this.plugin.getLogger().at(Level.SEVERE).log("Live asset loading failed: " + var27.getMessage());
         var27.printStackTrace();
      }

      return loadedCount;
   }

   @Nullable
   private ModelAsset createModelAsset(@Nonnull String modelAssetId, @Nonnull String texturePath, int width, int height, boolean billboard) {
      try {
         double aspectRatio = (double)width / (double)height;
         double normalizedWidth;
         double normalizedHeight;
         if (width >= height) {
            normalizedWidth = 1.0D;
            normalizedHeight = 1.0D / aspectRatio;
         } else {
            normalizedHeight = 1.0D;
            normalizedWidth = aspectRatio;
         }

         double halfWidth = normalizedWidth / 2.0D;
         String blockymodelSuffix = billboard ? "_Billboard" : "";
         String baseAssetId = modelAssetId.endsWith("_Billboard") ? modelAssetId.substring(0, modelAssetId.length() - "_Billboard".length()) : modelAssetId;
         String imageModelPath = "Characters/HologramService/" + baseAssetId + blockymodelSuffix + ".blockymodel";
         ModelAsset modelAsset = new ModelAsset();
         Field idField = ModelAsset.class.getDeclaredField("id");
         idField.setAccessible(true);
         idField.set(modelAsset, modelAssetId);
         Field modelField = ModelAsset.class.getDeclaredField("model");
         modelField.setAccessible(true);
         modelField.set(modelAsset, imageModelPath);
         Field textureField = ModelAsset.class.getDeclaredField("texture");
         textureField.setAccessible(true);
         textureField.set(modelAsset, texturePath);
         Field minScaleField = ModelAsset.class.getDeclaredField("minScale");
         minScaleField.setAccessible(true);
         minScaleField.set(modelAsset, 0.01F);
         Field maxScaleField = ModelAsset.class.getDeclaredField("maxScale");
         maxScaleField.setAccessible(true);
         maxScaleField.set(modelAsset, 100.0F);
         Field boundingBoxField = ModelAsset.class.getDeclaredField("boundingBox");
         boundingBoxField.setAccessible(true);
         Box hitBox = new Box(-halfWidth, 0.0D, -0.01D, halfWidth, normalizedHeight, 0.01D);
         boundingBoxField.set(modelAsset, hitBox);
         return modelAsset;
      } catch (Exception var25) {
         this.plugin.getLogger().at(Level.WARNING).log("Failed to create ModelAsset " + modelAssetId + ": " + var25.getMessage());
         var25.printStackTrace();
         return null;
      }
   }

   public void liveReload() {
      this.plugin.getLogger().at(Level.INFO).log("Performing live reload of image assets...");
      this.reload(true);
   }

   @Nonnull
   public Path getImagesFolder() {
      return this.imagesFolder;
   }

   @Nonnull
   public Path getAssetsZipPath() {
      return this.modsFolder.resolve(ASSETS_ZIP_NAME);
   }

   private static class ImageFileInfo {
      final Path path;
      final String imageName;
      final String modelAssetId;
      final String texturePath;
      final int width;
      final int height;

      ImageFileInfo(Path path, String imageName, String modelAssetId, String texturePath, int width, int height) {
         this.path = path;
         this.imageName = imageName;
         this.modelAssetId = modelAssetId;
         this.texturePath = texturePath;
         this.width = width;
         this.height = height;
      }
   }

   public static class ImageData {
      public final String name;
      public final String modelAssetId;
      public final String texturePath;
      public final int width;
      public final int height;
      public final boolean loaded;

      public ImageData(String name, String modelAssetId, String texturePath, int width, int height, boolean loaded) {
         this.name = name;
         this.modelAssetId = modelAssetId;
         this.texturePath = texturePath;
         this.width = width;
         this.height = height;
         this.loaded = loaded;
      }
   }
}


