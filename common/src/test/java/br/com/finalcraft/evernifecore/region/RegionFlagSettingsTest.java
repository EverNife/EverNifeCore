package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.region.event.RegionChangedEvent;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A region's own settings for a flag: typed, falling back to the server's, evented and written. */
class RegionFlagSettingsTest extends ManagedRegionFixture {

    @Test
    void settingsOfTheWrongTypeAreRefusedNamingTheRightOne() {
        RoomFlag flag = registeredRoomFlag(owner);
        Region region = created(manager().create("room", WORLD, box(0, 0, 9, 9)));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> region.setFlagSettings(flag, new FlagSettings()));

        assertTrue(refused.getMessage().contains(RoomSettings.class.getName()), refused.getMessage());
        assertNull(region.getFlagSettings(flag));
    }

    @Test
    void effectiveSettingsAreTheRegionsOwnElseTheServers() {
        RoomFlag flag = registeredRoomFlag(owner);
        Region region = created(manager().create("room", WORLD, box(0, 0, 9, 9)));
        RoomSettings own = new RoomSettings();
        own.setGreeting("welcome to the room");

        assertSame(flag.getSettings(), region.getEffectiveFlagSettings(flag));
        region.setFlagSettings(flag, own);
        assertSame(own, region.getFlagSettings(flag));
        assertSame(own, region.getEffectiveFlagSettings(flag));

        assertTrue(region.removeFlagSettings(flag));
        assertFalse(region.removeFlagSettings(flag), "nothing left to remove");
        assertSame(flag.getSettings(), region.getEffectiveFlagSettings(flag));
    }

    @Test
    void everyChangePostsFlagSettingsAndIsWritten() {
        RoomFlag flag = registeredRoomFlag(owner);
        Region region = created(manager().create("room", WORLD, box(0, 0, 9, 9)));
        manager().flush().join();
        List<RegionChangedEvent> changes = record(RegionChangedEvent.class);
        backend.resetCalls();

        region.setFlagSettings(flag, new RoomSettings());
        manager().flush().join();
        int writesAfterSet = backend.callsTo("save");
        region.removeFlagSettings(flag);
        manager().flush().join();

        assertEquals(2, changes.size());
        for (RegionChangedEvent change : changes) {
            assertEquals(RegionChangedEvent.Type.FLAG_SETTINGS, change.getType());
        }
        assertEquals(1, writesAfterSet);
        assertEquals(2, backend.callsTo("save"));
    }
}
