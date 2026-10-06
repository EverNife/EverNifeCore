package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.RegionId;
import jakarta.annotation.Nullable;

/**
 * The identity of a flag: {@code owner:name}, both lowercased, with the same rules as a {@link RegionId}.
 * Equality and hashing work on the text form only; a key built by {@link #of} also remembers the owning plugin,
 * which is what a {@link RegionFlag} needs.
 */
public final class FlagKey {

    private final String owner;
    private final String name;
    private final String text;
    private final @Nullable ECPluginData plugin;

    private FlagKey(String owner, String name, @Nullable ECPluginData plugin) {
        this.owner = owner;
        this.name = name;
        this.text = owner + ":" + name;
        this.plugin = plugin;
    }

    /** @throws IllegalArgumentException when the name breaks the rule; the message shows it and an example */
    public static FlagKey of(ECPluginData owner, String name) {
        return new FlagKey(RegionId.requireOwner(owner.getMetaInfo().getName()), RegionId.requireName("flag", name), owner);
    }

    /**
     * Parses {@code owner:name}, case-insensitively. The parsed key carries no plugin: use it to look a flag up,
     * not to build one.
     *
     * @throws IllegalArgumentException when the text is not one owner and one name around a single {@code :}
     */
    public static FlagKey parse(String text) {
        int colon = text == null ? -1 : text.indexOf(':');
        if (colon < 0 || text.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("Invalid flag key '" + text + "': write it as owner:name with exactly one ':',"
                    + " e.g. 'evernifecore:deny-pvp'.");
        }
        return new FlagKey(RegionId.requireOwner(text.substring(0, colon)), RegionId.requireName("flag", text.substring(colon + 1)), null);
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    /** The plugin this key was built for by {@link #of}; null for a {@link #parse parsed} key. */
    @Nullable ECPluginData getPlugin() {
        return plugin;
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
