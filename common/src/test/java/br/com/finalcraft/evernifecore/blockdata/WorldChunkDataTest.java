package br.com.finalcraft.evernifecore.blockdata;

import br.com.finalcraft.evernifecore.blockdata.storage.WorldChunkData;
import br.com.finalcraft.evernifecore.blockdata.storage.WorldChunkDataCodec;
import br.com.finalcraft.evernifecore.config.factory.ConfigFactoryCodec;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The persisted entity on its own: the chunk key that has to survive a world name carrying the very
 * separator it is built from, the reserved metadata key that must never be mistaken for one, and the codec
 * that re-binds the erased block values to their concrete type.
 */
@ECoreTest
class WorldChunkDataTest {

    // -----------------------------------------------------------------------------------------------------
    //  Chunk key
    // -----------------------------------------------------------------------------------------------------

    @Test
    void chunkKeyRoundTrips() {
        String key = WorldChunkData.keyOf("world", ChunkPos.of(3, -7));

        assertEquals("world/3/-7", key);
        assertEquals("world", WorldChunkData.worldOf(key));
        assertEquals(ChunkPos.of(3, -7), WorldChunkData.chunkPosOf(key));
    }

    @Test
    void aWorldNameCarryingSlashesIsReadRightToLeft() {
        String key = WorldChunkData.keyOf("nether/deep/end", ChunkPos.of(-3, 7));

        assertEquals("nether/deep/end/-3/7", key);
        assertEquals("nether/deep/end", WorldChunkData.worldOf(key),
                "the world is everything before the LAST two separators, not before the first");
        assertEquals(ChunkPos.of(-3, 7), WorldChunkData.chunkPosOf(key));
    }

    @Test
    void theMetadataKeyIsRecognizedAndRefusedByTheChunkParse() {
        assertTrue(WorldChunkData.isMetaKey(WorldChunkData.META_KEY));
        assertFalse(WorldChunkData.isMetaKey("world/0/0"));

        IllegalArgumentException notAChunk = assertThrows(IllegalArgumentException.class,
                () -> WorldChunkData.worldOf(WorldChunkData.META_KEY));
        assertTrue(notAChunk.getMessage().contains("isMetaKey"), notAChunk.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> WorldChunkData.chunkPosOf(WorldChunkData.META_KEY));
    }

    // -----------------------------------------------------------------------------------------------------
    //  Dirty tracking and the flush snapshot
    // -----------------------------------------------------------------------------------------------------

    @Test
    void mutationFlagsTheChunkAndHandsBackWhatWasThere() {
        WorldChunkData<Marker> chunk = new WorldChunkData<>("world/0/0");
        assertFalse(chunk.isDirty());

        assertNull(chunk.putValue("1|2|3", new Marker("alice", 10)));
        assertTrue(chunk.isDirty());

        chunk.markClean();
        Marker replaced = chunk.putValue("1|2|3", new Marker("bob", 20));
        assertEquals("alice", replaced.getOwner(), "a replace hands back the value the notification needs");
        assertTrue(chunk.isDirty());

        chunk.markClean();
        assertNull(chunk.removeValue("9|9|9"));
        assertFalse(chunk.isDirty(), "removing a block that was not there changes nothing");
        assertNotNull(chunk.removeValue("1|2|3"));
        assertTrue(chunk.isDirty());
        assertTrue(chunk.isEmpty());
    }

    @Test
    void copyForSaveDetachesFromLaterWrites() {
        WorldChunkData<Marker> chunk = new WorldChunkData<>("world/0/0");
        chunk.putValue("0|0|0", new Marker("alice", 1));

        WorldChunkData<Marker> snapshot = chunk.copyForSave();
        chunk.putValue("1|1|1", new Marker("bob", 2));

        assertEquals(1, snapshot.getValues().size(), "the flush encodes what the chunk held under the lock");
        assertEquals(2, chunk.getValues().size());
        assertEquals("world/0/0", snapshot.getChunkKey());
    }

    // -----------------------------------------------------------------------------------------------------
    //  Codec
    // -----------------------------------------------------------------------------------------------------

    @Test
    void theCodecRebindsTheValuesToTheConcreteType() {
        WorldChunkDataCodec<Marker> codec = WorldChunkDataCodec.jsonFallback(Marker.class);
        WorldChunkData<Marker> chunk = new WorldChunkData<>(WorldChunkData.keyOf("world", ChunkPos.of(1, 2)));
        chunk.putValue(BlockPos.of(1, 2, 3).serialize(), new Marker("alice", 4));

        byte[] encoded = codec.encode(chunk);
        assertFalse(new String(encoded, StandardCharsets.UTF_8).contains("gridChunkSize"),
                "a real chunk carries no grid size, so it must not write the key at all");

        WorldChunkData<Marker> decoded = codec.decode(encoded);
        assertEquals("world/1/2", decoded.getChunkKey());
        assertNull(decoded.getGridChunkSize());
        Marker value = decoded.getValue("1|2|3");
        assertNotNull(value, "a raw Jackson read would have handed back a LinkedHashMap here");
        assertEquals("alice", value.getOwner());
        assertEquals(4, value.getAmount());
    }

    @Test
    void theCodecAdoptsTheFormatOfTheBackendItComposesOn() {
        WorldChunkDataCodec<Marker> codec = WorldChunkDataCodec
                .composing(Marker.class, ConfigFactoryCodec.yaml(WorldChunkData.class, null));

        assertEquals("application/yaml", codec.contentType());

        WorldChunkData<Marker> chunk = new WorldChunkData<>("world/0/0");
        chunk.putValue("0|64|0", new Marker("alice", 1));
        chunk.putValue("1|64|0", new Marker("bob", 2));

        String yaml = new String(codec.encode(chunk), StandardCharsets.UTF_8);
        assertTrue(yaml.startsWith("---"), yaml);

        Map<String, Marker> values = codec.decode(yaml.getBytes(StandardCharsets.UTF_8)).getValues();
        assertEquals(2, values.size());
        assertEquals("bob", values.get("1|64|0").getOwner());
    }

    @Test
    void theGridSentinelCarriesItsChunkSizeAcrossTheWire() {
        WorldChunkDataCodec<Marker> codec = WorldChunkDataCodec.jsonFallback(Marker.class);

        WorldChunkData<Marker> decoded = codec.decode(codec.encode(WorldChunkData.metaSentinel(16)));

        assertEquals(WorldChunkData.META_KEY, decoded.getChunkKey());
        assertEquals(Integer.valueOf(16), decoded.getGridChunkSize());
        assertTrue(decoded.isEmpty());
    }
}
