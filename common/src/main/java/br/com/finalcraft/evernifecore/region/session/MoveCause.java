package br.com.finalcraft.evernifecore.region.session;

/** Why a player crossed a region border. Only MOVE, TELEPORT and VEHICLE can be refused. */
public enum MoveCause {
    MOVE, TELEPORT, VEHICLE, RESPAWN, WORLD_CHANGE, JOIN, QUIT, REGION_CREATED, REGION_REMOVED;

    /** Whether the crossing can still be refused: the player is about to move, not already there. */
    public boolean isCancellable() {
        return this == MOVE || this == TELEPORT || this == VEHICLE;
    }
}
