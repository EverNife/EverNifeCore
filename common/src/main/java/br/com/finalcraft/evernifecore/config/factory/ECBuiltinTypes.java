package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.Cooldowns;
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
        CFShapeFamily.register();
        CFEconomyFamily.register();
        CFBoosterFamily.register();
        CFScreenFamily.register();
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

    /**
     * A server cooldown in {@code Cooldowns.yml} is a compact map of its {@code CooldownEntry} - identifier, start,
     * duration, mutation clock, and uses/window once a limit counted any - without the persist flag (a stored cooldown
     * is always persistent); read back, it is routed to that file again.
     */
    private static void registerCooldown() {
        ConfigFactory.register(Cooldown.class).asMap(Cooldowns::toConfigMap, Cooldowns::fromConfigMap);
    }
}
