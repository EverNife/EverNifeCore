package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.economy.LevelCurve;
import br.com.finalcraft.everylibs.util.numberwrapper.Balance;
import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The economy types in a config: a {@link Currency} by its id, a {@link CurrencyOwner} as {@code player:<uuid>},
 * {@code group:<id>} or {@code global}, a {@link LevelCurve} as a map with its {@code type}, and a {@link Balance} as
 * the plain number, read back under {@link NumericSpec#MONEY} (a section with another rule reapplies it in
 * {@code postLoad}).
 */
final class CFEconomyFamily {

    private CFEconomyFamily() {
    }

    static void register() {
        ConfigFactory.register(Currency.class).asString(Currency::getId, Currencies::require);
        ConfigFactory.register(CurrencyOwner.class).asString(CurrencyOwner::toString, CurrencyOwner::parse);
        ConfigFactory.register(LevelCurve.class).asMap(CFEconomyFamily::encode, CFEconomyFamily::decode);
        ConfigFactory.register(Balance.class).jackson(
                new JsonSerializer<Balance>() {
                    @Override
                    public void serialize(Balance value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                        gen.writeNumber(value.get());
                    }
                },
                new JsonDeserializer<Balance>() {
                    @Override
                    public Balance deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                        return Balance.of(NumericSpec.MONEY, new BigDecimal(parser.getValueAsString().trim()));
                    }
                });
    }

    private static Map<String, Object> encode(LevelCurve curve) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (curve instanceof LevelCurve.Polynomial && ((LevelCurve.Polynomial) curve).isLinear()) {
            map.put("type", "linear");
            map.put("per-level", ((LevelCurve.Polynomial) curve).getB());
        } else if (curve instanceof LevelCurve.Polynomial) {
            map.put("type", "quadratic");
            map.put("a", ((LevelCurve.Polynomial) curve).getA());
            map.put("b", ((LevelCurve.Polynomial) curve).getB());
        } else if (curve instanceof LevelCurve.Table) {
            map.put("type", "table");
            map.put("totals", ((LevelCurve.Table) curve).getTotals());
        } else {
            throw new IllegalArgumentException("A " + curve.getClass().getName() + " has no config form: only LevelCurve.linear,"
                    + " quadratic and table are written to a config.");
        }
        return map;
    }

    private static LevelCurve decode(Map<String, Object> map) {
        String type = String.valueOf(map.get("type")).trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case "linear":
                return LevelCurve.linear(number(map, "per-level"));
            case "quadratic":
                return LevelCurve.quadratic(number(map, "a"), number(map, "b"));
            case "table":
                Object totals = map.get("totals");
                if (!(totals instanceof List)) {
                    throw new IllegalArgumentException("A table level curve lists its totals: { type: table, totals: [100, 300, 700] }.");
                }
                List<BigDecimal> values = new ArrayList<>();
                for (Object total : (List<?>) totals) {
                    values.add(new BigDecimal(String.valueOf(total).trim()));
                }
                return LevelCurve.table(values);
            default:
                throw new IllegalArgumentException("'" + map.get("type") + "' is not a level curve: write { type: linear, per-level: 1000 },"
                        + " { type: quadratic, a: 2.5, b: 40 } or { type: table, totals: [100, 300, 700] }.");
        }
    }

    private static BigDecimal number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("A " + map.get("type") + " level curve needs '" + key + "'.");
        }
        return new BigDecimal(String.valueOf(value).trim());
    }
}
