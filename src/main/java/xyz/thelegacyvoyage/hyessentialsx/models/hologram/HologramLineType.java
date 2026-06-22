package xyz.thelegacyvoyage.hyessentialsx.models.hologram;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum HologramLineType {
   TEXT,
   ITEM,
   IMAGE,
   GIF;

   private static final Pattern ANIMATION_TAG_PATTERN = Pattern.compile(":anim_([a-zA-Z0-9_]+)");
   private static final Pattern DOUBLE_SIDED_PATTERN = Pattern.compile(":ds(?:$|:)|:doublesided(?:$|:)", 2);

   @Nonnull
   public static HologramLineType fromLine(@Nonnull String line) {
      String trimmed = line.trim().toLowerCase();
      if (trimmed.startsWith("item:")) {
         return ITEM;
      } else if (trimmed.startsWith("image:")) {
         return IMAGE;
      } else {
         return trimmed.startsWith("gif:") ? GIF : TEXT;
      }
   }

   public static boolean isItem(@Nonnull String line) {
      return line.trim().toLowerCase().startsWith("item:");
   }

   public static boolean isImage(@Nonnull String line) {
      return line.trim().toLowerCase().startsWith("image:");
   }

   public static boolean isGif(@Nonnull String line) {
      return line.trim().toLowerCase().startsWith("gif:");
   }

   public static boolean isText(@Nonnull String line) {
      return !isItem(line) && !isImage(line) && !isGif(line);
   }

   @Nullable
   public static String extractAnimationName(@Nonnull String line) {
      Matcher matcher = ANIMATION_TAG_PATTERN.matcher(line.toLowerCase());
      return matcher.find() ? matcher.group(1) : null;
   }

   public static boolean hasAnimationTag(@Nonnull String line) {
      return ANIMATION_TAG_PATTERN.matcher(line.toLowerCase()).find();
   }

   @Nonnull
   public static String stripAnimationTags(@Nonnull String line) {
      return ANIMATION_TAG_PATTERN.matcher(line).replaceAll("").trim();
   }

   public static boolean hasDoubleSidedFlag(@Nonnull String line) {
      return DOUBLE_SIDED_PATTERN.matcher(line.toLowerCase()).find();
   }

   @Nonnull
   public static String stripDoubleSidedFlag(@Nonnull String line) {
      String result = line.replaceAll("(?i):ds:", ":").replaceAll("(?i):doublesided:", ":");
      result = result.replaceAll("(?i):ds$", "").replaceAll("(?i):doublesided$", "");
      return result.trim();
   }

   @Nonnull
   public static HologramLineType.ItemLineData parseItemLine(@Nonnull String line) {
      if (!isItem(line)) {
         return new HologramLineType.ItemLineData("unknown", 1.0F, 0.0F, 0.0F, 0.0F);
      } else {
         String animationName = extractAnimationName(line);
         String cleanLine = stripAnimationTags(line);
         String data = cleanLine.substring(5).trim();
         String[] parts = data.split(":");
         String itemId = parts[0];
         float scale = 1.0F;
         float pitch = 0.0F;
         float yaw = 0.0F;
         float roll = 0.0F;
         if (parts.length >= 2) {
            try {
               scale = Float.parseFloat(parts[1]);
            } catch (NumberFormatException var12) {
            }
         }

         if (parts.length >= 5) {
            try {
               pitch = Float.parseFloat(parts[2]);
               yaw = Float.parseFloat(parts[3]);
               roll = Float.parseFloat(parts[4]);
            } catch (NumberFormatException var11) {
            }
         }

         return new HologramLineType.ItemLineData(itemId, scale, pitch, yaw, roll, animationName);
      }
   }

   @Nonnull
   public static HologramLineType.ImageLineData parseImageLine(@Nonnull String line) {
      if (!isImage(line)) {
         return new HologramLineType.ImageLineData("unknown", 1.0F, false, -1.0F, FacingDirection.getDefault());
      } else {
         String animationName = extractAnimationName(line);
         boolean doubleSided = hasDoubleSidedFlag(line);
         String cleanLine = stripAnimationTags(line);
         cleanLine = stripDoubleSidedFlag(cleanLine);
         String data = cleanLine.substring(6).trim();
         String[] parts = data.split(":");
         String imageName = parts[0];
         float scale = 1.0F;
         boolean billboard = false;
         float distance = -1.0F;
         FacingDirection facing = FacingDirection.getDefault();
         String secondPart;
         if (parts.length == 2) {
            secondPart = parts[1].trim().toLowerCase();
            if (isBillboardFlag(secondPart)) {
               billboard = parseBillboardFlag(secondPart);
            } else if (FacingDirection.isDirection(secondPart)) {
               facing = FacingDirection.fromString(secondPart);
            } else {
               try {
                  scale = Float.parseFloat(parts[1]);
               } catch (NumberFormatException var21) {
               }
            }
         } else {
            String thirdPart;
            if (parts.length == 3) {
               secondPart = parts[1].trim().toLowerCase();
               thirdPart = parts[2].trim().toLowerCase();
               if (isBillboardFlag(secondPart)) {
                  billboard = parseBillboardFlag(secondPart);

                  try {
                     distance = Float.parseFloat(parts[2]);
                  } catch (NumberFormatException var20) {
                  }
               } else {
                  try {
                     scale = Float.parseFloat(parts[1]);
                  } catch (NumberFormatException var19) {
                  }

                  if (isBillboardFlag(thirdPart)) {
                     billboard = parseBillboardFlag(thirdPart);
                  } else if (FacingDirection.isDirection(thirdPart)) {
                     facing = FacingDirection.fromString(thirdPart);
                  }
               }
            } else if (parts.length == 4) {
               secondPart = parts[1].trim().toLowerCase();
               if (isBillboardFlag(secondPart)) {
                  billboard = parseBillboardFlag(secondPart);

                  try {
                     distance = Float.parseFloat(parts[2]);
                  } catch (NumberFormatException var18) {
                  }

                  facing = FacingDirection.fromString(parts[3]);
                  if (facing == null) {
                     facing = FacingDirection.getDefault();
                  }
               } else {
                  try {
                     scale = Float.parseFloat(parts[1]);
                  } catch (NumberFormatException var17) {
                  }

                  billboard = parseBillboardFlag(parts[2].trim().toLowerCase());
                  thirdPart = parts[3].trim().toLowerCase();
                  if (FacingDirection.isDirection(thirdPart)) {
                     facing = FacingDirection.fromString(thirdPart);
                  } else {
                     try {
                        distance = Float.parseFloat(parts[3]);
                     } catch (NumberFormatException var16) {
                     }
                  }
               }
            } else if (parts.length >= 5) {
               try {
                  scale = Float.parseFloat(parts[1]);
               } catch (NumberFormatException var15) {
               }

               billboard = parseBillboardFlag(parts[2].trim().toLowerCase());

               try {
                  distance = Float.parseFloat(parts[3]);
               } catch (NumberFormatException var14) {
               }

               facing = FacingDirection.fromString(parts[4]);
               if (facing == null) {
                  facing = FacingDirection.getDefault();
               }
            }
         }

         return new HologramLineType.ImageLineData(imageName, scale, billboard, distance, facing, doubleSided, animationName);
      }
   }

   private static boolean isBillboardFlag(@Nonnull String str) {
      return str.equals("true") || str.equals("false") || str.equals("yes") || str.equals("no") || str.equals("1") || str.equals("0") || str.equals("billboard");
   }

   private static boolean parseBillboardFlag(@Nonnull String str) {
      return str.equals("true") || str.equals("yes") || str.equals("1") || str.equals("billboard");
   }

   @Nonnull
   public static HologramLineType.GifLineData parseGifLine(@Nonnull String line) {
      if (!isGif(line)) {
         return new HologramLineType.GifLineData("unknown", 1.0F, 1.0F, false, -1.0F, FacingDirection.getDefault());
      } else {
         String animationName = extractAnimationName(line);
         boolean doubleSided = hasDoubleSidedFlag(line);
         String cleanLine = stripAnimationTags(line);
         cleanLine = stripDoubleSidedFlag(cleanLine);
         String data = cleanLine.substring(4).trim();
         String[] parts = data.split(":");
         String gifName = parts[0];
         float scale = 1.0F;
         float speedMultiplier = 1.0F;
         boolean billboard = false;
         float trackingDistance = -1.0F;
         FacingDirection facing = FacingDirection.getDefault();

         for(int i = 1; i < parts.length; ++i) {
            String part = parts[i].trim().toLowerCase();
            if (part.startsWith("scale_")) {
               try {
                  scale = Float.parseFloat(part.substring(6));
               } catch (NumberFormatException var15) {
               }
            } else if (part.startsWith("speed_")) {
               try {
                  speedMultiplier = Float.parseFloat(part.substring(6));
               } catch (NumberFormatException var18) {
               }
            } else if (part.startsWith("dir_")) {
               String dir = part.substring(4);
               if (FacingDirection.isDirection(dir)) {
                  facing = FacingDirection.fromString(dir);
               }
            } else if (part.startsWith("blocks_")) {
               try {
                  trackingDistance = Float.parseFloat(part.substring(7));
               } catch (NumberFormatException var17) {
               }
            } else if (isBillboardFlag(part)) {
               billboard = parseBillboardFlag(part);
            } else if (FacingDirection.isDirection(part)) {
               facing = FacingDirection.fromString(part);
            } else {
               try {
                  float value = Float.parseFloat(part);
                  if (scale == 1.0F) {
                     scale = value;
                  } else if (speedMultiplier == 1.0F) {
                     speedMultiplier = value;
                  } else if (billboard && trackingDistance < 0.0F) {
                     trackingDistance = value;
                  }
               } catch (NumberFormatException var16) {
               }
            }
         }

         return new HologramLineType.GifLineData(gifName, scale, speedMultiplier, billboard, trackingDistance, facing, doubleSided, animationName);
      }
   }

   // $FF: synthetic method
   private static HologramLineType[] $values() {
      return new HologramLineType[]{TEXT, ITEM, IMAGE, GIF};
   }

   public static class ItemLineData {
      public final String itemId;
      public final float scale;
      public final float pitch;
      public final float yaw;
      public final float roll;
      @Nullable
      public final String animationName;

      public ItemLineData(String itemId, float scale, float pitch, float yaw, float roll) {
         this(itemId, scale, pitch, yaw, roll, (String)null);
      }

      public ItemLineData(String itemId, float scale, float pitch, float yaw, float roll, @Nullable String animationName) {
         this.itemId = itemId;
         this.scale = scale;
         this.pitch = pitch;
         this.yaw = yaw;
         this.roll = roll;
         this.animationName = animationName;
      }

      public boolean hasAnimation() {
         return this.animationName != null && !this.animationName.isEmpty();
      }
   }

   public static class ImageLineData {
      public final String imageName;
      public final float scale;
      public final boolean billboard;
      public final float trackingDistance;
      public final FacingDirection facing;
      public final boolean doubleSided;
      @Nullable
      public final String animationName;

      public ImageLineData(String imageName, float scale, boolean billboard, float trackingDistance, FacingDirection facing) {
         this(imageName, scale, billboard, trackingDistance, facing, false, (String)null);
      }

      public ImageLineData(String imageName, float scale, boolean billboard, float trackingDistance, FacingDirection facing, @Nullable String animationName) {
         this(imageName, scale, billboard, trackingDistance, facing, false, animationName);
      }

      public ImageLineData(String imageName, float scale, boolean billboard, float trackingDistance, FacingDirection facing, boolean doubleSided, @Nullable String animationName) {
         this.imageName = imageName;
         this.scale = scale;
         this.billboard = billboard;
         this.trackingDistance = trackingDistance;
         this.facing = facing != null ? facing : FacingDirection.getDefault();
         this.doubleSided = doubleSided;
         this.animationName = animationName;
      }

      public boolean hasCustomDistance() {
         return this.trackingDistance > 0.0F;
      }

      public boolean hasAnimation() {
         return this.animationName != null && !this.animationName.isEmpty();
      }
   }

   public static class GifLineData {
      public final String gifName;
      public final float scale;
      public final float speedMultiplier;
      public final boolean billboard;
      public final float trackingDistance;
      public final FacingDirection facing;
      public final boolean doubleSided;
      @Nullable
      public final String animationName;

      public GifLineData(String gifName, float scale, float speedMultiplier, boolean billboard, float trackingDistance, FacingDirection facing) {
         this(gifName, scale, speedMultiplier, billboard, trackingDistance, facing, false, (String)null);
      }

      public GifLineData(String gifName, float scale, float speedMultiplier, boolean billboard, float trackingDistance, FacingDirection facing, @Nullable String animationName) {
         this(gifName, scale, speedMultiplier, billboard, trackingDistance, facing, false, animationName);
      }

      public GifLineData(String gifName, float scale, float speedMultiplier, boolean billboard, float trackingDistance, FacingDirection facing, boolean doubleSided, @Nullable String animationName) {
         this.gifName = gifName;
         this.scale = scale;
         this.speedMultiplier = speedMultiplier;
         this.billboard = billboard;
         this.trackingDistance = trackingDistance;
         this.facing = facing != null ? facing : FacingDirection.getDefault();
         this.doubleSided = doubleSided;
         this.animationName = animationName;
      }

      public boolean hasAnimation() {
         return this.animationName != null && !this.animationName.isEmpty();
      }
   }
}


