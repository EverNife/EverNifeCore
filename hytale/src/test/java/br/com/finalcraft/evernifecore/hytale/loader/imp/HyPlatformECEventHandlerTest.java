package br.com.finalcraft.evernifecore.hytale.loader.imp;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.eventbus.ECEventHandler;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where an {@code @ECEventHandler} method ends up on Hytale. The parameter decides, and the one this
 * platform used to refuse outright - an event of the framework's own - is now simply not its business:
 * the event bus takes it.
 *
 * <p>The plugin is allocated without its constructor and given nothing but an {@link EventRegistry}
 * over a real {@link EventBus}. That registry is all this route touches, and building the real thing
 * would mean standing up fifteen others plus the server behind them.</p>
 */
class HyPlatformECEventHandlerTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld platformWorld;
    private EventBus serverBus;
    private ECPluginData ecPluginData;
    private HyPlatform platform;
    private final List<ECListener> registered = new ArrayList<>();

    @BeforeEach
    void aPluginWithNothingButAnEventRegistry() {
        String pluginName = "HyEventHandlerTest";
        platformWorld = Platforms.lenient().install()
                .withPluginExtractor(Plugins.fake(pluginName, tempDir.toFile()));
        serverBus = new EventBus(false);
        ecPluginData = ECPluginManager.getOrCreateECorePluginData(pluginWithRegistryOver(serverBus));
        platform = new HyPlatform();
    }

    @AfterEach
    void undoTheRegistrations() {
        for (ECListener listener : registered) {
            platform.unregisterECListener(listener);
        }
        ECPluginManager.removePluginData(ecPluginData.getMetaInfo().getName());
        platformWorld.close();
    }

    @Test
    void aListenerMixingFrameworkAndNativeEventsRegistersWithoutBeingRefused() {
        MixedListener listener = new MixedListener();

        platform.registerECListener(ecPluginData, listener);
        registered.add(listener);

        serverBus.<Void, NativeHytaleEvent>dispatchFor(NativeHytaleEvent.class).dispatch(new NativeHytaleEvent());
        assertEquals(Arrays.asList("native"), listener.heard, "the native handler is the only one this route took");

        assertFalse(serverBus.<Void, SampleEcEvent>dispatchFor(SampleEcEvent.class).hasListener(),
                "an ECEvent is a Hytale event too, and registering it here as well would deliver it "
                        + "twice: once from the bus, once from the mirror that bus feeds");
        assertTrue(HyPlatform.MAP_OF_ECLISTENERS.containsKey(listener));
    }

    @Test
    void aParameterThatIsNoEventAtAllStillRefusesTheWholeListener() {
        RuntimeException refused = assertThrows(RuntimeException.class,
                () -> platform.registerECListener(ecPluginData, new BrokenListener()));

        assertTrue(refused.getMessage().contains(BrokenListener.class.getName()), refused.getMessage());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers and fixtures
    // -----------------------------------------------------------------------------------------------------------------

    /** A JavaPlugin whose only living part is its event registry, wired to {@code bus}. */
    private static JavaPlugin pluginWithRegistryOver(EventBus bus) {
        EventRegistry registry = new EventRegistry(new CopyOnWriteArrayList<>(), () -> true, "not enabled", bus);
        try {
            Constructor<Object> objectConstructor = Object.class.getDeclaredConstructor();
            Constructor<?> allocator = ReflectionFactory.getReflectionFactory()
                    .newConstructorForSerialization(TestJavaPlugin.class, objectConstructor);
            JavaPlugin plugin = (JavaPlugin) allocator.newInstance();
            Field eventRegistry = PluginBase.class.getDeclaredField("eventRegistry");
            eventRegistry.setAccessible(true);
            eventRegistry.set(plugin, registry);
            return plugin;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("The fake plugin this test registers listeners for could "
                    + "not be built", e);
        }
    }

    /** Never constructed: the allocator above skips every constructor up to Object's. */
    public static class TestJavaPlugin extends JavaPlugin {
        public TestJavaPlugin(JavaPluginInit init) {
            super(init);
        }
    }

    /** A Hytale event and nothing else - the only parameter this platform registers. */
    public static class NativeHytaleEvent implements IEvent<Void> {
    }

    /** Platform-visible: a Hytale event AND an IECEvent, the parameter both routes could claim. */
    public static class SampleEcEvent extends ECEvent {
    }

    /** The local/hot shape: an IECEvent that is no Hytale event at all. */
    public static class LocalOnlyEvent implements IECEvent {
    }

    public static class MixedListener implements ECListener {
        final List<String> heard = new ArrayList<>();

        @ECEventHandler
        public void onNative(NativeHytaleEvent event) {
            heard.add("native");
        }

        @ECEventHandler
        public void onSample(SampleEcEvent event) {
            heard.add("sample");
        }

        @ECEventHandler
        public void onLocalOnly(LocalOnlyEvent event) {
            heard.add("local-only");
        }
    }

    public static class BrokenListener implements ECListener {
        @ECEventHandler
        public void onNothing(String notAnEventAtAll) {
        }
    }

}
