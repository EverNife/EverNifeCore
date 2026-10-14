package br.com.finalcraft.evernifecore.region.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What a thing in the world is, as {@code namespace:path}: {@code minecraft:creeper}, {@code hytale:Trork_Brute},
 * {@code environment:fire}. The text is kept as the platform gives it; {@link #matches} ignores case.
 */
public final class TypeKey {

    private final String namespace;
    private final String path;
    private final String text;

    private TypeKey(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
        this.text = namespace + ":" + path;
    }

    /** @throws IllegalArgumentException when a part is empty or the namespace holds a {@code :} */
    public static TypeKey of(String namespace, String path) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (namespace.isEmpty() || path.isEmpty() || namespace.indexOf(':') >= 0) {
            throw invalid(namespace + ":" + path);
        }
        return new TypeKey(namespace, path);
    }

    /**
     * {@code namespace:path}, split at the first {@code :}.
     *
     * @throws IllegalArgumentException when there is no {@code :} or a side of it is empty
     */
    public static TypeKey parse(String text) {
        int colon = Objects.requireNonNull(text, "text").indexOf(':');
        if (colon <= 0 || colon == text.length() - 1) {
            throw invalid(text);
        }
        return new TypeKey(text.substring(0, colon), text.substring(colon + 1));
    }

    private static IllegalArgumentException invalid(String text) {
        return new IllegalArgumentException("'" + text + "' is not a type key: write it as namespace:path with both parts filled,"
                + " like minecraft:creeper or environment:fire.");
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    /**
     * Whether this key is {@code pattern}, ignoring case, with {@code *} standing for any run of characters:
     * {@code minecraft:*}, {@code *:creeper}, {@code minecraft:*chest*}.
     */
    public boolean matches(String pattern) {
        return globMatches(pattern.toLowerCase(Locale.ROOT), text.toLowerCase(Locale.ROOT));
    }

    private static boolean globMatches(String pattern, String text) {
        if (pattern.indexOf('*') < 0) {
            return pattern.equals(text);
        }
        List<String> literals = new ArrayList<>();
        for (String literal : pattern.split("\\*", -1)) {
            literals.add(Pattern.quote(literal));
        }
        return Pattern.matches(String.join(".*", literals), text);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TypeKey && text.equals(((TypeKey) other).text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
