package br.com.finalcraft.evernifecore.screen.prompt;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * A question for a value no button can express: what is asked, how the answer is read, and the ways the asking can end
 * without one. Nothing here picks the window it goes through - {@link PromptRegistry} does, steered by
 * {@link #prefer(PromptKind)}.
 *
 * <pre>{@code
 * ctx.ask(PromptSpec.of("§eType the new price, or 'cancel':")
 *                 .parse(Double::parseDouble)
 *                 .prefer(PromptKind.ANVIL)
 *                 .timeout(Duration.ofSeconds(45))
 *                 .cancelWord("cancel"))
 *    .thenAccept(price -> auction.setPrice(price));
 * }</pre>
 *
 * @param <T> what the question is asking for - {@link #parse(PromptParser)} is what decides it
 */
public final class PromptSpec<T> {

    /** Long enough to walk to the keyboard, short enough that a screen is never set aside forever. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 60_000L;

    private final String question;
    private PromptParser<T> parser;
    private PromptKind preferredKind;
    private String initialText = "";
    private ItemSpec icon;
    private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    private Consumer<ScreenView> onTimeout;
    private Runnable onQuit;
    private String cancelWord = "";

    private PromptSpec(String question, PromptParser<T> parser) {
        this.question = question;
        this.parser = parser;
    }

    /** The question, answered by whatever the player types verbatim until {@link #parse} says otherwise. */
    @Nonnull
    public static PromptSpec<String> of(@Nonnull String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("A prompt needs a question: the screen goes away while it is out, so a "
                    + "player who was told nothing has no idea why.");
        }
        return new PromptSpec<>(question, input -> input);
    }

    /**
     * How the typed line becomes the value. Throwing refuses the attempt: the player reads the exception's message,
     * the question stays open, and the wait is not restarted.
     */
    @Nonnull
    public <R> PromptSpec<R> parse(@Nonnull PromptParser<R> parser) {
        if (parser == null) {
            throw new IllegalArgumentException("A prompt needs a parser, or none at all - leave the call out and the "
                    + "answer arrives as the String the player typed.");
        }
        PromptSpec<R> retyped = new PromptSpec<>(question, parser);
        retyped.preferredKind = preferredKind;
        retyped.initialText = initialText;
        retyped.icon = icon;
        retyped.timeoutMillis = timeoutMillis;
        retyped.onTimeout = onTimeout;
        retyped.onQuit = onQuit;
        retyped.cancelWord = cancelWord;
        return retyped;
    }

    /**
     * The window to ask through when the player's platform has it; {@code null} (the default) lets the registry pick
     * the richest one that serves the player, in {@link PromptKind} order.
     */
    @Nonnull
    public PromptSpec<T> prefer(@Nullable PromptKind kind) {
        this.preferredKind = kind;
        return this;
    }

    /** What the text field starts with, on a channel that has one (anvil, page). Chat ignores it. */
    @Nonnull
    public PromptSpec<T> initialText(@Nullable String text) {
        this.initialText = text == null ? "" : text;
        return this;
    }

    /** The item the anvil shows in its input slot; {@code null} (the default) is paper. Other channels ignore it. */
    @Nonnull
    public PromptSpec<T> icon(@Nullable ItemSpec icon) {
        this.icon = icon;
        return this;
    }

    /** How long to wait. {@link #DEFAULT_TIMEOUT_MILLIS} when it is not said, and never zero. */
    @Nonnull
    public PromptSpec<T> timeout(@Nonnull Duration timeout) {
        long millis = timeout == null ? 0L : timeout.toMillis();
        if (millis <= 0L) {
            throw new IllegalArgumentException("A prompt has to end on its own: with no timeout, a player who simply "
                    + "walks away leaves their screen set aside for good. Give it a duration, or leave the call out for "
                    + "the default of " + (DEFAULT_TIMEOUT_MILLIS / 1000L) + "s.");
        }
        this.timeoutMillis = millis;
        return this;
    }

    /** Run when nobody answered in time, just before the screen comes back. */
    @Nonnull
    public PromptSpec<T> onTimeout(@Nullable Consumer<ScreenView> onTimeout) {
        this.onTimeout = onTimeout;
        return this;
    }

    /** Run when the player left (or the screen was torn down) mid-question. Nothing comes back afterwards. */
    @Nonnull
    public PromptSpec<T> onQuit(@Nullable Runnable onQuit) {
        this.onQuit = onQuit;
        return this;
    }

    /** Typing this instead of an answer calls the question off; closing the window of an anvil or a page does too. */
    @Nonnull
    public PromptSpec<T> cancelWord(@Nullable String cancelWord) {
        this.cancelWord = cancelWord == null ? "" : cancelWord.trim();
        return this;
    }

    /** What the player is asked, already coloured and ready to be sent. */
    @Nonnull
    public String getQuestion() {
        return question;
    }

    @Nonnull
    public PromptParser<T> getParser() {
        return parser;
    }

    @Nullable
    public PromptKind getPreferredKind() {
        return preferredKind;
    }

    /** Empty when there is none. */
    @Nonnull
    public String getInitialText() {
        return initialText;
    }

    @Nullable
    public ItemSpec getIcon() {
        return icon;
    }

    /** Always more than zero. */
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    @Nullable
    public Consumer<ScreenView> getOnTimeout() {
        return onTimeout;
    }

    @Nullable
    public Runnable getOnQuit() {
        return onQuit;
    }

    /** Empty when there is no such word. */
    @Nonnull
    public String getCancelWord() {
        return cancelWord;
    }

}
