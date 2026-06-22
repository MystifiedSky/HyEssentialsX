package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.Objects;

public final class HomeModel {

    private String name;
    private String worldId;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    @SuppressWarnings("unused")
    public HomeModel() {}

    public HomeModel(@Nonnull String name,
                     @Nonnull String worldName,
                     double x, double y, double z,
                     float yaw, float pitch) {
        this(name, null, worldName, x, y, z, yaw, pitch);
    }

    public HomeModel(@Nonnull String name,
                     String worldId,
                     @Nonnull String worldName,
                     double x, double y, double z,
                     float yaw, float pitch) {
        this.name = Objects.requireNonNull(name, "name");
        this.worldId = worldId;
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.x = sanitizeDouble(x);
        this.y = sanitizeDouble(y);
        this.z = sanitizeDouble(z);
        this.yaw = sanitizeFloat(yaw);
        this.pitch = sanitizeFloat(pitch);
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public String getWorldName() {
        return worldName;
    }

    public String getWorldId() {
        return worldId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

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

