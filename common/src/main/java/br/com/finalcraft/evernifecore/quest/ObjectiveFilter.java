package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.logic.Condition;
import jakarta.annotation.Nullable;

/** One objective's parsed filter, bound to the type that reads it. */
public final class ObjectiveFilter {

    private final ObjectiveType<Object> type;
    private final Object filter;

    @SuppressWarnings("unchecked")
    private ObjectiveFilter(ObjectiveType<?> type, Object filter) {
        this.type = (ObjectiveType<Object>) type;
        this.filter = filter;
    }

    public static <C> ObjectiveFilter of(ObjectiveType<C> type, C filter) {
        return new ObjectiveFilter(type, filter);
    }

    public ObjectiveType<?> getType() {
        return type;
    }

    public Object getFilter() {
        return filter;
    }

    public boolean matches(ActionEvent<?> event) {
        return type.matches(event, filter);
    }

    public long countOf(ActionEvent<?> event) {
        return type.countOf(event, filter);
    }

    public @Nullable Condition stateOf() {
        return type.stateOf(filter);
    }

    @Override
    public String toString() {
        return type.getKey() + "(" + filter + ")";
    }
}
