package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.CommandContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;

/** {@code command}: running a command, by its label or any of its names ({@code command: spawn}, globs allowed). */
public final class CommandObjectiveType extends ActionObjectiveType {

    public CommandObjectiveType() {
        super("command", EventSource.COMMAND, "command", "Running commands.");
    }

    @Override
    public boolean matches(ActionEvent<?> event, KeyFilter filter) {
        if (filter.getPattern() == null) {
            return true;
        }
        String pattern = "command:" + filter.getPattern().replaceFirst("^/", "");
        for (String name : ((CommandContext) event.getContext()).getNames()) {
            if (!name.isEmpty() && TypeKey.of("command", name).matches(pattern)) {
                return true;
            }
        }
        return false;
    }
}
