package br.com.finalcraft.evernifecore.playerdata.storage;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The storage identity of a section: the developer-supplied id, and the names derived from it.
 *
 * <p>A section is identified by {@code <plugin>:<id>}, never by its class name - renaming the class
 * must not move the data. The id is REQUIRED at registration and validated here, because everything
 * else is derived from it: the collection ({@code pd_<plugin>_<id>} / {@code acs_<plugin>_<id>}),
 * the {@code pdsections.<plugin>.<id>} entry in storage.yml and the id admin commands take.</p>
 *
 * <p>An invalid id is REJECTED rather than repaired: silently stripping characters would map
 * {@code my-section} and {@code mysection} onto one collection, and the two owners would then fight
 * over the same rows.</p>
 */
public final class SectionIds {

    /** What a section id may look like, before it is lowercased. */
    public static final Pattern VALID_SECTION_ID = Pattern.compile("[a-zA-Z0-9_]{1,32}");

    private SectionIds() {
    }

    /**
     * Validates and canonicalizes a developer-supplied section id (lowercase).
     *
     * @throws IllegalArgumentException when the id is null, empty or has a character outside
     *                                  {@link #VALID_SECTION_ID}
     */
    public static String requireValid(String sectionId, Class<?> sectionClass) {
        String owner = sectionClass != null ? sectionClass.getName() : "section";
        if (sectionId == null || sectionId.isEmpty()) {
            throw new IllegalArgumentException("The section id of [" + owner + "] is required:"
                    + " it is what names its collection and its storage.yml entry, so the class may be"
                    + " renamed without moving the data. Expected a value matching "
                    + VALID_SECTION_ID.pattern() + ".");
        }
        if (!VALID_SECTION_ID.matcher(sectionId).matches()) {
            throw new IllegalArgumentException("The section id '" + sectionId + "' of [" + owner
                    + "] is invalid - it must match " + VALID_SECTION_ID.pattern()
                    + " (letters, digits and underscore, up to 32 characters).");
        }
        return sectionId.toLowerCase(Locale.ROOT);
    }

    /**
     * A plugin name reduced to what a collection name and a config key may hold. Unlike a section
     * id this one IS sanitized: the plugin name is not ours to reject, and it is already scoped by
     * the collection-claim check (two plugins whose names sanitize alike collide loudly there).
     */
    public static String sanitizePlugin(String pluginName) {
        return pluginName == null ? "" : pluginName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }
}
