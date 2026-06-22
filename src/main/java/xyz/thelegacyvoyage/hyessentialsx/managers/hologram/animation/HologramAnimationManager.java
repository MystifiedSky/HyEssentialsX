package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation;

import org.joml.Vector3d;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class HologramAnimationManager {
   private final Map<UUID, HologramAnimationManager.AnimationState> activeAnimations = new HashMap();

   public void registerAnimation(@Nonnull UUID entityUuid, @Nonnull String hologramName, int lineIndex, @Nonnull AnimationData animation, @Nonnull Vector3d basePosition, @Nonnull Vector3f baseRotation, float baseScale) {
      HologramAnimationManager.AnimationState state = new HologramAnimationManager.AnimationState(hologramName, lineIndex, animation, basePosition, baseRotation, baseScale);
      if (animation.isAutoPlay()) {
         state.play();
      }

      this.activeAnimations.put(entityUuid, state);
   }

   public void unregisterAnimation(@Nonnull UUID entityUuid) {
      this.activeAnimations.remove(entityUuid);
   }

   public void clearAnimationsForHologram(@Nonnull String hologramName) {
      this.activeAnimations.entrySet().removeIf((entry) -> {
         return ((HologramAnimationManager.AnimationState)entry.getValue()).getHologramName().equals(hologramName);
      });
   }

   @Nullable
   public HologramAnimationManager.AnimationState getAnimationState(@Nonnull UUID entityUuid) {
      return (HologramAnimationManager.AnimationState)this.activeAnimations.get(entityUuid);
   }

   public boolean hasAnimation(@Nonnull UUID entityUuid) {
      return this.activeAnimations.containsKey(entityUuid);
   }

   @Nonnull
   public Map<UUID, HologramAnimationManager.AnimationState> getAllActiveAnimations() {
      return new HashMap(this.activeAnimations);
   }

   public int getActiveAnimationCount() {
      return this.activeAnimations.size();
   }

   public void clearAll() {
      this.activeAnimations.clear();
   }

   public static class AnimationState {
      private final String hologramName;
      private final int lineIndex;
      private final AnimationData animation;
      private final Vector3d basePosition;
      private final Vector3f baseRotation;
      private final float baseScale;
      private float currentTime;
      private boolean playing;

      public AnimationState(String hologramName, int lineIndex, AnimationData animation, Vector3d basePosition, Vector3f baseRotation, float baseScale) {
         this.hologramName = hologramName;
         this.lineIndex = lineIndex;
         this.animation = animation;
         this.basePosition = new Vector3d(basePosition);
         this.baseRotation = new Vector3f(baseRotation);
         this.baseScale = baseScale;
         this.currentTime = 0.0F;
         this.playing = false;
      }

      public String getHologramName() {
         return this.hologramName;
      }

      public int getLineIndex() {
         return this.lineIndex;
      }

      public AnimationData getAnimation() {
         return this.animation;
      }

      public Vector3d getBasePosition() {
         return new Vector3d(this.basePosition);
      }

      public Vector3f getBaseRotation() {
         return new Vector3f(this.baseRotation);
      }

      public float getBaseScale() {
         return this.baseScale;
      }

      public float getCurrentTime() {
         return this.currentTime;
      }

      public boolean isPlaying() {
         return this.playing;
      }

      public void advanceTime(float dt) {
         if (this.playing) {
            this.currentTime += dt;
            if (this.animation.isLoop() && this.currentTime > this.animation.getDuration()) {
               this.currentTime %= this.animation.getDuration();
            }
         }

      }

      public void play() {
         this.playing = true;
      }

      public void pause() {
         this.playing = false;
      }

      public void stop() {
         this.playing = false;
         this.currentTime = 0.0F;
      }

      public void reset() {
         this.currentTime = 0.0F;
      }

      public boolean isFinished() {
         return !this.animation.isLoop() && this.currentTime >= this.animation.getDuration();
      }

      @Nonnull
      public Vector3d getCurrentPosition() {
         Keyframe sampled = this.animation.sample(this.currentTime);
         if (sampled == null) {
            return new Vector3d(this.basePosition);
         } else {
            Vector3d offset = sampled.getPosition();
            return new Vector3d(this.basePosition.x + offset.x, this.basePosition.y + offset.y, this.basePosition.z + offset.z);
         }
      }

      @Nonnull
      public Vector3f getCurrentRotation() {
         Keyframe sampled = this.animation.sample(this.currentTime);
         if (sampled == null) {
            return new Vector3f(this.baseRotation);
         } else {
            Vector3f animRot = sampled.getRotation();
            return new Vector3f(this.baseRotation.x + (float)Math.toRadians((double)animRot.x), this.baseRotation.y + (float)Math.toRadians((double)animRot.y), this.baseRotation.z + (float)Math.toRadians((double)animRot.z));
         }
      }

      public float getCurrentScale() {
         Keyframe sampled = this.animation.sample(this.currentTime);
         return sampled == null ? this.baseScale : this.baseScale * sampled.getScale();
      }
   }
}


