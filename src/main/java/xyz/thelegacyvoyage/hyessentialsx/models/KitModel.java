package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class KitModel {

    private String name;
    private int cooldownSeconds;
    private int maxUses;
    private int sortOrder;
    private List<KitItemModel> items = new ArrayList<>();

    @SuppressWarnings("unused")
    public KitModel() {}

    public KitModel(@Nonnull String name, int cooldownSeconds, @Nonnull List<KitItemModel> items) {
        this(name, cooldownSeconds, 0, 0, items);
    }

    public KitModel(@Nonnull String name, int cooldownSeconds, int maxUses, @Nonnull List<KitItemModel> items) {
        this(name, cooldownSeconds, maxUses, 0, items);
    }

    public KitModel(@Nonnull String name, int cooldownSeconds, int maxUses, int sortOrder, @Nonnull List<KitItemModel> items) {
        this.name = Objects.requireNonNull(name, "name");
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.maxUses = Math.max(0, maxUses);
        this.sortOrder = Math.max(0, sortOrder);
        this.items = new ArrayList<>(items);
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public int getMaxUses() {
        return Math.max(0, maxUses);
    }

    public int getSortOrder() {
        return Math.max(0, sortOrder);
    }

    @Nonnull
    public List<KitItemModel> getItems() {
        return items;
    }
}

