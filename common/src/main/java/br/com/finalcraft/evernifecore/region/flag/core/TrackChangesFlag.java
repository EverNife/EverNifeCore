package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.terrain.RegionTerrain;

import java.util.Arrays;
import java.util.HashSet;

/**
 * TRUE: the region's {@link RegionTerrain} journal keeps the original state of every position changed inside it, so
 * {@link RegionTerrain#restoreJournal} can put it back. Only changes that happened count - confirmed after every
 * plugin had its say. Covered: block breaks, block places, the blocks an explosion destroys, and the non-player
 * entities they remove. Fire spread, fluid flow and pistons are not recorded.
 */
public final class TrackChangesFlag extends TristateFlag {

    public TrackChangesFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "track-changes"),
                new HashSet<>(Arrays.asList(EventSource.BREAK, EventSource.PLACE, EventSource.EXPLOSION)), Tristate.FALSE, Tristate.TRUE);
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.DATA;
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return TrackChangesSettings.class;
    }

    @Override
    public String getDescription() {
        return "TRUE: the region remembers the original blocks changed inside it, so they can be restored.";
    }
}
