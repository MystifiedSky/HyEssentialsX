package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import org.bson.BsonDocument;
import xyz.thelegacyvoyage.hyessentialsx.models.KitItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopItemModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("removal")
public final class InventoryUtil {

    private InventoryUtil() {}

    @Nonnull
    public static List<KitItemModel> snapshot(@Nonnull Inventory inventory) {
        List<KitItemModel> out = new ArrayList<>();
        ItemContainer container = getCombinedInventory(inventory);
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
        ItemContainer container = getCombinedInventory(inventory);
        if (container == null) return List.of();

        List<ItemStack> overflow = new ArrayList<>();
        for (KitItemModel model : items) {
            ItemStack stack = toItemStack(model);
            if (stack == null) continue;
            if (!tryAddToPreferredOrContainer(container, model.getSlot(), stack)) {
                overflow.add(stack);
            }
        }
        return overflow;
    }

    public static void clear(@Nonnull Inventory inventory) {
        inventory.clear();
    }

    public static int repairAll(@Nonnull Inventory inventory) {
        ItemContainer container = getCombinedInventory(inventory);
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
        // 1) Try to merge into an existing similar stack.
        short cap = container.getCapacity();
        for (short i = 0; i < cap; i++) {
            ItemStack existing = container.getItemStack(i);
            if (existing == null || existing.isEmpty()) continue;
            if (!canMerge(existing, stack)) continue;
            ItemStack merged = cloneWithQuantity(existing, existing.getQuantity() + stack.getQuantity());
            if (trySetSlot(container, i, merged)) {
                return true;
            }
        }

        // 2) Place into first empty slot.
        for (short i = 0; i < cap; i++) {
            ItemStack existing = container.getItemStack(i);
            if (existing == null || existing.isEmpty()) {
                if (trySetSlot(container, i, stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tryAddToPreferredOrContainer(@Nonnull ItemContainer container, int preferredSlot, @Nonnull ItemStack stack) {
        if (preferredSlot >= 0 && preferredSlot < container.getCapacity()) {
            short slot = (short) preferredSlot;
            ItemStack existing = container.getItemStack(slot);
            if (existing == null || existing.isEmpty()) {
                if (trySetSlot(container, slot, stack)) {
                    return true;
                }
            }
            if (canMerge(existing, stack)) {
                ItemStack merged = cloneWithQuantity(existing, existing.getQuantity() + stack.getQuantity());
                if (trySetSlot(container, slot, merged)) {
                    return true;
                }
            }
        }
        return tryAddToContainer(container, stack);
    }

    private static boolean canMerge(@Nonnull ItemStack left, @Nonnull ItemStack right) {
        if (!left.getItemId().equals(right.getItemId())) return false;
        // Durable items (tools/armor/weapons) should never stack.
        if (left.getMaxDurability() > 0 || right.getMaxDurability() > 0) return false;
        if (left.getDurability() != right.getDurability()) return false;
        BsonDocument leftMeta = left.getMetadata();
        BsonDocument rightMeta = right.getMetadata();
        if (leftMeta == null && rightMeta == null) return true;
        if (leftMeta == null || rightMeta == null) return false;
        return leftMeta.equals(rightMeta);
    }

    public static int countItem(@Nonnull Inventory inventory, @Nonnull String itemId) {
        if (itemId.isBlank()) return 0;
        ItemContainer container = getCombinedInventory(inventory);
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
        ItemContainer container = getCombinedInventory(inventory);
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
                return false;
            }
        }
        return true;
    }

    public static boolean addItems(@Nonnull Inventory inventory, @Nonnull List<ShopItemModel> items) {
        return addItemsWithOverflow(inventory, items).isEmpty();
    }

    @Nonnull
    public static List<ItemStack> addItemsWithOverflow(@Nonnull Inventory inventory, @Nonnull List<ShopItemModel> items) {
        List<ItemStack> overflow = new ArrayList<>();
        if (items.isEmpty()) return overflow;
        ItemContainer container = getCombinedInventory(inventory);
        if (container == null) {
            for (ShopItemModel item : items) {
                if (item == null) continue;
                String itemId = item.getItemId();
                int qty = item.getQuantity();
                if (itemId.isBlank() || qty <= 0) continue;
                overflow.add(new ItemStack(itemId, qty, 0, 0, null));
            }
            return overflow;
        }
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
        return overflow;
    }

    @Nullable
    private static ItemContainer getCombinedInventory(@Nonnull Inventory inventory) {
        List<ItemContainer> containers = new ArrayList<>(6);
        addContainer(containers, inventory.getArmor());
        addContainer(containers, inventory.getHotbar());
        addContainer(containers, inventory.getUtility());
        addContainer(containers, inventory.getStorage());
        addContainer(containers, inventory.getTools());
        addContainer(containers, inventory.getBackpack());
        if (containers.isEmpty()) return null;
        if (containers.size() == 1) return containers.get(0);
        return new CombinedItemContainer(containers.toArray(new ItemContainer[0]));
    }

    private static void addContainer(@Nonnull List<ItemContainer> containers, @Nullable ItemContainer container) {
        if (container != null) {
            containers.add(container);
        }
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

    private static boolean trySetSlot(@Nonnull ItemContainer container, short slot, @Nonnull ItemStack stack) {
        try {
            container.setItemStackForSlot(slot, stack);
            ItemStack after = container.getItemStack(slot);
            if (after == null || after.isEmpty()) return false;
            if (!after.getItemId().equals(stack.getItemId())) return false;
            if (after.getQuantity() != stack.getQuantity()) return false;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}

