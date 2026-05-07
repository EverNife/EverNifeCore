package br.com.finalcraft.evernifecore.api.common.providers.platform;

import br.com.finalcraft.evernifecore.math.vector.base.IVec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.minecraft.math.game.adapter.GameVecPlatformAdapterConverter;

public interface IPlatformVecAdapter {

    public GameVecPlatformAdapterConverter getPosConverter();

    public GameVecPlatformAdapterConverter.AdaptBlockPos adaptBlockPos(IVec3i iVec3i);

    public GameVecPlatformAdapterConverter.AdaptBlockPosWorld adaptBlockPosWorld(IVec3i iVec3i);

    public GameVecPlatformAdapterConverter.AdaptLocPos adaptLocPos(IVec3d iVec3d);

    public GameVecPlatformAdapterConverter.AdaptLocPosWorld adaptLocPosWorld(IVec3d iVec3d);

    public GameVecPlatformAdapterConverter.AdaptChunkPos adaptChunkPos(IVec2i iVec2i);

    public GameVecPlatformAdapterConverter.AdaptChunkPosWorld adaptChunkPosWorld(IVec2i iVec2i);


}
