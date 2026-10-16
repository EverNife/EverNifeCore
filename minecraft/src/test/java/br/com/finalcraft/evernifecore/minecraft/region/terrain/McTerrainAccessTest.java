package br.com.finalcraft.evernifecore.minecraft.region.terrain;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bukkit snapshots are WorldEdit's to write, without it the way out is named; a journaled entity respawns where Bukkit takes it. */
class McTerrainAccessTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void withoutWorldEditASnapshotFailsNamingWorldEditAndTheJournal() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            File file = tempDir.resolve("1.schem").toFile();
            IllegalStateException refused = assertThrows(IllegalStateException.class, () -> new McTerrainAccess()
                    .writeSnapshot(RegionServer.WORLD, new BlockPos(0, 0, 0), new BlockPos(1, 1, 1), TerrainContent.ALL, file));

            assertTrue(refused.getMessage().contains("install WorldEdit"), refused.getMessage());
            assertTrue(refused.getMessage().contains("track-changes") && refused.getMessage().contains("restoreJournal"), refused.getMessage());
            assertFalse(file.exists());
        }
    }

    @Test
    void withoutWorldEditTheSnapshotExtensionStillAnswersSoListingFindsNothingInsteadOfThrowing() {
        try (RegionServer server = RegionServer.open(tempDir)) {
            McTerrainAccess access = new McTerrainAccess();

            assertEquals("schem", access.getSnapshotExtension(), "a 1.13+ server lists WorldEdit 7's .schem files");
            assertTrue(access.getGameVersion().startsWith("minecraft-"), access.getGameVersion());
        }
    }

    @Test
    void beforeOneEightAFrameRespawnsAtTheBlockItHangsOnAndAnythingElseWhereItStood() {
        Location stood = new Location(null, 11.03, 64.0, 20.5, 90F, 0F);

        Location frame = McTerrainAccess.spawnPoint(EntityType.ITEM_FRAME, stood, 10, 64, 20, true);
        assertEquals(10, frame.getBlockX());
        assertEquals(64, frame.getBlockY());
        assertEquals(20, frame.getBlockZ());
        assertSame(stood, McTerrainAccess.spawnPoint(EntityType.ITEM_FRAME, stood, 10, 64, 20, false), "1.8+ spawns it where it stood");
        assertSame(stood, McTerrainAccess.spawnPoint(EntityType.PIG, stood, null, null, null, true));
    }
}
