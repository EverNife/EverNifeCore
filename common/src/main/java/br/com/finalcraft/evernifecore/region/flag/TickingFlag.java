package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;

/**
 * A flag that acts on the players inside its regions over time. It needs {@link EventSource#TICK}: the region tick
 * runs only while some region holds such a flag, once a second, and calls {@link #onTick} for each player standing
 * where the flag's value applies, on that player's world thread. How often it really acts is the flag's own business -
 * the tick is only its heartbeat.
 */
public interface TickingFlag<T> {

    void onTick(SessionContext context, T value);
}
