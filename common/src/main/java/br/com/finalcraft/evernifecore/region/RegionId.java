package br.com.finalcraft.evernifecore.region;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The identity of a region: {@code owner:name}, where the owner is the name of the plugin that created it.
 * Both parts are lowercased on the way in, so {@code TheDuel:Dragon} and {@code theduel:dragon} are the same
 * id - equality, hashing and ordering all work on the lowercase text form.
 */
public final class RegionId implements Comparable<RegionId> {

    private static final Pattern OWNER_RULE = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern NAME_RULE = Pattern.compile("[a-z0-9_-]{1,64}");

    private final String owner;
    private final String name;
    private final String text;

    private RegionId(String owner, String name) {
        this.owner = owner;
        this.name = name;
        this.text = owner + ":" + name;
    }

    /**
     * @param owner the owning plugin's name; letters, digits, {@code _ . -}, up to 64 characters
     * @param name  the region name; letters, digits, {@code _ -}, up to 64 characters
     * @throws IllegalArgumentException naming the offending part, the rule and a valid example
     */
    public static RegionId of(String owner, String name) {
        String lowerOwner = owner == null ? "" : owner.toLowerCase(Locale.ROOT);
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!OWNER_RULE.matcher(lowerOwner).matches()) {
            throw new IllegalArgumentException("Invalid region owner '" + owner + "': the owner is the plugin name and must match "
                    + OWNER_RULE.pattern() + " once lowercased (letters, digits, '_', '.' or '-'), e.g. 'theduel'.");
        }
        if (!NAME_RULE.matcher(lowerName).matches()) {
            throw new IllegalArgumentException("Invalid region name '" + name + "': it must match " + NAME_RULE.pattern()
                    + " once lowercased (letters, digits, '_' or '-', 1 to 64 characters), e.g. 'dragon' or 'arena_1'.");
        }
        return new RegionId(lowerOwner, lowerName);
    }

    /**
     * Parses the {@code owner:name} text form, case-insensitively.
     *
     * @throws IllegalArgumentException when the text is not exactly one owner and one name around a single {@code :}
     */
    public static RegionId parse(String text) {
        int colon = text == null ? -1 : text.indexOf(':');
        if (colon < 0 || text.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("Invalid region id '" + text + "': write it as owner:name with exactly one ':',"
                    + " the name matching " + NAME_RULE.pattern() + ", e.g. 'theduel:dragon'.");
        }
        return of(text.substring(0, colon), text.substring(colon + 1));
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    @Override
    public int compareTo(RegionId other) {
        return text.compareTo(other.text);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RegionId && text.equals(((RegionId) other).text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /** The {@code owner:name} form, always lowercase. */
    @Override
    public String toString() {
        return text;
    }
}
