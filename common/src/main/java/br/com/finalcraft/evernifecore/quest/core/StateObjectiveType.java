package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.quest.ObjectiveNature;
import br.com.finalcraft.evernifecore.quest.ObjectiveType;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;
import br.com.finalcraft.evernifecore.types.PendingDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * {@code state}: done once any condition passes - "hold 32 iron" is
 * {@code {type: state, condition: {type: compare, variable: inventory-count, ...}}}. Checked again after each source the
 * condition needs; a condition with no needs is sampled by the engine's sweep.
 */
public final class StateObjectiveType implements ObjectiveType<Condition> {

    @Override
    public String getKey() {
        return "evernifecore:state";
    }

    @Override
    public String getDescription() {
        return "Done once a condition passes, checked again after the action sources it needs.";
    }

    @Override
    public List<ParamSpec> getParams() {
        return Collections.singletonList(ParamSpec.required("condition", ParamSpec.Kind.NODE, "The condition whose pass completes it.")
                .nested(Conditions.registry()));
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
    public Condition parseFilter(ParsedParams params) {
        PendingDefinition<Condition> condition = params.getPending("condition");
        return Conditions.of(condition);
    }

    @Override
    public Condition stateOf(Condition filter) {
        return filter;
    }
}
