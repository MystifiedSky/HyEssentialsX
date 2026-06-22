package xyz.thelegacyvoyage.hyessentialsx.api;

import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public final class DefaultHyEssentialsXApi implements HyEssentialsXApi {

    private final EconomyApi economy;
    private final PlaytimeApi playtime;
    private final ShopApi shops;

    public DefaultHyEssentialsXApi(@Nonnull EconomyManager economyManager,
                                   @Nonnull PlaytimeManager playtimeManager,
                                   @Nonnull ShopManager shopManager) {
        this.economy = new EconomyApiImpl(economyManager);
        this.playtime = new PlaytimeApiImpl(playtimeManager);
        this.shops = new ShopApiImpl(shopManager);
    }

    @Override
    public EconomyApi economy() {
        return economy;
    }

    @Override
    public PlaytimeApi playtime() {
        return playtime;
    }

    @Override
    public ShopApi shops() {
        return shops;
    }

    private static final class EconomyApiImpl implements EconomyApi {
        private final EconomyManager economy;

        private EconomyApiImpl(@Nonnull EconomyManager economy) {
            this.economy = economy;
        }

        @Override
        public boolean isEnabled() {
            return economy.isEnabled();
        }

        @Nonnull
        @Override
        public String getCurrencySymbol() {
            return economy.getCurrencySymbol();
        }

        @Nonnull
        @Override
        public String formatAmount(long amount) {
            return economy.formatAmount(amount);
        }

        @Override
        public long getBalance(@Nonnull UUID uuid) {
            return economy.getBalance(uuid);
        }

        @Override
        public long setBalance(@Nonnull UUID uuid, long amount) {
            return economy.setBalance(uuid, amount);
        }

        @Override
        public long deposit(@Nonnull UUID uuid, long amount) {
            return economy.deposit(uuid, amount);
        }

        @Override
        public boolean withdraw(@Nonnull UUID uuid, long amount) {
            return economy.withdraw(uuid, amount);
        }
    }

    private static final class PlaytimeApiImpl implements PlaytimeApi {
        private final PlaytimeManager playtime;

        private PlaytimeApiImpl(@Nonnull PlaytimeManager playtime) {
            this.playtime = playtime;
        }

        @Override
        public long getPlaytimeSeconds(@Nonnull UUID uuid) {
            return playtime.getPlaytimeSeconds(uuid);
        }

        @Override
        public void setPlaytimeSeconds(@Nonnull UUID uuid, long seconds) {
            playtime.setPlaytimeSeconds(uuid, seconds);
        }

        @Override
        public void addPlaytimeSeconds(@Nonnull UUID uuid, long seconds) {
            playtime.addPlaytimeSeconds(uuid, seconds);
        }

        @Override
        public void resetPlaytime(@Nonnull UUID uuid) {
            playtime.setPlaytimeSeconds(uuid, 0L);
        }
    }

    private static final class ShopApiImpl implements ShopApi {
        private final ShopManager shops;

        private ShopApiImpl(@Nonnull ShopManager shops) {
            this.shops = shops;
        }

        @Nonnull
        @Override
        public List<String> listShops() {
            return shops.listShops();
        }

        @Nonnull
        @Override
        public List<String> listAdminShops() {
            return shops.listAdminShops();
        }

        @Nonnull
        @Override
        public List<String> listPlayerShops() {
            return shops.listPlayerShops();
        }

        @Override
        public ShopModel getShop(@Nonnull String name) {
            return shops.getShop(name);
        }

        @Override
        public ShopModel createAdminShop(@Nonnull String name) {
            return shops.createShop(name);
        }

        @Override
        public ShopModel createPlayerShop(@Nonnull String name, @Nonnull UUID ownerUuid) {
            return shops.createPlayerShop(name, ownerUuid.toString());
        }

        @Override
        public void saveShop(@Nonnull ShopModel shop) {
            shops.saveShop(shop);
        }

        @Override
        public boolean deleteShop(@Nonnull String name) {
            return shops.deleteShop(name);
        }
    }
}
