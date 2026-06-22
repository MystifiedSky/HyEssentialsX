package xyz.thelegacyvoyage.hyessentialsx.storage;

import xyz.thelegacyvoyage.hyessentialsx.models.KitModel;
import xyz.thelegacyvoyage.hyessentialsx.models.AuctionHouseDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.IpBanModel;
import xyz.thelegacyvoyage.hyessentialsx.models.PlayerDataModel;
import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;
import xyz.thelegacyvoyage.hyessentialsx.models.WarpModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface StorageBackend {

    @Nullable
    PlayerDataModel loadPlayerData(@Nonnull UUID uuid);

    void savePlayerData(@Nonnull UUID uuid, @Nonnull PlayerDataModel data);

    @Nonnull
    Set<UUID> listPlayerIds();

    @Nonnull
    Map<String, WarpModel> loadWarps();

    void saveWarps(@Nonnull Map<String, WarpModel> warps);

    @Nonnull
    Map<String, KitModel> loadKits();

    void saveKits(@Nonnull Map<String, KitModel> kits);

    @Nonnull
    Map<String, ShopModel> loadShops();

    void saveShops(@Nonnull Map<String, ShopModel> shops);

    @Nonnull
    Map<String, IpBanModel> loadIpBans();

    void saveIpBans(@Nonnull Map<String, IpBanModel> bans);

    @Nonnull
    AuctionHouseDataModel loadAuctionHouseData();

    void saveAuctionHouseData(@Nonnull AuctionHouseDataModel data);

    void shutdown();
}

