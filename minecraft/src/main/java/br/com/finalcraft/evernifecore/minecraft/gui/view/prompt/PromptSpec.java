package br.com.finalcraft.evernifecore.minecraft.gui.view.prompt;

import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Consumer;

/**
 * What a {@link PromptChannel} is asked to obtain: the question, how to read the answer, and the
 * three ways the asking can end without one.
 *
 * <p>Nothing here names a channel, which is the point - the same question can be put through chat
 * today and through something else later. {@link ChatPrompt} is the chat channel's form of it.</p>
 *
 * @param <T> what the prompt is asking for
 */
public interface PromptSpec<T> {

    /** What the player is asked, already coloured and ready to be sent. */
    @Nonnull
    String getQuestion();

    /** How the answer becomes the value. Throwing refuses the attempt - see {@link PromptParser}. */
    @Nonnull
    PromptParser<T> getParser();

    /** How long to wait for an answer, in milliseconds. Always more than zero. */
    long getTimeoutMillis();

    /** Run when the wait elapses, before the screen comes back, or {@code null}. */
    @Nullable
    Consumer<GuiView> getOnTimeout();

    /** Run when the player leaves mid-question, when nothing comes back, or {@code null}. */
    @Nullable
    Runnable getOnQuit();

    /** Typing this instead of an answer calls the prompt off. Empty when there is no such word. */
    @Nonnull
    String getCancelWord();

}
