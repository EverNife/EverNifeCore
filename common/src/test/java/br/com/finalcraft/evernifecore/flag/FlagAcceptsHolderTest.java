package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A value on a holder its flag does not serve is kept, ignored by the resolution, and warned about when it loads. */
@ECoreTest
class FlagAcceptsHolderTest {

    private static final String WORLD = "world";

    /** A group-only setting: a region by itself has no members to count. */
    private static final class MaxMembers extends Flag<Integer> {
        MaxMembers(ECPluginData owner) {
            super(FlagKey.of(owner, "max-members"), FlagTypes.INTEGER, Collections.singleton(EventSource.NONE), null);
        }

        @Override
        public boolean accepts(FlagHolder holder) {
            return !(holder instanceof Region);
        }
    }

    /** A value that follows the player by scope, but read only where a place gives it: a holder of an actor is refused. */
    private static final class PlacesOnly extends Flag<Integer> {
        PlacesOnly(ECPluginData owner) {
            super(FlagKey.of(owner, "places-only"), FlagTypes.INTEGER, Collections.singleton(EventSource.NONE), null);
        }

        @Override
        public FlagScope getScope() {
            return FlagScope.ACTOR;
        }

        @Override
        public boolean accepts(FlagHolder holder) {
            return holder.getHolderScope() == FlagScope.PLACE;
        }
    }

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private RegionWorld regions;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        regions = RegionWorld.open("Guilds", tempDir.resolve("guilds"));
    }

    @AfterEach
    void close() {
        FlagRegistry.get().unregisterAll(regions.getOwner());
        regions.close();
        world.close();
    }

    @Test
    void aValueOnARefusedHolderIsIgnoredAndItsLoadWarnsOnce() {
        Region hall = regions.cuboid("hall", WORLD, 0, 0, 0, 10, 10, 10);
        MaxMembers maxMembers = new MaxMembers(regions.getOwner());
        ObjectNode stored = JsonNodeFactory.instance.objectNode();
        stored.set("value", FlagTypes.INTEGER.toJson(12));
        hall.getFlagStore().putRaw(maxMembers.getKey().toString(), stored);

        List<String> logged = Logs.capture(() -> FlagRegistry.get().register(regions.getOwner(), maxMembers));
        assertTrue(logged.stream().anyMatch(line -> line.contains("does not apply to") && line.contains(hall.getHolderId())), String.valueOf(logged));
        assertEquals(Integer.valueOf(12), hall.getFlag(maxMembers), "the value is kept");

        Resolution<Integer> resolution = regions.getLookup().explain(maxMembers, WORLD, 5, 5, 5, null);
        assertEquals(null, resolution.getValue(), "the resolution ignores it");
        assertTrue(resolution.getLayers().isEmpty());
    }

    @Test
    void aHolderTheFlagServesStillCounts() {
        Region hall = regions.cuboid("hall", WORLD, 0, 0, 0, 10, 10, 10);
        MaxMembers maxMembers = new MaxMembers(regions.getOwner());
        FlagRegistry.get().register(regions.getOwner(), maxMembers);
        TestFPlayerSender officer = Senders.player("officer");
        hall.getGroup("officers").add(officer.getUniqueId());
        hall.getGroup("officers").setFlag(maxMembers, 5);
        assertEquals(Integer.valueOf(5), regions.getLookup().resolve(maxMembers, hall, officer));
    }

    @Test
    void aValueOnAnActorHolderTheFlagRefusesIsIgnoredByTheResolution() {
        Region hall = regions.cuboid("hall", WORLD, 0, 0, 0, 10, 10, 10);
        PlacesOnly placesOnly = new PlacesOnly(regions.getOwner());
        FlagRegistry.get().register(regions.getOwner(), placesOnly);
        TestFPlayerSender member = Senders.player("member").at(WORLD, 5, 5, 5);
        regions.actorHolder("party", member.getUniqueId()).setFlag(placesOnly, 7);

        Resolution<Integer> fromTheParty = regions.explain(placesOnly, member);
        assertEquals(null, fromTheParty.getValue(), "the party's value is refused by the flag");
        assertTrue(fromTheParty.getLayers().isEmpty(), String.valueOf(fromTheParty.getLayers()));

        hall.setFlag(placesOnly, 3);
        assertEquals(Integer.valueOf(3), regions.getLookup().resolve(placesOnly, member), "the place still counts, alone");
        assertEquals(1, regions.explain(placesOnly, member).getLayers().size());
    }
}
