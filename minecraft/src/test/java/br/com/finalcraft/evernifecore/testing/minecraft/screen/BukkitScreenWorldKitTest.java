package br.com.finalcraft.evernifecore.testing.minecraft.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** What a plugin's chest test gets from {@link BukkitScreenWorld#install} with no setup of its own. */
class BukkitScreenWorldKitTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void theNameOfAnIconIsReadBackFromAPlainInstall() {
        try (BukkitScreenWorld world = BukkitScreenWorld.install(tempDir)) {
            PlayerDouble player = world.newPlayer("Steve");
            Screen<Layout> screen = Screen.of(1);
            screen.icon(0, Icon.of("minecraft:paper").displayName("§eHello"));

            world.open(screen, player);
            world.advanceTicks(1);

            assertEquals("§eHello", world.getSurface().getItem(0).getItemMeta().getDisplayName());
        }
    }

    @Test
    void aTimeFrameInAScreensTextNeedsNoLocaleScanFirst() throws Exception {
        Map<Field, Object> words = clearTimeFrameWords();
        try (BukkitScreenWorld world = BukkitScreenWorld.install(tempDir)) {
            PlayerDouble player = world.newPlayer("Steve");
            Screen<Layout> screen = Screen.of(1);
            screen.icon(0, Icon.of("minecraft:paper").displayName("%cooldown%"));
            screen.addReplacer(new RegexReplacer<Long>()
                    .addParser("cooldown", millis -> FCTimeFrame.of(millis).getFormattedDiscursive()), 90_000L);

            world.open(screen, player);
            world.advanceTicks(1);

            assertEquals("1 minute and 30 seconds", world.getSurface().getItem(0).getItemMeta().getDisplayName());
        } finally {
            for (Map.Entry<Field, Object> word : words.entrySet()) {
                word.getKey().set(null, word.getValue());
            }
        }
    }

    @Test
    void aPlayerOfTheWorldIsOnlineOnItsPlatform() {
        try (BukkitScreenWorld world = BukkitScreenWorld.install(tempDir)) {
            PlayerDouble steve = world.newPlayer("Steve");

            FPlayer found = EverNifeCore.getPlatform().getPlayer(steve.getUniqueId());

            assertNotNull(found, "the player the world made is the platform's too");
            assertEquals("Steve", found.getName());
        }
    }

    //another test of this JVM may have loaded the core locale already: put FCTimeFrame back to before any scan
    private static Map<Field, Object> clearTimeFrameWords() throws IllegalAccessException {
        Map<Field, Object> previous = new LinkedHashMap<>();
        for (Field field : FCTimeFrame.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && LocaleMessage.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                previous.put(field, field.get(null));
                field.set(null, null);
            }
        }
        return previous;
    }
}
