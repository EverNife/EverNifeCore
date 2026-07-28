package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.util.FCEcoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The other half of "no economy": a provider IS registered, it just never resolves one - a server with
 * Vault and no economy plugin behind it. The error text differs from the not-registered case because the
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
    void everyCallThrowsAndPointsAtTheFix() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoGet(PLAYER_UUID));
        assertTrue(error.getMessage().contains("No economy available"), error.getMessage());
        assertTrue(error.getMessage().contains("isEcoAvailable"), error.getMessage());

        assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoGive(PLAYER_UUID, 1));
        assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoTake(PLAYER_UUID, 1));
        assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoSet(PLAYER_UUID, 1));
        assertThrows(IllegalStateException.class, () -> FCEcoUtil.ecoHasEnough(PLAYER_UUID, 1));
    }

    @Test
    void availabilityIsFalseWithoutThrowing() {
        assertFalse(FCEcoUtil.isEcoAvailable());
    }

}
