package br.com.finalcraft.evernifecore.minecraft.ecplugin;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The handle a Bukkit plugin keeps on its own {@link ECPluginData}.
 *
 * <p>{@code getPluginData()} is the first call of every log line a plugin writes, so the base class
 * resolves it once and answers from a field afterwards. That field is a cache like any other: it
 * lasts exactly as long as the registry entry it copied, and not one call longer.</p>
 */
class ECBukkitPluginDataTest {

    private static final String PLUGIN_NAME = "BukkitPluginDataTest";

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void repeatedCallsAnswerWithTheSamePluginData() {
        ECBukkitPlugin plugin = allocateWithoutTheServer();

        try (ECoreTestWorld world = world()) {
            try {
                ECPluginData data = plugin.getPluginData();

                assertSame(data, plugin.getPluginData(), "the plugin was given two different datas");
                assertSame(data.getLog(), plugin.getLog(), "and so were its loggers");
            } finally {
                ECPluginManager.removePluginData(PLUGIN_NAME);
            }
        }
    }

    @Test
    void dataDroppedFromTheRegistryIsNotHandedOutAgain() {
        ECBukkitPlugin plugin = allocateWithoutTheServer();

        try (ECoreTestWorld world = world()) {
            try {
                ECPluginData beforeTheDrop = plugin.getPluginData();
                ECPluginManager.removePluginData(PLUGIN_NAME);

                assertNotSame(beforeTheDrop, plugin.getPluginData(),
                        "the plugin kept answering with data the registry had already dropped");
            } finally {
                ECPluginManager.removePluginData(PLUGIN_NAME);
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers and fixtures
    // -----------------------------------------------------------------------------------------------------------------

    private ECoreTestWorld world() {
        return Platforms.strict().loggingToStdout().ignoringPlatformRegistrations().install()
                .withPluginExtractor(Plugins.fake(PLUGIN_NAME, tempDir.toFile()));
    }

    /** The allocator skips every constructor up to Object's - see {@link TestBukkitPlugin}. */
    private static ECBukkitPlugin allocateWithoutTheServer() {
        try {
            Constructor<Object> objectConstructor = Object.class.getDeclaredConstructor();
            Constructor<?> allocator = ReflectionFactory.getReflectionFactory()
                    .newConstructorForSerialization(TestBukkitPlugin.class, objectConstructor);
            return (ECBukkitPlugin) allocator.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The plugin this test asks for its data could not be built", e);
        }
    }

    /** Never constructed: JavaPlugin's constructor demands a plugin class loader that is not here. */
    public static class TestBukkitPlugin extends ECBukkitPlugin {

        @Override
        public void onECPluginEnable() {

        }

        @Override
        public void onECPluginShutdown() {

        }

        @Override
        public void onECPluginReload() {

        }
    }
}
