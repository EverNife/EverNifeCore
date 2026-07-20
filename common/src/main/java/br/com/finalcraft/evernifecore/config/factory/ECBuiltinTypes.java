package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.GenericCooldown;
import br.com.finalcraft.evernifecore.fancytext.FancyTextConfigCodec;
import br.com.finalcraft.everylibs.util.numberwrapper.NumberWrapper;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Map;

/**
 * The framework's built-in type registrations for {@link ConfigFactory}
 */
public final class ECBuiltinTypes {

    private ECBuiltinTypes() {
    }

    /**
     * Register every built-in type into {@link ConfigFactory}.
     * Meant to run once at {@link ConfigFactory} class-init.
     * */
    public static void register() {
        CFPositionFamily.register();
        registerCooldown();
        registerNumberWrapper();
        FancyTextConfigCodec.register();
    }

    // ==================== NumberWrapper ====================

    /**
     * The {@link NumberWrapper} (from EveryLibs) has no getter-bean, so Jackson cannot serialize it
     * on its own. This adapter reproduces the bridge the old EverNifeCore copy carried inline
     * ({@code @JsonValue}/{@code @JsonCreator}): it emits the raw underlying {@link Number} - so the
     * exact numeric scalar form is preserved on disk - and rebuilds the wrapper from that number,
     * rejecting a null value just like the original {@code fromConfig}.
     */
    private static void registerNumberWrapper() {
        ConfigFactory.register(NumberWrapper.class).jackson(
                new JsonSerializer<NumberWrapper>() {
                    @Override
                    public void serialize(NumberWrapper value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                        gen.writeObject(value.get()); //raw Number -> Jackson emits the matching numeric scalar
                    }
                },
                new JsonDeserializer<NumberWrapper>() {
                    @Override
                    public NumberWrapper deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                        Number number = parser.readValueAs(Number.class);
                        if (number == null) {
                            throw new IllegalArgumentException("Tried to load a NumberWrapper that is not a Number [null]");
                        }
                        return NumberWrapper.of(number);
                    }
                });
    }

    // ==================== cooldown ====================

    private static final TypeReference<Map<String, Object>> COOLDOWN_MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    /**
     * The standalone, config-backed cooldown ({@link GenericCooldown}) persists as a compact map that omits the
     * implicit persist flag (a stored cooldown is always persistent). The map serializer is keyed to the leaf
     * {@link GenericCooldown} so a future sibling subclass serialized elsewhere is never intercepted by the
     * hierarchy walk. A deserializer keyed to the EXACT abstract {@link Cooldown} type lets
     * {@code getValue(path, Cooldown.class)} rebuild a GenericCooldown (deserialization matches the exact class).
     */
    private static void registerCooldown() {
        ConfigFactory.register(GenericCooldown.class).asMap(
                GenericCooldown::toConfigMap,
                map -> (GenericCooldown) Cooldown.fromConfigMap(map));

        ConfigFactory.register(Cooldown.class).jackson(null, new JsonDeserializer<Cooldown>() {
            @Override
            public Cooldown deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                return Cooldown.fromConfigMap(parser.readValueAs(COOLDOWN_MAP_TYPE));
            }
        });
    }
}
