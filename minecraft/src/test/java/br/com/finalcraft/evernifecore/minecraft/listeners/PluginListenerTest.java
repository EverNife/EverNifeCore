package br.com.finalcraft.evernifecore.minecraft.listeners;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The listener is registered when the core enables, so it never hears the disable of a plugin that died before that;
 * {@code onRegister} catches up with the plugin manager.
 */
class PluginListenerTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private String core;

    public static class GhostSection extends PDSection {
    }

    public static class LateSection extends PDSection {
    }

    @BeforeEach
    void setUp() {
        world = Platforms.lenient().install();
        core = EverNifeCore.getEcPluginData().getMetaInfo().getName();
    }

    @AfterEach
    void tearDown() {
        for (String name : Arrays.asList("Ghost", "Stranger", "Late")) {
            ECPluginData data = ECPluginManager.getECPluginsMap().get(name);
            PlayerController.unregisterPDSections(data);
            Plugins.forget(name);
        }
        world.close();
    }

    @Test
    void onRegisterCleansUpAPluginDisabledBeforeTheCore() {
        ECPluginData ghost = Plugins.fakePluginData("Ghost", tempDir.resolve("ghost").toFile());
        PlayerController.registerPDSectionCfg(ghost, GhostSection.class, "ghostsection");

        new PluginListener().onRegister(pluginManager(plugin("Ghost", false), plugin(core, true)));

        assertFalse(ECPluginManager.getECPluginsMap().containsKey("Ghost"), "a plugin dead before the core leaves no data");
        assertFalse(PlayerController.getConfiguredPDSections().containsKey(GhostSection.class), "nor a section binding");
    }

    @Test
    void aNameThePluginManagerDoesNotKnowStays() {
        Plugins.fakePluginData("Stranger", tempDir.resolve("stranger").toFile());

        new PluginListener().onRegister(pluginManager(plugin(core, true)));

        assertTrue(ECPluginManager.getECPluginsMap().containsKey("Stranger"), "an owner that is no Bukkit plugin is not reconciled");
    }

    @Test
    void aPluginAfterTheCoreIsOnlyNotEnabledYetAndStays() {
        ECPluginData late = Plugins.fakePluginData("Late", tempDir.resolve("late").toFile());
        PlayerController.registerPDSectionCfg(late, LateSection.class, "latesection");

        new PluginListener().onRegister(pluginManager(plugin(core, true), plugin("Late", false)));

        assertTrue(ECPluginManager.getECPluginsMap().containsKey("Late"), "what a later plugin built during its load stays");
        assertTrue(PlayerController.getConfiguredPDSections().containsKey(LateSection.class));
    }

    /** A plugin manager whose {@code getPlugins()} is {@code plugins}, in that (enable) order. */
    private static PluginManager pluginManager(Plugin... plugins) {
        return (PluginManager) Proxy.newProxyInstance(PluginListenerTest.class.getClassLoader(), new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getPlugins":
                            return plugins;
                        case "getPlugin":
                            return Arrays.stream(plugins).filter(p -> p.getName().equals(args[0])).findFirst().orElse(null);
                        default:
                            throw new UnsupportedOperationException("PluginManager." + method.getName());
                    }
                });
    }

    private static Plugin plugin(String name, boolean enabled) {
        return (Plugin) Proxy.newProxyInstance(PluginListenerTest.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return name;
                        case "isEnabled":
                            return enabled;
                        case "toString":
                            return "Plugin[" + name + "]";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            throw new UnsupportedOperationException("Plugin." + method.getName());
                    }
                });
    }
}
