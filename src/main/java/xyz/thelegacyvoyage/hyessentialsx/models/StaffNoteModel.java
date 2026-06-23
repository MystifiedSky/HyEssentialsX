package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nullable;

public final class StaffNoteModel {

    private String id;
    private String actor;
    private String note;
    private long createdAt;

    @SuppressWarnings("unused")
    public StaffNoteModel() {
    }

    public StaffNoteModel(@Nullable String id,
                          @Nullable String actor,
                          @Nullable String note,
                          long createdAt) {
        this.id = id;
        this.actor = actor;
        this.note = note;
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
    public String getActor() {
        return actor;
    }

    public void setActor(@Nullable String actor) {
        this.actor = actor;
    }

    @Nullable
    public String getNote() {
        return note;
    }

    public void setNote(@Nullable String note) {
        this.note = note;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = Math.max(0L, createdAt);
    }
}
