package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;

import java.util.Collections;
import java.util.List;

/** Where teleporting to the region takes a player. */
public final class TeleportPointFlag extends Flag<WorldLocPos> {

    public TeleportPointFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "teleport-point"), FlagTypes.LOCATION, Collections.singleton(EventSource.NONE), null);
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
