package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.api.events.account.ECAccountLinkedEvent;
import br.com.finalcraft.evernifecore.api.events.account.ECAccountMergedEvent;
import br.com.finalcraft.evernifecore.api.events.account.ECAccountUnlinkedEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.playerdata.account.Account;
import br.com.finalcraft.evernifecore.playerdata.account.AccountActor;
import br.com.finalcraft.evernifecore.playerdata.account.AccountMember;
import br.com.finalcraft.evernifecore.playerdata.account.Accounts;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The account layer's audit stamps ({@code createdAt} on a minted account, {@code linkedAt}/
 * {@code linkedBy} on a linked member) and the three bus-only events it posts as its link/unlink/merge
 * operations complete. Each op {@code .join()}s, so its posting stage has run by the time control
 * returns and the captured event is already there.
 */
@ECoreTest
class AccountEventsAndAuditTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerDataWorld.tearDown();
    }

    private File writeStorageYml(String dbName) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "network:",
                "  storage-backend-id: test_h2",
                "");
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void linkingTwoPlayers_postsMergedEvent_stampsMembers_andSetsCreatedAt() throws IOException {
        PlayerController.initialize(writeStorageYml("acc_merged_event"));

        UUID target = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        AccountActor actor = AccountActor.admin("Tester");

        List<ECAccountMergedEvent> merged = new CopyOnWriteArrayList<>();
        ECEventSubscription<ECAccountMergedEvent> sub =
                ECEventBus.global().subscribe(ECAccountMergedEvent.class, merged::add);
        try {
            Account fused = Accounts.get().link(target, source, actor).join();

            assertEquals(1, merged.size(), "exactly one merged event");
            ECAccountMergedEvent event = merged.get(0);
            assertEquals(fused.getAccountId(), event.getCanonicalAccountId(), "canonical is the surviving account");
            assertEquals(target, event.getTargetAccountId());
            assertEquals(source, event.getSourceAccountId());
            assertNotEquals(target, event.getCanonicalAccountId(), "two singletons mint a fresh id");
            assertNotEquals(source, event.getCanonicalAccountId());
            assertEquals(2, event.getMembersAfter().size(), "both identities are members after the merge");
            assertEquals(AccountActor.Kind.ADMIN, event.getActor().getKind());
            assertEquals("Tester", event.getActor().getDetail());

            for (AccountMember member : event.getMembersAfter()) {
                assertTrue(member.getLinkedAt() > 0, "each linked member is time-stamped");
                assertEquals("admin:Tester", member.getLinkedBy(), "each linked member records the actor");
            }
        } finally {
            sub.unsubscribe();
        }

        assertTrue(Accounts.get().account(target).join().getCreatedAt() > 0,
                "the minted account records its birth time");
    }

    @Test
    void linkingAnExternalIdentity_postsLinkedEvent_andStampsTheNewMember() throws IOException {
        PlayerController.initialize(writeStorageYml("acc_linked_event"));

        UUID player = UUID.randomUUID();
        AccountActor actor = AccountActor.integration("finalcraftlogin");

        List<ECAccountLinkedEvent> linked = new CopyOnWriteArrayList<>();
        ECEventSubscription<ECAccountLinkedEvent> sub =
                ECEventBus.global().subscribe(ECAccountLinkedEvent.class, linked::add);
        try {
            Account account = Accounts.get().linkExternal(player, "site", "user-1", actor).join();

            assertEquals(1, linked.size(), "exactly one linked event");
            ECAccountLinkedEvent event = linked.get(0);
            assertEquals(account.getAccountId(), event.getAccountId());
            assertEquals("site", event.getLinkedMember().getProvider());
            assertEquals("user-1", event.getLinkedMember().getProviderUid());
            assertEquals(AccountActor.Kind.INTEGRATION, event.getActor().getKind());

            AccountMember external = event.getLinkedMember();
            assertTrue(external.getLinkedAt() > 0, "the linked external identity is time-stamped");
            assertEquals("integration:finalcraftlogin", external.getLinkedBy());
        } finally {
            sub.unsubscribe();
        }

        assertTrue(Accounts.get().account(player).join().getCreatedAt() > 0,
                "the account born from the first link records its birth time");
    }

    @Test
    void unlinkingAMember_postsUnlinkedEvent() throws IOException {
        PlayerController.initialize(writeStorageYml("acc_unlinked_event"));

        UUID target = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        Accounts.get().link(target, source, AccountActor.admin("Tester")).join();

        List<ECAccountUnlinkedEvent> unlinked = new CopyOnWriteArrayList<>();
        ECEventSubscription<ECAccountUnlinkedEvent> sub =
                ECEventBus.global().subscribe(ECAccountUnlinkedEvent.class, unlinked::add);
        try {
            Account after = Accounts.get().unlink(source, AccountActor.admin("Tester")).join();

            assertEquals(1, unlinked.size(), "exactly one unlinked event");
            ECAccountUnlinkedEvent event = unlinked.get(0);
            assertEquals(after.getAccountId(), event.getAccountId());
            assertEquals(source.toString(), event.getUnlinkedMember().getProviderUid(),
                    "the platform member that left is the source uuid");
            assertEquals(1, event.getMembersAfter().size(), "only the target identity remains");
            assertEquals(AccountActor.Kind.ADMIN, event.getActor().getKind());
        } finally {
            sub.unsubscribe();
        }
    }

    @Test
    void noSubscriber_meansNoEventIsBuilt() throws IOException {
        PlayerController.initialize(writeStorageYml("acc_no_listener"));

        //postIfListened: with nobody subscribed the operation still succeeds, it just posts nothing.
        Account fused = Accounts.get()
                .link(UUID.randomUUID(), UUID.randomUUID(), AccountActor.system()).join();
        assertEquals(2, fused.getMembers().size(),
                "the merge itself completes whether or not anyone listens");
    }
}
