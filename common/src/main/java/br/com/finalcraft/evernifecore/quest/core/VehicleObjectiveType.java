package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code vehicle}: Entering or leaving vehicles. */
public final class VehicleObjectiveType extends ActionObjectiveType {

    public VehicleObjectiveType() {
        super("vehicle", EventSource.VEHICLE, "vehicle", "Entering or leaving vehicles.");
    }
}
