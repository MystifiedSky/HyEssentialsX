package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class AnimationRegistry {
   private static final Logger LOGGER = Logger.getLogger("HologramService");
   private final Map<String, AnimationData> animations = new HashMap();

   public AnimationRegistry() {
      this.registerPresetAnimations();
   }

   private void registerPresetAnimations() {
      this.animations.put("float", AnimationData.createFloat("float", 0.15D, 2.0F));
      this.animations.put("float_slow", AnimationData.createFloat("float_slow", 0.1D, 3.0F));
      this.animations.put("float_fast", AnimationData.createFloat("float_fast", 0.2D, 1.0F));
      this.animations.put("spin", AnimationData.createSpin("spin", 4.0F, 1));
      this.animations.put("spin_fast", AnimationData.createSpin("spin_fast", 2.0F, 1));
      this.animations.put("spin_slow", AnimationData.createSpin("spin_slow", 8.0F, 1));
      this.animations.put("tumble", AnimationData.createSpin("tumble", 3.0F, 0));
      this.animations.put("pulse", AnimationData.createPulse("pulse", 0.9F, 1.1F, 1.5F));
      this.animations.put("pulse_big", AnimationData.createPulse("pulse_big", 0.8F, 1.2F, 2.0F));
      this.animations.put("heartbeat", AnimationData.createPulse("heartbeat", 1.0F, 1.15F, 0.8F));
      this.animations.put("bounce", AnimationData.createBounce("bounce", 0.3D, 1.0F));
      this.animations.put("bounce_small", AnimationData.createBounce("bounce_small", 0.15D, 0.8F));
      this.animations.put("sway", AnimationData.createSway("sway", 0.1D, 2.0F));
      this.animations.put("sway_big", AnimationData.createSway("sway_big", 0.2D, 2.5F));
      this.animations.put("wobble", AnimationData.createWobble("wobble", 15.0F, 1.5F));
      this.animations.put("wobble_slow", AnimationData.createWobble("wobble_slow", 10.0F, 2.5F));
      this.animations.put("wobble_fast", AnimationData.createWobble("wobble_fast", 20.0F, 0.8F));
      this.animations.put("shake", AnimationData.createShake("shake", 0.03D, 0.15F));
      this.animations.put("shake_big", AnimationData.createShake("shake_big", 0.15D, 0.25F));
      this.animations.put("orbit", AnimationData.createOrbit("orbit", 0.3D, 3.0F));
      this.animations.put("orbit_small", AnimationData.createOrbit("orbit_small", 0.15D, 2.0F));
      this.animations.put("orbit_fast", AnimationData.createOrbit("orbit_fast", 0.3D, 1.5F));
      this.animations.put("wave", AnimationData.createWave("wave", 0.1D, 10.0F, 2.0F));
      this.animations.put("breathe", AnimationData.createPulse("breathe", 0.95F, 1.05F, 3.0F));
      this.animations.put("flip", AnimationData.createFlip("flip", 2.0F, false));
      this.animations.put("flip_full", AnimationData.createFlip("flip_full", 2.0F, true));
      LOGGER.info("[HologramService] Registered " + this.animations.size() + " preset animations");
   }

   @Nullable
   public AnimationData getAnimation(@Nonnull String name) {
      return (AnimationData)this.animations.get(name.toLowerCase());
   }

   @Nonnull
   public Map<String, AnimationData> getAllAnimations() {
      return new HashMap(this.animations);
   }

   public boolean hasAnimation(@Nonnull String name) {
      return this.animations.containsKey(name.toLowerCase());
   }
}


