package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.region.flag.FlagParseException;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How the region commands turn typed text into a flag value or into a flag's settings - one place, so the region and
 * the group verbs read a value the same way.
 */
public final class FlagValues {

    /** Typed as a value, it clears the flag; typed as settings, it drops the region's own settings. */
    public static final String CLEAR = "-";

    /** Not a flag: the command's shortcut for {@code deny-block-break} and {@code deny-block-place} together. */
    public static final String DENY_BUILD = "deny-build";

    private FlagValues() {
    }

    /**
     * The flags {@code text} names: the one it resolves to in the registry ({@code owner:name}, or a name only one
     * flag has), both build flags for {@link #DENY_BUILD}, or none.
     */
    public static List<RegionFlag<?>> targets(@Nullable String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        if (text.equalsIgnoreCase(DENY_BUILD)) {
            Optional<RegionFlag<?>> breaking = FlagRegistry.get().find("deny-block-break");
            Optional<RegionFlag<?>> placing = FlagRegistry.get().find("deny-block-place");
            return breaking.isPresent() && placing.isPresent()
                    ? Arrays.<RegionFlag<?>>asList(breaking.get(), placing.get())
                    : Collections.<RegionFlag<?>>emptyList();
        }
        return FlagRegistry.get().find(text).map(Collections::<RegionFlag<?>>singletonList).orElse(Collections.<RegionFlag<?>>emptyList());
    }

    /**
     * The value {@code text} gives the flag, or null for {@link #CLEAR}. A composite value keeps its spaces - the
     * flag's type reads {@code k=v k2="two words"} itself.
     *
     * @throws FlagParseException with the type's own message when it refuses the text
     */
    public static <T> @Nullable T parse(RegionFlag<T> flag, String text) throws FlagParseException {
        String trimmed = text.trim();
        if (trimmed.equals(CLEAR)) {
            return null;
        }
        return flag.getType().parse(trimmed);
    }

    /** {@code value} as the flag's type writes it back. */
    @SuppressWarnings("unchecked")
    public static <T> String format(RegionFlag<T> flag, @Nullable Object value) {
        return value == null ? "null" : flag.getType().format((T) value);
    }

    /**
     * New settings for {@code flag}: {@code current} (the region's own, or the server's when null) with each
     * {@code field=value} of {@code text} written over a copy. Values may be quoted to keep spaces.
     *
     * @throws FlagParseException naming the field that is unknown or that refused its value, and every field accepted
     */
    public static FlagSettings parseSettings(RegionFlag<?> flag, @Nullable FlagSettings current, String text) throws FlagParseException {
        ObjectMapper mapper = mapper();
        Map<String, JsonNode> fields = getSettingsFields(flag, current == null ? flag.getSettings() : current);
        ObjectNode tree = mapper.createObjectNode();
        tree.setAll(fields);
        for (String pair : splitPairs(text)) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                throw new FlagParseException("'" + pair + "' is not field=value - write each setting of " + flag.getKey()
                        + " as field=value, the fields being: " + String.join(", ", fields.keySet()) + ".");
            }
            String field = pair.substring(0, equals);
            if (!fields.containsKey(field)) {
                throw new FlagParseException("The flag " + flag.getKey() + " has no setting called '" + field + "' - the fields are: "
                        + String.join(", ", fields.keySet()) + ".");
            }
            tree.put(field, pair.substring(equals + 1));
        }
        try {
            return mapper.treeToValue(tree, flag.getSettingsType());
        } catch (JsonProcessingException | IllegalArgumentException refused) {
            throw new FlagParseException("The settings of " + flag.getKey() + " refused '" + text.trim() + "': " + rootMessage(refused)
                    + " - the fields are: " + String.join(", ", fields.keySet()) + ".", refused);
        }
    }

    /**
     * Each setting {@code settings} has, by the name a command types it with, in declaration order; a field with no
     * value is a {@code null} node.
     */
    public static Map<String, JsonNode> getSettingsFields(RegionFlag<?> flag, FlagSettings settings) {
        ObjectMapper mapper = mapper();
        JsonNode written = mapper.valueToTree(settings);
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        for (String name : getSettingsFieldNames(flag)) {
            JsonNode value = written.get(name);
            fields.put(name, value == null ? NullNode.getInstance() : value);
        }
        return fields;
    }

    /** The names of the settings of {@code flag}, as {@code field=value} takes them. */
    public static List<String> getSettingsFieldNames(RegionFlag<?> flag) {
        ObjectMapper mapper = mapper();
        BeanDescription description = mapper.getSerializationConfig().introspect(mapper.constructType(flag.getSettingsType()));
        List<String> names = new ArrayList<>();
        for (BeanPropertyDefinition property : description.findProperties()) {
            if (property.couldSerialize() && property.couldDeserialize()) {
                names.add(property.getName());
            }
        }
        return names;
    }

    /** Splits on whitespace outside double quotes, dropping the quotes: {@code a=1 b="x y"} gives {@code a=1}, {@code b=x y}. */
    static List<String> splitPairs(String text) {
        List<String> pairs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : text.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    pairs.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            pairs.add(current.toString());
        }
        return pairs;
    }

    private static String rootMessage(Exception refused) {
        Throwable root = refused;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root instanceof JsonProcessingException ? ((JsonProcessingException) root).getOriginalMessage() : root.getMessage();
        return message == null ? root.getClass().getSimpleName() : message;
    }

    private static ObjectMapper mapper() {
        return ConfigFactory.inMemoryCodec().getObjectMapper();
    }
}
