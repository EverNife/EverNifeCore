package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** A vehicle - a minecart, a boat - placed, broken or entered ({@link EventSource#VEHICLE}). */
public final class VehicleContext extends ActionContext {

    public enum Action {
        PLACE, BREAK, ENTER
    }

    private final Action action;
    private final @Nullable Actor vehicle;

    /**
     * @param position where the vehicle is, or where it is being placed
     * @param actor    who places, breaks or enters it - the environment for one broken by nobody
     * @param vehicle  the vehicle; null while it is being placed and does not exist yet
     */
    public VehicleContext(WorldLocPos position, Actor actor, @Nullable Object nativeEvent, Action action, @Nullable Actor vehicle) {
        super(EventSource.VEHICLE, position, actor, nativeEvent);
        this.action = Objects.requireNonNull(action, "action");
        this.vehicle = vehicle;
    }

    public Action getAction() {
        return action;
    }

    /** The vehicle; null while it is being placed. */
    public @Nullable Actor getVehicle() {
        return vehicle;
    }

    @Override
    public void close() {
        super.close();
        if (vehicle != null) {
            vehicle.close();
        }
    }
}
