package br.com.finalcraft.evernifecore.minecraft.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Exercises the pure resolver; getCurrent() is deliberately not touched, since only it reads Bukkit.
public class MCDetailedVersionTest {

    @ParameterizedTest(name = "[{index}] {0} reporting {1} is {2}")
    @CsvSource(nullValues = "nothing", value = {
            // A versioned package names the revision outright, and beats whatever the release says.
            "org.bukkit.craftbukkit.v1_7_R4  , 1.7.10-R0.1-SNAPSHOT  , v1_7_R4",
            "org.bukkit.craftbukkit.v1_16_R3 , 1.16.5-R0.1-SNAPSHOT  , v1_16_R3",
            "org.bukkit.craftbukkit.v1_16_R1 , 1.16.5-R0.1-SNAPSHOT  , v1_16_R1",

            // From 1.20.5 the package stopped carrying the revision, leaving the release as the only
            // evidence - and inside a family it is the patch that tells two revisions apart.
            "org.bukkit.craftbukkit          , 1.20-R0.1-SNAPSHOT    , v1_20_R1",
            "org.bukkit.craftbukkit          , 1.20.1-R0.1-SNAPSHOT  , v1_20_R1",
            "org.bukkit.craftbukkit          , 1.20.2-R0.1-SNAPSHOT  , v1_20_R2",
            "org.bukkit.craftbukkit          , 1.20.4-R0.1-SNAPSHOT  , v1_20_R3",
            "org.bukkit.craftbukkit          , 1.20.5-R0.1-SNAPSHOT  , v1_20_R4",
            "org.bukkit.craftbukkit          , 1.20.6-R0.1-SNAPSHOT  , v1_20_R4",
            "org.bukkit.craftbukkit          , 1.21-R0.1-SNAPSHOT    , v1_21_R1",
            "org.bukkit.craftbukkit          , 1.21.1-R0.1-SNAPSHOT  , v1_21_R1",
            "org.bukkit.craftbukkit          , 1.21.4-R0.1-SNAPSHOT  , v1_21_R3",
            "org.bukkit.craftbukkit          , 1.21.8-R0.1-SNAPSHOT  , v1_21_R5",
            "org.bukkit.craftbukkit          , 1.21.11-R0.1-SNAPSHOT , v1_21_R7",

            // Releases whose servers always carried a package resolve by the same rule anyway.
            "org.bukkit.craftbukkit          , 1.8.8-R0.1-SNAPSHOT   , v1_8_R3",
            "org.bukkit.craftbukkit          , 1.12.2-R0.1-SNAPSHOT  , v1_12_R1",
            "org.bukkit.craftbukkit          , 1.19.4-R0.1-SNAPSHOT  , v1_19_R3",

            // A patch published after this table was written still lands on its family's newest.
            "org.bukkit.craftbukkit          , 1.21.99-R0.1-SNAPSHOT , v1_21_R7",

            // The year-based scheme: same plain package, and the drop is where the family used to be.
            "org.bukkit.craftbukkit          , 26.1-R0.1-SNAPSHOT    , v26_1",
            "org.bukkit.craftbukkit          , 26.1.1-R0.1-SNAPSHOT  , v26_1_1",
            "org.bukkit.craftbukkit          , 26.1.2-R0.1-SNAPSHOT  , v26_1_2",
            "org.bukkit.craftbukkit          , 26.2-R0.1-SNAPSHOT    , v26_2",
            // A hotfix of a known drop lands on that drop's newest, same rule as a 1.x patch.
            "org.bukkit.craftbukkit          , 26.2.7-R0.1-SNAPSHOT  , v26_2",

            // Nothing known: the caller decides what to default to, and says so out loud. A drop or a
            // year this table has never heard of is as unknown as an unheard-of 1.x family.
            "org.bukkit.craftbukkit          , 1.99.0-R0.1-SNAPSHOT  , nothing",
            "org.bukkit.craftbukkit          , 26.9-R0.1-SNAPSHOT    , nothing",
            "org.bukkit.craftbukkit          , 27.1-R0.1-SNAPSHOT    , nothing",
            "org.bukkit.craftbukkit          , not-a-version         , nothing",
            "org.bukkit.craftbukkit          , nothing               , nothing",
    })
    void resolvesTheVersionTheServerIsRunning(String serverPackageName, String bukkitVersion, MCDetailedVersion expected) {
        assertEquals(expected, MCDetailedVersion.resolve(serverPackageName, bukkitVersion));
    }

    // The release a constant declares is where its numbers come from, in either naming scheme.
    @Test
    void theDeclaredReleaseIsWhatProducesTheNumbers() {
        assertEquals("1.7", MCDetailedVersion.v1_7_R4.getReleaseFamily());
        assertEquals("1.7.10", MCDetailedVersion.v1_7_R4.getLowestRelease());

        assertEquals("1.21", MCDetailedVersion.v1_21_R1.getReleaseFamily());
        assertEquals("1.21", MCDetailedVersion.v1_21_R1.getLowestRelease());

        assertEquals("1.21", MCDetailedVersion.v1_21_R7.getReleaseFamily());
        assertEquals("1.21.11", MCDetailedVersion.v1_21_R7.getLowestRelease());

        assertEquals("26.1", MCDetailedVersion.v26_1_2.getReleaseFamily());
        assertEquals("26.1.2", MCDetailedVersion.v26_1_2.getLowestRelease());

        assertEquals("26.2", MCDetailedVersion.v26_2.getReleaseFamily());
        assertEquals("26.2", MCDetailedVersion.v26_2.getLowestRelease());
    }

    // isLower/isHigher order the whole table, so a constant out of order would quietly invert them.
    @Test
    void theLadderOnlyEverClimbs() {
        MCDetailedVersion[] ladder = MCDetailedVersion.values();
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(ladder[i].isHigher(ladder[i - 1]),
                    ladder[i] + " (" + ladder[i].getLowestRelease() + ") is declared after "
                            + ladder[i - 1] + " (" + ladder[i - 1].getLowestRelease() + ") but does not compare higher");
        }
    }

    // Minecraft left the 1.x scheme behind, and the comparators have to cross that with it: 26.1 came
    // out after 1.21.11 even though 26 and 1 say nothing about each other digit by digit.
    @Test
    void everyYearBasedReleaseSitsAboveEveryOneThatCameBefore() {
        for (MCDetailedVersion older : MCDetailedVersion.values()) {
            if (!older.getReleaseFamily().startsWith("1.")) continue;

            for (MCDetailedVersion newer : MCDetailedVersion.values()) {
                if (newer.getReleaseFamily().startsWith("1.")) continue;

                assertTrue(newer.isHigher(older), newer + " does not compare higher than " + older);
                assertTrue(newer.isHigherEquals(older), newer + " does not compare higher or equal to " + older);
                assertTrue(older.isLower(newer), older + " does not compare lower than " + newer);
                assertFalse(newer.isEqual(older), newer + " compares equal to " + older);
            }
        }
    }

    // The revision only ever existed as a package segment, so an era that never relocated CraftBukkit
    // must not get a relocated name built for it.
    @Test
    void craftBukkitClassNamesFollowTheRelocationOfTheirEra() {
        assertArrayEquals(
                new String[]{"org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer", "org.bukkit.craftbukkit.entity.CraftPlayer"},
                MCDetailedVersion.v1_16_R3.getCraftBukkitClassNames("entity.CraftPlayer"));
        assertArrayEquals(
                new String[]{"org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer", "org.bukkit.craftbukkit.entity.CraftPlayer"},
                MCDetailedVersion.v1_20_R3.getCraftBukkitClassNames("entity.CraftPlayer"));
        assertArrayEquals(
                new String[]{"org.bukkit.craftbukkit.entity.CraftPlayer"},
                MCDetailedVersion.v1_20_R4.getCraftBukkitClassNames("entity.CraftPlayer"));
        assertArrayEquals(
                new String[]{"org.bukkit.craftbukkit.entity.CraftPlayer"},
                MCDetailedVersion.v26_2.getCraftBukkitClassNames("entity.CraftPlayer"));
    }

    // MCVersion answers per release family, so constants inside one family have to be one version there.
    @Test
    void aReleaseFamilyCountsAsOneVersion() {
        assertEquals(0, MCDetailedVersion.v26_1.compareFamily(MCDetailedVersion.v26_1_2));
        assertEquals(0, MCDetailedVersion.v1_21_R1.compareFamily(MCDetailedVersion.v1_21_R7));
        assertTrue(MCDetailedVersion.v26_1.compareFamily(MCDetailedVersion.v1_21_R7) > 0);
        assertTrue(MCDetailedVersion.v1_21_R7.compareFamily(MCDetailedVersion.v26_2) < 0);
        assertTrue(MCDetailedVersion.v26_2.compareFamily(MCDetailedVersion.v26_1_2) > 0);
    }

}
