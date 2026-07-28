package br.com.finalcraft.evernifecore.minecraft.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// Exercises the pure resolver; getCurrent() is deliberately not touched, since only it reads Bukkit.
public class MCDetailedVersionTest {

    @Test
    public void readsTheRevisionStraightFromAVersionedPackage() {
        assertEquals(MCDetailedVersion.v1_7_R4, MCDetailedVersion.resolve("org.bukkit.craftbukkit.v1_7_R4", "1.7.10-R0.1-SNAPSHOT"));
        assertEquals(MCDetailedVersion.v1_16_R3, MCDetailedVersion.resolve("org.bukkit.craftbukkit.v1_16_R3", "1.16.5-R0.1-SNAPSHOT"));
    }

    // From 1.20.5 the server stopped relocating CraftBukkit per version, which is what used to make
    // every modern Paper report the newest known version regardless of what it was running.
    @Test
    public void fallsBackToTheReportedReleaseWhenThePackageIsNotVersioned() {
        assertEquals(MCDetailedVersion.v1_21_R1, MCDetailedVersion.resolve("org.bukkit.craftbukkit", "1.21.1-R0.1-SNAPSHOT"));
        assertEquals(MCDetailedVersion.v1_19_R3, MCDetailedVersion.resolve("org.bukkit.craftbukkit", "1.19.4-R0.1-SNAPSHOT"));
    }

    // A release names no revision, so the newest known one for that release is the closest answer.
    @Test
    public void aReleaseResolvesToItsHighestKnownRevision() {
        assertEquals(MCDetailedVersion.v1_8_R3, MCDetailedVersion.resolve("org.bukkit.craftbukkit", "1.8.8-R0.1-SNAPSHOT"));
        assertEquals(MCDetailedVersion.v1_12_R2, MCDetailedVersion.resolve("org.bukkit.craftbukkit", "1.12.2-R0.1-SNAPSHOT"));
    }

    @Test
    public void aReleaseWithNoMinorPartStillResolves() {
        assertEquals(MCDetailedVersion.v1_21_R1, MCDetailedVersion.resolve("org.bukkit.craftbukkit", "1.21-R0.1-SNAPSHOT"));
    }

    // Nothing known: the caller decides what to default to, and says so out loud.
    @Test
    public void anUnknownReleaseResolvesToNothing() {
        assertNull(MCDetailedVersion.resolve("org.bukkit.craftbukkit", "1.99.0-R0.1-SNAPSHOT"));
        assertNull(MCDetailedVersion.resolve("org.bukkit.craftbukkit", "not-a-version"));
        assertNull(MCDetailedVersion.resolve("org.bukkit.craftbukkit", null));
    }

    @Test
    public void aVersionedPackageWinsOverTheReportedRelease() {
        assertEquals(MCDetailedVersion.v1_16_R1, MCDetailedVersion.resolve("org.bukkit.craftbukkit.v1_16_R1", "1.16.5-R0.1-SNAPSHOT"));
    }

}
