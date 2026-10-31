package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.group.event.GroupFlagChangedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberLeftEvent;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The region sweeper also sweeps the groups of every manager: timed members, timed flag values, stored deadlines. */
@ECoreTest
class GroupSweepTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private GroupWorld parties;
    private GroupKind club;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        club = parties.kind("club", GroupKind.Cardinality.MANY);
    }

    @AfterEach
    void close() {
        parties.close();
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        world.close();
    }

    @Test
    void theSweeperExpiresATimedMemberOfAManagerGroup() {
        Group alpha = parties.group("alpha", club);
        UUID guest = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        alpha.add(guest, Duration.ofMinutes(1));
        alpha.add(member);
        List<GroupMemberLeftEvent> left = parties.recorded(GroupMemberLeftEvent.class);

        parties.advance(Duration.ofMinutes(2));
        parties.runDue();

        assertEquals(1, left.size());
        assertEquals(LeaveCause.EXPIRED, left.get(0).getCause());
        assertEquals(guest, left.get(0).getPlayer());
        assertTrue(alpha.isMember(member));
        assertNull(alpha.getNextDeadline());
    }

    @Test
    void theSweeperDropsATimedFlagValueOfAGroup() {
        Group alpha = parties.group("alpha", club);
        alpha.setFlag(CoreFlags.INVINCIBLE, Tristate.TRUE, Duration.ofSeconds(30));
        List<GroupFlagChangedEvent> changed = parties.recorded(GroupFlagChangedEvent.class);

        parties.advance(Duration.ofMinutes(1));
        parties.runDue();

        assertNull(alpha.getFlag(CoreFlags.INVINCIBLE));
        assertFalse(changed.isEmpty(), "the expiry is a flag change of the group");
    }

    @Test
    void aTimedMemberReadFromStorageIsSweptByTheNextServer() {
        UUID guest = UUID.randomUUID();
        parties.group("alpha", club).add(guest, Duration.ofMinutes(1));
        parties.flush();
        GroupManager next = parties.reopen();
        next.registerKind(club);
        parties.load();
        Group reread = next.get("alpha");
        assertTrue(reread.isMember(guest));

        parties.advance(Duration.ofMinutes(2));
        parties.runDue();

        assertFalse(reread.isMember(guest), "the loaded deadline started the sweeper");
        assertTrue(reread.getStoredMembers().isEmpty());
    }
}
