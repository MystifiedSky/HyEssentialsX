package xyz.thelegacyvoyage.hyessentialsx.models.hologram;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum FacingDirection {
   NORTH("n", "north", 0.0F),
   NORTH_EAST("ne", "northeast", 0.7853982F),
   EAST("e", "east", 1.5707964F),
   SOUTH_EAST("se", "southeast", 2.3561945F),
   SOUTH("s", "south", 3.1415927F),
   SOUTH_WEST("sw", "southwest", 3.9269907F),
   WEST("w", "west", 4.712389F),
   NORTH_WEST("nw", "northwest", 5.497787F);

   private final String shortName;
   private final String fullName;
   private final float yaw;

   private FacingDirection(String shortName, String fullName, float yaw) {
      this.shortName = shortName;
      this.fullName = fullName;
      this.yaw = yaw;
   }

   public String getShortName() {
      return this.shortName;
   }

   public String getFullName() {
      return this.fullName;
   }

   public float getYaw() {
      return this.yaw;
   }

   @Nullable
   public static FacingDirection fromString(@Nonnull String str) {
      String lower = str.trim().toLowerCase();
      FacingDirection[] var2 = values();
      int var3 = var2.length;

      for(int var4 = 0; var4 < var3; ++var4) {
         FacingDirection dir = var2[var4];
         if (dir.shortName.equals(lower) || dir.fullName.equals(lower)) {
            return dir;
         }
      }

      return null;
   }

   public static boolean isDirection(@Nonnull String str) {
      return fromString(str) != null;
   }

   public static FacingDirection getDefault() {
      return NORTH;
   }

   public static String getAllShortNames() {
      StringBuilder sb = new StringBuilder();
      FacingDirection[] var1 = values();
      int var2 = var1.length;

      for(int var3 = 0; var3 < var2; ++var3) {
         FacingDirection dir = var1[var3];
         if (sb.length() > 0) {
            sb.append(", ");
         }

         sb.append(dir.shortName);
      }

      return sb.toString();
   }

   // $FF: synthetic method
   private static FacingDirection[] $values() {
      return new FacingDirection[]{NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST};
   }
}


