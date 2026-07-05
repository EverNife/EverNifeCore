package br.com.finalcraft.evernifecore.config.factory;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.GenericCooldown;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

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
    }

    // ==================== cooldown ====================

    private static final TypeReference<Map<String, Object>> COOLDOWN_MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    /**
     * The standalone, config-backed cooldown ({@link GenericCooldown}) persists as a compact map that omits the
     * implicit persist flag (a stored cooldown is always persistent). The map serializer is keyed to the leaf
     * {@link GenericCooldown} so a {@link br.com.finalcraft.evernifecore.cooldown.PlayerCooldown} - a sibling
     * subclass serialized by fields inside its PlayerData - is never intercepted by the hierarchy walk. A
     * deserializer keyed to the EXACT abstract {@link Cooldown} type lets {@code getValue(path, Cooldown.class)}
     * rebuild a GenericCooldown without touching PlayerCooldown (deserialization matches the exact class).
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
