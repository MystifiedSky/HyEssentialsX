package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopChestModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopNpcModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ShopContainerUtil {

    private static final double TARGET_STEP = 0.05D;

    private ShopContainerUtil() {}

    @Nonnull
    public static List<ItemContainer> resolveContainers(@Nonnull World world,
                                                        @Nonnull ShopModel shop,
                                                        int maxRadius) {
        List<ItemContainer> containers = new ArrayList<>();
        List<ShopNpcModel> npcs = shop.getNpcs();
        boolean enforceRadius = maxRadius > 0 && !npcs.isEmpty();
        for (ShopChestModel chest : shop.getChests()) {
            if (chest == null) continue;
            if (!chest.getWorldId().isBlank()
                    && !chest.getWorldId().equalsIgnoreCase(world.getName())) {
                continue;
            }
            Vector3i pos = chest.getPosition();
            if (enforceRadius && !isWithinRadius(pos, npcs, maxRadius)) {
                continue;
            }
            ItemContainer container = getContainerAt(world, pos);
            if (container != null) {
                containers.add(container);
            }
        }
        return containers;
    }

    @Nullable
    public static ItemContainer getContainerAt(@Nonnull World world, @Nonnull Vector3i pos) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ());
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) return null;
        BlockState state = chunk.getState(pos.getX(), pos.getY(), pos.getZ());
        if (state instanceof ItemContainerBlockState containerState) {
            return containerState.getItemContainer();
        }
        return null;
    }

    public static boolean hasItems(@Nonnull List<ItemContainer> containers,
                                   @Nonnull List<ShopItemModel> items) {
        if (items.isEmpty()) return true;
        CombinedItemContainer combined = combine(containers);
        if (combined == null) return false;
        for (ShopItemModel item : items) {
            if (item == null) continue;
            String id = item.getItemId();
            int qty = item.getQuantity();
            if (id == null || id.isBlank() || qty <= 0) continue;
            if (countItem(combined, id) < qty) {
                return false;
            }
        }
        return true;
    }

    public static boolean canAddItems(@Nonnull List<ItemContainer> containers,
                                      @Nonnull List<ShopItemModel> items) {
        if (items.isEmpty()) return true;
        CombinedItemContainer combined = combine(containers);
        if (combined == null) return false;
        List<ItemStack> stacks = toItemStacks(items);
        return stacks.isEmpty() || combined.canAddItemStacks(stacks);
    }

    public static boolean addItems(@Nonnull List<ItemContainer> containers,
                                   @Nonnull List<ShopItemModel> items) {
        if (items.isEmpty()) return true;
        CombinedItemContainer combined = combine(containers);
        if (combined == null) return false;
        List<ItemStack> stacks = toItemStacks(items);
        if (stacks.isEmpty()) return true;
        if (!combined.canAddItemStacks(stacks)) return false;
        ListTransaction<ItemStackTransaction> tx = combined.addItemStacks(stacks);
        return tx != null && tx.succeeded();
    }

    public static boolean removeItems(@Nonnull List<ItemContainer> containers,
                                      @Nonnull List<ShopItemModel> items) {
        if (items.isEmpty()) return true;
        CombinedItemContainer combined = combine(containers);
        if (combined == null) return false;
        List<ItemStack> stacks = toItemStacks(items);
        if (stacks.isEmpty()) return true;
        if (!combined.canRemoveItemStacks(stacks)) return false;
        ListTransaction<ItemStackTransaction> tx = combined.removeItemStacks(stacks);
        return tx != null && tx.succeeded();
    }

    public static boolean removeItemsById(@Nonnull List<ItemContainer> containers,
                                          @Nonnull List<ShopItemModel> items) {
        if (items.isEmpty()) return true;
        if (!hasItems(containers, items)) return false;
        for (ShopItemModel item : items) {
            if (item == null) continue;
            String itemId = item.getItemId();
            int remaining = item.getQuantity();
            if (itemId == null || itemId.isBlank() || remaining <= 0) continue;

            for (ItemContainer container : containers) {
                if (container == null) continue;
                short cap = container.getCapacity();
                for (short slot = 0; slot < cap && remaining > 0; slot++) {
                    ItemStack stack = container.getItemStack(slot);
                    if (stack == null || stack.isEmpty()) continue;
                    if (!itemId.equals(stack.getItemId())) continue;

                    int removeAmount = Math.min(remaining, Math.max(0, stack.getQuantity()));
                    if (removeAmount <= 0) continue;
                    int newQty = stack.getQuantity() - removeAmount;
                    if (newQty <= 0) {
                        setSlotEmpty(container, slot);
                    } else {
                        container.setItemStackForSlot(slot, cloneWithQuantity(stack, newQty));
                    }
                    remaining -= removeAmount;
                }
                if (remaining <= 0) break;
            }

            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    public static int countItem(@Nonnull List<ItemContainer> containers, @Nonnull String itemId) {
        CombinedItemContainer combined = combine(containers);
        if (combined == null) return 0;
        return countItem(combined, itemId);
    }

    public static int computeTradeStock(@Nonnull List<ItemContainer> containers,
                                        @Nonnull List<ShopItemModel> rewards) {
        int stock = Integer.MAX_VALUE;
        boolean found = false;
        for (ShopItemModel item : rewards) {
            if (item == null) continue;
            String id = item.getItemId();
            int qty = item.getQuantity();
            if (id == null || id.isBlank() || qty <= 0) continue;
            int have = countItem(containers, id);
            int possible = have / qty;
            stock = Math.min(stock, possible);
            found = true;
        }
        return found ? stock : 0;
    }

    @Nullable
    public static Vector3i findTargetedContainer(@Nonnull World world,
                                                 @Nonnull Store<EntityStore> store,
                                                 @Nonnull Ref<EntityStore> playerRef,
                                                 double maxDistance) {
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        HeadRotation headRotation = store.getComponent(playerRef, HeadRotation.getComponentType());
        if (transform == null || headRotation == null || transform.getPosition() == null) {
            return null;
        }
        Vector3d eye = transform.getPosition().clone().add(0.0, 1.59375, 0.0);
        Vector3d direction = getDirectionFromRotation(
                headRotation.getRotation().getX(),
                headRotation.getRotation().getY()
        );
        Set<Long> checked = new HashSet<>();
        for (double distance = 0.0; distance <= maxDistance; distance += TARGET_STEP) {
            Vector3d current = eye.add(
                    direction.getX() * distance,
                    direction.getY() * distance,
                    direction.getZ() * distance
            );
            int blockX = (int) Math.floor(current.getX());
            int blockY = (int) Math.floor(current.getY());
            int blockZ = (int) Math.floor(current.getZ());
            long key = ((long) blockX << 40) | ((long) (blockY & 0xFFFFF) << 20) | (long) (blockZ & 0xFFFFF);
            if (!checked.add(key)) continue;
            ItemContainer container = getContainerAt(world, new Vector3i(blockX, blockY, blockZ));
            if (container != null) {
                return new Vector3i(blockX, blockY, blockZ);
            }
        }
        return null;
    }

    private static Vector3d getDirectionFromRotation(float pitch, float yaw) {
        double x = -Math.cos(pitch) * Math.sin(yaw);
        double y = Math.sin(pitch);
        double z = -Math.cos(pitch) * Math.cos(yaw);
        return new Vector3d(x, y, z);
    }

    private static int countItem(@Nonnull ItemContainer container, @Nonnull String itemId) {
        if (itemId.isBlank()) return 0;
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

    @Nullable
    private static CombinedItemContainer combine(@Nonnull List<ItemContainer> containers) {
        if (containers.isEmpty()) return null;
        return new CombinedItemContainer(containers.toArray(new ItemContainer[0]));
    }

    @Nonnull
    private static List<ItemStack> toItemStacks(@Nonnull List<ShopItemModel> items) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ShopItemModel item : items) {
            if (item == null) continue;
            String id = item.getItemId();
            int qty = item.getQuantity();
            if (id == null || id.isBlank() || qty <= 0) continue;
            stacks.add(new ItemStack(id, qty));
        }
        return stacks;
    }

    public static boolean isWithinRadius(@Nonnull Vector3i pos,
                                         @Nonnull List<ShopNpcModel> npcs,
                                         int radius) {
        double radiusSq = radius * radius;
        for (ShopNpcModel npc : npcs) {
            if (npc == null) continue;
            Vector3i npcPos = npc.getPosition();
            double dx = pos.getX() - npcPos.getX();
            double dy = pos.getY() - npcPos.getY();
            double dz = pos.getZ() - npcPos.getZ();
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distSq <= radiusSq) {
                return true;
            }
        }
        return false;
    }
}
