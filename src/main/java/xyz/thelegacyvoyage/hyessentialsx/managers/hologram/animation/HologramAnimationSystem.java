package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public class HologramAnimationSystem extends EntityTickingSystem<EntityStore> {
   private final HologramAnimationManager animationManager;

   public HologramAnimationSystem(@Nonnull HologramAnimationManager animationManager) {
      this.animationManager = animationManager;
   }

   @Nonnull
   public Query<EntityStore> getQuery() {
      return Query.and(new Query[]{TransformComponent.getComponentType(), UUIDComponent.getComponentType()});
   }

   public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
      UUIDComponent uuidComp = (UUIDComponent)archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
      if (uuidComp != null) {
         UUID entityUuid = uuidComp.getUuid();
         HologramAnimationManager.AnimationState state = this.animationManager.getAnimationState(entityUuid);
         if (state != null && state.isPlaying()) {
            if (state.getAnimation() != null) {
               TransformComponent transform = (TransformComponent)archetypeChunk.getComponent(index, TransformComponent.getComponentType());
               if (transform != null) {
                  Keyframe sampled = state.getAnimation().sample(state.getCurrentTime());
                  if (sampled == null) {
                     state.advanceTime(dt);
                  } else {
                     Vector3d animOffset = sampled.getPosition();
                     Vector3d basePos = state.getBasePosition();
                     Vector3d finalPos = new Vector3d(basePos.x + animOffset.x, basePos.y + animOffset.y, basePos.z + animOffset.z);
                     transform.setPosition(finalPos);
                     Vector3f animRot = sampled.getRotation();
                     Vector3f baseRot = state.getBaseRotation();
                     Rotation3f finalRot = new Rotation3f(baseRot.x + (float)Math.toRadians((double)animRot.x), baseRot.y + (float)Math.toRadians((double)animRot.y), baseRot.z + (float)Math.toRadians((double)animRot.z));
                     transform.setRotation(finalRot);
                     EntityScaleComponent scaleComp = (EntityScaleComponent)archetypeChunk.getComponent(index, EntityScaleComponent.getComponentType());
                     if (scaleComp != null) {
                        float finalScale = state.getBaseScale() * sampled.getScale();
                        scaleComp.setScale(finalScale);
                     }

                     state.advanceTime(dt);
                     if (state.isFinished()) {
                        state.stop();
                     }

                  }
               }
            }
         }
      }
   }
}


