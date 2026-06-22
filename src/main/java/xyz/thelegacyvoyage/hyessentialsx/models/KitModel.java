package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class KitModel {

    private String name;
    private int cooldownSeconds;
    private int maxUses;
    private List<KitItemModel> items = new ArrayList<>();

    @SuppressWarnings("unused")
    public KitModel() {}

    public KitModel(@Nonnull String name, int cooldownSeconds, @Nonnull List<KitItemModel> items) {
        this(name, cooldownSeconds, 0, items);
    }

    public KitModel(@Nonnull String name, int cooldownSeconds, int maxUses, @Nonnull List<KitItemModel> items) {
        this.name = Objects.requireNonNull(name, "name");
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.maxUses = Math.max(0, maxUses);
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

    @Nonnull
    public List<KitItemModel> getItems() {
        return items;
    }
}

