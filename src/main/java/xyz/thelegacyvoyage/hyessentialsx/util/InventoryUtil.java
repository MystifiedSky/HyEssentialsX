package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import org.bson.BsonDocument;
import xyz.thelegacyvoyage.hyessentialsx.models.KitItemModel;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class InventoryUtil {

    private InventoryUtil() {}

    @Nonnull
    public static List<KitItemModel> snapshot(@Nonnull Inventory inventory) {
        List<KitItemModel> out = new ArrayList<>();
        ItemContainer container = inventory.getCombinedEverything();
        if (container == null) return out;

        short cap = container.getCapacity();
        for (short i = 0; i < cap; i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack == null || stack.isEmpty()) continue;
            String meta = stack.getMetadata() != null ? stack.getMetadata().toJson() : null;
            out.add(new KitItemModel(
                    i,
                    stack.getItemId(),
                    stack.getQuantity(),
                    stack.getDurability(),
                    stack.getMaxDurability(),
                    meta
            ));
        }
        return out;
    }

    @Nonnull
    public static List<ItemStack> applyKit(@Nonnull Inventory inventory, @Nonnull List<KitItemModel> items) {
        ItemContainer container = inventory.getCombinedEverything();
        if (container == null) return List.of();

        List<ItemStack> overflow = new ArrayList<>();
        for (KitItemModel model : items) {
            ItemStack stack = toItemStack(model);
            if (stack == null) continue;
            if (!tryAddToContainer(container, stack)) {
                overflow.add(stack);
            }
        }
        inventory.markChanged();
        return overflow;
    }

    public static void clear(@Nonnull Inventory inventory) {
        inventory.clear();
    }

    public static int repairAll(@Nonnull Inventory inventory) {
        ItemContainer container = inventory.getCombinedEverything();
        if (container == null) return 0;

        int repaired = 0;
        short cap = container.getCapacity();
        for (short i = 0; i < cap; i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack == null || stack.isEmpty()) continue;
            if (stack.getMaxDurability() <= 0) continue;
            if (stack.getDurability() >= stack.getMaxDurability()) continue;

            ItemStack fixed = new ItemStack(
                    stack.getItemId(),
                    stack.getQuantity(),
                    stack.getMaxDurability(),
                    stack.getMaxDurability(),
                    stack.getMetadata()
            );
            container.setItemStackForSlot(i, fixed);
            repaired++;
        }
        inventory.markChanged();
        return repaired;
    }

    public static int repairInHand(@Nonnull Inventory inventory) {
        ItemStack held = inventory.getItemInHand();
        if (held == null || held.isEmpty()) return 0;
        if (held.getMaxDurability() <= 0) return 0;
        if (held.getDurability() >= held.getMaxDurability()) return 0;

        byte slot = inventory.getActiveHotbarSlot();
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) return 0;

        ItemStack fixed = new ItemStack(
                held.getItemId(),
                held.getQuantity(),
                held.getMaxDurability(),
                held.getMaxDurability(),
                held.getMetadata()
        );
        hotbar.setItemStackForSlot(slot, fixed);
        inventory.markChanged();
        return 1;
    }

    private static ItemStack toItemStack(@Nonnull KitItemModel model) {
        if (model.getItemId() == null || model.getItemId().isBlank()) return null;
        BsonDocument meta = null;
        if (model.getMetadataJson() != null && !model.getMetadataJson().isBlank()) {
            try {
                meta = BsonDocument.parse(model.getMetadataJson());
            } catch (Exception ignored) {
            }
        }
        return new ItemStack(
                model.getItemId(),
                model.getQuantity(),
                model.getDurability(),
                model.getMaxDurability(),
                meta
        );
    }

    private static boolean tryAddToContainer(@Nonnull ItemContainer container, @Nonnull ItemStack stack) {
        // Try container add method if available (handles stacking).
        for (Method method : container.getClass().getMethods()) {
            String name = method.getName().toLowerCase();
            if (!name.contains("add") && !name.contains("insert") && !name.contains("offer")) continue;
            if (method.getParameterCount() != 1) continue;
            if (!method.getParameterTypes()[0].isAssignableFrom(ItemStack.class)) continue;
            try {
                Object result = method.invoke(container, stack);
                if (result instanceof Boolean bool) return bool;
                if (result instanceof ItemStack remaining) return remaining.isEmpty();
                return true;
            } catch (Exception ignored) {
            }
        }

        // Fallback: place into first empty slot.
        short cap = container.getCapacity();
        for (short i = 0; i < cap; i++) {
            ItemStack existing = container.getItemStack(i);
            if (existing == null || existing.isEmpty()) {
                container.setItemStackForSlot(i, stack);
                return true;
            }
        }
        return false;
    }
}
