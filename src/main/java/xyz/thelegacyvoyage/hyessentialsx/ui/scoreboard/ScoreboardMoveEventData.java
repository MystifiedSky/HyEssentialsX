package xyz.thelegacyvoyage.hyessentialsx.ui.scoreboard;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

public class ScoreboardMoveEventData {

    public static final BuilderCodec<ScoreboardMoveEventData> CODEC = BuilderCodec.builder(ScoreboardMoveEventData.class, ScoreboardMoveEventData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action)
            .build();

    @Nullable
    private String action;

    @Nullable
    public String getAction() {
        return action;
    }
}
