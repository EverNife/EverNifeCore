package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which other owners' regions a {@link RegionCommandNode} may edit when they are named by their full id. The owner of
 * a region still has the last word: {@code RegionManager.setForeignAccess(false)} refuses every foreign edit, whatever
 * this says.
 */
public final class ForeignAccess {

    private static final ForeignAccess NONE = new ForeignAccess(false, Collections.<String>emptySet());
    private static final ForeignAccess ALL = new ForeignAccess(true, Collections.<String>emptySet());

    private final boolean everyone;
    private final Set<String> ownerNames;

    private ForeignAccess(boolean everyone, Set<String> ownerNames) {
        this.everyone = everyone;
        this.ownerNames = ownerNames;
    }

    /** Only the node's own regions - the default. */
    public static ForeignAccess none() {
        return NONE;
    }

    /** Any owner that accepts edits from other plugins. */
    public static ForeignAccess all() {
        return ALL;
    }

    /** The owners named here, by plugin name, case-insensitively. */
    public static ForeignAccess owners(String... ownerNames) {
        Set<String> lowered = new LinkedHashSet<>();
        for (String name : ownerNames) {
            lowered.add(name.toLowerCase(Locale.ROOT));
        }
        return new ForeignAccess(false, Collections.unmodifiableSet(lowered));
    }

    public boolean allows(ECPluginData owner) {
        return everyone || ownerNames.contains(owner.getMetaInfo().getName().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return everyone ? "ForeignAccess[all]" : "ForeignAccess" + Arrays.toString(ownerNames.toArray());
    }
}
