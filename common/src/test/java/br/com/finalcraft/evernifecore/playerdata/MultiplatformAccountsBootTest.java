package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.playerdata.account.Account;
import br.com.finalcraft.evernifecore.playerdata.account.AccountMember;
import br.com.finalcraft.evernifecore.playerdata.account.Accounts;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigratingCodec;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The multi-platform-accounts opt-in: parse defaults, the disabled/enabled bootstrap paths, the
 * boot guard against disabling while linked accounts exist, and the stamped accountId on the base
 * entity (creation default, lazy upcast of pre-account rows, and login re-stamp).
 */
@ECoreTest
class MultiplatformAccountsBootTest {


    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerController.shutdown();
        PlayerController.getConfiguredPDSections().clear();
        EntitySchemaMigrations.clear();
    }

    private File writeStorageYml(String dbName, boolean accountsEnabled) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "multi-platform-accounts:",
                "  enabled: " + accountsEnabled,
                "");
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ------------------------------------------------------------------
    // parse
    // ------------------------------------------------------------------

    @Test
    void parseDefaultsToDisabledAndDefaultBackend() throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:parse_defaults;DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "");
        File file = tempDir.resolve("storage_parse_defaults.yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));

        ParsedStorageConfig parsed = StorageYamlParser.parse(file);
        assertFalse(parsed.isMultiplatformAccountsEnabled(), "the account layer must be opt-in (disabled by default)");
        assertEquals("test_h2", parsed.getAccountBackendName(), "absent backend falls back to the default backend");
    }

    @Test
    void parseRejectsDisabledAccountBackend() throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:parse_reject;DB_CLOSE_DELAY=-1\"",
                "  off_backend:",
                "    enabled: false",
                "    type: h2",
                "    url: \"jdbc:h2:mem:parse_reject_off;DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "multi-platform-accounts:",
                "  enabled: true",
                "  storage-backend-id: off_backend",
                "");
        File file = tempDir.resolve("storage_parse_reject.yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(file));
        assertTrue(error.getMessage().contains("multi-platform-accounts.storage-backend-id"), error.getMessage());
    }

    // ------------------------------------------------------------------
    // disabled boot: no identity layer, singleton keying, stamped accountId == uuid
    // ------------------------------------------------------------------

    @Test
    void disabledBootLeavesIdentityLayerDown_andStampsAccountIdWithUuid() throws IOException {
        PlayerController.initialize(writeStorageYml("mpa_disabled", false));
        assertFalse(Accounts.isEnabled(), "the identity layer must not bootstrap when disabled");

        UUID uuid = UUID.randomUUID();
        PlayerData playerData = PlayerController.handleLogin(uuid, "Solo").join();
        assertEquals(uuid, playerData.getAccountId(), "a never-linked player's accountId equals its uuid");
    }

    // ------------------------------------------------------------------
    // boot guard: enable -> link rows exist -> disable refuses; enable again is fine
    // ------------------------------------------------------------------

    @Test
    void enablingThenDisablingWithoutLinksIsHarmless() throws IOException {
        File enabledYml = writeStorageYml("mpa_toggle", true);
        PlayerController.initialize(enabledYml);
        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Toggler").join();
        PlayerController.shutdown();

        //no link was ever made: nothing was written to ec_accounts, so disabling boots cleanly
        PlayerController.initialize(writeStorageYml("mpa_toggle", false));
        assertFalse(Accounts.isEnabled());
        assertEquals(uuid, PlayerController.handleLogin(uuid, "Toggler").join().getAccountId());
    }

    @Test
    void disablingWithStoredLinkedAccountsFailsFast() throws IOException {
        PlayerController.initialize(writeStorageYml("mpa_guard", true));

        //simulate a real link: persist a canonical account row
        UUID canonicalId = UUID.randomUUID();
        Account canonical = Account.singleton(canonicalId, Accounts.PLATFORM_PROVIDER,
                canonicalId.toString(), "Owner");
        canonical.addMember(new AccountMember("discord", "123456", "Owner#0001"));
        Accounts.get().getManager().saveAndCache(canonical).join();
        PlayerController.shutdown();

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> PlayerController.initialize(writeStorageYml("mpa_guard", false)));
        assertTrue(error.getMessage().contains("multi-platform-accounts.enabled"), error.getMessage());

        //re-enabling boots normally and still sees the stored account
        PlayerController.initialize(writeStorageYml("mpa_guard", true));
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
        PlayerController.initialize(writeStorageYml("mpa_upcast", false));

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
        PlayerController.initialize(writeStorageYml("mpa_restamp", true));

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
