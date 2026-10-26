package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.region.RegionId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The text form of a wallet owner and the owners of logic that have a wallet. */
class CurrencyOwnerTest {

    @Test
    void everyKindReadsBackWhatItWrites() {
        for (CurrencyOwner owner : Arrays.asList(CurrencyOwner.player(UUID.randomUUID()), CurrencyOwner.group("finalparty:abc"),
                CurrencyOwner.global())) {
            assertEquals(owner, CurrencyOwner.parse(owner.toString()));
        }
        assertEquals("global", CurrencyOwner.global().toString());
        assertEquals("group:finalparty:abc", CurrencyOwner.group("finalparty:abc").toString());
    }

    @Test
    void aPlayerAGroupAndTheServerHaveWalletsButAPlaceDoesNot() {
        UUID uuid = UUID.randomUUID();

        assertEquals(CurrencyOwner.player(uuid), CurrencyOwner.of(ProgressOwner.player(uuid)).orElse(null));
        assertEquals(CurrencyOwner.group("finalparty:abc"), CurrencyOwner.of(ProgressOwner.of("group", "finalparty:abc")).orElse(null));
        assertEquals(CurrencyOwner.global(), CurrencyOwner.of(ProgressOwner.global()).orElse(null));
        assertFalse(CurrencyOwner.of(ProgressOwner.region(RegionId.parse("fakeshop:arena"))).isPresent());
        assertFalse(CurrencyOwner.of(ProgressOwner.world("world")).isPresent());
    }

    @Test
    void aTextThatIsNoOwnerIsRefusedShowingEveryForm() {
        for (String text : Arrays.asList("player:not-a-uuid", "region:fakeshop:arena", "", "group:")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> CurrencyOwner.parse(text));
            assertTrue(error.getMessage().contains("player:<uuid>, group:<group id> or global"), error.getMessage());
        }
    }
}
