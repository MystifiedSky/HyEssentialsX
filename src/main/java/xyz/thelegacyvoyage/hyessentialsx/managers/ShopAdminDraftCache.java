package xyz.thelegacyvoyage.hyessentialsx.managers;

import xyz.thelegacyvoyage.hyessentialsx.models.ShopItemModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopAdminDraftCache {

    private final ConcurrentHashMap<UUID, Draft> drafts = new ConcurrentHashMap<>();

    @Nullable
    public Draft get(@Nonnull UUID uuid) {
        Draft draft = drafts.get(uuid);
        return draft == null ? null : draft.copy();
    }

    public void save(@Nonnull UUID uuid, @Nonnull Draft draft) {
        drafts.put(uuid, draft.copy());
    }

    public void clear(@Nonnull UUID uuid) {
        drafts.remove(uuid);
    }

    public static final class Draft {
        public String shopName = "";
        public String tab = "TRADES";
        public int tradePage = 0;
        public boolean useMoney = true;
        public boolean sellTrade = false;
        public String npcRole = "";
        public String stockResetDaysText = "";
        public String moneyStockLimitText = "";
        public String stockLimitText = "";
        public String costQtyText = "";
        public String outputQtyText = "";
        public ShopItemModel pendingCostItem;
        public ShopItemModel pendingOutputItem;
        public String pendingPriceText = "";
        public String pendingNameText = "";
        public int editingIndex = -1;

        @Nonnull
        public Draft copy() {
            Draft copy = new Draft();
            copy.shopName = shopName;
            copy.tab = tab;
            copy.tradePage = tradePage;
            copy.useMoney = useMoney;
            copy.sellTrade = sellTrade;
            copy.npcRole = npcRole;
            copy.stockResetDaysText = stockResetDaysText;
            copy.moneyStockLimitText = moneyStockLimitText;
            copy.stockLimitText = stockLimitText;
            copy.costQtyText = costQtyText;
            copy.outputQtyText = outputQtyText;
            copy.pendingCostItem = cloneItem(pendingCostItem);
            copy.pendingOutputItem = cloneItem(pendingOutputItem);
            copy.pendingPriceText = pendingPriceText;
            copy.pendingNameText = pendingNameText;
            copy.editingIndex = editingIndex;
            return copy;
        }

        @Nullable
        private static ShopItemModel cloneItem(@Nullable ShopItemModel item) {
            if (item == null) return null;
            return new ShopItemModel(item.getItemId(), item.getQuantity());
        }
    }
}

