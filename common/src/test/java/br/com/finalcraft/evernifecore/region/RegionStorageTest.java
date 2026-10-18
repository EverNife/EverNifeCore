package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.MembershipCheck;
import br.com.finalcraft.evernifecore.testing.Plugins;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a manager writes is what the next manager on the same storage reads; transient regions write nothing. */
class RegionStorageTest extends ManagedRegionFixture {

    private static final String FLAG_OWNER = "RegionFlagOwner";

    @Test
    void aSecondManagerReadsBackEverythingTheFirstWrote() {
        RoomFlag flag = registeredRoomFlag(owner);
        Instant deadline = Instant.parse("2031-02-03T04:05:06Z");
        UUID member = UUID.randomUUID();
        RegionManager first = manager();
        Region parent = created(first.create("parent", WORLD, box(0, 0, 20, 20)));
        Region child = created(first.create("child", WORLD, box(1, 1, 5, 5), RegionOptions.defaults()
                .withParent(parent).withPriority(7).withTags("arena", "pvp").withExpiresAt(deadline)
                .withMetadata(JsonNodeFactory.instance.objectNode().put("rounds", 3))));
        child.setFlag(flag, Tristate.TRUE);
        RoomSettings settings = new RoomSettings();
        settings.setGreeting("fight!");
        child.setFlagSettings(flag, settings);
        AccessGroup fighters = child.getGroup("fighters");
        fighters.setPriority(2);
        fighters.setPermission("arena.fight");
        fighters.setMembershipCheck(MembershipCheck.onBoundary());
        fighters.addMember(member, Duration.ofDays(3650));
        fighters.setFlag(flag, Tristate.FALSE);
        first.flush().join();
        first.close().join();
        assertTrue(RegionLookup.get().byOwner(owner).isEmpty(), "closing takes the regions out of the index");

        RegionManager second = manager();
        second.load().join();

        Region loaded = second.get("child");
        assertNotNull(loaded);
        assertSame(second.get("parent"), loaded.getParent(), "the parent is restored by name");
        assertEquals(child.getShape(), loaded.getShape());
        assertEquals(7, loaded.getPriority());
        assertEquals(child.getTags(), loaded.getTags());
        assertEquals(3, loaded.getMetadata().get("rounds").asInt());
        assertEquals(deadline, loaded.getExpiresAt());
        assertEquals(Tristate.TRUE, loaded.getFlag(flag));
        assertEquals(settings, loaded.getFlagSettings(flag));
        AccessGroup loadedGroup = loaded.getGroupIfPresent("fighters");
        assertEquals(2, loadedGroup.getPriority());
        assertEquals("arena.fight", loadedGroup.getPermission());
        assertEquals(MembershipCheck.onBoundary(), loadedGroup.getMembershipCheck());
        assertEquals(fighters.getMembership(member).getExpiresAt(), loadedGroup.getMembership(member).getExpiresAt());
        assertEquals(Tristate.FALSE, loadedGroup.getFlag(flag));
        assertEquals(Collections.singletonList(loaded), RegionLookup.get().at(WORLD, 3, 50, 3).subList(0, 1));
    }

    @Test
    void theDisplayNameAndTheDescriptionComeBackAndSoDoesTheirAbsence() {
        RegionManager first = manager();
        Region named = created(first.create("named", WORLD, box(0, 0, 9, 9)));
        created(first.create("plain", WORLD, box(20, 20, 29, 29)));
        named.setDisplayName("&6Arena Vermelha");
        named.setDescription("&7Two teams, one flag");
        first.flush().join();
        first.close().join();

        RegionManager second = manager();
        second.load().join();

        assertEquals("&6Arena Vermelha", second.get("named").getDisplayName());
        assertEquals("&7Two teams, one flag", second.get("named").getDescription());
        assertNull(second.get("plain").getDisplayName());
        assertNull(second.get("plain").getDescription());
    }

    @Test
    void valuesAndSettingsOfAFlagWhoseOwnerIsGoneAreWrittenBackAsTheyWere() {
        ECPluginData flagOwner = Plugins.fakePluginData(FLAG_OWNER, tempDir.resolve("flags").toFile());
        try {
            RoomFlag flag = registeredRoomFlag(flagOwner);
            Region region = created(manager().create("room", WORLD, box(0, 0, 9, 9)));
            region.setFlag(flag, Tristate.TRUE);
            RoomSettings settings = new RoomSettings();
            settings.setGreeting("kept raw");
            region.setFlagSettings(flag, settings);
            manager().flush().join();
            manager().close().join();
            FlagRegistry.get().unregisterAll(flagOwner);

            RegionManager reader = manager();
            reader.load().join();
            Region raw = reader.get("room");
            assertNotNull(raw.getUnknownFlags().get("regionflagowner:room"), "the value stays raw while its flag is unknown");
            raw.addTag("touched");   //any change rewrites the whole record
            reader.flush().join();
            reader.close().join();

            RoomFlag back = registeredRoomFlag(flagOwner);
            RegionManager third = manager();
            third.load().join();
            Region again = third.get("room");
            assertEquals(Tristate.TRUE, again.getFlag(back));
            assertEquals(settings, again.getFlagSettings(back));
        } finally {
            FlagRegistry.get().unregisterAll(flagOwner);
            Plugins.forget(FLAG_OWNER);
        }
    }

    @Test
    void aFlagHandlerIsNeverStored() {
        RoomFlag flag = registeredRoomFlag(owner);
        Region region = created(manager().create("handled", WORLD, box(0, 0, 9, 9)));
        region.setFlag(flag, Tristate.TRUE);
        region.setFlagHandler(owner, flag, context -> {
        });
        manager().flush().join();
        manager().close().join();

        RegionManager reader = manager();
        reader.load().join();
        Region loaded = reader.get("handled");

        assertEquals(Tristate.TRUE, loaded.getFlag(flag), "the value is stored");
        assertNull(loaded.getFlagHandler(flag), "the handler is code: the owner sets it again on every enable");
    }

    @Test
    void removingARegionDeletesItAndATransientRegionIsNeverWritten() {
        RegionManager manager = manager();
        Region doomed = created(manager.create("doomed", WORLD, box(0, 0, 9, 9)));
        manager.flush().join();
        manager.remove(doomed);
        manager.flush().join();
        backend.resetCalls();

        Region transientRegion = created(manager.create("fleeting", WORLD, box(20, 20, 29, 29),
                RegionOptions.defaults().withPersistence(Persistence.TRANSIENT)));
        transientRegion.addTag("changed");
        manager.flush().join();
        assertEquals(0, backend.callsTo("save"), "a live transient region is never written, even after a change");
        manager.remove(transientRegion);
        manager.flush().join();
        assertEquals(0, backend.callsTo("save"));
        assertEquals(0, backend.callsTo("delete"));

        manager.close().join();
        assertTrue(storage.isOpen(), "the core never closes the owner's storage");
        RegionManager reader = manager();
        reader.load().join();
        assertNull(reader.get("doomed"));
        assertNull(reader.get("fleeting"));
    }
}
