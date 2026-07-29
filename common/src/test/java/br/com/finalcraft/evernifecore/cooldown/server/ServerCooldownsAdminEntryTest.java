package br.com.finalcraft.evernifecore.cooldown.server;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code network.server-cooldowns} entry. Before it existed the collection was a constant with no
 * mention in storage.yml at all, so a name already taken by another plugin killed the boot with
 * nothing for the admin to edit.
 */
@ECoreTest
class ServerCooldownsAdminEntryTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    @Test
    void theEntryIsGeneratedWhenAbsent() {
        File yml = Storages.h2("cd_generated").writeTo(tempDir);
        PlayerController.initialize(yml);

        Config written = ConfigFactory.open(yml);
        assertEquals(ServerCooldowns.DEFAULT_COLLECTION,
                written.getString("network.server-cooldowns.collection", null),
                "the boot must write the entry an admin would otherwise have no way to discover");
    }

    @Test
    void aRenamedCollectionIsObeyed() {
        File yml = Storages.h2("cd_renamed")
                .networkLines("  server-cooldowns:", "    collection: my_own_cooldowns")
                .writeTo(tempDir);
        PlayerController.initialize(yml);

        assertEquals("my_own_cooldowns", ServerCooldowns.get().getCollection());
    }

    @Test
    void aRenamedCollectionIsNotOverwrittenOnTheNextBoot() {
        File yml = Storages.h2("cd_keep")
                .networkLines("  server-cooldowns:", "    collection: my_own_cooldowns")
                .writeTo(tempDir);
        PlayerController.initialize(yml);

        //the entry writer is idempotent: it must never walk back over the admin's own value
        Config written = ConfigFactory.open(yml);
        assertEquals("my_own_cooldowns", written.getString("network.server-cooldowns.collection", null));
    }

    @Test
    void anInvalidCollectionNameIsRefusedAtParse() {
        File yml = Storages.h2("cd_invalid")
                .networkLines("  server-cooldowns:", "    collection: \"not a name\"")
                .writeTo(tempDir);

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> PlayerController.initialize(yml));
        assertTrue(error.getMessage().contains("network.server-cooldowns.collection"), error.getMessage());
    }

    @Test
    void noCacheIsRefusedAtBind() {
        File yml = Storages.h2("cd_nocache")
                .networkLines("  server-cooldowns:", "    cache:", "      policy: NOCACHE")
                .writeTo(tempDir);

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> PlayerController.initialize(yml));
        //the reason matters as much as the refusal: the flush walks the cached rows
        assertTrue(error.getMessage().contains("NOCACHE"), error.getMessage());
        assertTrue(error.getMessage().contains("network.server-cooldowns"), error.getMessage());
    }

    @Test
    void aTtlPolicyIsAccepted() {
        File yml = Storages.h2("cd_ttl")
                .networkLines("  server-cooldowns:", "    cache:", "      policy: TTL",
                        "      ttlSeconds: 300")
                .writeTo(tempDir);

        //TTL is the useful setting on a network with no change feed, where ALWAYS means another
        //server's write is never seen at all
        PlayerController.initialize(yml);
        assertTrue(ServerCooldowns.isEnabled());
    }

    @Test
    void theCooldownsAreBoundOnTheNetworkBackend() {
        File yml = Storages.h2("cd_backend")
                .extraBackend("other", "    type: memory")
                .networkBackendId("other")
                .writeTo(tempDir);
        PlayerController.initialize(yml);

        //they have no storage-backend-id of their own: the network family moves as one unit
        assertEquals("other", ServerCooldowns.get().getBackendName());
    }

    @Test
    void aLockLessNetworkBackendWarnsAboutTheServerCooldowns() {
        File yml = Storages.groupedFile().writeTo(tempDir);

        List<String> warnings = Logs.capture(() -> PlayerController.initialize(yml));

        //the bind guard's network-family branch, reached through the real call site: a call site that
        //went back to claiming these rows are not network-wide would drop this line and nothing else
        assertTrue(warnings.stream().anyMatch(line -> line.contains("Server cooldowns")
                        && line.contains("optimistic lock")),
                "expected the lock-less network backend warning, got: " + warnings);
    }
}
