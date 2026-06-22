package xyz.thelegacyvoyage.hyessentialsx.util;

import xyz.thelegacyvoyage.hyessentialsx.models.ShopItemModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopTradeModel;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class ShopTradeQuantityUtil {

    public static final int DEFAULT_MAX_QUANTITY = 9999;

    private ShopTradeQuantityUtil() {
    }

    public static int parseQuantity(String raw, int maxQuantity) {
        int max = normalizeMaxQuantity(maxQuantity);
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        try {
            int quantity = Integer.parseInt(raw.trim());
            return quantity >= 1 && quantity <= max ? quantity : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static int normalizeMaxQuantity(int maxQuantity) {
        return Math.max(1, maxQuantity);
    }

    @Nonnull
    public static ShopTradeModel scaleTrade(@Nonnull ShopTradeModel trade, int quantity) {
        ShopTradeModel scaled = new ShopTradeModel(trade.getId());
        scaled.setEnabled(trade.isEnabled());
        scaled.setSellTrade(trade.isSellTrade());
        scaled.setMoneyScale(trade.getMoneyScale());
        scaled.setMoneyCost(multiplyMoney(trade.getMoneyCost(), quantity));
        scaled.setCostItems(scaleItems(trade.getCostItems(), quantity));
        scaled.setRewardItems(scaleItems(trade.getRewardItems(), quantity));
        scaled.setStockLimit(trade.getStockLimit());
        scaled.setStockCurrent(trade.getStockCurrent());
        return scaled;
    }

    @Nonnull
    public static List<ShopItemModel> scaleItems(@Nonnull List<ShopItemModel> items, int quantity) {
        List<ShopItemModel> scaled = new ArrayList<>();
        int multiplier = Math.max(1, quantity);
        for (ShopItemModel item : items) {
            if (item == null) continue;
            String id = item.getItemId();
            int amount = item.getQuantity();
            if (id.isBlank() || amount <= 0) continue;
            long total = (long) amount * multiplier;
            if (total > Integer.MAX_VALUE) {
                total = Integer.MAX_VALUE;
            }
            scaled.add(new ShopItemModel(id, (int) total));
        }
        return scaled;
    }

    public static long multiplyMoney(long amount, int quantity) {
        if (amount <= 0L) {
            return 0L;
        }
        try {
            return Math.multiplyExact(amount, Math.max(1, quantity));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
