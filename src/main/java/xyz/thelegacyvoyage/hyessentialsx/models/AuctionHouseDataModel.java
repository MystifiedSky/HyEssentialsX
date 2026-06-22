package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class AuctionHouseDataModel {

    private List<AuctionListingModel> listings = new ArrayList<>();
    private List<AuctionNpcModel> npcs = new ArrayList<>();

    @Nonnull
    public List<AuctionListingModel> getListings() {
        if (listings == null) {
            listings = new ArrayList<>();
        }
        return listings;
    }

    public void setListings(@Nonnull List<AuctionListingModel> listings) {
        this.listings = listings;
    }

    @Nonnull
    public List<AuctionNpcModel> getNpcs() {
        if (npcs == null) {
            npcs = new ArrayList<>();
        }
        return npcs;
    }

    public void setNpcs(@Nonnull List<AuctionNpcModel> npcs) {
        this.npcs = npcs;
    }
}
