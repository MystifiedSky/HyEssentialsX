package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.Objects;

public final class WarpModel {

    private String name;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    @SuppressWarnings("unused")
    public WarpModel() {}

    public WarpModel(@Nonnull String name,
                     @Nonnull String worldName,
                     double x, double y, double z,
                     float yaw, float pitch) {
        this.name = Objects.requireNonNull(name, "name");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public String getWorldName() {
        return worldName;
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
}

