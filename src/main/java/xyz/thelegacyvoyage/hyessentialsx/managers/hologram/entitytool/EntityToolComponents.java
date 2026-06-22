package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.entitytool;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class EntityToolComponents {
   public static final double TEXT_BOX_HALF_WIDTH = 1.0D;
   public static final double TEXT_BOX_HALF_HEIGHT = 0.15D;
   public static final double TEXT_BOX_HALF_DEPTH = 0.1D;
   public static final double ITEM_BOX_HALF_SIZE = 0.5D;
   public static final double IMAGE_BOX_HALF_SIZE = 1.0D;

   private EntityToolComponents() {
   }

   public static void addEntityToolComponents(@Nonnull Holder<EntityStore> holder, double boxHalfWidth, double boxHalfHeight, double boxHalfDepth) {
      holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
   }

   public static void addTextLineComponents(@Nonnull Holder<EntityStore> holder) {
      addEntityToolComponents(holder, 1.0D, 0.15D, 0.1D);
   }

   public static void addItemLineComponents(@Nonnull Holder<EntityStore> holder) {
      addEntityToolComponents(holder, 0.5D, 0.5D, 0.5D);
   }

   public static void addImageLineComponents(@Nonnull Holder<EntityStore> holder, float scale) {
      double halfSize = 1.0D * (double)scale;
      addEntityToolComponents(holder, halfSize, halfSize, 0.1D);
   }

   public static void addPropComponent(@Nonnull Ref<EntityStore> entityRef, @Nonnull Store<EntityStore> store) {
      if (store.getComponent(entityRef, PropComponent.getComponentType()) == null) {
         store.addComponent(entityRef, PropComponent.getComponentType(), PropComponent.get());
      }

   }

   public static void addBoundingBox(@Nonnull Ref<EntityStore> entityRef, @Nonnull Store<EntityStore> store, double halfWidth, double halfHeight, double halfDepth) {
      Box box = new Box(new Vector3d(-halfWidth, -halfHeight, -halfDepth), new Vector3d(halfWidth, halfHeight, halfDepth));
      BoundingBox existing = (BoundingBox)store.getComponent(entityRef, BoundingBox.getComponentType());
      if (existing != null) {
         store.putComponent(entityRef, BoundingBox.getComponentType(), new BoundingBox(box));
      } else {
         store.addComponent(entityRef, BoundingBox.getComponentType(), new BoundingBox(box));
      }

   }

   public static void makeSelectable(@Nonnull Ref<EntityStore> entityRef, @Nonnull Store<EntityStore> store, double halfWidth, double halfHeight, double halfDepth) {
      addPropComponent(entityRef, store);
   }
}


