package br.com.finalcraft.evernifecore.minecraft.protection;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.minecraft.listeners.PluginListener;
import br.com.finalcraft.evernifecore.minecraft.protection.integration.ProtectionHandler;
import br.com.finalcraft.evernifecore.minecraft.protection.integration.imp.GriefPreventionPlusHandler;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protection handlers belong to the plugin that registered them: they come with it and leave with it. */
class ProtectionAllTest {

    @TempDirNobodyCleans
    Path tempDir;

    private final ProtectionAll all = ProtectionAll.getInstance();

    private static ProtectionHandler allowing(boolean allows) {
        return Doubles.of(ProtectionHandler.class)
                .returning("getName", allows ? "Allows" : "Denies")
                .returning("canBuild", allows)
                .build();
    }

    /** Another plugin's data, leaving the rig's extractor in place for the rig's own plugin. */
    private ECPluginData otherPlugin(String name) {
        IECPluginExtractor previous = EverNifeCore.getProviders().getBaseProvider().provideOrNull(IECPluginExtractor.class);
        try {
            return Plugins.fakePluginData(name, tempDir.resolve(name).toFile());
        } finally {
            EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class, previous);
        }
    }

    private static Plugin plugin(String name) {
        PluginDescriptionFile description = new PluginDescriptionFile(name, "1.0.0", name);
        return Doubles.of(Plugin.class)
                .returning("getName", name)
                .returning("getDescription", description)
                .returning("isEnabled", true)
                .build();
    }

    @Test
    void aHandlerLeavesWithItsOwner() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ECPluginData first = otherPlugin("ProtOwnerA");
            ECPluginData second = otherPlugin("ProtOwnerB");
            ProtectionHandler ofFirst = allowing(true);
            ProtectionHandler ofSecond = allowing(true);
            try {
                all.register(first, ofFirst);
                all.register(second, ofSecond);
                assertTrue(all.getProtectionHandlers().contains(ofFirst), "registered, before anything leaves");

                assertEquals(1, all.unregisterAll(first));

                assertFalse(all.getProtectionHandlers().contains(ofFirst));
                assertTrue(all.getProtectionHandlers().contains(ofSecond), "the other owner's handler stays");
            } finally {
                all.unregisterAll(first);
                all.unregisterAll(second);
                Plugins.forget("ProtOwnerA");
                Plugins.forget("ProtOwnerB");
            }
        }
    }

    @Test
    void everyHandlerMustAllow() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ECPluginData owner = world.getPluginData();
            try {
                all.register(owner, allowing(true));
                all.register(owner, allowing(true));
                assertTrue(all.canBuild(null, null), "every handler so far allows");
                all.register(owner, allowing(false));
                all.register(owner, allowing(true));

                assertFalse(all.canBuild(null, null), "one false among three true denies");
            } finally {
                all.unregisterAll(owner);
            }
        }
    }

    @Test
    void registeringTheSameInstanceTwiceIsOneRegistration() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ECPluginData owner = world.getPluginData();
            ProtectionHandler handler = allowing(true);
            try {
                all.register(owner, handler);
                all.register(owner, handler);

                assertEquals(1, all.getProtectionHandlers().size());
            } finally {
                all.unregisterAll(owner);
            }
        }
    }

    @Test
    void theListIsASnapshot() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ECPluginData owner = world.getPluginData();
            try {
                all.register(owner, allowing(true));
                List<ProtectionHandler> handlers = all.getProtectionHandlers();

                assertThrows(UnsupportedOperationException.class, () -> handlers.add(allowing(true)));
                all.register(owner, allowing(false));
                assertEquals(1, handlers.size(), "a later registration does not reach a list already handed out");
            } finally {
                all.unregisterAll(owner);
            }
        }
    }

    @Test
    void aBuiltInArrivesWhenItsPluginEnablesAndLeavesWhenItDisables() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            world.registerECListener(new PluginListener());
            Plugin gpp = plugin("GriefPreventionPlus");
            try {
                world.getPluginManager().callEvent(new PluginEnableEvent(gpp));

                assertEquals(1, all.getProtectionHandlers().size());
                assertTrue(all.getProtectionHandlers().get(0) instanceof GriefPreventionPlusHandler);

                world.getPluginManager().callEvent(new PluginDisableEvent(gpp));

                assertTrue(all.getProtectionHandlers().isEmpty(), "the protection plugin went, its handler with it");
            } finally {
                all.unregisterAll(world.getPluginData());
            }
        }
    }

    @Test
    void aDisabledECPluginTakesItsHandlersAlong() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            world.registerECListener(new PluginListener());
            ECPluginData leaving = otherPlugin("ProtLeaving");
            ProtectionHandler handler = allowing(true);
            try {
                all.register(leaving, handler);
                all.register(world.getPluginData(), allowing(true));
                assertTrue(all.getProtectionHandlers().contains(handler), "registered, before its plugin disables");

                world.getPluginManager().callEvent(new PluginDisableEvent(plugin("ProtLeaving")));

                assertFalse(all.getProtectionHandlers().contains(handler));
                assertEquals(1, all.getProtectionHandlers().size(), "the core's handler stays");
                assertNull(ECPluginManager.getECPluginsMap().get("ProtLeaving"), "and its plugin data with it");
            } finally {
                all.unregisterAll(leaving);
                all.unregisterAll(world.getPluginData());
                Plugins.forget("ProtLeaving");
            }
        }
    }
}
