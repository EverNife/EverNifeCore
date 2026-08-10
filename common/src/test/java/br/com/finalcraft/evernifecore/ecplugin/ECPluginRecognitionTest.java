package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the core accepts as "a plugin".
 *
 * <p>Everything downstream of {@link ECPluginData} - its name, its data folder, its logger - is read
 * off the object through the platform's extractor, so an object the platform does not know is not a
 * half-working plugin: it is a name lookup that would fail later, somewhere with no clue as to who
 * registered it. The registration is where that has to be said.</p>
 */
class ECPluginRecognitionTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld worldFor(String pluginName, Object thePlugin) {
        return Platforms.strict().loggingToStdout().ignoringPlatformRegistrations().install()
                .withPluginExtractor(Plugins.fakeRecognisingOnly(pluginName, tempDir.toFile(), thePlugin));
    }

    @Test
    void anObjectThePlatformDoesNotKnowIsRefusedAndNamed() {
        Object thePlugin = new Object();

        try (ECoreTestWorld world = worldFor("Recognised", thePlugin)) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> ECPluginManager.getOrCreateECorePluginData("not a plugin at all"));

            assertTrue(refused.getMessage().contains(String.class.getName()),
                    "the refusal has to name what was handed in: " + refused.getMessage());
        }
    }

    /** The same refusal reaches whoever builds the data directly, which third-party code can do. */
    @Test
    void buildingThePluginDataDirectlyIsRefusedTheSameWay() {
        Object thePlugin = new Object();

        try (ECoreTestWorld world = worldFor("RecognisedDirect", thePlugin)) {
            assertThrows(IllegalArgumentException.class, () -> new ECPluginData("not a plugin at all"));
        }
    }

    @Test
    void theObjectThePlatformDoesKnowGoesThroughAndIsCachedUnderItsName() {
        Object thePlugin = new Object();

        try (ECoreTestWorld world = worldFor("Accepted", thePlugin)) {
            ECPluginData data = ECPluginManager.getOrCreateECorePluginData(thePlugin);
            try {
                assertEquals("Accepted", data.getMetaInfo().getName());
                assertSame(data, ECPluginManager.getOrCreateECorePluginData(thePlugin),
                        "a second registration of the same plugin is the same plugin");
            } finally {
                ECPluginManager.removePluginData("Accepted");
            }
        }
    }
}
