package xyz.thelegacyvoyage.hyessentialsx.ui;

import com.google.gson.Gson;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public final class KitsUI extends com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage {

    private static final String LAYOUT = "hyessentialsx/KitsPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/KitRow.ui";
    private static final String PERMISSION_NODE = "hyessentialsx.kit";
    private static final String BYPASS_PERMISSION = "hyessentialsx.kit.bypass";

    private final PlayerRef playerRef;
    private final KitManager kitManager;
    private final ConfigManager config;
    private final Gson gson = new Gson();

    public KitsUI(@Nonnull PlayerRef playerRef,
                  @Nonnull KitManager kitManager,
                  @Nonnull ConfigManager config) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.playerRef = playerRef;
        this.kitManager = kitManager;
        this.config = config;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        List<String> kits = kitManager.listKits();
        List<String> visible = kits;
        if (config.isKitsRequirePermission()) {
            visible = new java.util.ArrayList<>();
            for (String kit : kits) {
                if (kit == null || kit.isBlank()) continue;
                String kitPerm = "hyessentialsx.kit." + kit.toLowerCase();
                if (PermissionsModule.get().hasPermission(playerRef.getUuid(), kitPerm)) {
                    visible.add(kit);
                }
            }
        }
        cmd.set("#KitCount.Text", Messages.tr(playerRef, "kit.ui.count", Map.of(
                "count", String.valueOf(visible.size())
        )));
        buildKitsList(cmd, evt, visible);

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("action", "close"),
                false
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            String data
    ) {
        if (data == null || data.isEmpty()) {
            return;
        }

        Map<?, ?> payload;
        try {
            payload = gson.fromJson(data, Map.class);
        } catch (Exception e) {
            return;
        }
        if (payload == null) {
            return;
        }
        Object actionObj = payload.get("action");
        if (!(actionObj instanceof String)) {
            return;
        }
        String action = (String) actionObj;
        if (action.isEmpty()) {
            return;
        }

        if (action.equals("close")) {
            close();
            return;
        }

        if (action.startsWith("kit:")) {
            String name = action.substring("kit:".length());
            claimKit(ref, store, name);
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private void buildKitsList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull List<String> kits) {
        cmd.clear("#KitList");

        if (kits.isEmpty()) {
            String emptyText = Messages.tr(playerRef, "kit.ui.none_available", Map.of());
            cmd.appendInline("#KitList",
                    "Label { Text: \"" + emptyText + "\"; " +
                            "Style: (FontSize: 13, TextColor: #666666, HorizontalAlignment: Center); " +
                            "Anchor: (Top: 30); }");
            return;
        }

        for (int i = 0; i < kits.size(); i++) {
            String name = kits.get(i);
            cmd.append("#KitList", ROW_LAYOUT);
            String selector = "#KitList[" + i + "]";
            cmd.set(selector + ".Text", name);
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    EventData.of("action", "kit:" + name),
                    false
            );
        }
    }

    private void claimKit(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String name) {
        if (name == null || name.isBlank()) {
            Messages.sendPrefixedKey(playerRef, "kit.not_found", Map.of());
            return;
        }
        String kitName = name.trim();
        KitModel kit = kitManager.getKit(kitName);
        if (kit == null) {
            Messages.sendPrefixedKey(playerRef, "kit.not_found", Map.of());
            return;
        }

        if (config.isKitsRequirePermission()) {
            String kitPermission = "hyessentialsx.kit." + kit.getName().toLowerCase();
            if (!PermissionsModule.get().hasPermission(playerRef.getUuid(), kitPermission)) {
                Messages.sendPrefixedKey(playerRef, "error.no_permission", Map.of(
                        "command", "/kit " + kit.getName()
                ));
                return;
            }
        }

        if (!PermissionsModule.get().hasPermission(playerRef.getUuid(), PERMISSION_NODE)) {
            Messages.sendPrefixedKey(playerRef, "error.no_permission", Map.of("command", "/kit"));
            return;
        }

        String kitBypass = "hyessentialsx.kit." + kit.getName().toLowerCase() + ".bypass";
        boolean bypassKitCooldown = PermissionsModule.get().hasPermission(playerRef.getUuid(), BYPASS_PERMISSION)
                || PermissionsModule.get().hasPermission(playerRef.getUuid(), kitBypass);
        if (!bypassKitCooldown) {
            long remaining = kitManager.getRemainingCooldownSeconds(playerRef.getUuid(), kit);
            if (remaining > 0) {
                Messages.sendPrefixedKey(playerRef, "kit.cooldown", Map.of(
                        "time", TimeUtil.formatDurationSeconds(remaining)
                ));
                return;
            }
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendPrefixedKey(playerRef, "error.inventory_access", Map.of());
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            Messages.sendPrefixedKey(playerRef, "error.inventory_access", Map.of());
            return;
        }

        List<ItemStack> overflow = InventoryUtil.applyKit(inventory, kit.getItems());
        if (!overflow.isEmpty()) {
            dropOverflow(playerRef, player, overflow);
        }
        kitManager.markUsed(playerRef.getUuid(), kit);
        Messages.sendPrefixedKey(playerRef, "kit.claimed", Map.of("kit", kit.getName()));
        close();
    }

    private void dropOverflow(@Nonnull PlayerRef playerRef, @Nonnull Player player, @Nonnull List<ItemStack> overflow) {
        for (ItemStack stack : overflow) {
            if (stack == null || stack.isEmpty()) continue;
            if (!tryDropItem(player, stack)) {
                Messages.send(playerRef, "kit.inventory_full");
                return;
            }
        }
    }

    private boolean tryDropItem(@Nonnull Player player, @Nonnull ItemStack stack) {
        String[] methods = {"dropItemStack", "dropItem", "dropItemAt", "spawnItemStack"};
        for (String name : methods) {
            for (Method method : player.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(ItemStack.class)) continue;
                try {
                    method.invoke(player, stack);
                    return true;
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }
}

