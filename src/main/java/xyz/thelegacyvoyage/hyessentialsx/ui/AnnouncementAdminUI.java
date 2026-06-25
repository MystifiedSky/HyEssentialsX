package xyz.thelegacyvoyage.hyessentialsx.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import xyz.thelegacyvoyage.hyessentialsx.managers.AutoBroadcastManager;
import xyz.thelegacyvoyage.hyessentialsx.models.AnnouncementPresetModel;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class AnnouncementAdminUI extends InteractiveCustomUIPage<AnnouncementAdminUI.UIEventData> {

    private static final String LAYOUT = "hyessentialsx/AnnouncementAdmin.ui";
    private static final int ROWS = 8;

    private final PlayerRef playerRef;
    private final AutoBroadcastManager manager;

    private int page = 0;
    @Nullable
    private String selectedName;
    private String nameInput = "";
    private String permissionInput = "";
    private String chatInput = "";
    private String notifyTitleInput = "";
    private String notifyMessageInput = "";
    private String notifyStyleInput = "Default";
    private String titlePrimaryInput = "";
    private String titleSecondaryInput = "";
    private String soundInput = "";
    private String particleInput = "";
    private String serverCommandInput = "";
    private String playerCommandInput = "";
    private String status = "Ready.";

    public AnnouncementAdminUI(@Nonnull PlayerRef playerRef, @Nonnull AutoBroadcastManager manager) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.manager = manager;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        bind(evt);
        rebuild(cmd);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {
        captureInputs(data);
        if (data.action == null) {
            return;
        }
        switch (data.action) {
            case "Close" -> close();
            case "Prev" -> {
                page = Math.max(0, page - 1);
                refresh();
            }
            case "Next" -> {
                int max = Math.max(0, (manager.presets().size() - 1) / ROWS);
                page = Math.min(max, page + 1);
                refresh();
            }
            case "New" -> {
                selectedName = null;
                nameInput = "new_announcement";
                permissionInput = "";
                chatInput = "<#38BDF8>[Announcement]</#38BDF8> <#E2E8F0>Edit me.</#E2E8F0>";
                notifyTitleInput = "";
                notifyMessageInput = "";
                titlePrimaryInput = "";
                titleSecondaryInput = "";
                soundInput = "";
                particleInput = "";
                serverCommandInput = "";
                playerCommandInput = "";
                status = "Editing new preset.";
                refresh();
            }
            case "Save" -> save();
            case "Send" -> send();
            case "Toggle" -> toggle();
            case "Delete" -> delete();
            case "Random" -> {
                manager.setRandom(true);
                status = "Scheduler order set to random.";
                refresh();
            }
            case "Sequential" -> {
                manager.setRandom(false);
                status = "Scheduler order set to sequential.";
                refresh();
            }
            case "NotifyDefault" -> setNotifyStyle("Default");
            case "NotifySuccess" -> setNotifyStyle("Success");
            case "NotifyWarning" -> setNotifyStyle("Warning");
            case "NotifyDanger" -> setNotifyStyle("Danger");
            case "SoundNone" -> setSound("", "Sound cleared.");
            case "SoundReward" -> setSound("15", "Sound preset: Reward.");
            case "SoundWelcome" -> setSound("20", "Sound preset: Welcome.");
            case "SoundEvent" -> setSound("24", "Sound preset: Event.");
            case "ParticleNone" -> setParticle("", "Particle cleared.");
            case "ParticleHeal" -> setParticle("Aura_Heal", "Particle preset: Aura Heal.");
            case "ParticleExplosion" -> setParticle("Explosion_Big", "Particle preset: Explosion.");
            case "ParticleRare" -> setParticle("Drop_Rare", "Particle preset: Rare Drop.");
            case "ParticleSparkles" -> setParticle("Dust_Sparkles", "Particle preset: Sparkles.");
            default -> {
                if (data.action.startsWith("Select")) {
                    select(data.action.substring("Select".length()));
                }
            }
        }
    }

    public void open(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        player.getPageManager().openCustomPage(ref, store, this);
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        bind(evt);
        rebuild(cmd);
        sendUpdate(cmd, evt, false);
    }

    private void bind(@Nonnull UIEventBuilder evt) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "Close"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevButton", EventData.of("Action", "Prev"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton", EventData.of("Action", "Next"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NewButton", EventData.of("Action", "New"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", inputEvent("Save"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SendButton", inputEvent("Send"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleButton", inputEvent("Toggle"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteButton", EventData.of("Action", "Delete"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#RandomButton", EventData.of("Action", "Random"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SequentialButton", EventData.of("Action", "Sequential"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NotifyDefaultButton", inputEvent("NotifyDefault"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NotifySuccessButton", inputEvent("NotifySuccess"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NotifyWarningButton", inputEvent("NotifyWarning"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NotifyDangerButton", inputEvent("NotifyDanger"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SoundNoneButton", inputEvent("SoundNone"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SoundRewardButton", inputEvent("SoundReward"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SoundWelcomeButton", inputEvent("SoundWelcome"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SoundEventButton", inputEvent("SoundEvent"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ParticleNoneButton", inputEvent("ParticleNone"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ParticleHealButton", inputEvent("ParticleHeal"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ParticleExplosionButton", inputEvent("ParticleExplosion"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ParticleRareButton", inputEvent("ParticleRare"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ParticleSparklesButton", inputEvent("ParticleSparkles"), false);
        for (int i = 0; i < ROWS; i++) {
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#PresetRow" + i,
                    EventData.of("Action", "Select" + i), false);
        }
    }

    @Nonnull
    private EventData inputEvent(@Nonnull String action) {
        return EventData.of("Action", action)
                .append("@Name", "#NameInput.Value")
                .append("@Permission", "#PermissionInput.Value")
                .append("@Chat", "#ChatInput.Value")
                .append("@NotifyTitle", "#NotifyTitleInput.Value")
                .append("@NotifyMessage", "#NotifyMessageInput.Value")
                .append("@NotifyStyle", "#NotifyStyleInput.Value")
                .append("@TitlePrimary", "#TitlePrimaryInput.Value")
                .append("@TitleSecondary", "#TitleSecondaryInput.Value")
                .append("@Sound", "#SoundInput.Value")
                .append("@Particle", "#ParticleInput.Value")
                .append("@ServerCommand", "#ServerCommandInput.Value")
                .append("@PlayerCommand", "#PlayerCommandInput.Value");
    }

    private void rebuild(@Nonnull UICommandBuilder cmd) {
        List<AnnouncementPresetModel> presets = manager.presets();
        int maxPage = Math.max(0, (presets.size() - 1) / ROWS);
        page = Math.min(page, maxPage);
        cmd.set("#StatusText.Text", status);
        cmd.set("#PageText.Text", "Page " + (page + 1) + "/" + (maxPage + 1));
        for (int i = 0; i < ROWS; i++) {
            int index = page * ROWS + i;
            if (index >= presets.size()) {
                cmd.set("#PresetRow" + i + ".Text", "-");
                continue;
            }
            AnnouncementPresetModel preset = presets.get(index);
            String marker = preset.getName().equalsIgnoreCase(selectedName == null ? "" : selectedName) ? "* " : "";
            cmd.set("#PresetRow" + i + ".Text", marker + preset.getName() + "  " + (preset.isEnabled() ? "ON" : "OFF"));
        }
        cmd.set("#NameInput.Value", nameInput);
        cmd.set("#PermissionInput.Value", permissionInput);
        cmd.set("#ChatInput.Value", chatInput);
        cmd.set("#NotifyTitleInput.Value", notifyTitleInput);
        cmd.set("#NotifyMessageInput.Value", notifyMessageInput);
        cmd.set("#NotifyStyleInput.Value", notifyStyleInput);
        cmd.set("#TitlePrimaryInput.Value", titlePrimaryInput);
        cmd.set("#TitleSecondaryInput.Value", titleSecondaryInput);
        cmd.set("#SoundInput.Value", soundInput);
        cmd.set("#ParticleInput.Value", particleInput);
        cmd.set("#ServerCommandInput.Value", serverCommandInput);
        cmd.set("#PlayerCommandInput.Value", playerCommandInput);
    }

    private void captureInputs(@Nonnull UIEventData data) {
        if (data.name != null) nameInput = data.name.trim();
        if (data.permission != null) permissionInput = data.permission.trim();
        if (data.chat != null) chatInput = data.chat.trim();
        if (data.notifyTitle != null) notifyTitleInput = data.notifyTitle.trim();
        if (data.notifyMessage != null) notifyMessageInput = data.notifyMessage.trim();
        if (data.notifyStyle != null) notifyStyleInput = data.notifyStyle.trim();
        if (data.titlePrimary != null) titlePrimaryInput = data.titlePrimary.trim();
        if (data.titleSecondary != null) titleSecondaryInput = data.titleSecondary.trim();
        if (data.sound != null) soundInput = data.sound.trim();
        if (data.particle != null) particleInput = data.particle.trim();
        if (data.serverCommand != null) serverCommandInput = data.serverCommand.trim();
        if (data.playerCommand != null) playerCommandInput = data.playerCommand.trim();
    }

    private void select(@Nonnull String indexRaw) {
        try {
            int row = Integer.parseInt(indexRaw);
            int index = page * ROWS + row;
            List<AnnouncementPresetModel> presets = manager.presets();
            if (index < 0 || index >= presets.size()) return;
            load(presets.get(index));
            status = "Selected " + selectedName + ".";
            refresh();
        } catch (NumberFormatException ignored) {
        }
    }

    private void load(@Nonnull AnnouncementPresetModel preset) {
        selectedName = preset.getName();
        nameInput = preset.getName();
        permissionInput = preset.getPermission();
        chatInput = String.join(" | ", preset.getChatMessages());
        AnnouncementPresetModel.NotificationAction notification = preset.getNotification();
        notifyTitleInput = notification == null ? "" : notification.getTitle();
        notifyMessageInput = notification == null ? "" : notification.getMessage();
        notifyStyleInput = notification == null ? "Default" : notification.getStyle();
        AnnouncementPresetModel.TitleAction title = preset.getTitle();
        titlePrimaryInput = title == null ? "" : title.getPrimary();
        titleSecondaryInput = title == null ? "" : title.getSecondary();
        AnnouncementPresetModel.SoundAction sound = preset.getSound();
        soundInput = sound == null || sound.getSoundEventIndex() < 0 ? "" : String.valueOf(sound.getSoundEventIndex());
        AnnouncementPresetModel.ParticleAction particle = preset.getParticle();
        particleInput = particle == null ? "" : particle.getParticleSystemId();
        serverCommandInput = String.join(" | ", preset.getServerCommands());
        playerCommandInput = String.join(" | ", preset.getPlayerCommands());
    }

    private void save() {
        if (nameInput.isBlank()) {
            status = "Name is required.";
            refresh();
            return;
        }
        AnnouncementPresetModel existing = selectedName == null ? null : manager.getPreset(selectedName);
        AnnouncementPresetModel preset = existing == null ? new AnnouncementPresetModel(nameInput) : existing;
        preset.setName(nameInput);
        preset.setPermission(permissionInput);
        preset.setChatMessages(splitPipe(chatInput));
        if (!notifyTitleInput.isBlank() || !notifyMessageInput.isBlank()) {
            AnnouncementPresetModel.NotificationAction notification = preset.getNotification();
            if (notification == null) notification = new AnnouncementPresetModel.NotificationAction();
            notification.setTitle(notifyTitleInput);
            notification.setMessage(notifyMessageInput);
            notification.setStyle(notifyStyleInput.isBlank() ? "Default" : notifyStyleInput);
            preset.setNotification(notification);
        } else {
            preset.setNotification(null);
        }
        if (!titlePrimaryInput.isBlank() || !titleSecondaryInput.isBlank()) {
            AnnouncementPresetModel.TitleAction title = preset.getTitle();
            if (title == null) title = new AnnouncementPresetModel.TitleAction();
            title.setPrimary(titlePrimaryInput);
            title.setSecondary(titleSecondaryInput);
            preset.setTitle(title);
        } else {
            preset.setTitle(null);
        }
        if (!soundInput.isBlank()) {
            try {
                AnnouncementPresetModel.SoundAction sound = new AnnouncementPresetModel.SoundAction();
                sound.setSoundEventIndex(Integer.parseInt(soundInput));
                preset.setSound(sound);
            } catch (NumberFormatException e) {
                status = "Sound must be a numeric event index.";
                refresh();
                return;
            }
        } else {
            preset.setSound(null);
        }
        if (!particleInput.isBlank()) {
            AnnouncementPresetModel.ParticleAction particle = new AnnouncementPresetModel.ParticleAction();
            particle.setParticleSystemId(particleInput);
            preset.setParticle(particle);
        } else {
            preset.setParticle(null);
        }
        preset.setServerCommands(splitPipe(serverCommandInput));
        preset.setPlayerCommands(splitPipe(playerCommandInput));
        manager.savePreset(preset);
        selectedName = preset.getName();
        status = "Saved " + selectedName + ".";
        notify("Announcement preset saved.", NotificationStyle.Success);
        refresh();
    }

    private void send() {
        String target = selectedName != null ? selectedName : nameInput;
        if (target == null || target.isBlank()) {
            status = "Select or save a preset first.";
            refresh();
            return;
        }
        status = manager.trigger(target) ? "Sent " + target + "." : "Preset not found.";
        refresh();
    }

    private void toggle() {
        String target = selectedName != null ? selectedName : nameInput;
        AnnouncementPresetModel preset = manager.getPreset(target);
        if (preset == null) {
            status = "Preset not found.";
            refresh();
            return;
        }
        preset.setEnabled(!preset.isEnabled());
        manager.savePreset(preset);
        load(preset);
        status = preset.getName() + " is now " + (preset.isEnabled() ? "enabled." : "disabled.");
        refresh();
    }

    private void delete() {
        String target = selectedName;
        if (target == null || target.isBlank()) {
            status = "Select a preset first.";
            refresh();
            return;
        }
        manager.deletePreset(target);
        selectedName = null;
        nameInput = "";
        status = "Deleted " + target + ".";
        refresh();
    }

    private void setNotifyStyle(@Nonnull String style) {
        notifyStyleInput = style;
        status = "Popup style set to " + style + ".";
        refresh();
    }

    private void setSound(@Nonnull String value, @Nonnull String message) {
        soundInput = value;
        status = message;
        refresh();
    }

    private void setParticle(@Nonnull String value, @Nonnull String message) {
        particleInput = value;
        status = message;
        refresh();
    }

    @Nonnull
    private List<String> splitPipe(@Nonnull String input) {
        List<String> out = new ArrayList<>();
        for (String part : input.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private void notify(@Nonnull String text, @Nonnull NotificationStyle style) {
        NotificationUtil.sendNotification(playerRef.getPacketHandler(), Messages.m(text), style);
    }

    public static final class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec
                .builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("@Name", Codec.STRING), (d, v) -> d.name = v, d -> d.name).add()
                .append(new KeyedCodec<>("@Permission", Codec.STRING), (d, v) -> d.permission = v, d -> d.permission).add()
                .append(new KeyedCodec<>("@Chat", Codec.STRING), (d, v) -> d.chat = v, d -> d.chat).add()
                .append(new KeyedCodec<>("@NotifyTitle", Codec.STRING), (d, v) -> d.notifyTitle = v, d -> d.notifyTitle).add()
                .append(new KeyedCodec<>("@NotifyMessage", Codec.STRING), (d, v) -> d.notifyMessage = v, d -> d.notifyMessage).add()
                .append(new KeyedCodec<>("@NotifyStyle", Codec.STRING), (d, v) -> d.notifyStyle = v, d -> d.notifyStyle).add()
                .append(new KeyedCodec<>("@TitlePrimary", Codec.STRING), (d, v) -> d.titlePrimary = v, d -> d.titlePrimary).add()
                .append(new KeyedCodec<>("@TitleSecondary", Codec.STRING), (d, v) -> d.titleSecondary = v, d -> d.titleSecondary).add()
                .append(new KeyedCodec<>("@Sound", Codec.STRING), (d, v) -> d.sound = v, d -> d.sound).add()
                .append(new KeyedCodec<>("@Particle", Codec.STRING), (d, v) -> d.particle = v, d -> d.particle).add()
                .append(new KeyedCodec<>("@ServerCommand", Codec.STRING), (d, v) -> d.serverCommand = v, d -> d.serverCommand).add()
                .append(new KeyedCodec<>("@PlayerCommand", Codec.STRING), (d, v) -> d.playerCommand = v, d -> d.playerCommand).add()
                .build();

        private String action;
        private String name;
        private String permission;
        private String chat;
        private String notifyTitle;
        private String notifyMessage;
        private String notifyStyle;
        private String titlePrimary;
        private String titleSecondary;
        private String sound;
        private String particle;
        private String serverCommand;
        private String playerCommand;
    }
}
