package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.PlayerDouble;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A message sent from a click reaches the player, even with no running core plugin to build a chat bridge from. */
class ChestChatTest {

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;
    private PlayerDouble player;

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
    void aMessageSentFromAClickReachesThePlayer() {
        Screen<Layout> screen = Screen.of(3);
        screen.icon(13, Icon.of("minecraft:emerald")).onClick(click -> click.getViewer().sendMessage("§aThanks for buying"));
        world.open(screen, player);

        world.getClicks().leftClick(player, 13);
        world.advanceTicks(1);

        assertEquals(Collections.singletonList("§aThanks for buying"), player.getMessages());
        assertTrue(world.getPlatform().getLoggedMessages().stream().noneMatch(line -> line.contains("SEVERE")),
                "nothing failed on the way: " + world.getPlatform().getLoggedMessages());
    }

}
