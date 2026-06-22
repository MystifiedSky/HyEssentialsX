package xyz.thelegacyvoyage.hyessentialsx.ui.hologram;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

public class HologramEditorEventData {

    public static final BuilderCodec<HologramEditorEventData> CODEC = BuilderCodec.builder(HologramEditorEventData.class, HologramEditorEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
            .append(new KeyedCodec<>("LineIndex", Codec.STRING), (e, v) -> e.lineIndex = v, e -> e.lineIndex).add()
            .append(new KeyedCodec<>("LineText", Codec.STRING), (e, v) -> e.lineText = v, e -> e.lineText).add()
            .append(new KeyedCodec<>("NewLineText", Codec.STRING), (e, v) -> e.newLineText = v, e -> e.newLineText).add()
            .append(new KeyedCodec<>("@NewLineInput", Codec.STRING), (e, v) -> e.newLineInput = v, e -> e.newLineInput).add()
            .append(new KeyedCodec<>("@NewText", Codec.STRING), (e, v) -> e.newText = v, e -> e.newText).add()
            .append(new KeyedCodec<>("@LineText", Codec.STRING), (e, v) -> e.editedLineText = v, e -> e.editedLineText).add()
            .append(new KeyedCodec<>("Close", Codec.STRING), (e, v) -> e.close = "true".equalsIgnoreCase(v), e -> e.close != null && e.close ? "true" : null).add()
            .append(new KeyedCodec<>("@X", Codec.STRING), (e, v) -> e.posX = v, e -> e.posX).add()
            .append(new KeyedCodec<>("@Y", Codec.STRING), (e, v) -> e.posY = v, e -> e.posY).add()
            .append(new KeyedCodec<>("@Z", Codec.STRING), (e, v) -> e.posZ = v, e -> e.posZ).add()
            .append(new KeyedCodec<>("Dir", Codec.STRING), (e, v) -> e.direction = v, e -> e.direction).add()
            .build();

    @Nullable
    private String action;
    @Nullable
    private String lineIndex;
    @Nullable
    private String lineText;
    @Nullable
    private String newLineText;
    @Nullable
    private String newLineInput;
    @Nullable
    private String newText;
    @Nullable
    private String editedLineText;
    @Nullable
    private Boolean close;
    @Nullable
    private String posX;
    @Nullable
    private String posY;
    @Nullable
    private String posZ;
    @Nullable
    private String direction;

    @Nullable
    public String getAction() {
        return action;
    }

    @Nullable
    public String getLineIndex() {
        return lineIndex;
    }

    @Nullable
    public String getLineText() {
        return lineText;
    }

    @Nullable
    public String getNewLineText() {
        if (newText != null) return newText;
        if (newLineText != null) return newLineText;
        return newLineInput != null ? newLineInput : null;
    }

    @Nullable
    public String getNewLineInput() {
        return newLineInput;
    }

    @Nullable
    public String getEditedLineText() {
        return editedLineText;
    }

    public boolean isCloseRequested() {
        return close != null && close;
    }

    public int getLineIndexInt() {
        if (lineIndex == null) return -1;
        try {
            return Integer.parseInt(lineIndex);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Nullable
    public String getPosX() {
        return posX;
    }

    @Nullable
    public String getPosY() {
        return posY;
    }

    @Nullable
    public String getPosZ() {
        return posZ;
    }

    public double getPosXDouble(double defaultValue) {
        if (posX == null || posX.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(posX);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public double getPosYDouble(double defaultValue) {
        if (posY == null || posY.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(posY);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public double getPosZDouble(double defaultValue) {
        if (posZ == null || posZ.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(posZ);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Nullable
    public String getDirection() {
        return direction;
    }
}

