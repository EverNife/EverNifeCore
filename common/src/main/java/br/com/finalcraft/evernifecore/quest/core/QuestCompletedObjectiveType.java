package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.quest.ObjectiveNature;
import br.com.finalcraft.evernifecore.quest.ObjectiveType;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestState;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** {@code quest-completed}: done once the owner completed another quest, of this engine or another; checked when any quest of the owner completes. */
public final class QuestCompletedObjectiveType implements ObjectiveType<QuestId> {

    @Override
    public String getKey() {
        return "evernifecore:quest-completed";
    }

    @Override
    public String getDescription() {
        return "Done once the owner completed another quest.";
    }

    @Override
    public List<ParamSpec> getParams() {
        return Collections.singletonList(ParamSpec.required("quest", ParamSpec.Kind.ID, "The quest, owner:name (a bare name is the same plugin's)."));
    }

    @Override
    public ObjectiveNature getNature() {
        return ObjectiveNature.STATE;
    }

    @Override
    public Set<EventSource> getSources() {
        return Collections.emptySet();
    }

    @Override
    public QuestId parseFilter(ParsedParams params) {
        return QuestId.parse(params.getString("quest"), params.getCaller().getMetaInfo().getName());
    }

    @Override
    public Condition stateOf(QuestId quest) {
        return ctx -> {
            QuestEngine engine = QuestEngine.find(quest.getOwner());
            Optional<QuestState> state = engine == null ? Optional.<QuestState>empty() : engine.getState(ctx.getOwner(), quest);
            return ConditionResult.of(state.isPresent() && state.get().getTimes() > 0, FancyText.of("The quest " + quest + " is not completed yet."));
        };
    }
}
