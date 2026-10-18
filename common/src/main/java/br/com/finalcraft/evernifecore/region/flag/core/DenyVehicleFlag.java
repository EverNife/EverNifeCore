package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.VehicleContext;
import jakarta.annotation.Nullable;

/**
 * TRUE: vehicles cannot be placed, broken or entered in the region. {@code deny-vehicle} speaks of all three; each
 * refinement speaks of one action and, where it is not set, reads {@code deny-vehicle}.
 */
public final class DenyVehicleFlag extends DenyFlag {

    private final @Nullable VehicleContext.Action action;
    private final String description;

    /** {@code deny-vehicle}, about every vehicle action. */
    public DenyVehicleFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-vehicle"), EventSource.VEHICLE);
        this.action = null;
        this.description = "TRUE: vehicles cannot be placed, broken or entered in the region.";
    }

    /** A refinement of {@code parent} about {@code action} only. */
    public DenyVehicleFlag(String name, DenyVehicleFlag parent, VehicleContext.Action action, String description) {
        super(FlagKey.of(CoreFlags.OWNER, name), EventSource.VEHICLE, parent);
        this.action = action;
        this.description = description;
    }

    /** The action this flag speaks of; null for every one. */
    public @Nullable VehicleContext.Action getAction() {
        return action;
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof VehicleContext && (action == null || ((VehicleContext) context).getAction() == action);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
