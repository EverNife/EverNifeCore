package br.com.finalcraft.evernifecore.placeholder.manipulation;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What one {@code [...]} block of a placeholder says, read: positional values ({@code [arena]}), named ones
 * ({@code [index=2|sort=entry]}) and several values for one key ({@code [sort=name,entry]}).
 *
 * <p>Inside a block, {@code | = , [ ] {} } are syntax. A value keeps them as text when quoted ({@code '...'} or
 * {@code "..."}), and any single character is taken as text after a backslash ({@code \]}, {@code \'}, {@code \\}).
 * A bracket pair inside the block is text too ({@code [a[b]c]} reads {@code a[b]c}). An unquoted {@code {key}} is
 * resolved through the replacer that owns the placeholder before the value is read; {@code \{} keeps it literal.</p>
 *
 * <p>Immutable. Two blocks that say the same thing are equal whatever order their keys were written in, so an
 * instance can key a cache.</p>
 */
public final class PlaceholderArgs {

    private static final PlaceholderArgs EMPTY = new PlaceholderArgs("", Collections.emptyList(), Collections.emptyMap());

    private final String block;
    private final List<String> positional;
    private final Map<String, List<String>> named;   //sorted by key: the canonical form

    private PlaceholderArgs(String block, List<String> positional, Map<String, List<String>> named) {
        this.block = block;
        this.positional = positional;
        this.named = named;
    }

    /** No value at all - what an optional block that was left out reads as. */
    public static PlaceholderArgs empty(String block) {
        return block.isEmpty() ? EMPTY : new PlaceholderArgs(block, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * Reads the text between a block's brackets.
     *
     * @param block    the block's name in its template, without brackets - it heads every argument error
     * @param content  the text between the brackets
     * @param resolver answers an unquoted {@code {key}}: its value, or null to keep {@code {key}} as written
     * @throws SyntaxException when the content does not follow the grammar (a quote never closed, a
     *                                  trailing backslash, a {@code {} never closed)
     */
    public static PlaceholderArgs parse(String block, String content, @Nullable Function<String, String> resolver) {
        List<String> positional = new ArrayList<>();
        Map<String, List<String>> named = new TreeMap<>();

        String key = null;
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean touched = false;   //an entry that is only '' still holds one (empty) value
        char quote = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\\') {
                if (++i == content.length()) {
                    throw new SyntaxException("a backslash ends the block with nothing to escape");
                }
                current.append(content.charAt(i));
                touched = true;
            } else if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                touched = true;
            } else if (c == '{') {
                int close = content.indexOf('}', i + 1);
                if (close < 0) {
                    throw new SyntaxException("a '{' is never closed");
                }
                String nestedKey = content.substring(i + 1, close);
                String resolved = resolver == null || nestedKey.isEmpty() ? null : resolver.apply(nestedKey);
                current.append(resolved != null ? resolved : "{" + nestedKey + "}");
                touched = true;
                i = close;
            } else if (c == '=' && key == null && values.isEmpty()) {
                key = current.toString().trim().toLowerCase(Locale.ROOT);
                current.setLength(0);
                touched = false;
            } else if (c == ',') {
                values.add(current.toString());
                current.setLength(0);
                touched = true;
            } else if (c == '|') {
                closeEntry(key, values, current, touched, positional, named);
                key = null;
                values = new ArrayList<>();
                current.setLength(0);
                touched = false;
            } else {
                current.append(c);
                touched = true;
            }
        }
        if (quote != 0) {
            throw new SyntaxException("a " + quote + " quote is never closed");
        }
        closeEntry(key, values, current, touched, positional, named);
        return new PlaceholderArgs(block, Collections.unmodifiableList(positional), Collections.unmodifiableMap(named));
    }

    private static void closeEntry(@Nullable String key, List<String> values, StringBuilder current, boolean touched,
                                   List<String> positional, Map<String, List<String>> named) {
        if (touched || current.length() > 0 || !values.isEmpty() || key != null) {
            values.add(current.toString());
        }
        if (key == null) {
            positional.addAll(values);
        } else {
            named.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(values);
        }
    }

    /** The block's name in its template, without brackets. */
    public String getBlock() {
        return block;
    }

    public boolean isEmpty() {
        return positional.isEmpty() && named.isEmpty();
    }

    /** The first positional value; null when there is none. */
    public @Nullable String getValue() {
        return positional.isEmpty() ? null : positional.get(0);
    }

    /** Every positional value, in order. */
    public List<String> getPositional() {
        return positional;
    }

    public boolean has(String key) {
        return named.containsKey(key.toLowerCase(Locale.ROOT));
    }

    /** The first value of {@code key}; null when the block does not name it. */
    public @Nullable String getValue(String key) {
        List<String> values = named.get(key.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    public String getValue(String key, String fallback) {
        String value = getValue(key);
        return value != null ? value : fallback;
    }

    /** Every value of {@code key}, in order; empty when the block does not name it. */
    public List<String> getList(String key) {
        List<String> values = named.get(key.toLowerCase(Locale.ROOT));
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(values);
    }

    /** @throws InvalidArgumentException when the value is not a whole number */
    public int getInt(String key, int fallback) {
        String value = getValue(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException notANumber) {
            throw new InvalidArgumentException(block, key + " must be a number, got '" + value + "'");
        }
    }

    /** @throws InvalidArgumentException when the value is neither {@code true} nor {@code false} */
    public boolean getBoolean(String key, boolean fallback) {
        String value = getValue(key);
        if (value == null) {
            return fallback;
        }
        if (value.trim().equalsIgnoreCase("true")) {
            return true;
        }
        if (value.trim().equalsIgnoreCase("false")) {
            return false;
        }
        throw new InvalidArgumentException(block, key + " must be true or false, got '" + value + "'");
    }

    /** @throws InvalidArgumentException when the value names none of the constants, case-insensitively */
    public <E extends Enum<E>> E getEnum(String key, Class<E> type, E fallback) {
        String value = getValue(key);
        if (value == null) {
            return fallback;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value.trim())) {
                return constant;
            }
        }
        throw new InvalidArgumentException(block, key + " must be one of " + Arrays.stream(type.getEnumConstants())
                .map(constant -> constant.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", ")) + ", got '" + value + "'");
    }

    /**
     * Refuses a named key outside {@code keys}.
     *
     * @return this, to chain the reads
     * @throws InvalidArgumentException naming the key and the accepted ones
     */
    public PlaceholderArgs allowOnly(String... keys) {
        List<String> accepted = Arrays.asList(keys);
        for (String key : named.keySet()) {
            if (!accepted.contains(key)) {
                throw new InvalidArgumentException(block, "unknown key '" + key + "', accepted: " + String.join(", ", keys));
            }
        }
        return this;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlaceholderArgs
                && positional.equals(((PlaceholderArgs) other).positional)
                && named.equals(((PlaceholderArgs) other).named);
    }

    @Override
    public int hashCode() {
        return Objects.hash(positional, named);
    }

    /** The canonical form: positional values first, then the keys in order. */
    @Override
    public String toString() {
        List<String> entries = new ArrayList<>();
        if (!positional.isEmpty()) {
            entries.add(String.join(",", positional));
        }
        named.forEach((key, values) -> entries.add(key + "=" + String.join(",", values)));
        return "[" + String.join("|", entries) + "]";
    }

    /** A block that does not follow the grammar: the placeholder is left as it was written. */
    public static final class SyntaxException extends IllegalArgumentException {

        public SyntaxException(String message) {
            super(message);
        }
    }

    /**
     * A block whose syntax was fine but one of whose values was not: the placeholder answers {@link #toVisibleText()}
     * instead of a wrong value.
     */
    public static final class InvalidArgumentException extends IllegalArgumentException {

        private final String block;

        public InvalidArgumentException(String block, String message) {
            super(message);
            this.block = block;
        }

        public String getBlock() {
            return block;
        }

        /** {@code [<block>: <what failed>]}, short enough to read where the value would have been. */
        public String toVisibleText() {
            return "[" + block + ": " + getMessage() + "]";
        }
    }
}
