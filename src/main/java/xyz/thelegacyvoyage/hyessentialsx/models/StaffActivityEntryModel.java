package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nullable;

public final class StaffActivityEntryModel {

    private String id;
    private long createdAt;
    private String actor;
    private String action;
    private String targetUuid;
    private String targetName;
    private String detail;

    @SuppressWarnings("unused")
    public StaffActivityEntryModel() {
    }

    public StaffActivityEntryModel(@Nullable String id,
                                   long createdAt,
                                   @Nullable String actor,
                                   @Nullable String action,
                                   @Nullable String targetUuid,
                                   @Nullable String targetName,
                                   @Nullable String detail) {
        this.id = id;
        this.createdAt = Math.max(0L, createdAt);
        this.actor = actor;
        this.action = action;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.detail = detail;
    }

    @Nullable
    public String getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public String getActor() {
        return actor;
    }

    @Nullable
    public String getAction() {
        return action;
    }

    @Nullable
    public String getTargetUuid() {
        return targetUuid;
    }

    @Nullable
    public String getTargetName() {
        return targetName;
    }

    @Nullable
    public String getDetail() {
        return detail;
    }
}
