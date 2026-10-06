package br.com.finalcraft.evernifecore.api.platoverride.math.game.adapter;

import br.com.finalcraft.evernifecore.api.platoverride.math.game.adapter.GameVecPlatformAdapterConverter.AdaptChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameVecPlatformAdapterConverterTest {

    // ChunkPos holds chunk coordinates, so the index must round-trip to the same chunk, not to chunk / 32.
    @ParameterizedTest(name = "chunk ({0}, {1})")
    @CsvSource({"5, 5", "-3, 40", "0, 0", "-1, -1"})
    void chunkIndexRoundTripsToTheSameChunk(int chunkX, int chunkZ) {
        long index = new AdaptChunkPos(new ChunkPos(chunkX, chunkZ)).getChunkIndex();

        assertEquals(chunkX, ChunkUtil.xOfChunkIndex(index));
        assertEquals(chunkZ, ChunkUtil.zOfChunkIndex(index));
    }
}
