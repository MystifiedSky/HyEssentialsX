package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class CommandRuleModel {

    private boolean enabled = true;
    private int cooldownSeconds = 0;
    private int warmupSeconds = 0;
    private boolean cancelWarmupOnMove = true;
    private long price = 0L;
    private List<String> blacklistedWorlds = new ArrayList<>();
    private List<Reduction> reductions = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCooldownSeconds() {
        return Math.max(0, cooldownSeconds);
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    public int getWarmupSeconds() {
        return Math.max(0, warmupSeconds);
    }

    public void setWarmupSeconds(int warmupSeconds) {
        this.warmupSeconds = Math.max(0, warmupSeconds);
    }

    public boolean isCancelWarmupOnMove() {
        return cancelWarmupOnMove;
    }

    public void setCancelWarmupOnMove(boolean cancelWarmupOnMove) {
        this.cancelWarmupOnMove = cancelWarmupOnMove;
    }

    public long getPrice() {
        return Math.max(0L, price);
    }

    public void setPrice(long price) {
        this.price = Math.max(0L, price);
    }

    @Nonnull
    public List<String> getBlacklistedWorlds() {
        return blacklistedWorlds == null ? List.of() : blacklistedWorlds;
    }

    public void setBlacklistedWorlds(@Nonnull List<String> blacklistedWorlds) {
        this.blacklistedWorlds = new ArrayList<>(blacklistedWorlds);
    }

    @Nonnull
    public List<Reduction> getReductions() {
        return reductions == null ? List.of() : reductions;
    }

    public void setReductions(@Nonnull List<Reduction> reductions) {
        this.reductions = new ArrayList<>(reductions);
    }

    public static final class Reduction {
        private String permission = "";
        private int cooldownReductionPercent = 0;
        private int warmupReductionPercent = 0;
        private int priceReductionPercent = 0;
        private int cooldownSeconds = -1;
        private int warmupSeconds = -1;
        private long price = -1L;

        @Nonnull
        public String getPermission() {
            return permission == null ? "" : permission.trim();
        }

        public void setPermission(@Nonnull String permission) {
            this.permission = permission;
        }

        public int getCooldownReductionPercent() {
            return clampPercent(cooldownReductionPercent);
        }

        public void setCooldownReductionPercent(int cooldownReductionPercent) {
            this.cooldownReductionPercent = clampPercent(cooldownReductionPercent);
        }

        public int getWarmupReductionPercent() {
            return clampPercent(warmupReductionPercent);
        }

        public void setWarmupReductionPercent(int warmupReductionPercent) {
            this.warmupReductionPercent = clampPercent(warmupReductionPercent);
        }

        public int getPriceReductionPercent() {
            return clampPercent(priceReductionPercent);
        }

        public void setPriceReductionPercent(int priceReductionPercent) {
            this.priceReductionPercent = clampPercent(priceReductionPercent);
        }

        public int getCooldownSeconds() {
            return cooldownSeconds;
        }

        public void setCooldownSeconds(int cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds < 0 ? -1 : cooldownSeconds;
        }

        public int getWarmupSeconds() {
            return warmupSeconds;
        }

        public void setWarmupSeconds(int warmupSeconds) {
            this.warmupSeconds = warmupSeconds < 0 ? -1 : warmupSeconds;
        }

        public long getPrice() {
            return price;
        }

        public void setPrice(long price) {
            this.price = price < 0L ? -1L : price;
        }

        private static int clampPercent(int value) {
            return Math.max(0, Math.min(100, value));
        }
    }
}
