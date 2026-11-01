package br.com.finalcraft.evernifecore.screen.prompt;

import jakarta.annotation.Nonnull;

/**
 * What {@link PromptSession#answer(String)} made of a typed line: taken, or refused with the text the player reads.
 */
public final class PromptReply {

    private static final PromptReply ACCEPTED = new PromptReply(true, "");

    private final boolean accepted;
    private final String message;

    private PromptReply(boolean accepted, String message) {
        this.accepted = accepted;
        this.message = message;
    }

    /** The question is over: the channel takes its window away. */
    @Nonnull
    public static PromptReply accepted() {
        return ACCEPTED;
    }

    /** The question stays open; {@code message} (never blank) tells the player why. */
    @Nonnull
    public static PromptReply refused(@Nonnull String message) {
        return new PromptReply(false, message);
    }

    public boolean isAccepted() {
        return accepted;
    }

    /** Why it was refused; {@code ""} when accepted. */
    @Nonnull
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return accepted ? "PromptReply{accepted}" : "PromptReply{refused: " + message + "}";
    }

}
