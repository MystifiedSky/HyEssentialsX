package xyz.thelegacyvoyage.hyessentialsx.ui.scoreboard;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

public class ScoreboardEditEventData {

    public static final BuilderCodec<ScoreboardEditEventData> CODEC = BuilderCodec.builder(ScoreboardEditEventData.class, ScoreboardEditEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
            .append(new KeyedCodec<>("LineIndex", Codec.STRING), (e, v) -> e.lineIndex = v, e -> e.lineIndex).add()
            .append(new KeyedCodec<>("@NewText", Codec.STRING), (e, v) -> e.newText = v, e -> e.newText).add()
            .append(new KeyedCodec<>("@NewLineInput", Codec.STRING), (e, v) -> e.newLineInput = v, e -> e.newLineInput).add()
            .build();

    @Nullable
    private String action;
    @Nullable
    private String lineIndex;
    @Nullable
    private String newText;
    @Nullable
    private String newLineInput;

    @Nullable
    public String getAction() {
        return action;
    }

    public int getLineIndexInt() {
        if (lineIndex == null) {
            return -1;
        }
        try {
            return Integer.parseInt(lineIndex);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Nullable
    public String getNewLineText() {
        if (newText != null) {
            return newText;
        }
        return newLineInput;
    }
}
