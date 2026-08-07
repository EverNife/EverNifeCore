package br.com.finalcraft.evernifecore.minecraft.gui.view.prompt;

import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import jakarta.annotation.Nonnull;

import java.util.concurrent.CompletableFuture;

/**
 * A way of asking a player for a value a button cannot express.
 *
 * <p>Asking is a family, not a method: the chat channel is the one that exists, and another - an
 * anvil, a sign - can join it without changing a line of what a screen writes. What a channel owes,
 * whichever it is:</p>
 *
 * <ul>
 *   <li>the screen is set aside while the question is out, and given back with its state intact once
 *       the answer, the cancel word or the timeout arrives;</li>
 *   <li>the future completes on the main thread, whatever thread the answer came in on;</li>
 *   <li>a second question on the same screen calls the first one off instead of stacking with it.</li>
 * </ul>
 */
public interface PromptChannel {

    /**
     * Asks {@code view}'s player for a value.
     *
     * @return how the asking ended, completed on the main thread. It never completes exceptionally:
     *         no answer is an ending, not a failure.
     */
    @Nonnull
    <T> CompletableFuture<PromptResult<T>> ask(@Nonnull GuiView view, @Nonnull PromptSpec<T> spec);

}
