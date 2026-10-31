package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The network SPI: refuses with guidance until one plugin registers an implementation. */
@ECoreTest
class GroupNetworkTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData proxy;

    /** A network that answers nothing - only its identity matters here. */
    private static final class NoopNetwork implements GroupNetwork {
        @Override
        public CompletableFuture<SendResult> send(Group group, String server) {
            return CompletableFuture.completedFuture(SendResult.of(Collections.emptySet(), Collections.emptyMap()));
        }

        @Override
        public Optional<String> getServer(UUID player) {
            return Optional.empty();
        }

        @Override
        public CompletableFuture<SendResult> follow(Group group) {
            return send(group, "lobby");
        }
    }

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        proxy = Plugins.fakePluginData("FakeProxy", tempDir.resolve("proxy").toFile());
    }

    @AfterEach
    void close() {
        Groups.unregisterNetwork(proxy);
        Plugins.forget("FakeProxy");
        world.close();
    }

    private static void assertTeaches(Throwable thrown) {
        assertInstanceOf(GroupNetworkUnavailableException.class, thrown);
        assertTrue(thrown.getMessage().contains("Groups.registerNetwork"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Groups.hasNetwork()"), thrown.getMessage());
    }

    @Test
    void withoutANetworkEveryCallIsRefusedAsUnavailableAndTeachesTheWayOut() {
        assertFalse(Groups.hasNetwork());
        GroupNetwork network = Groups.network();

        assertTeaches(assertThrows(ExecutionException.class, () -> network.send(null, "lobby").get()).getCause());
        assertTeaches(assertThrows(ExecutionException.class, () -> network.follow(null).get()).getCause());
        assertTeaches(assertThrows(IllegalStateException.class, () -> network.getServer(UUID.randomUUID())));
    }

    @Test
    void aRegisteredNetworkAnswersUntilItsOwnerUnregistersIt() {
        NoopNetwork mine = new NoopNetwork();
        Groups.registerNetwork(proxy, mine);

        assertTrue(Groups.hasNetwork());
        assertSame(mine, Groups.network());

        Groups.unregisterNetwork(proxy);
        assertFalse(Groups.hasNetwork());
    }

    @Test
    void aSecondNetworkIsRefusedNamingTheOwnerOfTheFirst() {
        Groups.registerNetwork(proxy, new NoopNetwork());
        ECPluginData other = Plugins.fakePluginData("OtherProxy", tempDir.resolve("other").toFile());
        try {
            IllegalStateException refused = assertThrows(IllegalStateException.class, () -> Groups.registerNetwork(other, new NoopNetwork()));

            assertTrue(refused.getMessage().contains("already registered by FakeProxy"), refused.getMessage());
            Groups.unregisterNetwork(other);
            assertTrue(Groups.hasNetwork(), "only the owner takes its network out");
        } finally {
            Plugins.forget("OtherProxy");
        }
    }
}
