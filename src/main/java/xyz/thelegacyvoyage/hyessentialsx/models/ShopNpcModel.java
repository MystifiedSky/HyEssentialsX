package xyz.thelegacyvoyage.hyessentialsx.models;

import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;

public final class ShopNpcModel {

    private String npcId;
    private int posX;
    private int posY;
    private int posZ;
    private String worldId;
    private String shopName;
    private String spawnerUuid;
    private String spawnerName;
    private String roleName;
    private long spawnedTime;

    public ShopNpcModel() {
    }

    public ShopNpcModel(@Nonnull String npcId,
                        @Nonnull Vector3i position,
                        @Nonnull String worldId,
                        @Nonnull String shopName,
                        @Nonnull String spawnerUuid,
                        @Nonnull String spawnerName,
                        @Nonnull String roleName) {
        this.npcId = npcId;
        this.posX = position.getX();
        this.posY = position.getY();
        this.posZ = position.getZ();
        this.worldId = worldId;
        this.shopName = shopName;
        this.spawnerUuid = spawnerUuid;
        this.spawnerName = spawnerName;
        this.roleName = roleName;
        this.spawnedTime = System.currentTimeMillis();
    }

    public String getNpcId() {
        return npcId == null ? "" : npcId;
    }

    public void setNpcId(@Nonnull String npcId) {
        this.npcId = npcId;
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

    public int getPosX() {
        return posX;
    }

    public void setPosX(int posX) {
        this.posX = posX;
    }

    public int getPosY() {
        return posY;
    }

    public void setPosY(int posY) {
        this.posY = posY;
    }

    public int getPosZ() {
        return posZ;
    }

    public void setPosZ(int posZ) {
        this.posZ = posZ;
    }

    public String getWorldId() {
        return worldId == null ? "" : worldId;
    }

    public void setWorldId(@Nonnull String worldId) {
        this.worldId = worldId;
    }

    public String getShopName() {
        return shopName == null ? "" : shopName;
    }

    public void setShopName(@Nonnull String shopName) {
        this.shopName = shopName;
    }

    public String getSpawnerUuid() {
        return spawnerUuid == null ? "" : spawnerUuid;
    }

    public void setSpawnerUuid(@Nonnull String spawnerUuid) {
        this.spawnerUuid = spawnerUuid;
    }

    public String getSpawnerName() {
        return spawnerName == null ? "" : spawnerName;
    }

    public void setSpawnerName(@Nonnull String spawnerName) {
        this.spawnerName = spawnerName;
    }

    public String getRoleName() {
        return roleName == null ? "" : roleName;
    }

    public void setRoleName(@Nonnull String roleName) {
        this.roleName = roleName;
    }

    public long getSpawnedTime() {
        return spawnedTime;
    }

    public void setSpawnedTime(long spawnedTime) {
        this.spawnedTime = spawnedTime;
    }
}

