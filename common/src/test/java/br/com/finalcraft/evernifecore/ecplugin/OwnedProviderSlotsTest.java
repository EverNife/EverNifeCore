package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.consolefilter.ECConsoleFilter;
import br.com.finalcraft.evernifecore.consolefilter.IConsoleFilterProvider;
import br.com.finalcraft.evernifecore.ignore.ECIgnores;
import br.com.finalcraft.evernifecore.ignore.IIgnoreProvider;
import br.com.finalcraft.evernifecore.ignore.IgnoreKind;
import br.com.finalcraft.evernifecore.ontime.IOntimeProvider;
import br.com.finalcraft.evernifecore.ontime.OntimeManager;
import br.com.finalcraft.evernifecore.spawn.ISpawnProvider;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The server-wide slots a plugin fills - ontime, console filter, an ignore kind, the spawn - remember who filled them and
 * empty themselves when that plugin is released, with no call from the plugin; a plugin already replaced leaves its
 * successor in place.
 */
@ECoreTest
class OwnedProviderSlotsTest {

    @TempDirNobodyCleans
    Path dir;

    private ECPluginData onTime;
    private ECPluginData successor;

    @BeforeEach
    void setup() {
        onTime = Plugins.fakeOther("FakeOnTime", dir.resolve("ontime").toFile());
        successor = Plugins.fakeOther("FakeSuccessor", dir.resolve("successor").toFile());
    }

    @AfterEach
    void teardown() {
        Plugins.forget("FakeOnTime");
        Plugins.forget("FakeSuccessor");
    }

    @Test
    void theOntimeProviderLeavesWithItsPlugin() {
        OntimeManager.setOntimeProvider(onTime, playerData -> 10);

        Plugins.forget("FakeOnTime");

        assertEquals(0L, OntimeManager.getProvider().getOntime(null));
        assertNull(OntimeManager.getOwner());
    }

    @Test
    void theConsoleFilterLeavesWithItsPlugin() {
        ECConsoleFilter.global().setProvider(onTime, message -> true);

        Plugins.forget("FakeOnTime");

        assertSame(IConsoleFilterProvider.CORE_COMMANDS, ECConsoleFilter.global().getProvider());
    }

    @Test
    void anIgnoreProviderLeavesWithItsPlugin() {
        ECIgnores.global().setProvider(onTime, IgnoreKind.CHAT, (ignorer, ignored) -> true);

        Plugins.forget("FakeOnTime");

        assertFalse(ECIgnores.global().hasProvider(IgnoreKind.CHAT));
    }

    @Test
    void theSpawnLeavesWithItsPlugin() {
        EverNifeCore.getProviders().setSpawn(onTime, () -> null);

        Plugins.forget("FakeOnTime");

        assertNull(EverNifeCore.getProviders().getSpawnOrNull());
    }

    @Test
    void aReplacedPluginLeavingKeepsItsSuccessorInEverySlot() {
        IOntimeProvider ontime = playerData -> 20;
        IConsoleFilterProvider filter = message -> false;
        IIgnoreProvider ignore = (ignorer, ignored) -> false;
        ISpawnProvider spawn = () -> null;
        OntimeManager.setOntimeProvider(onTime, playerData -> 10);
        ECConsoleFilter.global().setProvider(onTime, message -> true);
        ECIgnores.global().setProvider(onTime, IgnoreKind.CHAT, (ignorer, ignored) -> true);
        EverNifeCore.getProviders().setSpawn(onTime, () -> null);
        OntimeManager.setOntimeProvider(successor, ontime);
        ECConsoleFilter.global().setProvider(successor, filter);
        ECIgnores.global().setProvider(successor, IgnoreKind.CHAT, ignore);
        EverNifeCore.getProviders().setSpawn(successor, spawn);

        Plugins.forget("FakeOnTime");

        assertSame(ontime, OntimeManager.getProvider());
        assertSame(filter, ECConsoleFilter.global().getProvider());
        assertSame(ignore, ECIgnores.global().getProvider(IgnoreKind.CHAT));
        assertSame(spawn, EverNifeCore.getProviders().getSpawnOrNull());

        Plugins.forget("FakeSuccessor");
        assertNull(EverNifeCore.getProviders().getSpawnOrNull(), "and the successor's go when it leaves");
    }
}
