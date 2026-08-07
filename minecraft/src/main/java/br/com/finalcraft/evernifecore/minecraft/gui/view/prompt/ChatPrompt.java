package br.com.finalcraft.evernifecore.minecraft.gui.view.prompt;

import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * A question put to the player in chat.
 *
 * <pre>{@code
 * ctx.askOnChat(ChatPrompt.of("§eType the new price, or 'cancel':")
 *                 .parse(Double::parseDouble)
 *                 .timeout(Duration.ofSeconds(45))
 *                 .cancelWord("cancel"))
 *    .thenAccept(price -> auction.setPrice(price));
 * }</pre>
 *
 * @param <T> what the question is asking for - {@link #parse(PromptParser)} is what decides it
 */
public final class ChatPrompt<T> implements PromptSpec<T> {

    /** Long enough to walk to the keyboard, short enough that a screen is never set aside forever. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 60_000L;

    private final String question;
    private PromptParser<T> parser;
    private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    private Consumer<GuiView> onTimeout;
    private Runnable onQuit;
    private String cancelWord = "";

    private ChatPrompt(String question, PromptParser<T> parser) {
        this.question = question;
        this.parser = parser;
    }

    /** The question, answered by whatever the player types verbatim until {@link #parse} says otherwise. */
    @Nonnull
    public static ChatPrompt<String> of(@Nonnull String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("A chat prompt needs a question: the screen goes away while "
                    + "it is out, so a player who was told nothing has no idea why.");
        }
        return new ChatPrompt<>(question, input -> input);
    }

    /**
     * How the typed line becomes the value. Throwing refuses the attempt: the player reads the
     * exception's message, the question is put again, and the wait is not restarted.
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public <R> ChatPrompt<R> parse(@Nonnull PromptParser<R> parser) {
        if (parser == null) {
            throw new IllegalArgumentException("A chat prompt needs a parser, or none at all - leave the "
                    + "call out and the answer arrives as the String the player typed.");
        }
        //the builder is consumed by the chain it is written in, so re-typing it in place has no reader
        //left holding the old type
        ChatPrompt<R> retyped = (ChatPrompt<R>) this;
        retyped.parser = parser;
        return retyped;
    }

    /** How long to wait. {@link #DEFAULT_TIMEOUT_MILLIS} when it is not said, and never zero. */
    @Nonnull
    public ChatPrompt<T> timeout(@Nonnull Duration timeout) {
        long millis = timeout == null ? 0L : timeout.toMillis();
        if (millis <= 0L) {
            throw new IllegalArgumentException("A chat prompt has to end on its own: with no timeout, a "
                    + "player who simply walks away leaves their screen set aside for good. Give it a "
                    + "duration, or leave the call out for the default of "
                    + (DEFAULT_TIMEOUT_MILLIS / 1000L) + "s.");
        }
        this.timeoutMillis = millis;
        return this;
    }

    /** Run when nobody answered in time, just before the screen comes back. */
    @Nonnull
    public ChatPrompt<T> onTimeout(@Nullable Consumer<GuiView> onTimeout) {
        this.onTimeout = onTimeout;
        return this;
    }

    /** Run when the player left mid-question. Nothing comes back afterwards - there is nobody to show. */
    @Nonnull
    public ChatPrompt<T> onQuit(@Nullable Runnable onQuit) {
        this.onQuit = onQuit;
        return this;
    }

    /** Typing this instead of an answer calls the question off and brings the screen back. */
    @Nonnull
    public ChatPrompt<T> cancelWord(@Nullable String cancelWord) {
        this.cancelWord = cancelWord == null ? "" : cancelWord.trim();
        return this;
    }

    @Override
    @Nonnull
    public String getQuestion() {
        return question;
    }

    @Override
    @Nonnull
    public PromptParser<T> getParser() {
        return parser;
    }

    @Override
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    @Override
    @Nullable
    public Consumer<GuiView> getOnTimeout() {
        return onTimeout;
    }

    @Override
    @Nullable
    public Runnable getOnQuit() {
        return onQuit;
    }

    @Override
    @Nonnull
    public String getCancelWord() {
        return cancelWord;
    }

}
