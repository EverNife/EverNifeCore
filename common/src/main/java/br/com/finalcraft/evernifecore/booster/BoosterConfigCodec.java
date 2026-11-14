package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.time.Season;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal: a {@link Booster} in a plugin's config - {@code {on: duels:win, factor: 1.5, owner: global, lasting: 1h}},
 * with {@code bonus} instead of {@code factor}, {@code until} (an instant) instead of {@code lasting}, and an optional
 * {@code season: {id, from, to}}. {@code on} is a domain id, declared or not yet. A booster read from a config has no
 * origin; whoever grants it names one. Registered once by the core's config types.
 */
public final class BoosterConfigCodec {

    private BoosterConfigCodec() {
    }

    public static void register() {
        ConfigFactory.register(Booster.class).asMap(BoosterConfigCodec::encode, BoosterConfigCodec::decode);
    }

    private static Map<String, Object> encode(Booster booster) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("on", booster.getDomainId());
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
            throw new IllegalArgumentException("A booster names the domain it boosts: { on: duels:win, factor: 1.5 } or { on: finaljobs:money, bonus: 0.25 }.");
        }
        Booster.Builder builder = Booster.onId(String.valueOf(on));
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

    private static @Nullable BigDecimal number(@Nullable Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value).trim());
    }
}
