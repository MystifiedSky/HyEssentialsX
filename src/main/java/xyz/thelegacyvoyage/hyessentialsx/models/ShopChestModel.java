package xyz.thelegacyvoyage.hyessentialsx.models;

import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;

public final class ShopChestModel {

    private int posX;
    private int posY;
    private int posZ;
    private String worldId;

    public ShopChestModel() {
    }

    public ShopChestModel(@Nonnull Vector3i position, @Nonnull String worldId) {
        this.posX = position.getX();
        this.posY = position.getY();
        this.posZ = position.getZ();
        this.worldId = worldId;
    }

    @Nonnull
    public Vector3i getPosition() {
        return new Vector3i(posX, posY, posZ);
    }

    public void setPosition(@Nonnull Vector3i position) {
        this.posX = position.getX();
        this.posY = position.getY();
        this.posZ = position.getZ();
    }

    public String getWorldId() {
        return worldId == null ? "" : worldId;
    }

    public void setWorldId(@Nonnull String worldId) {
        this.worldId = worldId;
    }
}
