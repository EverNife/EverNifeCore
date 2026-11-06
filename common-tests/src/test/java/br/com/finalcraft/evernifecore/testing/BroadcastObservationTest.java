package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A broadcast in a test reaches who is online and the platform's console, where the test reads it. */
class BroadcastObservationTest {

    @Test
    void aBroadcastReachesTheOnlinePlayersAndTheBroadcastConsole() {
        TestFPlayerSender steve = Senders.player("Steve");
        try (ECoreTestWorld world = Platforms.lenient().neutralChatAdapter()
                .onlinePlayers(Collections.singletonList(steve)).install()) {
            EverNifeCore.getPlatform().getChatAdapter().broadcast(FancyText.of("arena red opens"));

            steve.assertAnyMessageContains("arena red opens");
            world.platform().getBroadcastConsole().assertAnyMessageContains("arena red opens");
        }
    }

    @Test
    void aTestLocaleMessageBroadcastsToTheSameAudience() {
        TestFPlayerSender steve = Senders.player("Steve");
        TestFPlayerSender alex = Senders.player("Alex");
        try (ECoreTestWorld world = Platforms.lenient().neutralChatAdapter()
                .onlinePlayers(Collections.singletonList(steve)).install()) {
            world.platform().addOnlinePlayer(alex);
            TestLocaleMessage ranking = new TestLocaleMessage("1st Steve");

            ranking.broadcast();

            steve.assertAnyMessageContains("1st Steve");
            alex.assertAnyMessageContains("1st Steve");
            world.platform().getBroadcastConsole().assertAnyMessageContains("1st Steve");
            assertEquals(3, ranking.sendCount());
        }
    }
}
