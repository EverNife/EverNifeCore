package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code flight-toggle}: Toggling flight. */
public final class FlightToggleObjectiveType extends ActionObjectiveType {

    public FlightToggleObjectiveType() {
        super("flight-toggle", EventSource.FLIGHT_TOGGLE, "key", "Toggling flight.");
    }
}
