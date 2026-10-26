package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.util.FCEcoUtil;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A platform that registered no economy bridge at all: nothing moves, nothing throws, and the refusal
 * names the wiring bug instead of blaming the server for a missing economy plugin.
 */
@ECoreTest
public class EconomyNotRegisteredTest {

    private static final UUID PLAYER_UUID = UUID.randomUUID();

    @Test
    void everyMutationIsRefusedAsAWiringBug() {
        EcoResponse take = FCEcoUtil.ecoTake(PLAYER_UUID, 1);

        assertEquals(EcoResponse.Reason.UNAVAILABLE, take.getReason());
        assertTrue(take.getDetail().contains("No economy provider registered"), take.getDetail());
        assertEquals(EcoResponse.Reason.UNAVAILABLE, FCEcoUtil.ecoGive(PLAYER_UUID, 1).getReason());
        assertEquals(EcoResponse.Reason.UNAVAILABLE, FCEcoUtil.ecoSet(PLAYER_UUID, 1).getReason());
    }

    @Test
    void availabilityIsTheWayToAskAndReadsAreZero() {
        assertFalse(FCEcoUtil.isEcoAvailable());
        assertEquals(0.0, FCEcoUtil.ecoGet(PLAYER_UUID));
        assertFalse(FCEcoUtil.ecoHasEnough(PLAYER_UUID, 1));
    }

}
