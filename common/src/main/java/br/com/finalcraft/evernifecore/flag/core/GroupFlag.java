package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagHolder;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagResolver;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.evernifecore.flag.FlagType;
import br.com.finalcraft.evernifecore.group.Group;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.function.BooleanSupplier;

/**
 * A value only a group carries, read by the group engine itself ({@code max-members}, {@code share-xp},
 * {@code share-money}, {@code glow-members}): no event asks it. On a region it means nothing, and the load warns once.
 * A platform without what the group engine reads it for leaves it {@link br.com.finalcraft.evernifecore.flag.FlagRegistry#isSupported unsupported}.
 */
public final class GroupFlag<T> extends Flag<T> {

    private final String description;
    private final boolean regionGroups;
    private final Class<? extends FlagSettings> settingsType;
    private final FlagResolver<T> resolver;
    private final BooleanSupplier availableOnPlatform;

    /**
     * @param regionGroups whether a region's own group carries it too; false for a team behaviour a region's list of
     *                     players has no use for
     * @param resolver     how equally specific layers join
     * @param availableOnPlatform whether this platform has what the group engine reads the flag for
     */
    GroupFlag(FlagKey key, FlagType<T> type, @Nullable T defaultValue, String description, boolean regionGroups,
              Class<? extends FlagSettings> settingsType, FlagResolver<T> resolver, BooleanSupplier availableOnPlatform) {
        super(key, type, Collections.emptySet(), defaultValue);
        this.description = description;
        this.regionGroups = regionGroups;
        this.settingsType = settingsType;
        this.resolver = resolver;
        this.availableOnPlatform = availableOnPlatform;
    }

    @Override
    public boolean isAvailableOnPlatform() {
        return availableOnPlatform.getAsBoolean();
    }

    @Override
    public FlagResolver<T> getResolver() {
        return resolver;
    }

    /** Only a group: a region's own group when {@code regionGroups}, else a group of a manager. */
    @Override
    public boolean accepts(FlagHolder holder) {
        return holder instanceof Group && (regionGroups || ((Group) holder).getRegion() == null);
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.DATA;
    }

    @Override
    public FlagScope getScope() {
        return FlagScope.ACTOR;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return settingsType;
    }
}
