package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.region.RegionManager;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A routing extractor answers each class with the plugin whose package it lives in. */
class PluginsRoutingTest {

    private final Plugins.Routing routing = Plugins.routing()
            .plugin("Core", new File("core"), "br.com.finalcraft.evernifecore.region")
            .plugin("Prot", new File("prot"), "br.com.finalcraft.evernifecore.testing.PluginsRoutingTest");

    @Test
    void classesOfDifferentPackagesGoToDifferentPlugins() {
        Object core = routing.getProvidingPlugin(RegionManager.class);
        Object prot = routing.getProvidingPlugin(PluginsRoutingTest.class);

        assertNotSame(core, prot);
        assertEquals("Core", routing.getPluginName(core));
        assertEquals("Prot", routing.getPluginName(prot));
        assertEquals(new File("prot"), routing.getPluginMetaInfo(prot).getDataFolder());
        assertTrue(routing.isJavaPlugin(prot));
    }

    @Test
    void theLongestPrefixWinsAndAnUnmatchedClassBelongsToTheFirst() {
        Plugins.Routing nested = Plugins.routing()
                .plugin("Outer", new File("outer"), "br.com.finalcraft")
                .plugin("Inner", new File("inner"), "br.com.finalcraft.evernifecore.testing");

        assertEquals("Inner", nested.getPluginName(nested.getProvidingPlugin(PluginsRoutingTest.class)));
        assertEquals("Outer", nested.getPluginName(nested.getProvidingPlugin(RegionManager.class)));
        assertSame(routing.getToken("Core"), routing.getProvidingPlugin(String.class));
    }
}
