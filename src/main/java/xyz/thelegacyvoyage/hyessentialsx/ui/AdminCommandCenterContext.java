package xyz.thelegacyvoyage.hyessentialsx.ui;

import xyz.thelegacyvoyage.hyessentialsx.managers.AuctionHouseManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.AutoBroadcastManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.BanManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyAuditManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyHudManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.EconomyManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.FreezeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.KitManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.MuteManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlayerWarpManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.PlaytimeRewardManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.RankupManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopAdminDraftCache;
import xyz.thelegacyvoyage.hyessentialsx.managers.ShopManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.SpawnManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.StorageManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarpManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.WarningEscalationManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.IpBanManager;
import xyz.thelegacyvoyage.hyessentialsx.util.ConfigManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record AdminCommandCenterContext(
        @Nonnull StorageManager storage,
        @Nonnull ConfigManager config,
        @Nonnull SpawnManager spawnManager,
        @Nonnull WarpManager warpManager,
        @Nonnull KitManager kitManager,
        @Nonnull BanManager banManager,
        @Nonnull IpBanManager ipBanManager,
        @Nonnull MuteManager muteManager,
        @Nonnull FreezeManager freezeManager,
        @Nonnull PlayerWarpManager playerWarpManager,
        @Nonnull WarningEscalationManager warningEscalationManager,
        @Nonnull AutoBroadcastManager autoBroadcastManager,
        @Nullable EconomyManager economyManager,
        @Nullable EconomyHudManager economyHudManager,
        @Nullable EconomyAuditManager economyAuditManager,
        @Nonnull PlaytimeManager playtimeManager,
        @Nonnull PlaytimeRewardManager playtimeRewardManager,
        @Nonnull RankupManager rankupManager,
        @Nullable ShopManager shopManager,
        @Nullable ShopAdminDraftCache shopAdminDraftCache,
        @Nullable AuctionHouseManager auctionHouseManager
) {
}
