package xyz.thelegacyvoyage.hyessentialsx.models.hologram;

import javax.annotation.Nonnull;

public final class Vec3d {
   public static final Vec3d ZERO = new Vec3d(0.0D, 0.0D, 0.0D);
   private final double x;
   private final double y;
   private final double z;

   public Vec3d(double x, double y, double z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public double x() {
      return this.x;
   }

   public double y() {
      return this.y;
   }

   public double z() {
      return this.z;
   }

   public double getX() {
      return this.x;
   }

   public double getY() {
      return this.y;
   }

   public double getZ() {
      return this.z;
   }

   @Nonnull
   public Vec3d add(double x, double y, double z) {
      return new Vec3d(this.x + x, this.y + y, this.z + z);
   }

   @Nonnull
   public Vec3d add(@Nonnull Vec3d other) {
      return new Vec3d(this.x + other.x, this.y + other.y, this.z + other.z);
   }

   @Nonnull
   public Vec3d subtract(double x, double y, double z) {
      return new Vec3d(this.x - x, this.y - y, this.z - z);
   }

   @Nonnull
   public Vec3d subtract(@Nonnull Vec3d other) {
      return new Vec3d(this.x - other.x, this.y - other.y, this.z - other.z);
   }

   @Nonnull
   public Vec3d multiply(double scalar) {
      return new Vec3d(this.x * scalar, this.y * scalar, this.z * scalar);
   }

   public double distanceTo(@Nonnull Vec3d other) {
      double dx = this.x - other.x;
      double dy = this.y - other.y;
      double dz = this.z - other.z;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   public double distanceSquaredTo(@Nonnull Vec3d other) {
      double dx = this.x - other.x;
      double dy = this.y - other.y;
      double dz = this.z - other.z;
      return dx * dx + dy * dy + dz * dz;
   }

   public double length() {
      return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
   }

   @Nonnull
   public Vec3d normalize() {
      double len = this.length();
      return len < 1.0E-4D ? ZERO : new Vec3d(this.x / len, this.y / len, this.z / len);
   }

   public String toString() {
      return String.format("Vec3d(%.2f, %.2f, %.2f)", this.x, this.y, this.z);
   }

   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Vec3d vec3d = (Vec3d)o;
         return Double.compare(vec3d.x, this.x) == 0 && Double.compare(vec3d.y, this.y) == 0 && Double.compare(vec3d.z, this.z) == 0;
      } else {
         return false;
      }
   }

   public int hashCode() {
      int result = Double.hashCode(this.x);
      result = 31 * result + Double.hashCode(this.y);
      result = 31 * result + Double.hashCode(this.z);
      return result;
   }
}


