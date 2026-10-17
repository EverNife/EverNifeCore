package br.com.finalcraft.evernifecore.minecraft.worldedit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldEditForgeTest {

    @Test
    void onlyA1_7_10ServerWithThePluginAndNoModIsWarned() {
        String warning = WorldEditForge.bootWarning(true, true, false);
        assertNotNull(warning);
        assertTrue(warning.contains("https://github.com/GTNewHorizons/worldedit-gtnh"), warning);
        assertTrue(warning.contains("mods/"), warning);

        assertNull(WorldEditForge.bootWarning(true, true, true), "plugin and mod both present");
        assertNull(WorldEditForge.bootWarning(true, false, false), "no WorldEdit at all");
        assertNull(WorldEditForge.bootWarning(false, true, false), "another game version: the plugin has its adapter there");
    }

    @Test
    void theModsWorldReplacesBukkitWorldOnlyOn1_7_10WithTheMod() {
        assertTrue(WorldEditForge.replacesBukkitWorld(true, true));
        assertFalse(WorldEditForge.replacesBukkitWorld(false, true), "1.12.2 runs WorldEdit 6 too and keeps its BukkitWorld");
        assertFalse(WorldEditForge.replacesBukkitWorld(true, false));
    }

    @Test
    void theModIsAbsentFromTheTestClasspath() {
        assertEquals(false, WorldEditForge.isModPresent());
    }
}
