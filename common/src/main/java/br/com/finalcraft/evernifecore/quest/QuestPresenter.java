package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import jakarta.annotation.Nullable;

/**
 * How an engine shows quest moments to the players they concern: the player of a player's quest, the online members of a
 * group's. {@link NotifierQuestPresenter} is every engine's default; a product swaps it with
 * {@link QuestEngine#setPresenter}. Called on the thread that moved the quest, with the owner's lane held - do no I/O,
 * hand slow work off. Every method is optional. The states are snapshots.
 */
public interface QuestPresenter {

    /** An objective counted something and is not done yet. */
    default void onProgress(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state, Objective objective) {
    }

    default void onObjectiveComplete(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state, Objective objective) {
    }

    default void onStageComplete(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state, Stage stage) {
    }

    default void onQuestComplete(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
    }

    default void onQuestFail(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
    }

    default void onQuestAbandon(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
    }

    /** {@code viewer} tracks {@code definition} now; {@code state} is empty when they never started it. */
    default void onTrack(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
    }

    /** {@code viewer} stopped tracking {@code quest}. */
    default void onUntrack(FPlayer viewer, QuestEngine engine, QuestId quest) {
    }

    /**
     * The text the quest command node and the offer screen send when a start or an offer of {@code definition} for
     * {@code player} did not happen, or null to keep the core's. {@link StartResult#getReason()} says which check said no.
     * Called on whatever thread answered the start, the lane not held.
     */
    default @Nullable FancyText refusal(FPlayer player, QuestDefinition definition, StartResult result) {
        return null;
    }
}
