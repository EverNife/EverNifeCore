package br.com.finalcraft.evernifecore.api.events.base;

import br.com.finalcraft.evernifecore.minecraft.eventbus.ECHandlerList;
import br.com.finalcraft.evernifecore.minecraft.eventbus.McHandlerLists;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * The Bukkit face of {@link IECEvent}: an EC event that extends this IS a Bukkit event, so a plain
 * {@code @EventHandler} listener can hear it once the bus mirrors it into the server.
 *
 * <p>Each event class gets a {@link HandlerList} of its own through {@link #getHandlerListOf(Class)},
 * as long as it declares the static {@code getHandlerList} Bukkit looks for; one that declares none
 * shares the family list of this base. Which of the two an event ends up with is decided by
 * {@link #getHandlers()} exactly the way the plugin manager decides where to register a listener, so
 * the two can never disagree.</p>
 */
public abstract class ECEvent extends Event implements IECEvent {

    //One list per event class, created the first time anyone asks for it - a registration, a gate
    //check or a dispatch. The class itself is the key: the list lives and dies with it.
    private static final ClassValue<ECHandlerList> LISTS = new ClassValue<ECHandlerList>() {
        @Override
        protected ECHandlerList computeValue(Class<?> type) {
            return new ECHandlerList();
        }
    };

    protected ECEvent() {
        //snapshot of the thread that built it - the producer's contract is to build and post on the
        //same thread, and Bukkit refuses a sync event posted off the main thread
        super(!Bukkit.isPrimaryThread());
    }

    protected ECEvent(boolean async) {
        super(async);
    }

    /**
     * The native handler list of {@code eventType}, created on first ask. Typed {@code Object} because
     * this is the platoverride of a stub that cannot name {@link HandlerList}; the value always is one.
     * An event declares it once, on the class native listeners subscribe to - in a platform-agnostic
     * module as {@code public static Object getHandlerList() { return ECEvent.getHandlerListOf(MyEvent.class); }},
     * and in this module, where the compiler sees {@link #getHandlerList()} and demands a compatible
     * return type, as {@code public static HandlerList getHandlerList() { return (HandlerList)
     * ECEvent.getHandlerListOf(MyEvent.class); }}.
     */
    public static Object getHandlerListOf(Class<? extends ECEvent> eventType) {
        return LISTS.get(eventType);
    }

    /**
     * The family list: what an EC event that declares no {@code getHandlerList} of its own resolves to,
     * on registration and on dispatch alike.
     */
    public static HandlerList getHandlerList() {
        return LISTS.get(ECEvent.class);
    }

    @Override
    public HandlerList getHandlers() {
        //the same resolution the plugin manager runs on registerEvent, so dispatch never reads a list
        //registration did not fill
        return McHandlerLists.registrationListOf(getClass());
    }

}
