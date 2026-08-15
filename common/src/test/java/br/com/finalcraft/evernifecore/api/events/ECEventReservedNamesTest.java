package br.com.finalcraft.evernifecore.api.events;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.testing.ECEventConformance;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The names an EC event may not take. The platform base is a different class on each side, and the
 * events in this module are written against a stub that has none of those members - so nothing but
 * this guard stands between a harmless-looking method and a Bukkit server refusing the class.
 */
class ECEventReservedNamesTest {

    @Test
    void theCanonicalEventsDeclareNothingReserved() {
        List<String> failures = ECEventConformance.checkAll(
                ECPlayerFullyLoggedInEvent.class,
                ECPlayerQuitEvent.class,
                ECPluginReloadEvent.class,
                ECPluginReloadEvent.Pre.class,
                ECPluginReloadEvent.Post.class);

        assertEquals(Collections.emptyList(), failures);
    }

    @Test
    void anEventThatRedeclaresThePlatformPlumbingIsCaught() {
        List<String> failures = ECEventConformance.check(EventWithItsOwnAsyncFlag.class);

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).contains("isAsynchronous"), failures.get(0));
        assertTrue(failures.get(0).contains("VerifyError"), "the message has to say what breaks: " + failures.get(0));
    }

    @Test
    void anEventThatCancelsOutsideTheContractIsCaught() {
        List<String> failures = ECEventConformance.check(EventCancellingOnItsOwn.class);

        assertEquals(2, failures.size(), "both halves of the pair are unreachable: " + failures);
        assertTrue(failures.get(0).contains("ECCancellable"), failures.get(0));
    }

    @Test
    void anEventThatImplementsECCancellableMayDeclareTheCancellationPair() {
        assertEquals(Collections.emptyList(), ECEventConformance.check(ProperlyCancellableEvent.class));
    }

    @Test
    void anEventMayDeclareTheHandlerListInTheShapeBukkitResolves() {
        assertEquals(Collections.emptyList(), ECEventConformance.check(EventWithItsOwnHandlerList.class));
    }

    @Test
    void aHandlerListThatIsNotStaticIsCaught() {
        List<String> failures = ECEventConformance.check(EventWithAnInstanceHandlerList.class);

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).contains("wrong shape"), failures.get(0));
        assertTrue(failures.get(0).contains("getHandlerList"), failures.get(0));
    }

    @Test
    void aHandlerListBukkitCouldNotSeeIsCaught() {
        List<String> failures = ECEventConformance.check(EventHidingItsHandlerList.class);

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).contains("wrong shape"), failures.get(0));
    }

    @Test
    void aHandlerListTakingAParameterIsCaught() {
        List<String> failures = ECEventConformance.check(EventWithAParameterisedHandlerList.class);

        assertEquals(1, failures.size(), "Bukkit looks the method up with no arguments: " + failures);
        assertTrue(failures.get(0).contains("wrong shape"), failures.get(0));
    }

    @Test
    void aClassThatIsNoECEventAtAllIsReported() {
        List<String> failures = ECEventConformance.check(LocalOnlyEvent.class);

        assertEquals(1, failures.size(), failures.toString());
        assertTrue(failures.get(0).contains("no ECEvent subtype"), failures.get(0));
    }

    // ------------------------------------------------------------------
    //  fixtures - each one is the mistake the guard is here to catch
    // ------------------------------------------------------------------

    /** Compiles here, where the base is the stub; on Bukkit the base declares it final. */
    static class EventWithItsOwnAsyncFlag extends ECEvent implements IECEvent {
        public boolean isAsynchronous() {
            return false;
        }
    }

    /** Cancellation nobody honours: neither the bus nor a platform looks at a pair like this. */
    static class EventCancellingOnItsOwn extends ECEvent implements IECEvent {
        private boolean cancelled;

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    /** The same pair, through the contract - which is what makes it reach the platforms. */
    static class ProperlyCancellableEvent extends ECEvent implements IECEvent, ECCancellable {
        private boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    /** The one shape that reaches a Bukkit server: found by name, invoked static, cast to a HandlerList. */
    static class EventWithItsOwnHandlerList extends ECEvent implements IECEvent {
        public static Object getHandlerList() {
            return ECEvent.getHandlerListOf(EventWithItsOwnHandlerList.class);
        }
    }

    /** Compiles here, where the base declares no static of that name; on Bukkit nothing would invoke it. */
    static class EventWithAnInstanceHandlerList extends ECEvent implements IECEvent {
        public Object getHandlerList() {
            return null;
        }
    }

    /** Static and shaped right, but the server cannot reach it - the registration fails at that plugin. */
    static class EventHidingItsHandlerList extends ECEvent implements IECEvent {
        private static Object getHandlerList() {
            return ECEvent.getHandlerListOf(EventHidingItsHandlerList.class);
        }
    }

    /** The name is what Bukkit searches for, and it searches for the no-argument one. */
    static class EventWithAParameterisedHandlerList extends ECEvent implements IECEvent {
        public static Object getHandlerList(Class<?> eventType) {
            return ECEvent.getHandlerListOf(EventWithAParameterisedHandlerList.class);
        }
    }

    /** The local/hot shape: it never becomes a platform event, so the reserved names bind nothing. */
    static class LocalOnlyEvent implements IECEvent {
    }

}
