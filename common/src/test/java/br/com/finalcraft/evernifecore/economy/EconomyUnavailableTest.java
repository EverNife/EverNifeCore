package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.util.FCEcoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The other half of "no economy": a bridge IS registered, it just never resolves one - a server with
 * Vault and no economy plugin behind it. The text differs from the not-registered case because the
 * fix differs, and telling the two apart is what let the missing wiring hide for releases.
 */
@ECoreTest
public class EconomyUnavailableTest {

    private static final UUID PLAYER_UUID = UUID.randomUUID();

    @BeforeEach
    void installAnEmptyEconomy(ECoreTestWorld world) {
        world.withEconomy(Economies.absent());
    }

    @Test
    void everyMutationAnswersUnavailableWithoutThrowingAndPointsAtTheFix() {
        EcoResponse give = FCEcoUtil.ecoGive(PLAYER_UUID, 1);

        assertEquals(EcoResponse.Reason.UNAVAILABLE, give.getReason());
        assertTrue(give.getDetail().contains("install Vault and an economy plugin"), give.getDetail());
        assertEquals(Currencies.money(), give.getCurrency());
        assertEquals(EcoResponse.Reason.UNAVAILABLE, FCEcoUtil.ecoTake(PLAYER_UUID, 1).getReason());
        assertEquals(EcoResponse.Reason.UNAVAILABLE, FCEcoUtil.ecoSet(PLAYER_UUID, 1).getReason());
    }

    @Test
    void readsAnswerNothingAndAvailabilityIsFalse() {
        assertFalse(FCEcoUtil.isEcoAvailable());
        assertEquals(0, FCEcoUtil.ecoGetInBigDecimal(PLAYER_UUID).signum());
        assertFalse(FCEcoUtil.ecoHasEnough(PLAYER_UUID, 1));
        assertEquals("10.00 money", FCEcoUtil.ecoFormat(BigDecimal.TEN), "without an economy plugin the amount is printed plain");
    }

}
