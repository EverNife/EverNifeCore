package br.com.finalcraft.evernifecore.screen.prompt;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import jakarta.annotation.Nonnull;

/**
 * One question out, as a {@link PromptChannel} sees it: who is asked, what, and the two things the player can do about
 * it. Every method may be called from any thread - the registry hops to the screen's thread itself.
 *
 * @param <T> what the question is asking for
 */
public interface PromptSession<T> {

    @Nonnull
    FPlayer getPlayer();

    @Nonnull
    PromptSpec<T> getSpec();

    /**
     * The player typed {@code typed}. The cancel word ends the question as {@code CANCELLED}; otherwise the spec's
     * parser reads it: a value ends the question ({@link PromptReply#accepted()}), a throw leaves it open and the reply
     * carries the parser's message ({@link PromptReply#refused}) without restarting the wait. A session that already
     * ended answers {@code accepted()} and changes nothing, so the channel just takes its window away.
     */
    @Nonnull
    PromptReply answer(@Nonnull String typed);

    /** The player closed the channel's window: the question ends as {@code CANCELLED}. No-op once it ended. */
    void cancel();

    /** Whether the question is still waiting for the player. */
    boolean isOpen();

}
