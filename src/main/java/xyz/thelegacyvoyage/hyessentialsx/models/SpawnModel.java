package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Immutable spawn record stored by HyEssentialsX.
 * Gson-friendly (no-args ctor + public getters).
 */
public final class SpawnModel {

    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    /** For Gson/serialization */
    @SuppressWarnings("unused")
    public SpawnModel() {}

    public SpawnModel(
            @Nonnull String worldName,
            double x, double y, double z,
            float yaw, float pitch
    ) {
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
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

    /** Nice helper */
    @Override
    public String toString() {
        return "SpawnModel{" +
                "worldName='" + worldName + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", yaw=" + yaw +
                ", pitch=" + pitch +
                '}';
    }
}

