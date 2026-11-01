package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.group.event.GroupDissolvedEvent;
import br.com.finalcraft.evernifecore.storage.Persistence;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The owner's shutdown writes its persistent groups and dissolves its transient ones; a discard writes nothing. */
@ECoreTest
class GroupOwnerShutdownTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private GroupWorld parties;
    private GroupKind party;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        party = parties.kind("party", GroupKind.Cardinality.MANY);
    }

    @AfterEach
    void close() {
        parties.close();
        world.close();
    }

    @Test
    void closeOwnedByWritesThePendingGroupsAndDissolvesTheTransientOnes() {
        UUID steve = UUID.randomUUID();
        parties.group("kept", party).add(steve);
        Group wave = parties.getManager().create("wave", party, GroupOptions.defaults().withPersistence(Persistence.TRANSIENT));
        wave.add(steve);
        List<GroupDissolvedEvent> dissolved = parties.recorded(GroupDissolvedEvent.class);

        GroupManager closing = parties.getManager();
        Thread closer = new Thread(() -> GroupManager.closeOwnedBy(parties.getOwner()));
        closer.start();
        while (closer.isAlive()) {
            parties.runDue();
        }

        assertTrue(closing.isClosed());
        assertNull(GroupManager.ofIfPresent(parties.getOwner()));
        assertEquals(1, dissolved.size());
        assertEquals(DissolveCause.OWNER_DISABLED, dissolved.get(0).getCause());
        assertTrue(wave.isDissolved());

        GroupManager next = parties.reopen();
        next.registerKind(party);
        parties.load();
        assertNotNull(next.get("kept"), "the pending write landed before the manager let go");
        assertTrue(next.get("kept").isMember(steve));
        assertNull(next.get("wave"), "a transient group never reaches the storage");
    }

    @Test
    void discardOwnedByWritesNothingAndForgetsTheManager() {
        parties.group("lost", party);
        GroupManager discarded = parties.getManager();
        int saves = parties.getBackend().callsTo("save");

        GroupManager.discardOwnedBy(parties.getOwner());
        parties.runDue();

        assertTrue(discarded.isClosed());
        assertEquals(saves, parties.getBackend().callsTo("save"));
        assertFalse(GroupManager.all().contains(discarded));
    }
}
