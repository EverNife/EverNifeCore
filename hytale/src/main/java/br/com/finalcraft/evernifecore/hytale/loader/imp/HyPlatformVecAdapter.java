package br.com.finalcraft.evernifecore.hytale.loader.imp;

import br.com.finalcraft.evernifecore.api.common.math.game.adapter.GameVecPlatformAdapterConverter;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformVecAdapter;
import br.com.finalcraft.evernifecore.math.vector.base.IVec2i;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;

public class HyPlatformVecAdapter implements IPlatformVecAdapter {

    @Override
    public GameVecPlatformAdapterConverter getPosConverter() {
        return GameVecPlatformAdapterConverter.INSTANCE;
    }

    @Override
    public GameVecPlatformAdapterConverter.AdaptBlockPos adaptBlockPos(IVec3i iVec3i) {
        return new GameVecPlatformAdapterConverter.AdaptBlockPos(iVec3i);
    }

    @Override
    public GameVecPlatformAdapterConverter.AdaptBlockPosWorld adaptBlockPosWorld(IVec3i iVec3i, String worldName) {
        return new GameVecPlatformAdapterConverter.AdaptBlockPosWorld(iVec3i, worldName);
    }

    @Override
    public GameVecPlatformAdapterConverter.AdaptLocPos adaptLocPos(IVec3d iVec3d) {
        return new GameVecPlatformAdapterConverter.AdaptLocPos(iVec3d);
    }

    @Override
    public GameVecPlatformAdapterConverter.AdaptLocPosWorld adaptLocPosWorld(IVec3d iVec3d, String worldName) {
        return new GameVecPlatformAdapterConverter.AdaptLocPosWorld(iVec3d, worldName);
    }

    @Override
    public GameVecPlatformAdapterConverter.AdaptChunkPos adaptChunkPos(IVec2i iVec2i) {
        return new GameVecPlatformAdapterConverter.AdaptChunkPos(iVec2i);
    }

    @Override
    public GameVecPlatformAdapterConverter.AdaptChunkPosWorld adaptChunkPosWorld(IVec2i iVec2i, String worldName) {
        return new GameVecPlatformAdapterConverter.AdaptChunkPosWorld(iVec2i, worldName);
    }

}
