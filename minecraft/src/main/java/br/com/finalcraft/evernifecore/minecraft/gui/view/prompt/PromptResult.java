package br.com.finalcraft.evernifecore.minecraft.gui.view.prompt;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * How a prompt ended. Exactly one of the four, and only {@link Kind#VALUE} carries something.
 *
 * @param <T> what the prompt was asking for
 */
public final class PromptResult<T> {

    public enum Kind {
        /** The player answered, and the answer parsed. */
        VALUE,
        /** The player said the cancel word. */
        CANCELLED,
        /** Nobody answered in time. */
        TIMEOUT,
        /** The player left while being asked. */
        QUIT
    }

    private final Kind kind;
    private final T value;

    private PromptResult(Kind kind, T value) {
        this.kind = kind;
        this.value = value;
    }

    @Nonnull
    public static <T> PromptResult<T> value(@Nullable T value) {
        return new PromptResult<>(Kind.VALUE, value);
    }

    @Nonnull
    public static <T> PromptResult<T> cancelled() {
        return new PromptResult<>(Kind.CANCELLED, null);
    }

    @Nonnull
    public static <T> PromptResult<T> timeout() {
        return new PromptResult<>(Kind.TIMEOUT, null);
    }

    @Nonnull
    public static <T> PromptResult<T> quit() {
        return new PromptResult<>(Kind.QUIT, null);
    }

    @Nonnull
    public Kind getKind() {
        return kind;
    }

    /** What the player answered, or {@code null} on any ending other than {@link Kind#VALUE}. */
    @Nullable
    public T getValue() {
        return value;
    }

    public boolean hasValue() {
        return kind == Kind.VALUE;
    }

    @Override
    public String toString() {
        return "PromptResult{" + kind + (kind == Kind.VALUE ? ", " + value : "") + "}";
    }

}
