package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.quest.ObjectiveNature;
import br.com.finalcraft.evernifecore.quest.ObjectiveType;
import br.com.finalcraft.evernifecore.quest.QuestState;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** {@code timer}: done once {@code duration} passed since its stage began; checked by the engine's sweep, once a minute. */
public final class TimerObjectiveType implements ObjectiveType<Duration> {

    @Override
    public String getKey() {
        return "evernifecore:timer";
    }

    @Override
    public String getDescription() {
        return "Done once a duration passed since its stage began.";
    }

    @Override
    public List<ParamSpec> getParams() {
        return Collections.singletonList(ParamSpec.required("duration", ParamSpec.Kind.DURATION, "How long after the stage began it is done."));
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
    public Duration parseFilter(ParsedParams params) {
        return params.getDuration("duration");
    }

    @Override
    public Condition stateOf(Duration duration) {
        return ctx -> {
            QuestState state = ctx.requireAttribute("quest", QuestState.class);
            long elapsed = ECClock.now().toEpochMilli() - state.getStageStartedAt();
            return ConditionResult.of(elapsed >= duration.toMillis(), FancyText.of("The timer has not run out yet."));
        };
    }
}
