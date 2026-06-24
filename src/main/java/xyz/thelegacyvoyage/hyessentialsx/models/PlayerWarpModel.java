package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

public final class PlayerWarpModel {

    private String name;
    private String ownerUuid;
    private String ownerName;
    private String description = "";
    private String worldId;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean publicWarp = true;
    private boolean approved = true;
    private boolean enabled = true;
    private long createdAt;
    private long updatedAt;
    private long visits;

    @SuppressWarnings("unused")
    public PlayerWarpModel() {
    }

    public PlayerWarpModel(@Nonnull String name,
                           @Nonnull String ownerUuid,
                           @Nonnull String ownerName,
                           @Nullable String description,
                           @Nullable String worldId,
                           @Nonnull String worldName,
                           double x,
                           double y,
                           double z,
                           float yaw,
                           float pitch,
                           long now) {
        this.name = normalizeName(name);
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.description = description == null ? "" : description.trim();
        this.worldId = worldId;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdAt = Math.max(0L, now);
        this.updatedAt = Math.max(0L, now);
    }

    @Nonnull
    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(@Nullable String name) {
        this.name = normalizeName(name);
    }

    @Nonnull
    public String getOwnerUuid() {
        return ownerUuid == null ? "" : ownerUuid;
    }

    public void setOwnerUuid(@Nullable String ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Nonnull
    public String getOwnerName() {
        return ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName;
    }

    public void setOwnerName(@Nullable String ownerName) {
        this.ownerName = ownerName;
    }

    @Nonnull
    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description == null ? "" : description.trim();
        touch();
    }

    @Nullable
    public String getWorldId() {
        return worldId;
    }

    public void setWorldId(@Nullable String worldId) {
        this.worldId = worldId;
    }

    @Nonnull
    public String getWorldName() {
        return worldName == null ? "" : worldName;
    }

    public void setWorldName(@Nullable String worldName) {
        this.worldName = worldName;
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

    public boolean isPublicWarp() {
        return publicWarp;
    }

    public void setPublicWarp(boolean publicWarp) {
        this.publicWarp = publicWarp;
        touch();
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
        touch();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        touch();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public long getVisits() {
        return Math.max(0L, visits);
    }

    public void incrementVisits() {
        visits = Math.max(0L, visits) + 1L;
        touch();
    }

    public void updateLocation(@Nullable String worldId,
                               @Nonnull String worldName,
                               double x,
                               double y,
                               double z,
                               float yaw,
                               float pitch) {
        this.worldId = worldId;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        touch();
    }

    public void sanitize() {
        name = normalizeName(name);
        if (ownerUuid == null) ownerUuid = "";
        if (ownerName == null || ownerName.isBlank()) ownerName = "Unknown";
        if (description == null) description = "";
        if (worldName == null) worldName = "";
        createdAt = Math.max(0L, createdAt);
        updatedAt = Math.max(createdAt, updatedAt);
        visits = Math.max(0L, visits);
    }

    private void touch() {
        updatedAt = System.currentTimeMillis();
    }

    @Nonnull
    public static String normalizeName(@Nullable String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
