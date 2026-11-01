package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A plugin's unit test builds and posts a Bukkit EC event on the global bus with no server in the JVM. */
class ECEventWithoutServerTest {

    @Test
    void anEventBuildsAndReachesASubscriberWithoutAServer() {
        assertNull(Bukkit.getServer(), "this test is about a JVM where no server was ever set");

        try (ECoreTestWorld ignored = Platforms.lenient().install()) {
            List<SampleEvent> heard = new ArrayList<>();
            ECEventSubscription<SampleEvent> subscription = ECEventBus.global().subscribe(SampleEvent.class, heard::add);
            try {
                SampleEvent posted = ECEventBus.global().post(new SampleEvent());

                assertFalse(posted.isAsynchronous(), "with no server there is only the main thread");
                assertEquals(1, heard.size());
                assertSame(posted, heard.get(0));
            } finally {
                subscription.unsubscribe();
            }
        }
    }

    public static final class SampleEvent extends ECEvent {
    }
}
