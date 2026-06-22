package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class EconomyAuditEntryModel {

    private long timestamp;
    private String action;
    private String actor;
    private String target;
    private long amount;
    private long balanceAfter;
    private String detail;

    @SuppressWarnings("unused")
    public EconomyAuditEntryModel() {}

    public EconomyAuditEntryModel(long timestamp,
                                  @Nonnull String action,
                                  @Nonnull String actor,
                                  @Nonnull String target,
                                  long amount,
                                  long balanceAfter,
                                  @Nullable String detail) {
        this.timestamp = Math.max(0L, timestamp);
        this.action = action;
        this.actor = actor;
        this.target = target;
        this.amount = Math.max(0L, amount);
        this.balanceAfter = Math.max(0L, balanceAfter);
        this.detail = detail;
    }

    public long getTimestamp() {
        return Math.max(0L, timestamp);
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = Math.max(0L, timestamp);
    }

    @Nonnull
    public String getAction() {
        return action == null ? "" : action;
    }

    public void setAction(@Nonnull String action) {
        this.action = action;
    }

    @Nonnull
    public String getActor() {
        return actor == null ? "" : actor;
    }

    public void setActor(@Nonnull String actor) {
        this.actor = actor;
    }

    @Nonnull
    public String getTarget() {
        return target == null ? "" : target;
    }

    public void setTarget(@Nonnull String target) {
        this.target = target;
    }

    public long getAmount() {
        return Math.max(0L, amount);
    }

    public void setAmount(long amount) {
        this.amount = Math.max(0L, amount);
    }

    public long getBalanceAfter() {
        return Math.max(0L, balanceAfter);
    }

    public void setBalanceAfter(long balanceAfter) {
        this.balanceAfter = Math.max(0L, balanceAfter);
    }

    @Nullable
    public String getDetail() {
        return detail;
    }

    public void setDetail(@Nullable String detail) {
        this.detail = detail;
    }
}
