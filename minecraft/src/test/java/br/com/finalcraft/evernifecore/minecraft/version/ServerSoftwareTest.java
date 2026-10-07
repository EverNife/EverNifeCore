package br.com.finalcraft.evernifecore.minecraft.version;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each server software posed by the classes it ships, including the ones it bundles from the software
 * it was built on - that bundle is what a detection in the wrong order would mistake it for.
 */
class ServerSoftwareTest {

    private static final String CRAFTBUKKIT = "org.bukkit.craftbukkit.Main";
    private static final String SPIGOT = "org.spigotmc.SpigotConfig";
    private static final String PAPER = "io.papermc.paper.configuration.Configuration";
    private static final String LEGACY_PAPER = "com.destroystokyo.paper.PaperConfig";

    @Test
    void aVanillaDerivedServerIsTheMostSpecificForkItCarries() {
        assertEquals(ServerSoftware.CRAFTBUKKIT, ServerSoftware.detect(runtimeWith(CRAFTBUKKIT)));
        assertEquals(ServerSoftware.SPIGOT, ServerSoftware.detect(runtimeWith(CRAFTBUKKIT, SPIGOT)));
        assertEquals(ServerSoftware.PAPER, ServerSoftware.detect(runtimeWith(CRAFTBUKKIT, SPIGOT, PAPER)));
        assertEquals(ServerSoftware.PAPER, ServerSoftware.detect(runtimeWith(CRAFTBUKKIT, SPIGOT, LEGACY_PAPER)),
                "Paper before its configuration rework still answers Paper");
    }

    @Test
    void aHybridIsItselfAndNotThePaperItBundles() {
        assertEquals(ServerSoftware.ARCLIGHT, ServerSoftware.detect(runtimeWith("io.izzel.arclight.api.Arclight", CRAFTBUKKIT, SPIGOT)));
        assertEquals(ServerSoftware.CRUCIBLE, ServerSoftware.detect(runtimeWith("io.github.crucible.api.CrucibleEventBus", CRAFTBUKKIT)));
        assertEquals(ServerSoftware.CATSERVER, ServerSoftware.detect(runtimeWith("catserver.api.bukkit.ForgeEventV2", SPIGOT)));
        assertEquals(ServerSoftware.MOHIST, ServerSoftware.detect(runtimeWith("com.mohistmc.forge.MohistEventBus", PAPER)));
        assertEquals(ServerSoftware.MOHIST, ServerSoftware.detect(runtimeWith("com.mohistmc.api.event.BukkitHookForgeEvent", SPIGOT)));
    }

    @Test
    void youerIsNotTheMohistItComesFrom() {
        //Youer 1.21.1 build 920 ships Paper and Purpur configuration next to its own class
        assertEquals(ServerSoftware.YOUER, ServerSoftware.detect(runtimeWith("com.mohistmc.youer.Youer", PAPER, SPIGOT, CRAFTBUKKIT)));
        assertEquals(ServerSoftware.YOUER, ServerSoftware.detect(runtimeWith("com.mohistmc.youer.Youer", "com.mohistmc.forge.MohistEventBus")),
                "even a build that also carried a Mohist bridge");
    }

    @Test
    void nothingRecognizableIsUnknownAndNoHybrid() {
        assertEquals(ServerSoftware.UNKNOWN, ServerSoftware.detect(runtimeWith()));
        assertFalse(ServerSoftware.UNKNOWN.isHybrid());
        assertFalse(ServerSoftware.PAPER.isHybrid());
        assertTrue(ServerSoftware.YOUER.isHybrid());
    }

    private static ServerSoftware.ClassPresence runtimeWith(String... loaded) {
        Set<String> present = new HashSet<>(Arrays.asList(loaded));
        return present::contains;
    }

}
