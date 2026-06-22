package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ShopNpcNameplateUtil {

    private ShopNpcNameplateUtil() {}

    public static void apply(@Nonnull Store<EntityStore> store,
                             @Nonnull Ref<EntityStore> ref,
                             @Nonnull String displayName) {
        applyNameplate(store, ref, displayName);
        applyDisplayNameComponent(store, ref, displayName);
    }

    private static void applyNameplate(@Nonnull Store<EntityStore> store,
                                       @Nonnull Ref<EntityStore> ref,
                                       @Nonnull String displayName) {
        Nameplate nameplate = store.getComponent(ref, Nameplate.getComponentType());
        if (nameplate == null) {
            store.addComponent(ref, Nameplate.getComponentType(), new Nameplate(displayName));
        } else {
            nameplate.setText(displayName);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyDisplayNameComponent(@Nonnull Store<EntityStore> store,
                                                  @Nonnull Ref<EntityStore> ref,
                                                  @Nonnull String displayName) {
        try {
            Class<?> cls = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent");
            Method getType = cls.getMethod("getComponentType");
            @SuppressWarnings("rawtypes")
            com.hypixel.hytale.component.ComponentType type =
                    (com.hypixel.hytale.component.ComponentType) getType.invoke(null);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object comp = ((Store) store).getComponent(ref, type);
            Message message = Message.raw(displayName);

            if (comp == null) {
                Object created = createDisplayNameComponent(cls, message);
                if (created != null) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Store rawStore = (Store) store;
                    rawStore.addComponent(ref, type,
                            (com.hypixel.hytale.component.Component) created);
                }
                return;
            }

            if (!tryInvokeSetter(comp, message)) {
                trySetField(comp, message);
            }
        } catch (Exception ignored) {
        }
    }

    private static Object createDisplayNameComponent(@Nonnull Class<?> cls, @Nonnull Message message) {
        try {
            Constructor<?> ctor = cls.getConstructor(Message.class);
            return ctor.newInstance(message);
        } catch (Exception ignored) {
        }
        try {
            Object instance = cls.getConstructor().newInstance();
            if (!tryInvokeSetter(instance, message)) {
                trySetField(instance, message);
            }
            return instance;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean tryInvokeSetter(@Nonnull Object comp, @Nonnull Message message) {
        try {
            Method method = comp.getClass().getMethod("setDisplayName", Message.class);
            method.invoke(comp, message);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void trySetField(@Nonnull Object comp, @Nonnull Message message) {
        try {
            Field field = comp.getClass().getDeclaredField("displayName");
            field.setAccessible(true);
            field.set(comp, message);
        } catch (Exception ignored) {
        }
    }
}

