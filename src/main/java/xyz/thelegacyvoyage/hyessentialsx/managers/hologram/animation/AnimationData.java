package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation;

import org.joml.Vector3d;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class AnimationData {
   private final String name;
   private final List<Keyframe> keyframes;
   private boolean loop;
   private boolean autoPlay;

   public AnimationData(@Nonnull String name) {
      this.name = name;
      this.keyframes = new ArrayList();
      this.loop = false;
      this.autoPlay = false;
   }

   public AnimationData(@Nonnull String name, boolean loop, boolean autoPlay) {
      this.name = name;
      this.keyframes = new ArrayList();
      this.loop = loop;
      this.autoPlay = autoPlay;
   }

   @Nonnull
   public String getName() {
      return this.name;
   }

   public boolean isLoop() {
      return this.loop;
   }

   public void setLoop(boolean loop) {
      this.loop = loop;
   }

   public boolean isAutoPlay() {
      return this.autoPlay;
   }

   public void setAutoPlay(boolean autoPlay) {
      this.autoPlay = autoPlay;
   }

   public float getDuration() {
      return this.keyframes.isEmpty() ? 0.0F : ((Keyframe)this.keyframes.get(this.keyframes.size() - 1)).getTime();
   }

   public void addKeyframe(@Nonnull Keyframe keyframe) {
      this.keyframes.add(keyframe);
      this.keyframes.sort(Comparator.comparingDouble(Keyframe::getTime));
   }

   @Nonnull
   public List<Keyframe> getKeyframes() {
      return new ArrayList(this.keyframes);
   }

   public void clearKeyframes() {
      this.keyframes.clear();
   }

   @Nullable
   public Keyframe sample(float time) {
      if (this.keyframes.isEmpty()) {
         return null;
      } else if (this.keyframes.size() == 1) {
         return (Keyframe)this.keyframes.get(0);
      } else {
         float duration = this.getDuration();
         if (duration <= 0.0F) {
            return (Keyframe)this.keyframes.get(0);
         } else {
            if (this.loop && time > duration) {
               time %= duration;
            } else if (time >= duration) {
               return (Keyframe)this.keyframes.get(this.keyframes.size() - 1);
            }

            if (time <= 0.0F) {
               return (Keyframe)this.keyframes.get(0);
            } else {
               Keyframe prev = (Keyframe)this.keyframes.get(0);

               for(int i = 1; i < this.keyframes.size(); ++i) {
                  Keyframe current = (Keyframe)this.keyframes.get(i);
                  if (time <= current.getTime()) {
                     float segmentDuration = current.getTime() - prev.getTime();
                     if (segmentDuration <= 0.0F) {
                        return prev;
                     }

                     double t = (double)((time - prev.getTime()) / segmentDuration);
                     return prev.interpolate(current, t);
                  }

                  prev = current;
               }

               return (Keyframe)this.keyframes.get(this.keyframes.size() - 1);
            }
         }
      }
   }

   public AnimationData copy(@Nonnull String newName) {
      AnimationData copy = new AnimationData(newName, this.loop, this.autoPlay);
      Iterator var3 = this.keyframes.iterator();

      while(var3.hasNext()) {
         Keyframe kf = (Keyframe)var3.next();
         copy.addKeyframe(new Keyframe(kf.getTime(), kf.getPosition(), kf.getRotation(), kf.getScale(), kf.getEasing()));
      }

      return copy;
   }

   public static AnimationData createFloat(@Nonnull String name, double amplitude, float duration) {
      AnimationData anim = new AnimationData(name, true, true);
      int steps = 8;

      for(int i = 0; i <= steps; ++i) {
         float t = duration / (float)steps * (float)i;
         double y = Math.sin((double)i / (double)steps * 3.141592653589793D * 2.0D) * amplitude;
         anim.addKeyframe(new Keyframe(t, new Vector3d(0.0D, y, 0.0D), (Vector3f)null, 1.0F, EasingType.EASE_IN_OUT));
      }

      return anim;
   }

   public static AnimationData createSpin(@Nonnull String name, float duration, int axis) {
      AnimationData anim = new AnimationData(name, true, true);
      float pitch = 0.0F;
      float yaw = 0.0F;
      float roll = 0.0F;
      if (axis == 0) {
         pitch = 360.0F;
      } else if (axis == 1) {
         yaw = 360.0F;
      } else {
         roll = 360.0F;
      }

      anim.addKeyframe(new Keyframe(0.0F, (Vector3d)null, new Vector3f(0.0F, 0.0F, 0.0F), 1.0F, EasingType.LINEAR));
      anim.addKeyframe(new Keyframe(duration, (Vector3d)null, new Vector3f(pitch, yaw, roll), 1.0F, EasingType.LINEAR));
      return anim;
   }

   public static AnimationData createPulse(@Nonnull String name, float minScale, float maxScale, float duration) {
      AnimationData anim = new AnimationData(name, true, true);
      anim.addKeyframe(new Keyframe(0.0F, (Vector3d)null, (Vector3f)null, minScale, EasingType.EASE_IN_OUT));
      anim.addKeyframe(new Keyframe(duration / 2.0F, (Vector3d)null, (Vector3f)null, maxScale, EasingType.EASE_IN_OUT));
      anim.addKeyframe(new Keyframe(duration, (Vector3d)null, (Vector3f)null, minScale, EasingType.EASE_IN_OUT));
      return anim;
   }

   public static AnimationData createBounce(@Nonnull String name, double height, float duration) {
      AnimationData anim = new AnimationData(name, true, true);
      anim.addKeyframe(new Keyframe(0.0F, new Vector3d(0.0D, 0.0D, 0.0D), (Vector3f)null, 1.0F, EasingType.EASE_OUT));
      anim.addKeyframe(new Keyframe(duration / 2.0F, new Vector3d(0.0D, height, 0.0D), (Vector3f)null, 1.0F, EasingType.EASE_IN));
      anim.addKeyframe(new Keyframe(duration, new Vector3d(0.0D, 0.0D, 0.0D), (Vector3f)null, 1.0F, EasingType.BOUNCE));
      return anim;
   }

   public static AnimationData createSway(@Nonnull String name, double amplitude, float duration) {
      AnimationData anim = new AnimationData(name, true, true);
      int steps = 8;

      for(int i = 0; i <= steps; ++i) {
         float t = duration / (float)steps * (float)i;
         double x = Math.sin((double)i / (double)steps * 3.141592653589793D * 2.0D) * amplitude;
         anim.addKeyframe(new Keyframe(t, new Vector3d(x, 0.0D, 0.0D), (Vector3f)null, 1.0F, EasingType.EASE_IN_OUT));
      }

      return anim;
   }

   public static AnimationData createWobble(@Nonnull String name, float angle, float duration) {
      AnimationData anim = new AnimationData(name, true, true);
      int steps = 8;

      for(int i = 0; i <= steps; ++i) {
         float t = duration / (float)steps * (float)i;
         float roll = (float)Math.sin((double)i / (double)steps * 3.141592653589793D * 2.0D) * angle;
         anim.addKeyframe(new Keyframe(t, (Vector3d)null, new Vector3f(0.0F, 0.0F, roll), 1.0F, EasingType.EASE_IN_OUT));
      }

      return anim;
   }

   public static AnimationData createShake(@Nonnull String name, double amplitude, float duration) {
      AnimationData anim = new AnimationData(name, true, true);
      anim.addKeyframe(new Keyframe(0.0F, new Vector3d(0.0D, 0.0D, 0.0D), (Vector3f)null, 1.0F, EasingType.LINEAR));
      anim.addKeyframe(new Keyframe(duration * 0.1F, new Vector3d(amplitude, 0.0D, 0.0D), (Vector3f)null, 1.0F, EasingType.LINEAR));
      anim.addKeyframe(new Keyframe(duration * 0.2F, new Vector3d(-amplitude, 0.0D, 0.0D), (Vector3f)null, 1.0F, EasingType.LINEAR));
      anim.addKeyframe(new Keyframe(duration * 0.3F, new Vector3d(amplitude, 0.0D, 0.0D), (Vector3f)null, 1.0F, EasingType.LINEAR));
      anim.addKeyframe(new Keyframe(duration * 0.4F, new Vector3d(-amplitude, 0.0D, 0.0D), (Vector3f)null, 1.0F, EasingType.LINEAR));
      anim.addKeyframe(new Keyframe(duration * 0.5F, new Vector3d(0.0D, 0.0D, 0.0D), (Vector3f)null, 1.0F, EasingType.LINEAR));
      anim.addKeyframe(new Keyframe(duration, new Vector3d(0.0D, 0.0D, 0.0D), (Vector3f)null, 1.0F, EasingType.LINEAR));
      return anim;
   }

   public static AnimationData createOrbit(@Nonnull String name, double radius, float duration) {
      AnimationData anim = new AnimationData(name, true, true);
      int steps = 16;

      for(int i = 0; i <= steps; ++i) {
         float t = duration / (float)steps * (float)i;
         double angle = (double)i / (double)steps * 3.141592653589793D * 2.0D;
         double x = Math.cos(angle) * radius;
         double z = Math.sin(angle) * radius;
         anim.addKeyframe(new Keyframe(t, new Vector3d(x, 0.0D, z), (Vector3f)null, 1.0F, EasingType.LINEAR));
      }

      return anim;
   }

   public static AnimationData createWave(@Nonnull String name, double amplitude, float rollAngle, float duration) {
      AnimationData anim = new AnimationData(name, true, true);
      int steps = 8;

      for(int i = 0; i <= steps; ++i) {
         float t = duration / (float)steps * (float)i;
         double sinVal = Math.sin((double)i / (double)steps * 3.141592653589793D * 2.0D);
         double y = sinVal * amplitude;
         float roll = (float)sinVal * rollAngle;
         anim.addKeyframe(new Keyframe(t, new Vector3d(0.0D, y, 0.0D), new Vector3f(0.0F, 0.0F, roll), 1.0F, EasingType.EASE_IN_OUT));
      }

      return anim;
   }

   public static AnimationData createFlip(@Nonnull String name, float duration, boolean fullRotation) {
      AnimationData anim = new AnimationData(name, true, true);
      if (fullRotation) {
         anim.addKeyframe(new Keyframe(0.0F, (Vector3d)null, new Vector3f(0.0F, 0.0F, 0.0F), 1.0F, EasingType.LINEAR));
         anim.addKeyframe(new Keyframe(duration, (Vector3d)null, new Vector3f(360.0F, 0.0F, 0.0F), 1.0F, EasingType.LINEAR));
      } else {
         anim.addKeyframe(new Keyframe(0.0F, (Vector3d)null, new Vector3f(0.0F, 0.0F, 0.0F), 1.0F, EasingType.EASE_IN_OUT));
         anim.addKeyframe(new Keyframe(duration / 2.0F, (Vector3d)null, new Vector3f(180.0F, 0.0F, 0.0F), 1.0F, EasingType.EASE_IN_OUT));
         anim.addKeyframe(new Keyframe(duration, (Vector3d)null, new Vector3f(0.0F, 0.0F, 0.0F), 1.0F, EasingType.EASE_IN_OUT));
      }

      return anim;
   }

   public String toString() {
      String var10000 = this.name;
      return "AnimationData{name='" + var10000 + "', loop=" + this.loop + ", autoPlay=" + this.autoPlay + ", keyframes=" + this.keyframes.size() + ", duration=" + this.getDuration() + "s}";
   }
}


