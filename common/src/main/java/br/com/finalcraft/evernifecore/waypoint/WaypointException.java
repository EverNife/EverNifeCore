package br.com.finalcraft.evernifecore.waypoint;

/** A waypoint that cannot point at all on this platform: none of its channels is supported here. */
public class WaypointException extends RuntimeException {

    public WaypointException(String message) {
        super(message);
    }
}
