package br.com.finalcraft.evernifecore.api.common.providers.platform;

import br.com.finalcraft.evernifecore.math.vector.base.IVec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.minecraft.math.game.adapter.MCGameVecAdapter;

public interface IPlatformVecAdapter {

    public MCGameVecAdapter.AdaptBlockPos adaptBlockPos(IVec3i iVec3i);

    public MCGameVecAdapter.AdaptBlockPosWorld adaptBlockPosWorld(IVec3i iVec3i);

    public MCGameVecAdapter.AdaptLocPos adaptLocPos(IVec3d iVec3d);

    public MCGameVecAdapter.AdaptLocPosWorld adaptLocPosWorld(IVec3d iVec3d);

    public MCGameVecAdapter.AdaptChunkPos adaptChunkPos(IVec2i iVec2i);

    public MCGameVecAdapter.AdaptChunkPosWorld adaptChunkPosWorld(IVec2i iVec2i);

}
