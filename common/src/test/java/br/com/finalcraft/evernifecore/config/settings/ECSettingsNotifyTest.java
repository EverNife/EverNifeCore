package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.notify.NotifyChain;
import br.com.finalcraft.evernifecore.notify.NotifyException;
import br.com.finalcraft.evernifecore.notify.NotifyKind;
import br.com.finalcraft.evernifecore.notify.Notifier;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code Settings.Notify} keys: the shipped chains and durations, and a channel name nobody has refused at boot. */
@ECoreTest
class ECSettingsNotifyTest {

    @TempDir
    Path dir;

    @AfterEach
    void restoreTheShippedDefaults() {
        ECSettings.loadNotifySettings(ConfigFactory.open(dir.resolve("defaults.yml")));
    }

    private static LinkedHashSet<String> step(String... channels) {
        return new LinkedHashSet<>(Arrays.asList(channels));
    }

    @Test
    void withoutTheAdminFileTheChainsAreTheShippedOnes() {
        assertEquals(Arrays.asList(step("title", "sound"), step("chat", "sound"), step("chat")),
                Notifier.get().getChain(NotifyKind.COMPLETE).getSteps());
        assertEquals(NotifyChain.parse("boss-bar", "tracker", "action-bar", "chat"), Notifier.get().getChain(NotifyKind.TRACKED));
        assertEquals(Duration.ZERO, ECSettings.getNotifyDuration(NotifyKind.TRACKED), "a tracked notification stays until cleared");
    }

    @Test
    void anEmptyFileIsSeededWithEveryKind() throws Exception {
        Path file = dir.resolve("config.yml");
        Config config = ConfigFactory.open(file);
        ECSettings.loadNotifySettings(config);
        config.save();

        Config written = ConfigFactory.open(file);
        assertEquals(Arrays.asList("title+sound", "chat+sound", "chat"), written.getStringList("Settings.Notify.chains.complete"));
        assertEquals("3s", written.getString("Settings.Notify.durations.progress"));
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        for (NotifyKind kind : NotifyKind.values()) {
            assertTrue(text.contains(kind.getKey() + ":"), kind + " missing from\n" + text);
        }
    }

    @Test
    void anAdminChainIsWhatTheKindUses() {
        Config config = ConfigFactory.open(dir.resolve("config.yml"));
        config.setValue("Settings.Notify.chains.progress", Arrays.asList("title", "chat"));

        ECSettings.loadNotifySettings(config);

        assertEquals(NotifyChain.parse("title", "chat"), Notifier.get().getChain(NotifyKind.PROGRESS));
    }

    @Test
    void aMisspelledChannelFailsTheBootNamingTheKeyTheValueAndTheChannels() {
        Config config = ConfigFactory.open(dir.resolve("config.yml"));
        config.setValue("Settings.Notify.chains.progress", Collections.singletonList("acton-bar"));

        NotifyException refused = assertThrows(NotifyException.class, () -> ECSettings.loadNotifySettings(config));

        String message = refused.getMessage();
        assertTrue(message.contains("Settings.Notify.chains.progress"), message);
        assertTrue(message.contains("acton-bar"), message);
        assertTrue(message.contains("[chat, action-bar, title, sound, boss-bar, sidebar, toast, tracker]"), message);
        assertEquals(NotifyChain.parse("action-bar", "chat"), Notifier.get().getChain(NotifyKind.PROGRESS),
                "a refused file changes nothing");
    }

    @Test
    void aPluginChannelIsAcceptedBeforeThePluginRegistersIt() {
        Config config = ConfigFactory.open(dir.resolve("config.yml"));
        config.setValue("Settings.Notify.chains.deny", Arrays.asList("finalquests:hologram", "chat"));

        ECSettings.loadNotifySettings(config);

        assertEquals(NotifyChain.parse("finalquests:hologram", "chat"), Notifier.get().getChain(NotifyKind.DENY));
    }
}
