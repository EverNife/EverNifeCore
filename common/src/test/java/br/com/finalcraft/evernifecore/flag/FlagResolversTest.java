package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.player.state.EffectLevel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The stock strategies over hand-built layers: most specific first, as the lookup hands them. */
class FlagResolversTest {

    private static final FlagHolder HOLDER = new FlagHolder() {
        @Override
        public String getHolderId() {
            return "test:holder";
        }

        @Override
        public FlagScope getHolderScope() {
            return FlagScope.PLACE;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public <T> FlagValue<T> getFlagValue(Flag<T> flag) {
            return null;
        }

        @Override
        public Map<FlagKey, FlagValue<?>> getFlags() {
            return Collections.emptyMap();
        }

        @Override
        public FlagStore getFlagStore() {
            throw new UnsupportedOperationException();
        }
    };

    /** One layer per value, every one in its own band (the first the highest): no two of them are equals. */
    @SafeVarargs
    private static <T> List<FlagLayer<T>> layers(T... values) {
        List<FlagLayer<T>> layers = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            layers.add(layer(values[i], values.length - i));
        }
        return layers;
    }

    /** The flag a layer names; a resolver never reads it. */
    private static final Flag<String> ANY = new Flag<String>(FlagKey.of("test", "any"), FlagTypes.TEXT, Collections.emptySet(), null) {
    };

    @SuppressWarnings("unchecked")
    private static <T> FlagLayer<T> layer(T value, int band) {
        FlagValue<T> held = FlagValue.permanent(value);
        return new FlagLayer<>((Flag<T>) (Flag<?>) ANY, HOLDER, null, held, Resolution.Reason.LOCAL_VALUE,
                LayerRank.place(held, band, null), FlagLayer.Use.COUNTED);
    }

    private static AmountEvery every(int amount, int seconds) {
        return AmountEvery.of(amount, Duration.ofSeconds(seconds));
    }

    @Test
    void sumByIntervalAddsTwoPlusTenEveryFiveSecondsTo12() {
        List<AmountEvery> sum = FlagResolvers.sumByInterval()
                .resolve(layers(Collections.singletonList(every(2, 5)), Collections.singletonList(every(10, 5)))).getValue();
        assertEquals(Collections.singletonList(every(12, 5)), sum);
    }

    @Test
    void sumByIntervalKeepsTwoEntriesForSevenEverySecondAndSevenEveryTwo() {
        FlagResolver.Resolved<List<AmountEvery>> resolved = FlagResolvers.sumByInterval()
                .resolve(layers(Collections.singletonList(every(7, 1)), Collections.singletonList(every(7, 2))));
        assertEquals(Arrays.asList(every(7, 1), every(7, 2)), resolved.getValue(), "distinct intervals each run on their own tick");
        assertEquals(2, resolved.getCounted().size());
    }

    @Test
    void unionMaxLevelKeepsSpeedTwoOverSpeedOneAndEveryOtherEffect() {
        List<EffectLevel> union = FlagResolvers.unionMaxLevel().resolve(layers(
                Arrays.asList(EffectLevel.of("speed", 1), EffectLevel.of("haste", 1)),
                Collections.singletonList(EffectLevel.of("speed", 2)))).getValue();
        assertEquals(Arrays.asList(EffectLevel.of("speed", 2), EffectLevel.of("haste", 1)), union);
    }

    @Test
    void concatJoinsEveryListInLayerOrderWithoutDedup() {
        List<String> joined = FlagResolvers.<String>concat().resolve(layers(Arrays.asList("say a", "say b"), Collections.singletonList("say a")))
                .getValue();
        assertEquals(Arrays.asList("say a", "say b", "say a"), joined);
    }

    @Test
    void productCappedMultipliesAndStopsAtTheCapAndTheFloor() {
        assertEquals(6.0, FlagResolvers.productCapped(0, 10).resolve(layers(2.0, 3.0)).getValue());
        assertEquals(4.0, FlagResolvers.productCapped(0, 4).resolve(layers(2.0, 3.0)).getValue(), "the cap holds");
        assertEquals(0.5, FlagResolvers.productCapped(0.5, 4).resolve(layers(0.1, 2.0)).getValue(), "the floor holds");
        assertThrows(IllegalArgumentException.class, () -> FlagResolvers.productCapped(5, 1));
    }

    @Test
    void preferAmongEqualsPicksThePreferredOnlyAmongLeadingEquals() {
        FlagResolver<Tristate> prefer = FlagResolvers.preferAmongEquals(Tristate.TRUE);
        List<FlagLayer<Tristate>> unequal = layers(Tristate.FALSE, Tristate.TRUE);
        assertEquals(Tristate.FALSE, prefer.resolve(unequal).getValue(), "a more specific FALSE is the answer, not a tie");

        List<FlagLayer<Tristate>> equal = Arrays.asList(layer(Tristate.FALSE, 0), layer(Tristate.TRUE, 0), layer(Tristate.FALSE, -1));
        FlagResolver.Resolved<Tristate> resolved = prefer.resolve(equal);
        assertEquals(Tristate.TRUE, resolved.getValue());
        assertSame(equal.get(1), resolved.getCounted().get(0), "the preferred layer is the one counted");
    }

    @Test
    void firstDefinedTakesTheFirstLayerAndCountsOnlyIt() {
        FlagResolver.Resolved<String> resolved = FlagResolvers.<String>firstDefined().resolve(layers("near", "far"));
        assertEquals("near", resolved.getValue());
        assertEquals(1, resolved.getCounted().size());
    }

    @Test
    void maxAndMinPickTheExtremeLayer() {
        assertEquals(9, FlagResolvers.<Integer>max().resolve(layers(3, 9, 1)).getValue());
        assertEquals(1, FlagResolvers.<Integer>min().resolve(layers(3, 9, 1)).getValue());
    }

    @Test
    void everyStockStrategyNamesTheIdTheSettingsUse() {
        assertEquals(Arrays.asList("first-defined", "prefer-among-equals", "sum-by-interval", "union-max-level", "concat", "product-capped",
                        "max", "min"),
                Arrays.asList(FlagResolvers.firstDefined().getId(), FlagResolvers.preferAmongEquals(Tristate.TRUE).getId(),
                        FlagResolvers.sumByInterval().getId(), FlagResolvers.unionMaxLevel().getId(), FlagResolvers.concat().getId(),
                        FlagResolvers.productCapped(0, 1).getId(), FlagResolvers.max().getId(), FlagResolvers.min().getId()));
    }
}
