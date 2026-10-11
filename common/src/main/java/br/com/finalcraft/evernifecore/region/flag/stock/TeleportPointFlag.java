package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;

import java.util.EnumSet;
import java.util.List;

/** Where teleporting to the region takes a player. */
public final class TeleportPointFlag extends RegionFlag<WorldLocPos> {

    public TeleportPointFlag(ECPluginData owner) {
        super(FlagKey.of(owner, "teleport-point"), FlagTypes.LOCATION, EnumSet.of(EventSource.NONE), null);
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.DATA;
    }

    /** The first. */
    @Override
    public WorldLocPos resolveTie(List<WorldLocPos> values) {
        return values.get(0);
    }

    @Override
    public String getDescription() {
        return "Where teleporting to the region takes a player.";
    }
}
