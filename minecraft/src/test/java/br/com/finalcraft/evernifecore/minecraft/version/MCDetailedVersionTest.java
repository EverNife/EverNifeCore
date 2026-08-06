package br.com.finalcraft.evernifecore.minecraft.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

            // Nothing known: the caller decides what to default to, and says so out loud.
            "org.bukkit.craftbukkit          , 1.99.0-R0.1-SNAPSHOT  , nothing",
            "org.bukkit.craftbukkit          , 26.1-R0.1-SNAPSHOT    , nothing",
            "org.bukkit.craftbukkit          , not-a-version         , nothing",
            "org.bukkit.craftbukkit          , nothing               , nothing",
    })
    void resolvesTheVersionTheServerIsRunning(String serverPackageName, String bukkitVersion, MCDetailedVersion expected) {
        assertEquals(expected, MCDetailedVersion.resolve(serverPackageName, bukkitVersion));
    }

    // The numbers are read off each constant's own name, so nothing but this pins what that produces.
    @Test
    void theNameIsWhatProducesTheNumbers() {
        assertEquals(174, MCDetailedVersion.v1_7_R4.getValue());
        assertEquals(17, MCDetailedVersion.v1_7_R4.getShortValue());
        assertEquals("v1_7", MCDetailedVersion.v1_7_R4.getShortVersion());

        assertEquals(1101, MCDetailedVersion.v1_10_R1.getValue());
        assertEquals(110, MCDetailedVersion.v1_10_R1.getShortValue());
        assertEquals("v1_10", MCDetailedVersion.v1_10_R1.getShortVersion());

        assertEquals(1204, MCDetailedVersion.v1_20_R4.getValue());
        assertEquals(1217, MCDetailedVersion.v1_21_R7.getValue());
        assertEquals(121, MCDetailedVersion.v1_21_R7.getShortValue());
        assertEquals("v1_21", MCDetailedVersion.v1_21_R7.getShortVersion());
    }

    // isLower/isHigher compare getValue(), so a constant out of order would quietly invert them.
    @Test
    void theLadderOnlyEverClimbs() {
        MCDetailedVersion[] ladder = MCDetailedVersion.values();
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(ladder[i - 1].getValue() < ladder[i].getValue(),
                    ladder[i - 1] + " (" + ladder[i - 1].getValue() + ") is declared before "
                            + ladder[i] + " (" + ladder[i].getValue() + ")");
        }
    }

}
