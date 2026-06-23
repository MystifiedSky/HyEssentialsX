package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nullable;

public final class StaffCaseModel {

    private String id;
    private String type;
    private String actor;
    private String detail;
    private long createdAt;

    @SuppressWarnings("unused")
    public StaffCaseModel() {
    }

    public StaffCaseModel(@Nullable String id,
                          @Nullable String type,
                          @Nullable String actor,
                          @Nullable String detail,
                          long createdAt) {
        this.id = id;
        this.type = type;
        this.actor = actor;
        this.detail = detail;
        this.createdAt = Math.max(0L, createdAt);
    }

    @Nullable
    public String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    @Nullable
    public String getType() {
        return type;
    }

    public void setType(@Nullable String type) {
        this.type = type;
    }

    @Nullable
    public String getActor() {
        return actor;
    }

    public void setActor(@Nullable String actor) {
        this.actor = actor;
    }

    @Nullable
    public String getDetail() {
        return detail;
    }

    public void setDetail(@Nullable String detail) {
        this.detail = detail;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = Math.max(0L, createdAt);
    }
}
