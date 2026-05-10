package br.com.finalcraft.evernifecore.api.common.game;

import br.com.finalcraft.evernifecore.api.common.IHasDelegate;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;

public class FLocation implements IHasDelegate {

    private final WorldLocPos worldLocPos;
    private final Object location;

    public FLocation(WorldLocPos worldLocPos, Object location) {
        this.worldLocPos = worldLocPos;
        this.location = location;
    }

    public String getWorldName() {
        return getWorldLocPos().getWorldName();
    }

    public WorldLocPos getWorldLocPos() {
        return worldLocPos;
    }

    public BlockPos getBlockPos() {
        return getWorldLocPos().getBlockPos();
    }

    public LocPos getLocPos() {
        return getWorldLocPos().getLocPos();
    }

    @Override
    public Object getDelegate() {
        return location;
    }
}
