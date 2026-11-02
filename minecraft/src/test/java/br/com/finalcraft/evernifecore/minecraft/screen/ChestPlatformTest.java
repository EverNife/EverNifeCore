package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.PlayerDouble;
import br.com.finalcraft.evernifecore.util.FCServerUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chest over a platform the test adjusted: what a button asks of the server beyond the chest itself. */
class ChestPlatformTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void aButtonThatRunsACommandAsThePlayerIsCapturedOnAConfiguredPlatform() {
        try (BukkitScreenWorld world = BukkitScreenWorld.install(tempDir, Platforms::capturingCommands)) {
            PlayerDouble player = world.newPlayer("Steve");
            Screen<Layout> screen = Screen.of(1);
            screen.icon(0, Icon.of("minecraft:compass"))
                    .onClick(context -> FCServerUtil.makePlayerExecuteCommand(context.getViewer(), "spawn"));
            world.open(screen, player);
            world.advanceTicks(1);

            world.getClicks().leftClick(player, 0);
            world.advanceTicks(1);

            List<TestPlatform.DispatchedCommand> commands = world.getPlatform().getSenderCommands();
            assertEquals(1, commands.size(), String.valueOf(commands));
            assertEquals("spawn", commands.get(0).command);
            assertEquals("Steve", commands.get(0).sender.getName());
        }
    }

    @Test
    void theDefaultPlatformRefusesToRunACommandAndNamesTheBuilderCall() {
        try (BukkitScreenWorld world = BukkitScreenWorld.install(tempDir)) {
            PlayerDouble player = world.newPlayer("Steve");
            RuntimeException refused = assertThrows(RuntimeException.class,
                    () -> FCServerUtil.makePlayerExecuteCommand(player.asFPlayer(), "spawn"));
            assertTrue(refused.getMessage().contains("capturingCommands()"), refused.getMessage());
        }
    }

    @Test
    void aPlaceholderOnAnIconIsResolvedByTheConfiguredParser() {
        try (BukkitScreenWorld world = BukkitScreenWorld.installWithItemMetadata(tempDir,
                p -> p.parsingWith((viewer, text) -> text.replace("%player_name%", viewer.getName())))) {
            PlayerDouble player = world.newPlayer("Steve");
            Screen<Layout> screen = Screen.of(1);
            screen.icon(0, Icon.of("minecraft:paper").displayName("&eHello %player_name%").integrateToPAPI(true));
            world.open(screen, player);
            world.advanceTicks(1);

            assertEquals("§eHello Steve", world.getSurface().getItem(0).getItemMeta().getDisplayName());
        }
    }
}
