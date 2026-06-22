package xyz.thelegacyvoyage.hyessentialsx.models;

public final class BackPointModel {

    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private long recordedAt;

    @SuppressWarnings("unused")
    public BackPointModel() {}

    public BackPointModel(String worldName, double x, double y, double z, float yaw, float pitch, long recordedAt) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.recordedAt = recordedAt;
    }

    public String getWorldName() { return worldName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public long getRecordedAt() { return recordedAt; }
}
