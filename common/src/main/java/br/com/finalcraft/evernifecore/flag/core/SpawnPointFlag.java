package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;

import java.util.Collections;
import java.util.List;

/**
 * Where a player who died inside the region respawns, ahead of a bed or a respawn anchor. The platform's respawn
 * listener reads it at the point of death, and is on the server only while some region holds it.
 */
public final class SpawnPointFlag extends Flag<WorldLocPos> {

    public SpawnPointFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "spawn-point"), FlagTypes.LOCATION, Collections.singleton(EventSource.RESPAWN), null);
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.DATA;
    }


    @Override
    public String getDescription() {
        return "Where a player who dies inside the region respawns - ahead of a bed or a respawn anchor.";
    }
}
