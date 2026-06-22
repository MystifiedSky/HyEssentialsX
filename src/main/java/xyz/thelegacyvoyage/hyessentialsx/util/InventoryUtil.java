package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import org.bson.BsonDocument;
import xyz.thelegacyvoyage.hyessentialsx.models.KitItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopItemModel;

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

    public static int countItem(@Nonnull Inventory inventory, @Nonnull String itemId) {
        if (itemId.isBlank()) return 0;
        ItemContainer container = inventory.getCombinedEverything();
        if (container == null) return 0;
        int total = 0;
        short cap = container.getCapacity();
        for (short i = 0; i < cap; i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack == null || stack.isEmpty()) continue;
            if (itemId.equals(stack.getItemId())) {
                total += Math.max(0, stack.getQuantity());
            }
        }
        return total;
    }

    public static boolean hasItems(@Nonnull Inventory inventory, @Nonnull List<ShopItemModel> items) {
        if (items.isEmpty()) return true;
        for (ShopItemModel item : items) {
            if (item == null) continue;
            String itemId = item.getItemId();
            int qty = item.getQuantity();
            if (itemId.isBlank() || qty <= 0) continue;
            if (countItem(inventory, itemId) < qty) {
                return false;
            }
        }
        return true;
    }

    public static boolean removeItems(@Nonnull Inventory inventory, @Nonnull List<ShopItemModel> items) {
        if (items.isEmpty()) return true;
        if (!hasItems(inventory, items)) return false;
        ItemContainer container = inventory.getCombinedEverything();
        if (container == null) return false;

        for (ShopItemModel item : items) {
            if (item == null) continue;
            String itemId = item.getItemId();
            int remaining = item.getQuantity();
            if (itemId.isBlank() || remaining <= 0) continue;

            short cap = container.getCapacity();
            for (short i = 0; i < cap && remaining > 0; i++) {
                ItemStack stack = container.getItemStack(i);
                if (stack == null || stack.isEmpty()) continue;
                if (!itemId.equals(stack.getItemId())) continue;

                int removeAmount = Math.min(remaining, Math.max(0, stack.getQuantity()));
                if (removeAmount <= 0) continue;

                int newQty = stack.getQuantity() - removeAmount;
                if (newQty <= 0) {
                    setSlotEmpty(container, i);
                } else {
                    ItemStack updated = cloneWithQuantity(stack, newQty);
                    container.setItemStackForSlot(i, updated);
                }
                remaining -= removeAmount;
            }

            if (remaining > 0) {
                inventory.markChanged();
                return false;
            }
        }
        inventory.markChanged();
        return true;
    }

    public static boolean addItems(@Nonnull Inventory inventory, @Nonnull List<ShopItemModel> items) {
        return addItemsWithOverflow(inventory, items).isEmpty();
    }

    @Nonnull
    public static List<ItemStack> addItemsWithOverflow(@Nonnull Inventory inventory, @Nonnull List<ShopItemModel> items) {
        List<ItemStack> overflow = new ArrayList<>();
        if (items.isEmpty()) return overflow;
        ItemContainer container = inventory.getCombinedEverything();
        if (container == null) return overflow;
        for (ShopItemModel item : items) {
            if (item == null) continue;
            String itemId = item.getItemId();
            int qty = item.getQuantity();
            if (itemId.isBlank() || qty <= 0) continue;
            ItemStack stack = new ItemStack(itemId, qty, 0, 0, null);
            if (!tryAddToContainer(container, stack)) {
                overflow.add(stack);
            }
        }
        inventory.markChanged();
        return overflow;
    }

    private static ItemStack cloneWithQuantity(@Nonnull ItemStack stack, int quantity) {
        try {
            java.lang.reflect.Method method = stack.getClass().getMethod("withQuantity", int.class);
            Object result = method.invoke(stack, quantity);
            if (result instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (Exception ignored) {
        }
        return new ItemStack(
                stack.getItemId(),
                quantity,
                stack.getDurability(),
                stack.getMaxDurability(),
                stack.getMetadata()
        );
    }

    private static void setSlotEmpty(@Nonnull ItemContainer container, short slot) {
        try {
            container.setItemStackForSlot(slot, null);
        } catch (Exception ignored) {
            ItemStack empty = new ItemStack("", 0, 0, 0, null);
            container.setItemStackForSlot(slot, empty);
        }
    }
}

