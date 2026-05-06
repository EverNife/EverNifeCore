package br.com.finalcraft.evernifecore.api.common.game;

import br.com.finalcraft.evernifecore.api.common.IHasDelegate;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;

public abstract class FLocation implements IHasDelegate {

    private final WorldLocPos worldLocPos;

    public FLocation(WorldLocPos worldLocPos) {
        this.worldLocPos = worldLocPos;
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

    @Override
    public Object getDelegate() {
        return null;
    }
}
