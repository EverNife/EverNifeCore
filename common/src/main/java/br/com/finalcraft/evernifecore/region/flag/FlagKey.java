package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.RegionId;

/**
 * The identity of a flag: {@code owner:name}, both lowercased, with the same rules as a {@link RegionId}. The owner
 * part names the plugin that registers the flag ({@link FlagRegistry#register}); equality and hashing work on the text
 * form only.
 */
public final class FlagKey {

    private final String owner;
    private final String name;
    private final String text;

    private FlagKey(String owner, String name) {
        this.owner = owner;
        this.name = name;
        this.text = owner + ":" + name;
    }

    /** @throws IllegalArgumentException when the owner or the name breaks the rule; the message shows it and an example */
    public static FlagKey of(String ownerName, String name) {
        return new FlagKey(RegionId.requireOwner(ownerName), RegionId.requireName("flag", name));
    }

    /** The key of a flag {@code owner} registers. */
    public static FlagKey of(ECPluginData owner, String name) {
        return of(owner.getMetaInfo().getName(), name);
    }

    /**
     * Parses {@code owner:name}, case-insensitively.
     *
     * @throws IllegalArgumentException when the text is not one owner and one name around a single {@code :}
     */
    public static FlagKey parse(String text) {
        int colon = text == null ? -1 : text.indexOf(':');
        if (colon < 0 || text.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("Invalid flag key '" + text + "': write it as owner:name with exactly one ':',"
                    + " e.g. 'evernifecore:deny-pvp'.");
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
    public boolean equals(Object other) {
        return other instanceof FlagKey && text.equals(((FlagKey) other).text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /** {@code owner:name}, always lowercase. */
    @Override
    public String toString() {
        return text;
    }
}
