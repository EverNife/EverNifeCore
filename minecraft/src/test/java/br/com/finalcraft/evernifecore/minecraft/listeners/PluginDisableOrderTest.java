package br.com.finalcraft.evernifecore.minecraft.listeners;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IECPluginBootstrap;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.Storages;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bukkit posts {@code PluginDisableEvent} BEFORE it calls the plugin's {@code onDisable}. A plugin that runs its own
 * shutdown must still have its storage-backed state written by it, so the event must not forget it first.
 */
@ECoreTest
class PluginDisableOrderTest {

    @TempDir
    Path tempDir;

    private ECStorage storage;

    @AfterEach
    void tearDown() throws Exception {
        RegionManager.closeAll();
        if (storage != null) {
            storage.close().get(5, TimeUnit.SECONDS);
        }
        Plugins.forget("BootProt");
        Plugins.forget("PlainProt");
    }

    /** A plugin that runs its own shutdown; its teardown has nothing of its own to close. */
    static final class BootPlugin implements IECPluginBootstrap {
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

    @Test
    void aBootstrapPluginDisabledInBukkitsOrderWritesItsDirtyRegionAndLeavesNoDataBehind() throws Exception {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class, Plugins.fake("BootProt", tempDir.toFile()));
        BootPlugin boot = new BootPlugin();
        ECPluginData owner = boot.getPluginData();
        ScriptedStorage backend = ScriptedStorage.wrapping(Storages.createInMemory());
        backend.init().get(5, TimeUnit.SECONDS);
        storage = backend.asECStorage();
        RegionManager manager = RegionManager.of(owner, storage);
        manager.load().get(10, TimeUnit.SECONDS);
        CreationResult arena = manager.create("arena", "world", CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(9, 9, 9)));
        assertTrue(arena.isCreated(), arena.toString());
        assertEquals(0, backend.callsTo("save"), "the region is still only pending");

        new PluginListener().onPluginDisable(new PluginDisableEvent(plugin("BootProt")));  //Bukkit: the event first
        boot.runECPluginShutdown();                                                          //then onDisable

        assertEquals(1, backend.callsTo("save"), "the dirty region was discarded instead of written by the plugin's shutdown");
        assertFalse(ECPluginManager.getECPluginsMap().containsKey("BootProt"), "a plugin data was left behind after onDisable");
    }

    @Test
    void aPluginThatIsNoBootstrapIsStillForgottenByTheEvent() {
        Plugins.fakePluginData("PlainProt", tempDir.toFile());

        new PluginListener().onPluginDisable(new PluginDisableEvent(plugin("PlainProt")));

        assertFalse(ECPluginManager.getECPluginsMap().containsKey("PlainProt"));
    }

    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(PluginDisableOrderTest.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return name;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "Plugin[" + name + "]";
                        default:
                            throw new UnsupportedOperationException("Plugin." + method.getName());
                    }
                });
    }
}
