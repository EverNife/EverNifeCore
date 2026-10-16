package br.com.finalcraft.evernifecore.region.terrain;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * How a platform reads and writes the world by position, for {@link RegionTerrain}. Every state is an opaque text the
 * platform alone reads back - of its own version only - and every call runs on the world's thread.
 */
public interface ITerrainAccess {

    /** The access of a platform that cannot touch blocks: every call throws, so a journal never pretends to work. */
    ITerrainAccess UNSUPPORTED = new ITerrainAccess() {
        @Override
        public String captureBlock(String world, int x, int y, int z, TerrainContent content) {
            throw unsupported();
        }

        @Override
        public void restoreBlock(String world, int x, int y, int z, String state) {
            throw unsupported();
        }

        @Override
        public List<String> captureEntities(String world, BlockPos min, BlockPos max) {
            throw unsupported();
        }

        @Override
        public void restoreEntity(String world, String state) {
            throw unsupported();
        }

        @Override
        public String getGameVersion() {
            throw unsupported();
        }

        @Override
        public String getSnapshotExtension() {
            throw unsupported();
        }

        @Override
        public void writeSnapshot(String world, BlockPos min, BlockPos max, TerrainContent content, File file) {
            throw unsupported();
        }

        @Override
        public TerrainPlacement readSnapshot(String world, File file) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("This platform cannot read or write blocks by position, so region terrain"
                    + " (track-changes, restore) does nothing here - run it on a platform whose IPlatform.getTerrainAccess() is not"
                    + " ITerrainAccess.UNSUPPORTED.");
        }
    };

    /** The block at the position; with {@link TerrainContent#isNbt()} also the data of its tile entity (a chest's items). */
    String captureBlock(String world, int x, int y, int z, TerrainContent content);

    /** Puts back a state {@link #captureBlock} produced. */
    void restoreBlock(String world, int x, int y, int z, String state);

    /** Every non-player entity standing in the box (both corners inclusive), one state each. */
    List<String> captureEntities(String world, BlockPos min, BlockPos max);

    /** Spawns again an entity a capture produced, where it stood. */
    void restoreEntity(String world, String state);

    /** The game version this server runs; a snapshot carries it and restores only on the same one. */
    String getGameVersion();

    /** The extension of this platform's snapshot files, without the dot. */
    String getSnapshotExtension();

    /** Saves the box (both corners inclusive) with {@code content} into {@code file}, in the platform's own format. */
    void writeSnapshot(String world, BlockPos min, BlockPos max, TerrainContent content, File file) throws IOException;

    /** Opens a file {@link #writeSnapshot} wrote, to be put back where it was taken, a budget at a time. */
    TerrainPlacement readSnapshot(String world, File file) throws IOException;
}
