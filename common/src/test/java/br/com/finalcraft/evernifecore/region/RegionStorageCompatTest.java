package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.MembershipCheck;
import br.com.finalcraft.evernifecore.region.storage.RegionRecord;
import br.com.finalcraft.evernifecore.region.storage.RegionStorage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A region record written before groups grew leaders, roles and invites still reads, member for member. */
class RegionStorageCompatTest extends ManagedRegionFixture {

    private static final UUID PERMANENT = UUID.fromString("6f1c2a3e-1111-4a5b-9c0d-000000000001");
    private static final UUID TIMED = UUID.fromString("6f1c2a3e-2222-4a5b-9c0d-000000000002");

    @Test
    void anAlphaRegionRecordWithGroupsStillReads() throws IOException {
        RoomFlag flag = registeredRoomFlag(owner);
        RegionRecord stored;
        try (InputStream in = getClass().getResourceAsStream("/region/record-v1-with-groups.json")) {
            assertNotNull(in, "the fixture is on the test classpath");
            stored = ConfigFactory.inMemoryCodec().getObjectMapper().readValue(in, RegionRecord.class);
        }
        new RegionStorage(storage).save(stored).join();

        RegionManager reader = unloadedManager();
        reader.load().join();

        Region arena = reader.get("arena");
        assertNotNull(arena, "the region of the old record is indexed");
        AccessGroup fighters = arena.getGroupIfPresent("fighters");
        assertNotNull(fighters);
        assertEquals(2, fighters.getPriority());
        assertEquals("arena.fight", fighters.getPermission());
        assertEquals(MembershipCheck.onBoundary(), fighters.getMembershipCheck());
        assertEquals(2, fighters.getMembers().size());
        assertNull(fighters.getMembership(PERMANENT).getExpiresAt());
        assertEquals(Instant.parse("2031-02-03T04:05:06Z"), fighters.getMembership(TIMED).getExpiresAt());
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), fighters.getMembership(TIMED).getJoinedAt());
        assertEquals(Tristate.FALSE, fighters.getFlag(flag), "the raw flag value relinks once its flag is registered");
    }
}
