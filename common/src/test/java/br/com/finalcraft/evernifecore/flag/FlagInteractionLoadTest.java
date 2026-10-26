package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two flags on one channel must say how they interact, or the second one does not register. */
@ECoreTest
class FlagInteractionLoadTest {

    private static final String OWNER = "WingsOwner";

    /** A flight flag of a plugin, on the channel the stock flight flags use. */
    private static class Wings extends TristateFlag {
        private final Set<FlagInteraction> interactions;

        Wings(ECPluginData owner, String name, Set<FlagInteraction> interactions) {
            super(FlagKey.of(owner, name), Collections.singleton(EventSource.MOVE), Tristate.UNDEFINED, Tristate.TRUE);
            this.interactions = interactions;
        }

        @Override
        public Set<String> getChannels() {
            return Collections.singleton("slot:flight");
        }

        @Override
        public Set<FlagInteraction> getInteractions() {
            return interactions;
        }
    }

    @TempDir
    Path tempDir;

    private ECPluginData owner;

    @BeforeEach
    void setUp() {
        owner = Plugins.fakePluginData(OWNER, tempDir.resolve("owner").toFile());
    }

    @AfterEach
    void tearDown() {
        FlagRegistry.get().unregisterAll(owner);
        Plugins.forget(OWNER);
    }

    @Test
    void twoFlagsOnOneChannelWithoutADeclarationFailNamingBoth() {
        Wings first = new Wings(owner, "wings", Collections.emptySet());
        Wings second = new Wings(owner, "no-wings", Collections.emptySet());
        FlagRegistry.get().register(owner, first);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> FlagRegistry.get().register(owner, second));
        assertTrue(refused.getMessage().contains(first.getKey().toString()) && refused.getMessage().contains(second.getKey().toString())
                && refused.getMessage().contains("slot:flight"), refused.getMessage());
        assertFalse(second.isRegistered());
    }

    @Test
    void aDeclarationOnEitherSideLetsBothRegister() {
        Wings first = new Wings(owner, "wings", Collections.emptySet());
        Wings second = new Wings(owner, "no-wings", Collections.singleton(
                FlagInteraction.with(first.getKey(), "no-wings = TRUE grounds whoever wings would lift")));
        FlagRegistry.get().register(owner, first);
        FlagRegistry.get().register(owner, second);
        assertTrue(second.isRegistered());
    }

    @Test
    void everyStockFlagRegistersBecauseTheStockDeclarationsAreComplete() throws Exception {
        ECPluginData core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        try {
            assertDoesNotThrow(() -> CoreFlags.registerAll(core));
            assertEquals(CoreFlags.all().size(), FlagRegistry.get().getByOwner(core).size());
        } finally {
            FlagRegistry.get().unregisterAll(core);
            Plugins.forget(Plugins.CORE);
        }
    }
}
