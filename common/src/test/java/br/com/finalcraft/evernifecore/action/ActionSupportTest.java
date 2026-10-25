package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a platform declares it raises, and the loud failure of a definition that needs what it never raises. */
@ECoreTest
class ActionSupportTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        ActionSupport.declare("an undeclared platform", null, null, true);
        Plugins.forget("SupportOwner");
    }

    @Test
    void declaringNothingServesEverySourceWithAPre() {
        ActionSupport.declare("Paper 1.21.1", null, null, true);

        assertTrue(ActionSupport.isServed(EventSource.FLUID));
        assertTrue(ActionSupport.hasPre(EventSource.BREAK));
        assertTrue(ActionSupport.isNativeCommandSource());
        assertDoesNotThrow(() -> ActionSupport.requireServed(EventSource.ITEM_PICKUP, true, "The objective 'pick' of test:quest"));
        assertFalse(ActionSupport.hasPre(EventSource.MOVE), "a source with no action event has no Pre");
    }

    @Test
    void aSourceThePlatformNeverRaisesFailsNamingTheSourceAndThePlatform() {
        ActionSupport.declare("Hytale 0.7.2", Arrays.asList(EventSource.BREAK, EventSource.PLACE), null, false);

        UnsupportedSourceException failure = assertThrows(UnsupportedSourceException.class,
                () -> ActionSupport.requireServed(EventSource.FLUID, false, "The objective 'flow' of test:quest"));

        assertSame(EventSource.FLUID, failure.getSource());
        List<String> words = Arrays.asList(EventSource.FLUID.toString(), "Hytale 0.7.2", "The objective 'flow' of test:quest");
        assertFalse(words.isEmpty());
        for (String word : words) {
            assertTrue(failure.getMessage().contains(word), "'" + word + "' missing from: " + failure.getMessage());
        }
        assertFalse(ActionSupport.isNativeCommandSource());
    }

    @Test
    void aSourceWithoutAPreOnThisPlatformRefusesTheNeedToCancelIt() {
        Set<EventSource> pre = new HashSet<>(Arrays.asList(EventSource.BREAK, EventSource.PLACE));
        ActionSupport.declare("Paper 1.21.1", null, pre, true);

        assertDoesNotThrow(() -> ActionSupport.requireServed(EventSource.RESPAWN, false, "The objective 'respawn' of test:quest"));
        UnsupportedSourceException failure = assertThrows(UnsupportedSourceException.class,
                () -> ActionSupport.requireServed(EventSource.RESPAWN, true, "The objective 'respawn' of test:quest"));
        assertTrue(failure.getMessage().contains(EventSource.RESPAWN.toString()) && failure.getMessage().contains("Paper 1.21.1")
                && failure.getMessage().contains("after the fact"), failure.getMessage());
    }

    @Test
    void aPluginsOwnSourceAlwaysCountsAsServed() {
        ECPluginData owner = Plugins.fakePluginData("SupportOwner", tempDir.toFile());
        EventSource own = EventSource.register(owner, "ritual");
        try {
            ActionSupport.declare("Hytale 0.7.2", Collections.singleton(EventSource.BREAK), Collections.<EventSource>emptySet(), false);

            assertTrue(ActionSupport.isServed(own));
            assertFalse(ActionSupport.isServed(EventSource.PLACE));
            assertFalse(ActionSupport.hasPre(EventSource.BREAK), "served, but with no pre-outcome point declared");
        } finally {
            EventSource.unregisterAll(owner);
        }
    }
}
