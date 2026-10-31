package br.com.finalcraft.evernifecore.npc;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Which NPC, as {@code provider:id}: {@code citizens:12}, {@code fancynpcs:<uuid>}, {@code znpcsplus:merchant},
 * {@code hytale:<uuid>}. Both halves are lowercased - NPC plugins differ in case, and a provider compares ids ignoring it.
 * A reference to a provider no plugin registered is still a valid reference: {@link Npcs} simply finds nothing for it.
 */
public final class NpcRef {

    private static final Pattern PROVIDER = Pattern.compile("[a-z0-9_-]+");

    private final String provider;
    private final String id;

    private NpcRef(String provider, String id) {
        this.provider = provider;
        this.id = id;
    }

    /**
     * @param provider the provider's id, e.g. {@code citizens}
     * @param id       the NPC's id within that provider, e.g. {@code 12}
     * @throws IllegalArgumentException for a provider outside {@code [a-z0-9_-]}, or an id that is empty or holds a space
     */
    public static NpcRef of(String provider, String id) {
        String p = Objects.requireNonNull(provider, "provider").trim().toLowerCase(Locale.ROOT);
        String i = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        if (!PROVIDER.matcher(p).matches() || i.isEmpty() || i.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Expected provider:id (e.g. citizens:12), got '" + provider + ":" + id + "': the provider is"
                    + " letters, digits, '-' or '_', and the id is not empty and has no spaces.");
        }
        return new NpcRef(p, i);
    }

    /**
     * {@code provider:id} read back, split at the first {@code :}.
     *
     * @throws IllegalArgumentException for a text with no {@code :} or a half {@link #of} refuses
     */
    public static NpcRef parse(String text) {
        int colon = text == null ? -1 : text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1) {
            throw new IllegalArgumentException("Expected provider:id (e.g. citizens:12), got '" + text + "'.");
        }
        return of(text.substring(0, colon), text.substring(colon + 1));
    }

    /** The provider's id: {@code citizens}, {@code fancynpcs}, {@code znpcsplus}, {@code hytale}. */
    public String getProvider() {
        return provider;
    }

    /** The NPC's id within its provider, lowercased. */
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NpcRef && provider.equals(((NpcRef) other).provider) && id.equals(((NpcRef) other).id);
    }

    @Override
    public int hashCode() {
        return 31 * provider.hashCode() + id.hashCode();
    }

    /** {@code provider:id}, what {@link #parse} reads. */
    @Override
    public String toString() {
        return provider + ":" + id;
    }
}
