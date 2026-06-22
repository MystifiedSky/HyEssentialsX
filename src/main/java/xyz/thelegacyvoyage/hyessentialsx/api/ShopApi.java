package xyz.thelegacyvoyage.hyessentialsx.api;

import xyz.thelegacyvoyage.hyessentialsx.models.ShopModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public interface ShopApi {
    @Nonnull
    List<String> listShops();

    @Nonnull
    List<String> listAdminShops();

    @Nonnull
    List<String> listPlayerShops();

    @Nullable
    ShopModel getShop(@Nonnull String name);

    @Nullable
    ShopModel createAdminShop(@Nonnull String name);

    @Nullable
    ShopModel createPlayerShop(@Nonnull String name, @Nonnull UUID ownerUuid);

    void saveShop(@Nonnull ShopModel shop);

    boolean deleteShop(@Nonnull String name);
}
