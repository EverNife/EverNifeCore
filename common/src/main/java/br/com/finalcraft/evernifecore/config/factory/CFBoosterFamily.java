package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.booster.Booster;
import br.com.finalcraft.evernifecore.booster.Boosters;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.value.DynamicValueSpec;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.time.Season;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link Booster} in a plugin's config - {@code {on: money, factor: 1.5, owner: global, lasting: 1h}}, with
 * {@code bonus} instead of {@code factor}, {@code until} (an instant) instead of {@code lasting}, and an optional
 * {@code season: {id, from, to}} - and a {@link DynamicValueSpec} - {@code {base: 100, min: 40, max: 400, step: 5,
 * decay: {amount: 10, every: 1h}}}. A booster read from a config has no origin; whoever grants it names one.
 */
final class CFBoosterFamily {

    private CFBoosterFamily() {
    }

    static void register() {
        ConfigFactory.register(Booster.class).asMap(CFBoosterFamily::encode, CFBoosterFamily::decode);
        ConfigFactory.register(DynamicValueSpec.class).asMap(CFBoosterFamily::encodeSpec, CFBoosterFamily::decodeSpec);
    }

    private static Map<String, Object> encode(Booster booster) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("on", Boosters.shortForm(booster.getVariableId()));
        if (booster.getFactor() != null) {
            map.put("factor", booster.getFactor());
        } else {
            map.put("bonus", booster.getBonus());
        }
        map.put("owner", booster.getOwner().toString());
        if (booster.getUntil() != null) {
            map.put("until", booster.getUntil().toString());
        }
        Season season = booster.getSeason();
        if (season != null) {
            Map<String, Object> written = new LinkedHashMap<>();
            written.put("id", season.getId());
            written.put("from", season.getFrom().toString());
            written.put("to", season.getTo().toString());
            map.put("season", written);
        }
        return map;
    }

    private static Booster decode(Map<String, Object> map) {
        Object on = map.get("on");
        if (on == null) {
            throw new IllegalArgumentException("A booster names what it boosts: { on: money, factor: 1.5 } or { on: finaljobs:money, bonus: 0.25 }.");
        }
        Booster.Builder builder = Booster.on(String.valueOf(on));
        BigDecimal factor = number(map.get("factor"));
        BigDecimal bonus = number(map.get("bonus"));
        if (factor != null) {
            builder.factor(factor);
        }
        if (bonus != null) {
            builder.bonus(bonus);
        }
        if (map.get("owner") != null) {
            builder.forOwner(ProgressOwner.parse(String.valueOf(map.get("owner"))));
        }
        if (map.get("lasting") != null) {
            builder.lasting(FCTimeFrame.fromConfigString(String.valueOf(map.get("lasting"))).toDuration());
        } else if (map.get("until") != null) {
            builder.until(Instant.parse(String.valueOf(map.get("until")).trim()));
        }
        Object season = map.get("season");
        if (season instanceof Map) {
            Map<?, ?> written = (Map<?, ?>) season;
            builder.during(Season.of(String.valueOf(written.get("id")), ZonedDateTime.parse(String.valueOf(written.get("from"))),
                    ZonedDateTime.parse(String.valueOf(written.get("to")))));
        }
        return builder.build();
    }

    private static Map<String, Object> encodeSpec(DynamicValueSpec spec) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("base", spec.getBase());
        if (spec.getMin() != null) {
            map.put("min", spec.getMin());
        }
        if (spec.getMax() != null) {
            map.put("max", spec.getMax());
        }
        map.put("step", spec.getStep());
        if (spec.getDecayEvery() != null) {
            Map<String, Object> decay = new LinkedHashMap<>();
            decay.put("amount", spec.getDecayAmount());
            decay.put("every", FCTimeFrame.of(spec.getDecayEvery()).toConfigString());
            map.put("decay", decay);
        }
        return map;
    }

    private static DynamicValueSpec decodeSpec(Map<String, Object> map) {
        BigDecimal base = number(map.get("base"));
        if (base == null) {
            throw new IllegalArgumentException("A dynamic value starts from a base: { base: 100, min: 40, max: 400, step: 5,"
                    + " decay: { amount: 10, every: 1h } }.");
        }
        DynamicValueSpec.Builder builder = DynamicValueSpec.base(base).min(number(map.get("min"))).max(number(map.get("max")));
        BigDecimal step = number(map.get("step"));
        if (step != null) {
            builder.step(step);
        }
        Object decay = map.get("decay");
        if (decay instanceof Map) {
            Map<?, ?> written = (Map<?, ?>) decay;
            BigDecimal amount = number(written.get("amount"));
            Object every = written.get("every");
            if (amount == null || every == null) {
                throw new IllegalArgumentException("A decay names how much and how often: decay: { amount: 10, every: 1h }.");
            }
            builder.decay(amount, FCTimeFrame.fromConfigString(String.valueOf(every)).toDuration());
        }
        return builder.build();
    }

    private static @Nullable BigDecimal number(@Nullable Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value).trim());
    }
}
