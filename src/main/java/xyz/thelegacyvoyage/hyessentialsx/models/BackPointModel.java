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
        this.x = sanitizeDouble(x);
        this.y = sanitizeDouble(y);
        this.z = sanitizeDouble(z);
        this.yaw = sanitizeFloat(yaw);
        this.pitch = sanitizeFloat(pitch);
        this.recordedAt = recordedAt;
    }

    public String getWorldName() { return worldName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public long getRecordedAt() { return recordedAt; }

    public void sanitize() {
        this.x = sanitizeDouble(this.x);
        this.y = sanitizeDouble(this.y);
        this.z = sanitizeDouble(this.z);
        this.yaw = sanitizeFloat(this.yaw);
        this.pitch = sanitizeFloat(this.pitch);
    }

    private static double sanitizeDouble(double value) {
        return Double.isFinite(value) ? value : 0D;
    }

    private static float sanitizeFloat(float value) {
        return Float.isFinite(value) ? value : 0F;
    }
}

