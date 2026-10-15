package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.terrain.ITerrainAccess;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.List;

/**
 * Block access by position on Hytale: a block is {@code <asset id>|<rotation>|<filler>}, what
 * {@link BlockOperations#setBlock} takes back. The data of block components (a chest's items) is not kept, so
 * {@link TerrainContent#isNbt()} changes nothing here, and entities are not captured - the journal of a Hytale region
 * keeps blocks only; a snapshot (prefab) keeps the rest.
 */
public final class HyTerrainAccess implements ITerrainAccess {

    @Override
    public String captureBlock(String world, int x, int y, int z, TerrainContent content) {
        Ref<ChunkStore> section = section(world(world), x, y, z);
        BlockSection blocks = section.getStore().getComponent(section, BlockSection.getComponentType());
        int index = ChunkUtil.indexBlock(x, y, z);
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(index));
        return (type == null ? BlockType.EMPTY_KEY : type.getId()) + "|" + blocks.getRotationIndex(index) + "|" + blocks.getFiller(index);
    }

    @Override
    public void restoreBlock(String world, int x, int y, int z, String state) {
        String[] parts = state.split("\\|");
        World at = world(world);
        BlockType type = BlockType.getAssetMap().getAsset(parts[0]);
        if (type == null) {
            throw new IllegalArgumentException("The journal holds the block " + parts[0] + ", which this server has no asset for;"
                    + " install the pack that adds it, or drop the journal of that region.");
        }
        BlockOperations.setBlock(at.getChunkStore(), section(at, x, y, z), x, y, z, BlockType.getAssetMap().getIndex(parts[0]), type,
                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), 0);
    }

    @Override
    public List<String> captureEntities(String world, BlockPos min, BlockPos max) {
        throw entitiesUnsupported();
    }

    @Override
    public void restoreEntity(String world, String state) {
        throw entitiesUnsupported();
    }

    private static UnsupportedOperationException entitiesUnsupported() {
        return new UnsupportedOperationException("Hytale terrain access keeps no entities by position; a region's entities come"
                + " back from a snapshot (prefab), not from its journal.");
    }

    private static Ref<ChunkStore> section(World world, int x, int y, int z) {
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null) {
            throw new IllegalStateException("The chunk section at " + x + "," + y + "," + z + " of " + world.getName() + " is not"
                    + " loaded; the server keeps only the chunks near a player - restore while someone stands there.");
        }
        return section;
    }

    private static World world(String name) {
        World world = Universe.get() == null ? null : Universe.get().getWorld(name);
        if (world == null) {
            throw new IllegalArgumentException("No world named " + name + " is loaded; load it before touching its terrain.");
        }
        return world;
    }
}
