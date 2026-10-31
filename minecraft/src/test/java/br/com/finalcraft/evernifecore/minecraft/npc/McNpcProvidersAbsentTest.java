package br.com.finalcraft.evernifecore.minecraft.npc;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.UsageHold;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.npc.NpcProvider;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.npc.Npcs;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A server without Citizens, FancyNpcs or ZNPCsPlus jars - even one that says the plugins are enabled: the three providers
 * register, answer unavailable, find nothing, never throw, and look the API class up once.
 */
@ECoreTest
class McNpcProvidersAbsentTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;

    @BeforeEach
    void open() {
        world = Platforms.lenient().pluginsLoaded("Citizens", "FancyNpcs", "ZNPCsPlus").install();
        core = Plugins.fakePluginData("EverNifeCoreNpcs", tempDir.toFile());
        McNpcProviders.registerAll(core, null);
    }

    @AfterEach
    void close() {
        Npcs.unregisterAll(core);
        world.close();
    }

    @Test
    void theThreeAreRegisteredAndUnavailable() {
        List<String> ids = new ArrayList<>();
        for (NpcProvider provider : Npcs.getProviders()) {
            ids.add(provider.getId());
            assertFalse(provider.isAvailable(), provider.getId() + " has no API class on this classpath");
        }
        assertEquals(Arrays.asList("citizens", "fancynpcs", "znpcsplus"), ids);
    }

    @Test
    void lookupsFindNothingAndThrowNothing() {
        NpcRef guard = NpcRef.parse("citizens:1");
        assertFalse(Npcs.find(guard).isPresent());
        assertFalse(Npcs.getLocation(NpcRef.parse("fancynpcs:0b7d")).isPresent());
        assertFalse(Npcs.getName(NpcRef.parse("znpcsplus:merchant")).isPresent());
    }

    @Test
    void theApiClassIsLookedUpOnceAndNoForeignClassIsLoaded() {
        ReflectedNpcProvider citizens = (ReflectedNpcProvider) Npcs.provider("citizens").get();
        citizens.isAvailable();
        citizens.isAvailable();
        citizens.isAvailable();

        assertEquals(1, citizens.getApiLookups());
        assertThrows(ClassNotFoundException.class, () -> Class.forName("net.citizensnpcs.api.CitizensAPI"));
    }

    @Test
    void needingInteractNpcStartsNothingOnAnAbsentPlugin() {
        UsageHold hold = EventSourceUsage.get().acquire(EventSource.INTERACT_NPC);
        try {
            assertDoesNotThrow(() -> Npcs.unregisterAll(core), "stop() of a provider that never subscribed is a no-op");
        } finally {
            hold.release();
        }
    }
}
