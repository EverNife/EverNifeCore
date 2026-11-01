package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.quest.Objective;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestPresenter;
import br.com.finalcraft.evernifecore.quest.QuestState;
import br.com.finalcraft.evernifecore.quest.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link QuestPresenter} that writes down every moment it is shown, one line each - {@code "progress Alex
 * fakequests:walk/dirt 2"}, {@code "objective Alex fakequests:walk/dirt"}, {@code "stage Alex fakequests:walk/s"},
 * {@code "complete Alex fakequests:walk"}, {@code "fail ..."}, {@code "abandon ..."}, {@code "track ..."},
 * {@code "untrack ..."}. Set it with {@code engine.setPresenter(recording)}.
 */
public final class RecordingQuestPresenter implements QuestPresenter {

    private final List<String> lines = Collections.synchronizedList(new ArrayList<String>());

    /** Every line so far, in order; a copy. */
    public List<String> getLines() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    public void clear() {
        lines.clear();
    }

    @Override
    public void onProgress(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state, Objective objective) {
        lines.add("progress " + viewer.getName() + " " + definition.getId() + "/" + objective.getId() + " " + state.getCount(objective.getId()));
    }

    @Override
    public void onObjectiveComplete(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state, Objective objective) {
        lines.add("objective " + viewer.getName() + " " + definition.getId() + "/" + objective.getId());
    }

    @Override
    public void onStageComplete(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state, Stage stage) {
        lines.add("stage " + viewer.getName() + " " + definition.getId() + "/" + stage.getId());
    }

    @Override
    public void onQuestComplete(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
        lines.add("complete " + viewer.getName() + " " + definition.getId());
    }

    @Override
    public void onQuestFail(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
        lines.add("fail " + viewer.getName() + " " + definition.getId());
    }

    @Override
    public void onQuestAbandon(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
        lines.add("abandon " + viewer.getName() + " " + definition.getId());
    }

    @Override
    public void onTrack(FPlayer viewer, QuestEngine engine, QuestDefinition definition, QuestState state) {
        lines.add("track " + viewer.getName() + " " + definition.getId());
    }

    @Override
    public void onUntrack(FPlayer viewer, QuestEngine engine, QuestId quest) {
        lines.add("untrack " + viewer.getName() + " " + quest);
    }
}
