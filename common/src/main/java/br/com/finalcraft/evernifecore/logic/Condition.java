package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.action.EventSource;

import java.util.Collections;
import java.util.Set;

/**
 * A question about a context that never charges anything: passed, or failed with a text that says why. Tested on the
 * caller's thread, without blocking I/O.
 */
public interface Condition {

    ConditionResult test(ExecutionContext ctx);

    /** The event sources after which the answer may change; empty means it is sampled at an interval. */
    default Set<EventSource> getNeeds() {
        return Collections.emptySet();
    }
}
