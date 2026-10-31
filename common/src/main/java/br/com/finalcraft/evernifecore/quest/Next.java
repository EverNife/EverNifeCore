package br.com.finalcraft.evernifecore.quest;

import jakarta.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;

/** Where a stage (or an objective that closed an {@code any} group) leads: the next stage, a named one, or the end. */
public final class Next {

    public enum Kind { NEXT_IN_LIST, GOTO, COMPLETE, FAIL }

    public static final Next NEXT_IN_LIST = new Next(Kind.NEXT_IN_LIST, null);
    public static final Next COMPLETE = new Next(Kind.COMPLETE, null);
    public static final Next FAIL = new Next(Kind.FAIL, null);

    private final Kind kind;
    private final @Nullable String stageId;

    private Next(Kind kind, @Nullable String stageId) {
        this.kind = kind;
        this.stageId = stageId;
    }

    public static Next goTo(String stageId) {
        return new Next(Kind.GOTO, Objects.requireNonNull(stageId, "stageId"));
    }

    /** {@code COMPLETE}, {@code FAIL}, {@code NEXT} (any case), or the id of a stage. */
    public static Next parse(String text) {
        String trimmed = text.trim();
        switch (trimmed.toUpperCase(Locale.ROOT)) {
            case "COMPLETE":
                return COMPLETE;
            case "FAIL":
                return FAIL;
            case "NEXT":
                return NEXT_IN_LIST;
            default:
                return goTo(trimmed);
        }
    }

    public Kind getKind() {
        return kind;
    }

    /** The stage a {@link Kind#GOTO} names; null otherwise. */
    public @Nullable String getStageId() {
        return stageId;
    }

    @Override
    public String toString() {
        return kind == Kind.GOTO ? stageId : kind.name();
    }
}
