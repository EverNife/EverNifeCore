package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;

/** A {@link BoosterManager} kept a new booster: posted after {@code grant} stored it, only when somebody listens. */
public class BoosterGrantedEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(BoosterGrantedEvent.class);
    }

    private final BoosterManager manager;
    private final Booster booster;

    public BoosterGrantedEvent(BoosterManager manager, Booster booster) {
        this.manager = manager;
        this.booster = booster;
    }

    public BoosterManager getManager() {
        return manager;
    }

    /** The booster as kept, its id assigned. */
    public Booster getBooster() {
        return booster;
    }
}
