package br.com.finalcraft.evernifecore.region.placeholder;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Which regions one placeholder prefix sees, and how it names them. {@link #everyRegion()} sees all of them by their
 * full id ({@code theduel:arena1}); {@link #ownedBy} sees only one plugin's, by the bare name its own commands take
 * ({@code arena1}).
 */
public final class RegionPlaceholderScope {

    private static final RegionPlaceholderScope EVERY_REGION = new RegionPlaceholderScope(null);

    private final @Nullable ECPluginData owner;

    private RegionPlaceholderScope(@Nullable ECPluginData owner) {
        this.owner = owner;
    }

    /** Every region of every plugin, named by its full id. */
    public static RegionPlaceholderScope everyRegion() {
        return EVERY_REGION;
    }

    /** Only the regions {@code owner} created, named by their bare name. */
    public static RegionPlaceholderScope ownedBy(ECPluginData owner) {
        return new RegionPlaceholderScope(Objects.requireNonNull(owner, "owner"));
    }

    public boolean includes(Region region) {
        return owner == null || region.getOwner() == owner;
    }

    /** The region's name in this scope. */
    public String idOf(Region region) {
        return owner == null ? region.getId().toString() : region.getId().getName();
    }

    /** The region this scope calls {@code text}; null when there is none, or the text is no valid name here. */
    public @Nullable Region find(@Nullable String text) {
        if (text == null) {
            return null;
        }
        try {
            RegionId id = owner == null ? RegionId.parse(text.trim()) : RegionId.of(owner.getMetaInfo().getName(), text.trim());
            Region region = RegionLookup.get().byId(id);
            return region != null && includes(region) ? region : null;
        } catch (IllegalArgumentException notAName) {
            return null;
        }
    }

    /**
     * The regions of this scope the player is in, the deciding one first: as the movement tracker last saw them, or
     * at the player's location when the tracker has not seen them yet.
     */
    public List<Region> at(FPlayer player) {
        RegionPlayerSession session = RegionLookup.get().getSessionIfPresent(player.getUniqueId());
        List<Region> all = session != null && session.getPosition() != null ? session.getCurrent() : RegionLookup.get().at(player);
        List<Region> inScope = new ArrayList<>(all.size());
        for (Region region : all) {
            if (includes(region)) {
                inScope.add(region);
            }
        }
        return inScope;
    }
}
