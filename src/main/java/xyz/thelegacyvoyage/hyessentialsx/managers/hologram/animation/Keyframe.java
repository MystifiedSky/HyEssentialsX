package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation;

import org.joml.Vector3d;
import org.joml.Vector3f;
import javax.annotation.Nonnull;

public class Keyframe {
   private final float time;
   private final Vector3d position;
   private final Vector3f rotation;
   private final float scale;
   private final EasingType easing;

   public Keyframe(float time, Vector3d position, Vector3f rotation, float scale, EasingType easing) {
      this.time = time;
      this.position = position != null ? new Vector3d(position) : new Vector3d(0.0D, 0.0D, 0.0D);
      this.rotation = rotation != null ? new Vector3f(rotation) : new Vector3f(0.0F, 0.0F, 0.0F);
      this.scale = scale;
      this.easing = easing != null ? easing : EasingType.LINEAR;
   }

   public static Keyframe position(float time, double x, double y, double z) {
      return new Keyframe(time, new Vector3d(x, y, z), (Vector3f)null, 1.0F, EasingType.LINEAR);
   }

   public static Keyframe rotation(float time, float pitch, float yaw, float roll) {
      return new Keyframe(time, (Vector3d)null, new Vector3f(pitch, yaw, roll), 1.0F, EasingType.LINEAR);
   }

   public static Keyframe scale(float time, float scale) {
      return new Keyframe(time, (Vector3d)null, (Vector3f)null, scale, EasingType.LINEAR);
   }

   public static Keyframe identity(float time) {
      return new Keyframe(time, new Vector3d(0.0D, 0.0D, 0.0D), new Vector3f(0.0F, 0.0F, 0.0F), 1.0F, EasingType.LINEAR);
   }

   public float getTime() {
      return this.time;
   }

   @Nonnull
   public Vector3d getPosition() {
      return new Vector3d(this.position);
   }

   @Nonnull
   public Vector3f getRotation() {
      return new Vector3f(this.rotation);
   }

   public float getScale() {
      return this.scale;
   }

   @Nonnull
   public EasingType getEasing() {
      return this.easing;
   }

   public Keyframe interpolate(Keyframe other, double t) {
      double eased = this.easing.apply(t);
      Vector3d pos = new Vector3d(lerp(this.position.x, other.position.x, eased), lerp(this.position.y, other.position.y, eased), lerp(this.position.z, other.position.z, eased));
      Vector3f rot = new Vector3f((float)lerp((double)this.rotation.x, (double)other.rotation.x, eased), (float)lerp((double)this.rotation.y, (double)other.rotation.y, eased), (float)lerp((double)this.rotation.z, (double)other.rotation.z, eased));
      float scl = (float)lerp((double)this.scale, (double)other.scale, eased);
      return new Keyframe((float)lerp((double)this.time, (double)other.time, t), pos, rot, scl, this.easing);
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   public String toString() {
      float var10000 = this.time;
      return "Keyframe{time=" + var10000 + ", pos=(" + this.position.x + "," + this.position.y + "," + this.position.z + "), rot=(" + this.rotation.x + "," + this.rotation.y + "," + this.rotation.z + "), scale=" + this.scale + ", easing=" + String.valueOf(this.easing) + "}";
   }
}


