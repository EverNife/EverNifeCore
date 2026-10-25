package br.com.finalcraft.evernifecore.types;

import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.time.Window;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One parameter of a {@link TypeDescriptor}: its name, what kind of value it takes, whether it is required (or the
 * default it takes when absent) and one sentence for the admin. The registry validates a definition against these
 * before the type's factory runs, and the exported reference lists them. Immutable: {@link #nested} and
 * {@link #oneOf} return a copy.
 */
public final class ParamSpec {

    /** What a parameter's value is, and so how it is read and what {@link ParsedParams} getter answers it. */
    public enum Kind {
        /** A plain value, read as text ({@link ParsedParams#getString}); {@link #oneOf} restricts it to a list. */
        STRING("a text value, e.g. vip"),
        /** A decimal number ({@link ParsedParams#getNumber}). */
        NUMBER("a number, e.g. 32 or 2.5"),
        /** {@code true} or {@code false} ({@link ParsedParams#getBoolean}). */
        BOOLEAN("true or false"),
        /** A duration in the config format ({@link ParsedParams#getDuration}). */
        DURATION("a duration, e.g. 30s, 10m or 1h30m"),
        /** A text shown to a player, color codes and placeholders allowed ({@link ParsedParams#getText}). */
        TEXT("a text, e.g. \"&aWelcome, {player}!\""),
        /** An identifier without spaces, bare or {@code owner:name} ({@link ParsedParams#getString}). */
        ID("an id without spaces, e.g. exp-level or finaljobs:level"),
        /** A list of plain values; a single value reads as a list of one ({@link ParsedParams#getList}). */
        LIST("a list, e.g. [a, b], or a single value"),
        /** A child definition read by the {@link #nested} registry ({@link ParsedParams#getNode}). */
        NODE("a {type: ...} node"),
        /** A list of child definitions read by the {@link #nested} registry ({@link ParsedParams#getNodes}). */
        NODE_LIST("a list of {type: ...} nodes"),
        /** A time window ({@link ParsedParams#getWindow}, {@link Window#parse(JsonNode, ZoneId)}). */
        WINDOW("a window, e.g. \"daily at 04:00\" or \"weekly on monday at 18:00\""),
        /** Who owns some progress or balance, as text: {@code player}, {@code global}, {@code kind:id} ({@link ParsedParams#getString}). */
        OWNER("an owner, e.g. player, global or group:finalparty/abc");

        private final String expected;

        Kind(String expected) {
            this.expected = expected;
        }

        /** What a value of this kind looks like, with an example - the end of a "it expects ..." sentence. */
        public String getExpected() {
            return expected;
        }
    }

    private final String name;
    private final Kind kind;
    private final boolean required;
    private final @Nullable Object defaultValue;
    private final String description;
    private final @Nullable TypeRegistry<?> nested;
    private final List<String> accepted;

    private ParamSpec(String name, Kind kind, boolean required, @Nullable Object defaultValue, String description,
                      @Nullable TypeRegistry<?> nested, List<String> accepted) {
        this.name = name;
        this.kind = kind;
        this.required = required;
        this.defaultValue = defaultValue;
        this.description = description;
        this.nested = nested;
        this.accepted = accepted;
    }

    /**
     * A parameter the definition must write.
     *
     * @throws IllegalArgumentException when the name breaks {@code [a-z0-9_-]{1,64}} or is {@code type}, or the
     * description is empty
     */
    public static ParamSpec required(String name, Kind kind, String description) {
        return new ParamSpec(requireParamName(name), kind, true, null, requireDescription(name, description), null,
                Collections.<String>emptyList());
    }

    /**
     * A parameter the definition may leave out; {@code defaultValue} (null for none) is what the factory reads then. A
     * text, number or boolean default is read like a written value ({@code "10m"} becomes a {@link Duration},
     * {@code 1} a {@link BigDecimal}); any other object is handed over as it is.
     *
     * @throws IllegalArgumentException when the name is invalid, the description empty, or the default does not read
     * as {@code kind}
     */
    public static ParamSpec optional(String name, Kind kind, @Nullable Object defaultValue, String description) {
        String checked = requireParamName(name);
        Object value = defaultValue;
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            if (kind == Kind.NODE || kind == Kind.NODE_LIST) {
                throw new IllegalArgumentException("Parameter '" + name + "' (" + kind + ") cannot default to " + value
                        + ": a node parameter has no default but null.");
            }
            try {
                value = readScalarKind(kind, toNode(value), null);
            } catch (IllegalArgumentException unreadable) {
                throw new IllegalArgumentException("The default of parameter '" + name + "' (" + value + ") is not "
                        + kind.getExpected() + ".", unreadable);
            }
        }
        return new ParamSpec(checked, kind, false, value, requireDescription(name, description), null,
                Collections.<String>emptyList());
    }

    /**
     * The registry that reads this {@link Kind#NODE}/{@link Kind#NODE_LIST} parameter's children; mandatory for those
     * kinds, refused for the others.
     */
    public ParamSpec nested(TypeRegistry<?> registry) {
        if (kind != Kind.NODE && kind != Kind.NODE_LIST) {
            throw new IllegalArgumentException("Parameter '" + name + "' is " + kind + ": only a NODE or NODE_LIST parameter"
                    + " reads its value through a nested registry.");
        }
        return new ParamSpec(name, kind, required, defaultValue, description, registry, accepted);
    }

    /** Restricts this {@link Kind#STRING} parameter to {@code values} (compared case-insensitively). */
    public ParamSpec oneOf(String... values) {
        if (kind != Kind.STRING) {
            throw new IllegalArgumentException("Parameter '" + name + "' is " + kind + ": only a STRING parameter takes a list of"
                    + " accepted values.");
        }
        if (values.length == 0) {
            throw new IllegalArgumentException("Parameter '" + name + "': oneOf needs at least one accepted value.");
        }
        return new ParamSpec(name, kind, required, defaultValue, description, nested,
                Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values))));
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isRequired() {
        return required;
    }

    /** What the factory reads when the definition leaves this optional parameter out; null for none. */
    public @Nullable Object getDefault() {
        return defaultValue;
    }

    public String getDescription() {
        return description;
    }

    /** The registry that reads this node parameter's children; null for the other kinds. */
    public @Nullable TypeRegistry<?> getNested() {
        return nested;
    }

    /** The values a {@link #oneOf} parameter accepts, in declaration order; empty when any value goes. */
    public List<String> getAccepted() {
        return accepted;
    }

    /** What a value looks like: "one of a, b" for a restricted parameter, else the kind's example. */
    public String getExpected() {
        return accepted.isEmpty() ? kind.getExpected() : "one of " + String.join(", ", accepted);
    }

    /** {@code name (kind, required)}, {@code name (one of <, >, required)}, {@code name (number, default 1)}. */
    public String describe() {
        String what = accepted.isEmpty() ? kind.name().toLowerCase(Locale.ROOT).replace('_', ' ') : "one of " + String.join(", ", accepted);
        String need = required ? "required" : defaultValue == null ? "optional" : "default " + describeDefault(defaultValue);
        return name + " (" + what + ", " + need + ")";
    }

    @Override
    public String toString() {
        return describe();
    }

    private static String describeDefault(Object value) {
        if (value instanceof Duration) {
            return FCTimeFrame.of((Duration) value).toConfigString();
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        return String.valueOf(value);
    }

    /**
     * Reads one scalar-kind value (every kind but the two node kinds); the message of the exception is the "expects"
     * part the registry wraps.
     */
    static Object readScalarKind(Kind kind, JsonNode value, @Nullable ZoneId zone) {
        switch (kind) {
            case NUMBER:
                if (value.isNumber()) {
                    return value.decimalValue();
                }
                if (value.isTextual()) {
                    try {
                        return new BigDecimal(value.asText().trim());
                    } catch (NumberFormatException notANumber) {
                        throw new IllegalArgumentException(kind.getExpected());
                    }
                }
                throw new IllegalArgumentException(kind.getExpected());
            case BOOLEAN:
                if (value.isBoolean()) {
                    return value.booleanValue();
                }
                if (value.isTextual()) {
                    String text = value.asText().trim();
                    if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
                        return Boolean.valueOf(text);
                    }
                }
                throw new IllegalArgumentException(kind.getExpected());
            case DURATION:
                if (!value.isValueNode() || value.isNull()) {
                    throw new IllegalArgumentException(kind.getExpected());
                }
                try {
                    return Duration.ofMillis(FCTimeFrame.fromConfigString(value.asText()).getMillis());
                } catch (IllegalArgumentException notADuration) {
                    throw new IllegalArgumentException(kind.getExpected() + ". " + FCTimeFrame.ACCEPTED_FORMAT);
                }
            case LIST:
                List<String> items = new ArrayList<>();
                if (value.isArray()) {
                    for (JsonNode item : value) {
                        if (!item.isValueNode() || item.isNull()) {
                            throw new IllegalArgumentException(kind.getExpected() + " of plain values");
                        }
                        items.add(item.asText());
                    }
                } else if (value.isValueNode() && !value.isNull()) {
                    items.add(value.asText());
                } else {
                    throw new IllegalArgumentException(kind.getExpected());
                }
                return Collections.unmodifiableList(items);
            case WINDOW:
                return Window.parse(value, zone == null ? Window.getServerZone() : zone);
            case ID:
                String id = requireText(kind, value);
                if (id.isEmpty() || id.chars().anyMatch(Character::isWhitespace)) {
                    throw new IllegalArgumentException(kind.getExpected());
                }
                return id;
            case STRING:
            case TEXT:
            case OWNER:
                String text = requireText(kind, value);
                if (text.isEmpty()) {
                    throw new IllegalArgumentException(kind.getExpected());
                }
                return text;
            default:
                throw new IllegalStateException(kind + " is read by the nested registry");
        }
    }

    private static String requireText(Kind kind, JsonNode value) {
        if (!value.isValueNode() || value.isNull()) {
            throw new IllegalArgumentException(kind.getExpected());
        }
        return value.asText().trim();
    }

    private static JsonNode toNode(Object value) {
        if (value instanceof Boolean) {
            return JsonNodeFactory.instance.booleanNode((Boolean) value);
        }
        if (value instanceof Number) {
            return JsonNodeFactory.instance.numberNode(new BigDecimal(value.toString()));
        }
        return JsonNodeFactory.instance.textNode((String) value);
    }

    private static String requireParamName(String name) {
        String checked = RegionId.requireName("parameter", name);
        if (checked.equals(TypeRegistry.TYPE_FIELD)) {
            throw new IllegalArgumentException("A parameter cannot be named '" + TypeRegistry.TYPE_FIELD + "': that field names the"
                    + " type of a node. Pick another name.");
        }
        return checked;
    }

    private static String requireDescription(String name, String description) {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter '" + name + "' has no description: write one sentence for the admin, it is"
                    + " what the error messages and the exported reference show.");
        }
        return description;
    }
}
