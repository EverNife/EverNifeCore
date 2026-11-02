package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A dynamic value moves with each bump inside its bounds and drifts back to its base with the injected clock. */
class DynamicValueTest {

    private static final ProgressOwner SERVER = ProgressOwner.global();

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;

    @BeforeEach
    void open() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().install();
        shop = LogicWorld.open("FakeShop", tempDir);
    }

    @AfterEach
    void close() {
        shop.close();
        world.close();
    }

    private static DynamicValueSpec diamondPrice() {
        return DynamicValueSpec.base(100).min(new BigDecimal("40")).max(new BigDecimal("120")).step(new BigDecimal("5"))
                .decay(new BigDecimal("10"), Duration.ofHours(1)).build();
    }

    private static void assertValue(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }

    @Test
    void eachBumpMovesItAStepUpToMaxAndTheDecayPullsItBackToTheBaseWithoutPassingIt() {
        DynamicValue price = shop.dynamicValues().define("diamond-price", diamondPrice());

        assertValue("100", price.get(SERVER));
        assertValue("105", price.bump(SERVER));
        for (int i = 0; i < 5; i++) {
            price.bump(SERVER);
        }
        assertValue("120", price.get(SERVER));

        shop.advance(Duration.ofMinutes(59));
        assertValue("120", price.get(SERVER));
        shop.advance(Duration.ofMinutes(1));
        assertValue("110", price.get(SERVER));
        shop.advance(Duration.ofMinutes(30));
        assertValue("110", price.bump(SERVER, BigDecimal.ZERO));
        shop.advance(Duration.ofMinutes(30));
        assertValue("100", price.get(SERVER)); //the zero bump kept the half hour already counted
        shop.advance(Duration.ofHours(5));
        assertValue("100", price.get(SERVER));
    }

    @Test
    void belowTheBaseItIsClampedToMinAndDecaysUpward() {
        DynamicValue price = shop.dynamicValues().define("diamond-price", diamondPrice());

        price.set(SERVER, new BigDecimal("10"));
        assertValue("40", price.get(SERVER));
        shop.advance(Duration.ofHours(2));
        assertValue("60", price.get(SERVER));
        price.reset(SERVER);
        assertValue("100", price.get(SERVER));
    }

    @Test
    void eachOwnerHasItsStateAndTheValueReadsAsAVariableOfTheContextsOwner() {
        DynamicValue price = shop.dynamicValues().define("diamond-price", diamondPrice());
        ProgressOwner market = ProgressOwner.world("market");

        price.bump(market);

        assertValue("100", price.get(SERVER));
        assertEquals("105", shop.context().owner(market).build().resolve("{fakeshop:diamond-price}"));
        assertEquals("100", shop.context().build().resolve("{fakeshop:diamond-price}"));
    }

    @Test
    void theStateOutlivesItsDynamicValuesAndARedefinitionKeepsIt() {
        DynamicValues values = DynamicValues.of(shop.getOwner(), shop.getStorage());
        values.define("diamond-price", diamondPrice()).bump(SERVER);
        DynamicValues.closeOwnedBy(shop.getOwner());

        DynamicValues reopened = DynamicValues.of(shop.getOwner(), shop.getStorage());
        DynamicValue price = reopened.define("fakeshop:diamond-price", DynamicValueSpec.base(100).max(new BigDecimal("1000")).step(BigDecimal.TEN).build());

        assertValue("105", price.get(SERVER));
        assertValue("115", price.bump(SERVER));
    }

    @Test
    void aSpecRefusesABaseOutsideItsBoundsAndRoundTripsThroughAConfig() {
        IllegalArgumentException outside = assertThrows(IllegalArgumentException.class,
                () -> DynamicValueSpec.base(10).min(new BigDecimal("40")).build());
        assertTrue(outside.getMessage().contains("between min and max"), outside.getMessage());
        IllegalArgumentException foreign = assertThrows(IllegalArgumentException.class,
                () -> shop.dynamicValues().define("otherplugin:price", diamondPrice()));
        assertTrue(foreign.getMessage().contains("another plugin"), foreign.getMessage());

        Config config = ConfigFactory.open(tempDir.resolve("prices.yml"));
        config.setValue("diamond", diamondPrice());
        config.save();
        assertEquals(diamondPrice(), ConfigFactory.open(tempDir.resolve("prices.yml")).getValue("diamond", DynamicValueSpec.class));
    }

    @Test
    void twoWritesOfOneRowLandingNewestFirstLeaveTheLastStateInTheBackend() {
        DynamicValue price = shop.dynamicValues().define("diamond-price", diamondPrice());
        ScriptedStorage backend = shop.getBackend();
        backend.holdWrites();
        price.bump(SERVER);
        price.bump(SERVER);

        while (backend.heldWrites() > 0) {
            backend.releaseNewestWrite();
        }
        backend.releaseWrites();
        DynamicValues.closeOwnedBy(shop.getOwner());

        assertValue("110", new BigDecimal(backend.lastLanded(DynamicValues.COLLECTION, DynamicValues.Row.class).get().value));
    }
}
