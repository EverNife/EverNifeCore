package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everylibs.util.numberwrapper.Balance;
import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The economy types cross a config file through the one {@code ConfigFactory} registration and come back equal. */
@ECoreTest
class EconomyCodecTest {

    private static Config open(Path dir) {
        return ConfigFactory.open(dir.resolve("economy.yml"));
    }

    @Test
    void everyLevelCurveRoundTripsInItsTypedMapForm(@TempDir Path dir) {
        List<LevelCurve> curves = Arrays.asList(LevelCurve.linear(new BigDecimal("1000")),
                LevelCurve.quadratic(new BigDecimal("2.5"), new BigDecimal("40")),
                LevelCurve.table(Arrays.asList(new BigDecimal("100"), new BigDecimal("300"), new BigDecimal("700"))));
        Config config = open(dir);
        for (int i = 0; i < curves.size(); i++) {
            config.setValue("curve" + i, curves.get(i));
        }
        config.save();

        Config reopened = open(dir);
        for (int i = 0; i < curves.size(); i++) {
            assertEquals(curves.get(i), reopened.getValue("curve" + i, LevelCurve.class));
        }
        Map<?, ?> quadratic = reopened.getValue("curve1", LinkedHashMap.class);
        assertEquals("quadratic", quadratic.get("type"));
    }

    @Test
    void aWalletOwnerAndABalanceRoundTripAsScalars(@TempDir Path dir) {
        CurrencyOwner player = CurrencyOwner.player(UUID.randomUUID());
        Config config = open(dir);
        config.setValue("owner", player);
        config.setValue("group", CurrencyOwner.group("finalparty:abc"));
        config.setValue("balance", Balance.of(NumericSpec.MONEY, new BigDecimal("12.5")));
        config.save();

        Config reopened = open(dir);
        assertEquals(player, reopened.getValue("owner", CurrencyOwner.class));
        assertEquals(player.toString(), reopened.getValue("owner", String.class));
        assertEquals(CurrencyOwner.group("finalparty:abc"), reopened.getValue("group", CurrencyOwner.class));
        assertEquals(0, reopened.getValue("balance", Balance.class).get().compareTo(new BigDecimal("12.50")));
    }

    @Test
    void aCurrencyIsWrittenByItsIdAndReadBackAsTheRegisteredOne(@TempDir Path dir, ECoreTestWorld world) {
        try (LogicWorld shop = LogicWorld.open("FakeShop", dir)) {
            TestCurrency gold = Economies.currency("fakeshop:gold");
            world.withCurrency(shop.getOwner(), gold);
            Config config = open(dir);
            config.setValue("price-currency", gold);
            config.save();

            Config reopened = open(dir);
            assertEquals("fakeshop:gold", reopened.getValue("price-currency", String.class));
            assertSame(gold, reopened.getValue("price-currency", Currency.class));
        }
    }
}
