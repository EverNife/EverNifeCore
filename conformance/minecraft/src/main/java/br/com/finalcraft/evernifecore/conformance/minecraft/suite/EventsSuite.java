package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.ForgeListener;
import br.com.finalcraft.evernifecore.minecraft.version.ServerSoftware;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The event bus on a real server: an {@link ECEvent} posted on the bus reaches its bus subscribers and, mirrored by
 * {@code callEvent}, a plain {@code @EventHandler}; and the Forge audience tells a hybrid from a plain server the
 * same way the server software does. Nothing crosses to the Forge bus yet, so a hybrid proves the detection only.
 */
public final class EventsSuite implements ConformanceSuite {

    private static final String OWNER = "ECoreConformance";

    @Override
    public String getId() {
        return "events";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("bus-to-bukkit", this::busToBukkit),
                ConformanceCase.of("forge-detection", () -> {
                    boolean forge = ForgeListener.isAvailable();
                    boolean hybrid = ServerSoftware.getCurrent().isHybrid();
                    return Verdict.of(forge == hybrid, "forgeSide=" + forge + " software=" + ServerSoftware.getCurrent() + " hybrid=" + hybrid);
                }));
    }

    private Verdict busToBukkit() {
        ProbeListener listener = new ProbeListener();
        AtomicInteger subscriber = new AtomicInteger();
        Bukkit.getPluginManager().registerEvents(listener, Bukkit.getPluginManager().getPlugin(OWNER));
        ECEventSubscription<ProbeEvent> subscription = ECEventBus.global().subscribe(ProbeEvent.class, event -> subscriber.incrementAndGet());
        try {
            ECEventBus.global().post(new ProbeEvent());
        } finally {
            subscription.unsubscribe();
            HandlerList.unregisterAll(listener);
        }
        return Verdict.of(listener.heard.get() == 1 && subscriber.get() == 1,
                "bukkitHandler=" + listener.heard.get() + " busSubscriber=" + subscriber.get());
    }

    /** Posted only by this suite. */
    public static final class ProbeEvent extends ECEvent {

        public static HandlerList getHandlerList() {
            return (HandlerList) ECEvent.getHandlerListOf(ProbeEvent.class);
        }
    }

    public static final class ProbeListener implements Listener {

        private final AtomicInteger heard = new AtomicInteger();

        @EventHandler
        public void onProbe(ProbeEvent event) {
            heard.incrementAndGet();
        }
    }
}
