package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.util.FCEcoUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A platform that registered no economy provider at all: every call has to fail loudly, reads included.
 * A shop that quietly charges nothing is worse than one that breaks.
 */
@ECoreTest
public class EconomyNotRegisteredTest {

    private static final UUID PLAYER_UUID = UUID.randomUUID();

    @Test
    void everyCallThrowsAnActionableError() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoGet(PLAYER_UUID));
        assertTrue(error.getMessage().contains("No economy provider registered"), error.getMessage());

        assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoGetInBigDecimal(PLAYER_UUID));
        assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoHasEnough(PLAYER_UUID, 1));
        assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoGive(PLAYER_UUID, 1));
        assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoTake(PLAYER_UUID, 1));
        assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoSet(PLAYER_UUID, 1));
        assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoFormat(BigDecimal.TEN));
    }

    @Test
    void availabilityIsTheWayToAskWithoutBreaking() {
        assertFalse(FCEcoUtil.isEcoAvailable());
    }

}
