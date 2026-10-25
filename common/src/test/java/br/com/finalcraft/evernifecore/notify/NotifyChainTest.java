package br.com.finalcraft.evernifecore.notify;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.notify.channel.TrackerChannel;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading a chain, picking its step on a platform, and the load-time check that refuses a chain that shows nothing. */
class NotifyChainTest {

    @TempDir
    Path dir;

    private ECoreTestWorld world;
    private ECPluginData core;

    private void platform(String id, IHudAccess.Capability... missing) {
        world = Platforms.lenient().hudWithout(missing).platformProviderId(id).install();
        core = Plugins.fakeCore(dir.resolve("core").toFile());
        Notifier.get().registerStock(core);
    }

    @AfterEach
    void teardown() {
        if (world != null) {
            Plugins.forget(Plugins.CORE);
            world.close();
        }
    }

    @Test
    void aStepJoinsItsChannelsWithAPlus() {
        NotifyChain chain = NotifyChain.parse("Title + sound", "chat");

        assertEquals(Arrays.asList(new LinkedHashSet<>(Arrays.asList("title", "sound")), new LinkedHashSet<>(Arrays.asList("chat"))),
                chain.getSteps());
        assertEquals("[title+sound, chat]", chain.describe());
        assertThrows(IllegalArgumentException.class, () -> NotifyChain.parse("title+", " "));
    }

    @Test
    void theFirstStepWhoseChannelsAreAllSupportedIsTaken() {
        platform("minecraft", IHudAccess.Capability.BOSS_BAR);

        List<NotifyChannel> step = Notifier.get().resolve(NotifyChain.parse("boss-bar+sound", "tracker+sound", "chat"));

        assertEquals(2, step.size(), "boss-bar is missing, so its whole step is skipped: " + step);
        assertTrue(step.get(0) instanceof TrackerChannel);
        assertNull(Notifier.get().resolve(NotifyChain.parse("boss-bar", "nobody:registered-this")),
                "a channel nobody registered is a step this platform does not have");
    }

    @Test
    void validateRefusesAChainWithNoSupportedStepNamingTheChainThePlatformAndWhatItHas() {
        platform("hytale", IHudAccess.Capability.ACTION_BAR, IHudAccess.Capability.BOSS_BAR,
                IHudAccess.Capability.SIDEBAR, IHudAccess.Capability.COMPASS);
        ECPluginData quests = Plugins.fakePluginData("FinalQuests", dir.resolve("quests").toFile());
        try {
            NotifyException refused = assertThrows(NotifyException.class,
                    () -> Notifier.get().validate(quests, "tracked", NotifyChain.parse("boss-bar", "action-bar")));

            String message = refused.getMessage();
            assertTrue(message.contains("[boss-bar, action-bar]"), message);
            assertTrue(message.contains("finalquests:tracked"), message);
            assertTrue(message.contains("(hytale)"), message);
            assertTrue(message.contains("[chat, title, sound, toast, tracker]"), message);

            Notifier.get().validate(quests, "complete", NotifyChain.parse("boss-bar", "title+sound"));
        } finally {
            Plugins.forget("FinalQuests");
        }
    }
}
