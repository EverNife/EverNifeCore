package br.com.finalcraft.evernifecore.consolefilter;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The console asks one provider: the core's own until a plugin replaces it, and the core's again after. */
@ECoreTest
class ECConsoleFilterTest {

    @TempDirNobodyCleans
    Path dir;

    private ECPluginData owner;

    @BeforeEach
    void setup() {
        owner = Plugins.fakeOther("FakeFilter", dir.resolve("filter").toFile());
    }

    @AfterEach
    void teardown() {
        Plugins.forget("FakeFilter");
    }

    private static final String DYNAMIC_COMMAND = "Steve issued server command: /ecdcmd 4f1c2b6e-0000-0000-0000-000000000000";
    private static final String PAGE_TURN = "Steve issued server command: /ecpage 2";
    private static final String OTHER_COMMAND = "Steve issued server command: /spawn";

    @Test
    void theDefaultHidesOnlyTheCoresOwnCommandEcho() {
        ECConsoleFilter filter = new ECConsoleFilter();

        assertFalse(filter.hasProvider());
        assertSame(IConsoleFilterProvider.CORE_COMMANDS, filter.getProvider());
        assertTrue(filter.isHidden(DYNAMIC_COMMAND));
        assertTrue(filter.isHidden(PAGE_TURN));
        assertFalse(filter.isHidden(OTHER_COMMAND));
        assertFalse(filter.isHidden("Done (3.2s)! For help, type \"help\""));
    }

    @Test
    void aPluginsProviderReplacesTheDefault() {
        ECConsoleFilter filter = new ECConsoleFilter();
        IConsoleFilterProvider spawnOnly = message -> message.contains("/spawn");

        filter.setProvider(owner, spawnOnly);

        assertTrue(filter.hasProvider());
        assertTrue(filter.isHidden(OTHER_COMMAND));
        assertFalse(filter.isHidden(DYNAMIC_COMMAND), "the replacement decides alone - it consults CORE_COMMANDS if it wants");
    }

    @Test
    void releasingPutsTheDefaultBackOnlyForTheCurrentOwner() {
        ECConsoleFilter filter = new ECConsoleFilter();
        ECPluginData next = Plugins.fakeOther("FakeFilterNext", dir.resolve("next").toFile());
        try {
            IConsoleFilterProvider successor = message -> true;
            filter.setProvider(owner, message -> false);
            filter.setProvider(next, successor);

            assertFalse(filter.unregister(owner), "a disabling plugin must not evict its successor");
            assertSame(successor, filter.getProvider());

            assertTrue(filter.unregister(next));
            assertSame(IConsoleFilterProvider.CORE_COMMANDS, filter.getProvider());
            assertFalse(filter.unregister(next));
        } finally {
            Plugins.forget("FakeFilterNext");
        }
    }

    @Test
    void aFailingProviderLetsTheLineThrough() {
        ECConsoleFilter filter = new ECConsoleFilter();
        filter.setProvider(owner, message -> {
            throw new IllegalStateException("rules not loaded");
        });

        assertFalse(filter.isHidden(DYNAMIC_COMMAND));
        assertFalse(filter.isHidden(null));
    }
}
