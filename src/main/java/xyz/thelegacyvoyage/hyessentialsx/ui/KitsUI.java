package xyz.thelegacyvoyage.hyessentialsx.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.DelegateItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.KitItemModel;
import xyz.thelegacyvoyage.hyessentialsx.util.CommandPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;
import xyz.thelegacyvoyage.hyessentialsx.util.InventoryUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import xyz.thelegacyvoyage.hyessentialsx.util.TimeUtil;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("removal")
public final class KitsUI extends InteractiveCustomUIPage<KitsUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/KitsPage.ui";
    private static final String ROW_LAYOUT = "hyessentialsx/KitRow.ui";
    private static final String PERMISSION_NODE = "hyessentialsx.kit";
    private static final String BYPASS_PERMISSION = "hyessentialsx.kit.bypass";

    private final PlayerRef playerRef;
    private final KitManager kitManager;
    private final ConfigManager config;
    private final String initialQuery;

    public KitsUI(@Nonnull PlayerRef playerRef,
                  @Nonnull KitManager kitManager,
                  @Nonnull ConfigManager config) {
        this(playerRef, kitManager, config, "");
    }

    public KitsUI(@Nonnull PlayerRef playerRef,
                  @Nonnull KitManager kitManager,
                  @Nonnull ConfigManager config,
                  String initialQuery) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.kitManager = kitManager;
        this.config = config;
        this.initialQuery = initialQuery;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        String query = initialQuery == null ? "" : initialQuery;
        cmd.set("#SearchBar #SearchInput.Value", query);

        List<KitModel> visible = collectVisibleKits(query);
        cmd.set("#KitCount.Text", Messages.tr(playerRef, "kit.ui.count", Map.of(
                "count", String.valueOf(visible.size())
        )));
        buildKitsList(cmd, evt, visible);

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "Close"),
                false
        );
        evt.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchBar #SearchInput",
                EventData.of("Action", "Search").append("@Query", "#SearchBar #SearchInput.Value"),
                false
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UIEventData data
    ) {
        if (data.action == null || data.action.isEmpty()) {
            return;
        }
        if (data.action.equals("Close")) {
            close();
            return;
        }

        if (data.action.equals("Search")) {
            String query = data.query == null ? "" : data.query.trim();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            KitsUI page = new KitsUI(playerRef, kitManager, config, query);
            player.getPageManager().openCustomPage(ref, store, page);
            return;
        }

        if (data.action.equals("Claim")) {
            claimKit(ref, store, data.kit);
            return;
        }

        if (data.action.equals("Preview")) {
            previewKit(ref, store, data.kit);
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    @Nonnull
    private List<KitModel> collectVisibleKits(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<KitModel> visible = new ArrayList<>();
        for (KitModel kit : kitManager.listKitModels()) {
            if (kit == null || kit.getName() == null || kit.getName().isBlank()) {
                continue;
            }
            if (!normalized.isEmpty() && !kit.getName().toLowerCase(Locale.ROOT).contains(normalized)) {
                continue;
            }
            if (!canAccessKit(kit)) {
                continue;
            }
            visible.add(kit);
        }
        return visible;
    }

    private boolean canAccessKit(@Nonnull KitModel kit) {
        if (!config.isKitsRequirePermission()) {
            return true;
        }
        String kitPerm = "hyessentialsx.kit." + kit.getName().toLowerCase(Locale.ROOT);
        return CommandPermissionUtil.hasPermission(playerRef, kitPerm);
    }

    private void buildKitsList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull List<KitModel> kits) {
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
            KitModel kit = kits.get(i);
            String name = kit.getName();

            cmd.append("#KitList", ROW_LAYOUT);
            String row = "#KitList[" + i + "]";
            cmd.set(row + " #Info #Name.Text", name);

            boolean bypass = hasBypassForKit(kit);
            boolean hasUses = bypass || kitManager.hasRemainingUses(playerRef.getUuid(), kit);
            long cooldown = bypass ? 0L : kitManager.getRemainingCooldownSeconds(playerRef.getUuid(), kit);
            boolean canClaim = hasUses && cooldown <= 0L;

            String statusText;
            String statusColor;
            String buttonText;
            if (!hasUses) {
                statusText = Messages.tr(playerRef, "kit.ui.status.max_uses", Map.of());
                statusColor = "#FF5555";
                buttonText = Messages.tr(playerRef, "kit.ui.button.maxed", Map.of());
            } else if (cooldown > 0L) {
                statusText = Messages.tr(playerRef, "kit.ui.status.cooldown", Map.of(
                        "time", TimeUtil.formatDurationSeconds(cooldown)
                ));
                statusColor = "#FACC15";
                buttonText = Messages.tr(playerRef, "kit.ui.button.cooldown", Map.of());
            } else {
                statusText = Messages.tr(playerRef, "kit.ui.status.ready", Map.of());
                statusColor = "#55FF55";
                buttonText = Messages.tr(playerRef, "kit.ui.button.claim", Map.of());
            }

            cmd.set(row + " #Info #Status.Text", statusText);
            cmd.set(row + " #Info #Status.Style.TextColor", statusColor);
            cmd.set(row + " #ClaimButton.Text", buttonText);
            cmd.set(row + " #PreviewButton.Text", "Preview");

            if (kit.getMaxUses() > 0) {
                int used = kitManager.getUsedCount(playerRef.getUuid(), kit);
                String usesText = Messages.tr(playerRef, "kit.ui.uses", Map.of(
                        "used", String.valueOf(Math.min(used, kit.getMaxUses())),
                        "max", String.valueOf(kit.getMaxUses())
                ));
                cmd.set(row + " #Info #Usage.Text", usesText);
            } else {
                cmd.set(row + " #Info #Usage.Text", Messages.tr(playerRef, "kit.ui.unlimited", Map.of()));
            }

            if (canClaim) {
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        row + " #ClaimButton",
                        EventData.of("Action", "Claim").append("Kit", name),
                        false
                );
            }
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    row + " #PreviewButton",
                    EventData.of("Action", "Preview").append("Kit", name),
                    false
            );
        }
    }

    private boolean hasBypassForKit(@Nonnull KitModel kit) {
        String kitBypass = "hyessentialsx.kit." + kit.getName().toLowerCase(Locale.ROOT) + ".bypass";
        return CommandPermissionUtil.hasPermission(playerRef, BYPASS_PERMISSION)
                || CommandPermissionUtil.hasPermission(playerRef, kitBypass);
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
            if (!CommandPermissionUtil.hasPermission(playerRef, kitPermission)) {
                Messages.sendPrefixedKey(playerRef, "error.no_permission", Map.of(
                        "command", "/kit " + kit.getName()
                ));
                return;
            }
        }

        if (!CommandPermissionUtil.hasPermission(playerRef, PERMISSION_NODE)) {
            Messages.sendPrefixedKey(playerRef, "error.no_permission", Map.of("command", "/kit"));
            return;
        }

        String kitBypass = "hyessentialsx.kit." + kit.getName().toLowerCase() + ".bypass";
        boolean bypassKitCooldown = CommandPermissionUtil.hasPermission(playerRef, BYPASS_PERMISSION)
                || CommandPermissionUtil.hasPermission(playerRef, kitBypass);
        if (!bypassKitCooldown) {
            if (!kitManager.hasRemainingUses(playerRef.getUuid(), kit)) {
                Messages.sendPrefixedKey(playerRef, "kit.max_uses_reached", Map.of());
                return;
            }
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

    private void previewKit(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        KitModel kit = kitManager.getKit(name.trim());
        if (kit == null) {
            Messages.sendPrefixedKey(playerRef, "kit.not_found", Map.of());
            return;
        }
        if (!canAccessKit(kit)) {
            Messages.sendPrefixedKey(playerRef, "error.no_permission", Map.of("command", "/kit " + kit.getName()));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Messages.sendPrefixedKey(playerRef, "error.inventory_access", Map.of());
            return;
        }

        short capacity = 54;
        SimpleItemContainer container = new SimpleItemContainer(capacity);
        loadKitItemsIntoContainer(kit.getItems(), container);
        DelegateItemContainer viewContainer = new DelegateItemContainer(container);
        viewContainer.setGlobalFilter(FilterType.DENY_ALL);

        boolean opened = player.getPageManager().setPageWithWindows(
                ref,
                store,
                Page.Bench,
                true,
                new Window[]{new ContainerWindow((ItemContainer) viewContainer)}
        );
        if (!opened) {
            Messages.sendPrefixedKey(playerRef, "error.inventory_access", Map.of());
        }
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

    private void loadKitItemsIntoContainer(@Nonnull List<KitItemModel> items, @Nonnull SimpleItemContainer container) {
        for (KitItemModel model : items) {
            if (model == null) continue;
            ItemStack stack = toItemStack(model);
            if (stack == null) continue;
            short slot = model.getSlot();
            if (slot >= 0 && slot < container.getCapacity()) {
                if (isEmpty(container.getItemStack(slot))) {
                    container.setItemStackForSlot(slot, stack);
                    continue;
                }
            }
            short empty = firstEmptySlot(container);
            if (empty >= 0) {
                container.setItemStackForSlot(empty, stack);
            }
        }
    }

    private short firstEmptySlot(@Nonnull SimpleItemContainer container) {
        short cap = container.getCapacity();
        for (short i = 0; i < cap; i++) {
            if (isEmpty(container.getItemStack(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    private ItemStack toItemStack(@Nonnull KitItemModel model) {
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

    public static final class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec
                .builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("Kit", Codec.STRING), (d, v) -> d.kit = v, d -> d.kit).add()
                .append(new KeyedCodec<>("@Query", Codec.STRING), (d, v) -> d.query = v, d -> d.query).add()
                .build();

        private String action;
        private String kit;
        private String query;
    }
}

