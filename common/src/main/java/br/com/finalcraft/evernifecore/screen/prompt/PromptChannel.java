package br.com.finalcraft.evernifecore.screen.prompt;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import jakarta.annotation.Nonnull;

/**
 * A window a question can be put through - chat, an anvil, a page. Dumb on purpose: {@link PromptRegistry} owns the
 * question (the screen set aside and given back, the timeout, the second question that calls off the first, leaving),
 * and the channel only shows it and passes on what the player did through the {@link PromptSession}.
 *
 * <p>One channel per {@link PromptKind}; a platform registers its own with {@link PromptRegistry#register}.</p>
 */
public interface PromptChannel {

    @Nonnull
    PromptKind getKind();

    /** Whether this channel can put a question to {@code player} right now. Screen thread; never throws. */
    boolean isSupported(@Nonnull FPlayer player);

    /**
     * Shows the question. Screen thread, after the registry set the screen aside: the screen's window may still be in
     * front of the player, and this channel replaces it with its own or takes it away.
     */
    void open(@Nonnull PromptSession<?> session);

    /**
     * Takes the window away because the question ended (answered, timed out, replaced, the screen released). Must not
     * call back into the session - a call would be ignored anyway. Screen thread.
     */
    void dismiss(@Nonnull PromptSession<?> session);

}
