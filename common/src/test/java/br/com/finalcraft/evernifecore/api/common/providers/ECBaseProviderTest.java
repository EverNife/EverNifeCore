package br.com.finalcraft.evernifecore.api.common.providers;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The registry has to be able to forget: a fixture that installs a provider and never removes it
 * leaks into every other test class sharing the JVM.
 */
class ECBaseProviderTest {

    interface Marker {
    }

    static final class First implements Marker {
    }

    static final class Second implements Marker {
    }

    @Test
    void unregisterRemovesTheProviderAndReportsWhatWasThere() {
        ECBaseProvider provider = new ECBaseProvider();
        Marker registered = provider.register(Marker.class, new First());

        assertSame(registered, provider.unregister(Marker.class));
        assertNull(provider.provideOrNull(Marker.class));
        assertThrows(NoSuchElementException.class, () -> provider.provide(Marker.class));
    }

    @Test
    void unregisteringSomethingThatWasNeverRegisteredIsNotAnError() {
        assertNull(new ECBaseProvider().unregister(Marker.class));
    }

    @Test
    void restoringThePreviousProviderIsWhatMakesAFixtureUndoable() {
        ECBaseProvider provider = new ECBaseProvider();
        Marker original = provider.register(Marker.class, new First());

        Marker previous = provider.provideOrNull(Marker.class);
        provider.register(Marker.class, new Second());
        provider.register(Marker.class, previous);

        assertSame(original, provider.provide(Marker.class));
        assertEquals(First.class, provider.provide(Marker.class).getClass());
    }
}
