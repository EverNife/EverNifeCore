package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How {@link ECPluginData} discovers the reload hook: an {@code @ECPlugin.Reload} annotated method
 * (own or inherited) always wins; with none, a plugin implementing {@link IECPluginBootstrap} is
 * reloadable through its mandatory {@code onECPluginReload()}. A plain plugin (no bootstrap, no
 * annotation) is not reloadable.
 */
@ECoreTest
class ECPluginDataReloadTest {

    private static final AtomicInteger ANNOTATED_RELOADS = new AtomicInteger();
    private static final AtomicInteger TRAIT_RELOADS = new AtomicInteger();
    private static final AtomicInteger BASE_RELOADS = new AtomicInteger();


    @TempDir
    Path tempDir;

    private final List<String> registeredNames = new ArrayList<>();

    @AfterEach
    void teardown() {
        //the ECPluginData cache is static and keyed by name: drop what this test registered so a
        //stale entry pointing at a dead @TempDir cannot reach the next test in this JVM
        for (String name : registeredNames) {
            ECPluginManager.removePluginData(name);
        }
        registeredNames.clear();
        ANNOTATED_RELOADS.set(0);
        TRAIT_RELOADS.set(0);
        BASE_RELOADS.set(0);
    }

    @Test
    void annotatedMethodOnThePluginClassIsFound() {
        ECPluginData data = pluginData("ReloadAnnotated", new AnnotatedPlugin());

        assertTrue(data.canReload());
        data.reloadPlugin();
        assertEquals(1, ANNOTATED_RELOADS.get());
    }

    @Test
    void bootstrapIsReloadableThroughOnECPluginReload() {
        //the intended layering: the reload override lives in a bootstrap interface of the plugin's
        //common module, not on the platform main class itself
        ECPluginData data = pluginData("ReloadTrait", new TraitPlugin());

        assertTrue(data.canReload());
        data.reloadPlugin();
        assertEquals(1, TRAIT_RELOADS.get());
    }

    @Test
    void plainPluginWithoutAnnotationIsNotReloadable() {
        ECPluginData data = pluginData("ReloadPlain", new PlainPlugin());

        assertFalse(data.canReload());
    }

    @Test
    void annotatedMethodWinsOverTheBootstrapHook() {
        ECPluginData data = pluginData("ReloadBoth", new AnnotatedTraitPlugin());

        assertTrue(data.canReload());
        data.reloadPlugin();
        assertEquals(1, ANNOTATED_RELOADS.get());
        assertEquals(0, TRAIT_RELOADS.get());
    }

    @Test
    void annotatedMethodInheritedFromASuperclassIsFound() {
        ECPluginData data = pluginData("ReloadInherited", new SubclassOfAnnotatedBase());

        assertTrue(data.canReload());
        data.reloadPlugin();
        assertEquals(1, BASE_RELOADS.get());
    }

    // ------------------------------------------------------------------
    // fake plugin shapes
    // ------------------------------------------------------------------

    /** A plain plugin (not a bootstrap) with no reload declaration. */
    public static class PlainPlugin {
    }

    public static class AnnotatedPlugin {
        @ECPlugin.Reload
        public void onReload() {
            ANNOTATED_RELOADS.incrementAndGet();
        }
    }

    /** The plugin-common bootstrap interface shape: the mandatory hooks are default methods here. */
    public interface TraitBootstrap extends IECPluginBootstrap {
        @Override
        default void onECPluginEnable() {
        }

        @Override
        default void onECPluginShutdown() {
        }

        @Override
        default void onECPluginReload() {
            TRAIT_RELOADS.incrementAndGet();
        }
    }

    public static class TraitPlugin implements TraitBootstrap {
    }

    public static class AnnotatedTraitPlugin implements TraitBootstrap {
        @ECPlugin.Reload
        public void onReload() {
            ANNOTATED_RELOADS.incrementAndGet();
        }
    }

    public static class AnnotatedBasePlugin {
        @ECPlugin.Reload
        public void onReload() {
            BASE_RELOADS.incrementAndGet();
        }
    }

    public static class SubclassOfAnnotatedBase extends AnnotatedBasePlugin {
    }

    // ------------------------------------------------------------------
    // fixture: a real ECPluginData, built the way production does (through the extractor)
    // ------------------------------------------------------------------

    private ECPluginData pluginData(String pluginName, Object plugin) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        registeredNames.add(pluginName);
        return ECPluginManager.getOrCreateECorePluginData(plugin);
    }


}
