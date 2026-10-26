package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.core.BoosterEntry;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.time.Window;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.player.state.EffectLevel;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/** The stock {@link FlagType}s. */
public final class FlagTypes {

    public static final FlagType<Tristate> TRISTATE = new ScalarType<>("tristate", Tristate.class, "true, false or undefined",
            text -> Tristate.fromText(text), value -> value.name().toLowerCase(Locale.ROOT), Arrays.asList("true", "false", "undefined"));

    public static final FlagType<Integer> INTEGER = new ScalarType<Integer>("integer", Integer.class, "a whole number, e.g. 5",
            text -> Integer.valueOf(text.trim()), String::valueOf, Collections.emptyList()) {
        @Override
        public JsonNode toJson(Integer value) {
            return IntNode.valueOf(value);
        }
    };

    public static final FlagType<Double> DOUBLE = new ScalarType<Double>("double", Double.class, "a number, e.g. 2.5",
            text -> Double.valueOf(text.trim()), String::valueOf, Collections.emptyList()) {
        @Override
        public JsonNode toJson(Double value) {
            return DoubleNode.valueOf(value);
        }
    };

    public static final FlagType<String> TEXT = new ScalarType<>("text", String.class, "any text",
            text -> text, value -> value, Collections.emptyList());

    public static final FlagType<WorldLocPos> LOCATION = new ScalarType<>("location", WorldLocPos.class,
            "world|x|y|z, e.g. world|10.5|64|-3", FlagTypes::parseLocation, WorldLocPos::serialize, Collections.emptyList());

    public static final FlagType<AmountEvery> AMOUNT_EVERY = new ScalarType<>("amount-every", AmountEvery.class,
            "<amount> every <duration>, the amount 0 or more and the duration 1s or more, e.g. 2 every 5s", AmountEvery::parse,
            AmountEvery::toString, Arrays.asList("1 every 1s", "1 every 5s", "2 every 5s"));

    public static final FlagType<EffectLevel> EFFECT = new ScalarType<>("effect", EffectLevel.class,
            "effect:level with the platform's effect id, the level 1 when left out, e.g. speed:2", EffectLevel::parse,
            EffectLevel::toString, Collections.emptyList());

    /**
     * A weather the platform knows ({@link IPlayerStateAccess#toWeatherId}): {@code clear}, {@code rain} or one of its own
     * ids. One it does not know is not a value - stored, it stays raw like any value that does not parse.
     */
    public static final FlagType<String> WEATHER = new ScalarType<>("weather", String.class, "clear, rain or a weather id of this server",
            text -> EverNifeCore.getPlatform().getPlayerState().toWeatherId(text), value -> value,
            Arrays.asList(IPlayerStateAccess.CLEAR, IPlayerStateAccess.RAIN));

    /** Lines of text, typed separated by {@code ;} - {@code console:say hi; player:spawn}. */
    /** A list of {@link #AMOUNT_EVERY} separated by commas; a single {@code 2 every 5s} is a list of one. */
    public static final FlagType<List<AmountEvery>> AMOUNT_EVERY_LIST = listOf(AMOUNT_EVERY, ",");

    public static final FlagType<List<String>> TEXT_LIST = listOf(TEXT, ";");

    /** Effects with their level, typed separated by {@code ,} - {@code speed:2, night_vision}. */
    public static final FlagType<List<EffectLevel>> EFFECT_LIST = listOf(EFFECT, ",");

    /**
     * What a region boosts for whoever stands in it, typed {@code on=finaljobs:money factor=1.25, on=exp-points bonus=0.5};
     * each entry names exactly one of {@code factor} and {@code bonus}.
     */
    public static final FlagType<List<BoosterEntry>> BOOSTERS = listOf(checked(jackson("booster", BoosterEntry.class), BoosterEntry::check), ",");

    private FlagTypes() {
    }

    /**
     * Effects of any plugin, each written as {@code {type: <name>, <parameter>: <value>...}} (or {@code <name>: <value>}),
     * typed as one such map or a {@code [...]} list of them. An effect whose type no plugin registered yet is kept as
     * written and reads once it registers; it is skipped meanwhile. They are read as definitions of the plugin that
     * registered {@code flag}.
     *
     * @param holdableOnly refuse an effect that cannot be held and given back, naming {@code flag}
     */
    public static FlagType<List<FlagDefinition<Effect>>> effects(FlagKey flag, boolean holdableOnly) {
        return new NodeType<List<FlagDefinition<Effect>>>("effect-list", "{type: message, text: hi} or a [...] list of such effects") {
            @Override
            List<FlagDefinition<Effect>> read(JsonNode node) throws FlagParseException {
                List<FlagDefinition<Effect>> effects = new ArrayList<>();
                for (JsonNode each : node.isArray() ? node : (Iterable<JsonNode>) Collections.singletonList(node)) {
                    effects.add(effect(flag, holdableOnly, each));
                }
                if (effects.isEmpty()) {
                    throw new FlagParseException("The list of effects of " + flag + " is empty: write at least one, e.g. " + accepted + ".");
                }
                return Collections.unmodifiableList(effects);
            }

            @Override
            public JsonNode toJson(List<FlagDefinition<Effect>> value) {
                ArrayNode array = mapper().createArrayNode();
                value.forEach(effect -> array.add(effect.getRaw()));
                return array;
            }
        };
    }

    /** A {@link Gate} - {@code {require: [<condition>...], cost: [<cost>...]}} - read as a definition of the plugin that registered {@code flag}. */
    public static FlagType<FlagDefinition<Gate>> gate(FlagKey flag) {
        return new NodeType<FlagDefinition<Gate>>("gate", "{require: [{permission: vip.mine}], cost: [{type: money, amount: 100}]}") {
            @Override
            FlagDefinition<Gate> read(JsonNode node) throws FlagParseException {
                try {
                    return new FlagDefinition<>(node, Gate.parse(callerOf(flag), node, Window.getServerZone()), () -> null);
                } catch (TypeRegistryException refused) {
                    throw new FlagParseException("The gate of " + flag + " does not read: " + refused.getMessage(), refused);
                }
            }

            @Override
            public JsonNode toJson(FlagDefinition<Gate> value) {
                return value.getRaw();
            }
        };
    }

    private static FlagDefinition<Effect> effect(FlagKey flag, boolean holdableOnly, JsonNode node) throws FlagParseException {
        Effect effect;
        try {
            effect = Effects.parse(callerOf(flag), node, Window.getServerZone());
        } catch (TypeRegistryException refused) {
            throw new FlagParseException("An effect of " + flag + " does not read: " + refused.getMessage(), refused);
        }
        if (holdableOnly && Effects.getPendingType(effect) == null && !effect.isHoldable()) {
            String type = node.has(TypeRegistry.TYPE_FIELD) ? node.get(TypeRegistry.TYPE_FIELD).asText() : node.fieldNames().next();
            throw new FlagParseException(flag.getName() + " holds effects that can be given back when the player leaves, and '" + type
                    + "' cannot: it is not holdable. Put it in effects-on-enter, or use a holdable effect (gamemode, potion-effect, fly,"
                    + " player-time, player-weather).");
        }
        return new FlagDefinition<>(node, effect, () -> Effects.getPendingType(effect));
    }

    private static ECPluginData callerOf(FlagKey flag) throws FlagParseException {
        Flag<?> registered = FlagRegistry.get().get(flag);
        if (registered == null) {
            throw new FlagParseException("Flag " + flag + " is not registered, so nobody owns the definitions in its value yet:"
                    + " register it first with FlagRegistry.get().register(owner, flag).");
        }
        return registered.getOwner();
    }

    /** {@code type}, with {@code check} run on every value it reads; an {@link IllegalArgumentException} of it refuses the value. */
    private static <T> FlagType<T> checked(FlagType<T> type, Consumer<T> check) {
        return new FlagType<T>() {
            @Override
            public String getId() {
                return type.getId();
            }

            @Override
            public Class<T> getValueType() {
                return type.getValueType();
            }

            @Override
            public T parse(String text) throws FlagParseException {
                return checked(type.parse(text));
            }

            @Override
            public String format(T value) {
                return type.format(value);
            }

            @Override
            public JsonNode toJson(T value) {
                return type.toJson(value);
            }

            @Override
            public T fromJson(JsonNode node) throws FlagParseException {
                return checked(type.fromJson(node));
            }

            @Override
            public List<String> tabComplete(String partial) {
                return type.tabComplete(partial);
            }

            private T checked(T value) throws FlagParseException {
                try {
                    check.accept(value);
                    return value;
                } catch (IllegalArgumentException refused) {
                    throw new FlagParseException(refused.getMessage(), refused);
                }
            }
        };
    }

    /**
     * A list of {@code element} values, typed separated by {@code separator} and stored as a JSON array. Blank entries are
     * skipped; an empty list is not a value.
     */
    public static <E> FlagType<List<E>> listOf(FlagType<E> element, String separator) {
        return new ListType<>(element, separator);
    }

    /** One of {@code type}'s constants, by name, case-insensitively. */
    public static <E extends Enum<E>> FlagType<E> enumOf(Class<E> type) {
        List<String> names = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            names.add(constant.name().toLowerCase(Locale.ROOT));
        }
        return new ScalarType<>("enum", type, "one of " + String.join(", ", names),
                text -> Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT), names);
    }

    /**
     * A composite value: a Jackson POJO (no-arg constructor, getters and setters) stored as a JSON object and typed
     * as {@code k=v} pairs separated by spaces. A value with spaces goes between double or single quotes:
     * {@code denied=true message="stay out"}. A pair left out keeps the POJO's default.
     */
    public static <T> FlagType<T> jackson(String id, Class<T> pojo) {
        return new JacksonType<>(id, pojo);
    }

    private static WorldLocPos parseLocation(String text) {
        String[] parts = text.trim().split("\\|", -1);
        if (parts.length != 4 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("expected four parts");
        }
        return WorldLocPos.of(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), parts[0]);
    }

    //late on purpose: the ConfigFactory codecs are rebuilt when a type registers, so never keep a mapper in a field
    private static ObjectMapper mapper() {
        return ConfigFactory.inMemoryCodec().getObjectMapper();
    }

    private interface Parser<T> {
        T parse(String text) throws Exception;
    }

    private static class ScalarType<T> implements FlagType<T> {

        private final String id;
        private final Class<T> valueType;
        private final String accepted;
        private final Parser<T> parser;
        private final Function<T, String> formatter;
        private final List<String> completions;

        ScalarType(String id, Class<T> valueType, String accepted, Parser<T> parser, Function<T, String> formatter, List<String> completions) {
            this.id = id;
            this.valueType = valueType;
            this.accepted = accepted;
            this.parser = parser;
            this.formatter = formatter;
            this.completions = completions;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Class<T> getValueType() {
            return valueType;
        }

        @Override
        public T parse(String text) throws FlagParseException {
            try {
                T value = text == null ? null : parser.parse(text);
                if (value != null) {
                    return value;
                }
            } catch (Exception refused) {
                //the message below names what was received and what is accepted
            }
            throw new FlagParseException("'" + text + "' is not a valid " + id + " value: write " + accepted + ".");
        }

        @Override
        public String format(T value) {
            return formatter.apply(value);
        }

        @Override
        public JsonNode toJson(T value) {
            return TextNode.valueOf(format(value));
        }

        @Override
        public T fromJson(JsonNode node) throws FlagParseException {
            if (node == null || !node.isValueNode() || node.isNull()) {
                throw new FlagParseException("Stored " + id + " value " + node + " is not a single value: it must be " + accepted + ".");
            }
            return parse(node.asText());
        }

        @Override
        public List<String> tabComplete(String partial) {
            String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String completion : completions) {
                if (completion.startsWith(prefix)) {
                    matches.add(completion);
                }
            }
            return matches;
        }
    }

    /**
     * A value written as a YAML/JSON node, typed in the same form ({@code {type: message, text: hi}}); shown back as the
     * stored node, which reads again as typed.
     */
    private abstract static class NodeType<T> implements FlagType<T> {

        private final String id;
        final String accepted;

        NodeType(String id, String accepted) {
            this.id = id;
            this.accepted = accepted;
        }

        abstract T read(JsonNode node) throws FlagParseException;

        @Override
        public String getId() {
            return id;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<T> getValueType() {
            return (Class<T>) (Class<?>) Object.class;
        }

        @Override
        public T parse(String text) throws FlagParseException {
            JsonNode node;
            try {
                node = mapper().readTree(text == null ? "" : text);
            } catch (Exception unreadable) {
                throw new FlagParseException("'" + text + "' does not read as a " + id + ": write it as " + accepted + ".", unreadable);
            }
            if (node == null || !(node.isObject() || node.isArray())) {
                throw new FlagParseException("'" + text + "' is not a " + id + ": write it as " + accepted + ".");
            }
            return read(node);
        }

        @Override
        public String format(T value) {
            return toJson(value).toString();
        }

        @Override
        public T fromJson(JsonNode node) throws FlagParseException {
            if (node != null && node.isTextual()) {
                return parse(node.asText()); //a hand-edited file may hold the typed form
            }
            if (node == null || !(node.isObject() || node.isArray())) {
                throw new FlagParseException("Stored " + id + " value " + node + " is not one: write it as " + accepted + ".");
            }
            return read(node);
        }

        @Override
        public List<String> tabComplete(String partial) {
            return Collections.emptyList();
        }
    }

    private static final class JacksonType<T> implements FlagType<T> {

        private final String id;
        private final Class<T> pojo;

        JacksonType(String id, Class<T> pojo) {
            this.id = id;
            this.pojo = pojo;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Class<T> getValueType() {
            return pojo;
        }

        @Override
        public T parse(String text) throws FlagParseException {
            ObjectNode node = mapper().createObjectNode();
            for (String token : tokens(text)) {
                int equals = token.indexOf('=');
                if (equals <= 0) {
                    throw new FlagParseException("'" + token + "' is not a key=value pair: write the " + id + " value as " + example()
                            + ", quoting a value that has spaces (message=\"stay out\").");
                }
                node.put(token.substring(0, equals), unquote(token.substring(equals + 1)));
            }
            return fromJson(node);
        }

        @Override
        public String format(T value) {
            StringBuilder text = new StringBuilder();
            Iterator<Map.Entry<String, JsonNode>> fields = toJson(value).fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue().isNull()) {
                    continue;
                }
                String fieldText = field.getValue().asText();
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(field.getKey()).append('=');
                text.append(fieldText.isEmpty() || fieldText.contains(" ") ? '"' + fieldText + '"' : fieldText);
            }
            return text.toString();
        }

        @Override
        public JsonNode toJson(T value) {
            return mapper().valueToTree(value);
        }

        @Override
        public T fromJson(JsonNode node) throws FlagParseException {
            try {
                return mapper().treeToValue(node, pojo);
            } catch (Exception refused) {
                throw new FlagParseException("Cannot read " + node + " as a " + id + " value (" + refused.getMessage()
                        + "): write it as " + example() + ".", refused);
            }
        }

        @Override
        public List<String> tabComplete(String partial) {
            List<String> keys = new ArrayList<>();
            String prefix = partial == null ? "" : partial;
            Iterator<String> names = mapper().valueToTree(newDefault()).fieldNames();
            while (names.hasNext()) {
                String key = names.next() + "=";
                if (key.startsWith(prefix)) {
                    keys.add(key);
                }
            }
            return keys;
        }

        private String example() {
            List<String> pairs = new ArrayList<>();
            Iterator<String> names = mapper().valueToTree(newDefault()).fieldNames();
            while (names.hasNext()) {
                pairs.add(names.next() + "=...");
            }
            return String.join(" ", pairs);
        }

        private T newDefault() {
            try {
                return pojo.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException noDefault) {
                throw new IllegalStateException("The " + id + " flag value " + pojo.getName() + " needs a public no-arg constructor"
                        + " for FlagTypes.jackson to build it.", noDefault);
            }
        }
    }

    private static final class ListType<E> implements FlagType<List<E>> {

        private final FlagType<E> element;
        private final String separator;

        ListType(FlagType<E> element, String separator) {
            this.element = element;
            this.separator = separator;
        }

        @Override
        public String getId() {
            return element.getId() + "-list";
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<List<E>> getValueType() {
            return (Class<List<E>>) (Class<?>) List.class;
        }

        @Override
        public List<E> parse(String text) throws FlagParseException {
            List<E> values = new ArrayList<>();
            for (String part : (text == null ? "" : text).split(Pattern.quote(separator))) {
                if (!part.trim().isEmpty()) {
                    values.add(element.parse(part.trim()));
                }
            }
            if (values.isEmpty()) {
                throw new FlagParseException("'" + text + "' has no " + element.getId() + " in it: write one or more, separated by '"
                        + separator + "'.");
            }
            return Collections.unmodifiableList(values);
        }

        @Override
        public String format(List<E> value) {
            List<String> parts = new ArrayList<>(value.size());
            for (E entry : value) {
                parts.add(element.format(entry));
            }
            return String.join(separator + " ", parts);
        }

        @Override
        public JsonNode toJson(List<E> value) {
            ArrayNode array = mapper().createArrayNode();
            for (E entry : value) {
                array.add(element.toJson(entry));
            }
            return array;
        }

        @Override
        public List<E> fromJson(JsonNode node) throws FlagParseException {
            if (node == null || !node.isArray()) {
                if (node != null && node.isTextual()) {
                    return parse(node.asText()); //a hand-edited file may hold the typed form
                }
                throw new FlagParseException("Stored " + getId() + " value " + node + " is not a list: it must be a JSON array of "
                        + element.getId() + " values.");
            }
            List<E> values = new ArrayList<>(node.size());
            for (JsonNode entry : node) {
                values.add(element.fromJson(entry));
            }
            return Collections.unmodifiableList(values);
        }

        /** Completes the entry being typed, after the last separator. */
        @Override
        public List<String> tabComplete(String partial) {
            String text = partial == null ? "" : partial;
            int cut = text.lastIndexOf(separator);
            String head = cut < 0 ? "" : text.substring(0, cut + separator.length());
            List<String> matches = new ArrayList<>();
            for (String completion : element.tabComplete(text.substring(head.length()).trim())) {
                matches.add(head + (head.isEmpty() ? "" : " ") + completion);
            }
            return matches;
        }
    }

    /** Splits on spaces, keeping a {@code "..."} or {@code '...'} run - and the spaces inside it - in one token. */
    private static List<String> tokens(String text) throws FlagParseException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (char c : (text == null ? "" : text).toCharArray()) {
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                current.append(c);
            } else if (c == ' ') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (quote != 0) {
            throw new FlagParseException("'" + text + "' opens a " + quote + " quote and never closes it: close it after the value,"
                    + " e.g. message=\"stay out\".");
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'') && value.charAt(value.length() - 1) == value.charAt(0)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
