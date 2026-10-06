package br.com.finalcraft.evernifecore.region.flag;

/** The kind of game event a flag needs to hear; {@link #NONE} for a flag that only stores a value. */
public enum EventSource {
    MOVE, BREAK, PLACE, INTERACT, DAMAGE, SPAWN, TICK, NONE
}
