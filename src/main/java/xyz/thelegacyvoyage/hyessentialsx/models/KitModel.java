package xyz.thelegacyvoyage.hyessentialsx.models;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class KitModel {

    private String name;
    private int cooldownSeconds;
    private List<KitItemModel> items = new ArrayList<>();

    @SuppressWarnings("unused")
    public KitModel() {}

    public KitModel(@Nonnull String name, int cooldownSeconds, @Nonnull List<KitItemModel> items) {
        this.name = Objects.requireNonNull(name, "name");
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.items = new ArrayList<>(items);
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    @Nonnull
    public List<KitItemModel> getItems() {
        return items;
    }
}

