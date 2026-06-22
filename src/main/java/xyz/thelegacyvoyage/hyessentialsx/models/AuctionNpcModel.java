package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;

public final class AuctionNpcModel {

    private String npcId;
    private int posX;
    private int posY;
    private int posZ;
    private String worldId;
    private String spawnerUuid;
    private String spawnerName;
    private String roleName;
    private long spawnedTime;

    public AuctionNpcModel() {
    }

    public AuctionNpcModel(@Nonnull ShopNpcModel npc) {
        this.npcId = npc.getNpcId();
        this.posX = npc.getPosX();
        this.posY = npc.getPosY();
        this.posZ = npc.getPosZ();
        this.worldId = npc.getWorldId();
        this.spawnerUuid = npc.getSpawnerUuid();
        this.spawnerName = npc.getSpawnerName();
        this.roleName = npc.getRoleName();
        this.spawnedTime = npc.getSpawnedTime();
    }

    @Nonnull
    public String getNpcId() {
        return npcId == null ? "" : npcId;
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    public int getPosZ() {
        return posZ;
    }

    @Nonnull
    public String getWorldId() {
        return worldId == null ? "" : worldId;
    }

    @Nonnull
    public String getSpawnerUuid() {
        return spawnerUuid == null ? "" : spawnerUuid;
    }

    @Nonnull
    public String getSpawnerName() {
        return spawnerName == null ? "" : spawnerName;
    }

    @Nonnull
    public String getRoleName() {
        return roleName == null ? "" : roleName;
    }

    public long getSpawnedTime() {
        return spawnedTime;
    }
}
