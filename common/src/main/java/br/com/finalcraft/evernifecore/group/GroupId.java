package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.RegionId;
import jakarta.annotation.Nullable;

/**
 * The identity of a group: {@code owner:name} for a group of a {@link GroupManager}, {@code owner:region/group} for
 * the group local to a region. Every part follows the {@link RegionId} rule and is lowercased, so equality, hashing
 * and {@link #toString()} work on the lowercase text form. Immutable.
 */
public final class GroupId {

    private final String owner;
    private final String name;
    private final @Nullable RegionId region;
    private final String text;

    private GroupId(String owner, String name, @Nullable RegionId region) {
        this.owner = owner;
        this.name = name;
        this.region = region;
        this.text = region == null ? owner + ":" + name : region + "/" + name;
    }

    /** @throws IllegalArgumentException when the name breaks the rule; the message shows it and an example */
    public static GroupId of(ECPluginData owner, String name) {
        return of(owner.getMetaInfo().getName(), name);
    }

    /** @throws IllegalArgumentException when the owner or the name breaks the rule */
    public static GroupId of(String ownerName, String name) {
        return new GroupId(RegionId.requireOwner(ownerName), RegionId.requireName("group", name), null);
    }

    /** The id of group {@code groupName} local to {@code region}; the owner is the region's. */
    public static GroupId regionLocal(RegionId region, String groupName) {
        return new GroupId(region.getOwner(), RegionId.requireName("group", groupName), region);
    }

    /**
     * Parses {@code owner:name} or {@code owner:region/group}, case-insensitively.
     *
     * @throws IllegalArgumentException naming the text and the two forms
     */
    public static GroupId parse(String text) {
        try {
            int slash = text == null ? -1 : text.indexOf('/');
            if (slash < 0) {
                int colon = text == null ? -1 : text.indexOf(':');
                if (colon < 0 || text.indexOf(':', colon + 1) >= 0) {
                    throw new IllegalArgumentException("no single ':'");
                }
                return of(text.substring(0, colon), text.substring(colon + 1));
            }
            if (text.indexOf('/', slash + 1) >= 0) {
                throw new IllegalArgumentException("more than one '/'");
            }
            return regionLocal(RegionId.parse(text.substring(0, slash)), text.substring(slash + 1));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid group id '" + text + "' (" + invalid.getMessage() + "): write owner:name for a group"
                    + " of a plugin, e.g. 'finalparty:alpha', or owner:region/group for the group of a region, e.g. 'finalprotection:spawn/vip'.",
                    invalid);
        }
    }

    /** The lowercase name of the plugin that owns the group (the region's owner for a region-local group). */
    public String getOwner() {
        return owner;
    }

    /** The group's own name - for a region-local group, the part after {@code /}. */
    public String getName() {
        return name;
    }

    public boolean isRegionLocal() {
        return region != null;
    }

    /** The region a region-local group belongs to; null otherwise. */
    public @Nullable RegionId getRegion() {
        return region;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GroupId && ((GroupId) other).text.equals(text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /** {@code owner:name} or {@code owner:region/group}, lowercase. */
    @Override
    public String toString() {
        return text;
    }
}
