package br.com.finalcraft.evernifecore.minecraft.world;

import org.bukkit.entity.Entity;

import java.util.List;

/**
 * Lives in this module because its API pin declares {@code Entity.getPassengers()}, which arrived in 1.11: a server
 * older than that never links this class, since the caller picks it by version.
 */
public final class PassengerAccess {

    private PassengerAccess() {
    }

    /** Everyone riding {@code vehicle}, however many there are. */
    public static List<Entity> of(Entity vehicle) {
        return vehicle.getPassengers();
    }
}
