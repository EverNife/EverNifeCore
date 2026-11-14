package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.booster.Booster;
import br.com.finalcraft.evernifecore.booster.BoosterConfigCodec;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.logic.value.DynamicValueSpec;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link Booster} in a plugin's config (its codec is {@link BoosterConfigCodec}) and a {@link DynamicValueSpec} -
 * {@code {base: 100, min: 40, max: 400, step: 5, decay: {amount: 10, every: 1h}}}.
 */
final class CFBoosterFamily {

    private CFBoosterFamily() {
    }

    static void register() {
        BoosterConfigCodec.register();
        ConfigFactory.register(DynamicValueSpec.class).asMap(CFBoosterFamily::encodeSpec, CFBoosterFamily::decodeSpec);
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
