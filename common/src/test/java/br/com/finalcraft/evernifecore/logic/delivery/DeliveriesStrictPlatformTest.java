package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static br.com.finalcraft.evernifecore.logic.delivery.DeliveriesTest.node;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A login claim that hits a platform call the strict double was never taught fails the test, not just the log. */
class DeliveriesStrictPlatformTest {

    @TempDir
    Path tempDir;

    @Test
    void aLoginClaimRefusedByAnUnconfiguredDoubleFailsTheWorldNamingTheBuilder() throws Exception {
        //no platformProviderId(...): reading the command effect asks the platform which one it is
        ECoreTestWorld world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().install();
        LogicWorld shop = LogicWorld.open("FakeShop", tempDir);
        try {
            TestFPlayerSender alex = shop.player("Alex");
            shop.deliveries().enqueue(alex.getUniqueId(), DeliveryOrigin.of("FakeShop", "test", ""), DeliveryPolicy.KEEP_SELF_CONTAINED,
                    null, Collections.singletonList(node("{'type': 'command', 'command': 'say hi'}"))).get(5, TimeUnit.SECONDS);
            Deliveries.start();
            PlayerData data = PlayerController.getOrCreate(alex.getUniqueId()).get(5, TimeUnit.SECONDS);

            ECEventBus.global().post(new ECPlayerFullyLoggedInEvent(data, false));
        } finally {
            shop.close();
        }

        AssertionError failure = assertThrows(AssertionError.class, world::close);
        assertTrue(failure.getMessage().contains("Platforms.strict().platformProviderId(\"test\")"), failure.getMessage());
    }

}
