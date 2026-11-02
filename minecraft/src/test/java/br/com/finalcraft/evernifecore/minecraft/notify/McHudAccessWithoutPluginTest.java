package br.com.finalcraft.evernifecore.minecraft.notify;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.PlayerDouble;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** With no core plugin to build the Adventure bridge from, an action bar and a title still reach the player through Bukkit. */
class McHudAccessWithoutPluginTest {

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;
    private PlayerDouble player;
    private final McHudAccess hud = new McHudAccess(MCDetailedVersion.v1_21_R1, false);

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.install(tempDir);
        player = world.newPlayer("Steve");
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    @Test
    void anActionBarReachesThePlayerThroughTheBukkitApi() {
        hud.sendActionBar(player.asFPlayer(), FancyText.of("§6+25 coins"));

        assertEquals(1, player.getActionBars().size(), player.getActionBars().toString());
        assertTrue(player.getActionBars().get(0).contains("§6+25 coins"), player.getActionBars().toString());
        assertEquals(Collections.emptyList(), player.getMessages(), "an action bar never spills into chat");
        assertTrue(world.getPlatform().getLoggedMessages().stream().noneMatch(line -> line.contains("dropping")),
                "nothing was dropped: " + world.getPlatform().getLoggedMessages());
    }

    @Test
    void aTitleIsShownAndClearedThroughTheBukkitApi() {
        hud.sendTitle(player.asFPlayer(), FancyText.of("§eHappy hour"), FancyText.of("§7double coins"), 10, 40, 10);
        hud.clearTitle(player.asFPlayer());

        assertEquals(2, player.getTitles().size(), player.getTitles().toString());
        assertEquals("§eHappy hour", player.getTitles().get(0).title);
        assertEquals("§7double coins", player.getTitles().get(0).subtitle);
        assertSame(PlayerDouble.Title.CLEARED, player.getTitles().get(1));
    }

}
