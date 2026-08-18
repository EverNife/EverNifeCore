package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.playerdata.account.Account;
import br.com.finalcraft.evernifecore.playerdata.account.AccountActor;
import br.com.finalcraft.evernifecore.playerdata.account.AccountMember;
import br.com.finalcraft.evernifecore.playerdata.account.Accounts;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigratingCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code network} block: it must name an enabled backend explicitly, the block it replaced is
 * refused rather than ignored, and the account layer it feeds now comes up on every boot - which
 * changes nothing for a server with no links, the property the last group of tests pins down.
 */
@ECoreTest
class NetworkBlockBootTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    /** A storage.yml whose only variable part is what sits under {@code network:}. */
    private File writeYml(String name, String... networkLines) throws IOException {
        StringBuilder yml = new StringBuilder(String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1\"",
                "  off_backend:",
                "    enabled: false",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + name + "_off;DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                ""));
        for (String line : networkLines) {
            yml.append(line).append('\n');
        }
        File file = tempDir.resolve("storage_" + name + ".yml").toFile();
        Files.write(file.toPath(), yml.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ------------------------------------------------------------------
    // the block is required, and explicit
    // ------------------------------------------------------------------

    @Test
    void absentNetworkBlockCancelsTheBoot() {
        File yml = Storages.h2("net_absent").withoutNetworkBlock().writeTo(tempDir);

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(yml));
        assertTrue(error.getMessage().contains("network.storage-backend-id"), error.getMessage());
        //the whole point of refusing: there is no silent inheritance to fall back on
        assertTrue(error.getMessage().contains("default-backend"), error.getMessage());
    }

    @Test
    void theRefusalIsRenderedAsTheBootBanner() {
        File yml = Storages.h2("net_banner").withoutNetworkBlock().writeTo(tempDir);

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(yml));

        //it costs the admin the server, same as an unreachable database, so it gets the same banner
        //instead of a one-line message buried in a stack trace
        String report = error.getMessage();
        assertTrue(report.contains("############"), report);
        assertTrue(report.contains("EVERNIFECORE - STORAGE MISCONFIGURED"), report);
        assertTrue(report.contains("HOW TO FIX"), report);
        assertTrue(report.contains(yml.getPath()), "the banner must name the file to edit: " + report);
        assertTrue(report.indexOf('§') < 0, "no console colour codes in a report that goes to a log file");
    }

    @Test
    void emptyNetworkBackendIsNotTakenAsInheritTheDefault() throws IOException {
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(writeYml("net_empty",
                        "network:", "  storage-backend-id: \"\"")));
        assertTrue(error.getMessage().contains("network.storage-backend-id"), error.getMessage());
    }

    @Test
    void undeclaredNetworkBackendCancelsTheBoot() throws IOException {
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(writeYml("net_undeclared",
                        "network:", "  storage-backend-id: nowhere")));
        assertTrue(error.getMessage().contains("network.storage-backend-id"), error.getMessage());
        assertTrue(error.getMessage().contains("nowhere"), error.getMessage());
    }

    @Test
    void disabledNetworkBackendCancelsTheBoot() throws IOException {
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(writeYml("net_disabled",
                        "network:", "  storage-backend-id: off_backend")));
        assertTrue(error.getMessage().contains("network.storage-backend-id"), error.getMessage());
        assertTrue(error.getMessage().contains("DISABLED"), error.getMessage());
    }

    @Test
    void anEnabledNetworkBackendParsesAndCarriesItsIdleGrace() throws IOException {
        ParsedStorageConfig parsed = StorageYamlParser.parse(writeYml("net_ok",
                "network:", "  storage-backend-id: test_h2", "  idle-grace-seconds: 42"));
        assertEquals("test_h2", parsed.getNetworkBackendName());
        assertEquals(42, parsed.getAccountIdleGraceSeconds());
    }

    @Test
    void aNegativeIdleGraceIsRefused() throws IOException {
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(writeYml("net_negative_grace",
                        "network:", "  storage-backend-id: test_h2", "  idle-grace-seconds: -1")));
        assertTrue(error.getMessage().contains("network.idle-grace-seconds"), error.getMessage());
    }

    // ------------------------------------------------------------------
    // the block it replaced
    // ------------------------------------------------------------------

    @Test
    void theOldAccountsBlockIsRefusedAndNamesWhatReplacedIt() throws IOException {
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(writeYml("net_legacy",
                        "network:", "  storage-backend-id: test_h2",
                        "multi-platform-accounts:", "  enabled: true")));
        assertTrue(error.getMessage().contains("multi-platform-accounts"), error.getMessage());
        assertTrue(error.getMessage().contains("network.storage-backend-id"), error.getMessage());
        assertTrue(error.getMessage().contains("network.idle-grace-seconds"), error.getMessage());
    }

    // ------------------------------------------------------------------
    // always on, and that changes nothing without a link
    // ------------------------------------------------------------------

    @Test
    void theAccountLayerBootstrapsWithNoFlagToTurnItOn() throws IOException {
        PlayerController.initialize(Storages.h2("net_always_on").writeTo(tempDir));
        assertTrue(Accounts.isEnabled(), "the layer comes up with the controller - there is nothing to enable");
    }

    @Test
    void withoutALinkTheCollectionStaysEmptyAndAccountIdEqualsUuid() throws IOException {
        PlayerController.initialize(Storages.h2("net_no_link").writeTo(tempDir));

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Solo").join();
        assertEquals(uuid, playerData.getAccountId(), "a never-linked player's accountId equals its uuid");
        assertEquals(0, Accounts.get().getManager().repository().count().join(),
                "a login alone must not persist an account row - only an explicit link does");
    }

    @Test
    void linkingNeedsNothingInTheYmlBeyondTheNetworkBackend() throws IOException {
        PlayerController.initialize(Storages.h2("net_link").writeTo(tempDir));

        UUID owner = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        PlayerController.handleLogin(owner, "Owner").join();
        PlayerController.handleLogin(alt, "Alt").join();

        Account fused = Accounts.get().link(owner, alt, AccountActor.system()).join();
        assertTrue(fused.getMembers().size() >= 2, "the link must fuse both identities into one account");

        //the member now resolves to the canonical key, which is the whole observable effect of a link
        assertEquals(fused.getAccountId(), PlayerController.handleLogin(alt, "Alt").join().getAccountId());
    }

    @Test
    void aStoredLinkSurvivesARestart() throws IOException {
        PlayerController.initialize(Storages.h2("net_restart").writeTo(tempDir));

        UUID canonicalId = UUID.randomUUID();
        Account canonical = Account.singleton(canonicalId, Accounts.PLATFORM_PROVIDER,
                canonicalId.toString(), "Owner");
        canonical.addMember(new AccountMember("discord", "123456", "Owner#0001"));
        Accounts.get().getManager().saveAndCache(canonical).join();
        PlayerController.shutdown();

        PlayerController.initialize(Storages.h2("net_restart").writeTo(tempDir));
        Account reloaded = Accounts.get().account(canonicalId).join();
        assertEquals(canonicalId, reloaded.getAccountId());
        assertEquals(2, reloaded.getMembers().size());
    }

    // ------------------------------------------------------------------
    // stamped accountId: creation default, lazy upcast, login re-stamp
    // ------------------------------------------------------------------

    @Test
    void freshPlayerDataIsStampedWithItsOwnUuid() {
        UUID uuid = UUID.randomUUID();
        PlayerData playerData = new PlayerData(uuid, "Fresh");
        assertEquals(uuid, playerData.getAccountId());
    }

    @Test
    void preAccountRowUpcastsToAccountIdEqualsUuid() throws IOException {
        //bootstrap registers the base v1->v2 chain (accountId absent -> accountId = uuid)
        PlayerController.initialize(Storages.h2("net_upcast").writeTo(tempDir));

        //a payload written before the account field existed: accountId absent, schemaVersion 1
        UUID uuid = UUID.randomUUID();
        String v1Json = "{\"uuid\":\"" + uuid + "\",\"name\":\"Veteran\",\"schemaVersion\":1}";

        //decode through the migrating codec (the framework's universal read seam) - the raw-node step runs
        Codec<PlayerData> codec = EntitySchemaMigratingCodec.wrap(PlayerData.class,
                new JacksonJsonCodec<>(PlayerData.class), "uuid");
        PlayerData old = codec.decode(v1Json.getBytes(StandardCharsets.UTF_8));

        assertEquals(uuid, old.getAccountId(), "the raw-node upcast defaults accountId to the uuid");
        assertEquals(2, old.getSchemaVersion(), "the upcast advances the base schema version");
        assertTrue(old.isDirty(), "a real upcast must mark the decoded entity dirty for re-persist");
    }

    @Test
    void loginRestampsAccountIdFromStoredTruth() throws IOException {
        PlayerController.initialize(Storages.h2("net_restamp").writeTo(tempDir));

        //a link decided elsewhere: canonical account + alias row for the member already stored
        UUID canonicalId = UUID.randomUUID();
        UUID memberUuid = UUID.randomUUID();
        Account canonical = Account.singleton(canonicalId, Accounts.PLATFORM_PROVIDER,
                canonicalId.toString(), "Owner");
        canonical.addMember(new AccountMember(Accounts.PLATFORM_PROVIDER, memberUuid.toString(), "Alt"));
        Accounts.get().getManager().saveAndCache(canonical).join();
        Accounts.get().getManager().saveAndCache(Account.alias(memberUuid, canonicalId)).join();

        PlayerData member = PlayerController.handleLogin(memberUuid, "Alt").join();
        assertEquals(canonicalId, member.getAccountId(),
                "login must re-stamp the accountId from the stored account truth");

        //stable across a re-login (the same truth re-stamps the same value)
        PlayerData again = PlayerController.handleLogin(memberUuid, "Alt").join();
        assertEquals(canonicalId, again.getAccountId());
    }
}
