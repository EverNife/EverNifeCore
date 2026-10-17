package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.hytale.region.HyTerrainAccess.BlockState;
import br.com.finalcraft.evernifecore.hytale.region.HyTerrainAccess.BlockWriter;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The text a Hytale journal keeps per block, and the order a restore writes it back. */
class HyTerrainAccessTest {

    private static final BsonDocument CHEST = BsonDocument.parse(
            "{\"ItemContainer\": {\"Items\": {\"0\": {\"Id\": \"Rock_Stone\", \"Quantity\": 7}}, \"Custom\": \"a|b\"}}");

    /** Records every write as one line. */
    static final class RecordingWriter implements BlockWriter {
        final List<String> writes = new ArrayList<>();

        @Override
        public void block(String blockId, int rotation, int filler) {
            writes.add("block " + blockId + " " + rotation + " " + filler);
        }

        @Override
        public void entity(String blockId, int rotation, BsonDocument components) {
            writes.add("entity " + blockId + " " + rotation + " " + components.toJson());
        }
    }

    @Test
    void aBlockEntitySurvivesTheTextEvenWithABarInside() {
        BlockState back = BlockState.parse(new BlockState("Furniture_Chest", 2, 0, CHEST).encode());

        assertEquals(new BlockState("Furniture_Chest", 2, 0, CHEST), back);
    }

    @Test
    void aBlockWithoutEntityKeepsTheThreeFieldText() {
        String state = new BlockState("Rock_Stone", 0, 0, null).encode();

        assertEquals("Rock_Stone|0|0", state);
        assertNull(BlockState.parse(state).components());
    }

    @Test
    void restorePutsTheBlockThenItsKeptEntity() {
        RecordingWriter writer = new RecordingWriter();

        BlockState.parse(new BlockState("Furniture_Chest", 2, 0, CHEST).encode()).putWith(writer);

        assertEquals(List.of("block Furniture_Chest 2 0", "entity Furniture_Chest 2 " + CHEST.toJson()), writer.writes);
    }

    @Test
    void restoreWithoutEntityWritesOnlyTheBlock() {
        RecordingWriter writer = new RecordingWriter();

        BlockState.parse("Rock_Stone|0|0").putWith(writer);

        assertEquals(List.of("block Rock_Stone 0 0"), writer.writes);
    }
}
