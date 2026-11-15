package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.config.plugin.RegionSettings;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A world one plugin disabled in its region settings: its regions there go silent, another plugin's still speak. */
@ECoreTest
class RegionDisabledWorldsTest {

    private static final String WORLD = "minigames";

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private RegionWorld arenas;
    private RegionWorld towns;
    private Flag<String> motto;
    private final TestFPlayerSender alice = Senders.player("alice");

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        arenas = RegionWorld.open("Arenas", tempDir.resolve("arenas"));
        towns = RegionWorld.open("Towns", tempDir.resolve("towns"));
        motto = new Flag<String>(FlagKey.of(towns.getOwner(), "motto"), FlagTypes.TEXT, Collections.singleton(EventSource.NONE), null) {
        };
        FlagRegistry.get().register(towns.getOwner(), motto);
    }

    @AfterEach
    void close() {
        FlagRegistry.get().unregisterAll(towns.getOwner());
        towns.close();
        arenas.close();
        world.close();
    }

    private static RegionSettings disabling(String... worlds) {
        RegionSettings settings = new RegionSettings();
        settings.setDisabledWorlds(Arrays.asList(worlds));
        return settings;
    }

    @Test
    void aRegionIsSilentInAWorldItsOwnerDisabledWhileAnotherOwnersRegionThereDecides() {
        Region ring = arenas.cuboid("ring", WORLD, 0, 0, 0, 10, 10, 10);
        ring.setPriority(10);
        ring.setFlag(motto, "arena rules");
        towns.cuboid("square", WORLD, 0, 0, 0, 20, 10, 20).setFlag(motto, "town rules");
        assertEquals("arena rules", arenas.getLookup().resolve(motto, WORLD, 5, 5, 5, alice));

        arenas.getManager().setSettings(disabling(WORLD));

        assertEquals("town rules", arenas.getLookup().resolve(motto, WORLD, 5, 5, 5, alice));
        assertEquals("town rules", arenas.getLookup().explain(motto, WORLD, 5, 5, 5, alice).getValue());
        assertNull(arenas.getLookup().explain(motto, ring, alice).getValue(), "asked alone, the ring has nothing to say");
        assertTrue(arenas.getLookup().at(WORLD, 5, 5, 5).contains(ring), "the region still exists and answers where it is");
    }

    @Test
    void aWorldIsDisabledEverywhereOnlyWhenEveryOpenManagerDisabledIt() {
        assertFalse(RegionManager.isDisabledEverywhere(WORLD));

        arenas.getManager().setSettings(disabling(WORLD));
        assertFalse(RegionManager.isDisabledEverywhere(WORLD), "the towns still act there");

        towns.getManager().setSettings(disabling(WORLD, "lobby"));
        assertTrue(RegionManager.isDisabledEverywhere(WORLD));
        assertFalse(RegionManager.isDisabledEverywhere("world"));
    }

    @Test
    void noOpenManagerDisablesNoWorld() {
        arenas.getManager().setSettings(disabling(WORLD));
        towns.getManager().setSettings(disabling(WORLD));
        assertTrue(RegionManager.isDisabledEverywhere(WORLD));

        arenas.close();
        towns.close();

        // A holder that follows the player (a party) still decides where no manager is open.
        assertFalse(RegionManager.isDisabledEverywhere(WORLD));
    }

    @Test
    void openingAgainWithSettingsReplacesThemOnTheSameManager() {
        RegionSettings reloaded = disabling(WORLD);

        RegionManager same = RegionManager.of(arenas.getOwner(), arenas.getStorage(), reloaded);

        assertSame(arenas.getManager(), same);
        assertSame(reloaded, same.getSettings());
        assertFalse(same.isActiveIn(WORLD));
    }
}
