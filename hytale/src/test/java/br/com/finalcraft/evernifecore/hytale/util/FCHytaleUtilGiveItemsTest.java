package br.com.finalcraft.evernifecore.hytale.util;

import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Items handed to a player who already left every world are not given - and nothing throws. */
@ECoreTest
class FCHytaleUtilGiveItemsTest {

    @Test
    void givingToAPlayerWhoLeftIsANoOp() {
        try (ECoreTestWorld ignored = Platforms.lenient().install()) {
            HytaleFPlayer gone = HytaleFPlayer.of(new PlayerRef(null, UUID.randomUUID(), "Gone", "en_us", null, null));

            assertDoesNotThrow(() -> FCHytaleUtil.giveItemsTo(gone, Collections.emptyList()));
        }
    }
}
