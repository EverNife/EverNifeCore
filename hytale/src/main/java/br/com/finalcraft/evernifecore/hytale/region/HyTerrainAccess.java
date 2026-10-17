package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.terrain.ITerrainAccess;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
import br.com.finalcraft.evernifecore.region.terrain.TerrainPlacement;
import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonDocument;
import org.joml.Vector3i;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

/**
 * Block access by position on Hytale: a block is {@code <asset id>|<rotation>|<filler>}, what
 * {@link BlockOperations#setBlock} takes back, and with {@link TerrainContent#isNbt()} a fourth field, its block
 * entity (a chest's items) as the server serializes it - the same copy a prefab keeps. No entities: the server fires no
 * event when an entity is removed, so nothing would record one. A snapshot (prefab) keeps the box's prefab-copyable
 * entities.
 */
public final class HyTerrainAccess implements ITerrainAccess {

    private static final String BOX_SUFFIX = ".box";

    @Override
    public String captureBlock(String world, int x, int y, int z, TerrainContent content) {
        Ref<ChunkStore> section = section(world(world), x, y, z);
        BlockSection blocks = section.getStore().getComponent(section, BlockSection.getComponentType());
        int index = ChunkUtil.indexBlock(x, y, z);
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(index));
        Holder<ChunkStore> entity = content.isNbt() ? blockEntity(section, index) : null;
        return new BlockState(type == null ? BlockType.EMPTY_KEY : type.getId(), blocks.getRotationIndex(index), blocks.getFiller(index),
                entity == null ? null : ChunkStore.REGISTRY.serialize(entity)).encode();
    }

    @Override
    public void restoreBlock(String world, int x, int y, int z, String state) {
        World at = world(world);
        BlockState.parse(state).putWith(new BlockWriter() {
            @Override
            public void block(String id, int rotation, int filler) {
                BlockOperations.setBlock(at.getChunkStore(), section(at, x, y, z), x, y, z, BlockType.getAssetMap().getIndex(id), type(id),
                        rotation, filler, 0);
            }

            @Override
            public void entity(String id, int rotation, BsonDocument components) {
                setBlockEntity(section(at, x, y, z), x, y, z, type(id), rotation, ChunkStore.REGISTRY.deserialize(components));
            }
        });
    }

    /**
     * A block as the journal keeps it; {@code components} is its block entity, null when there is none or nbt is off.
     * The JSON goes last, so a {@code |} inside it never splits the state.
     */
    record BlockState(String blockId, int rotation, int filler, BsonDocument components) {

        static BlockState parse(String state) {
            String[] parts = state.split("\\|", 4);
            return new BlockState(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    parts.length == 4 ? BsonDocument.parse(parts[3]) : null);
        }

        String encode() {
            return blockId + "|" + rotation + "|" + filler + (components == null ? "" : "|" + components.toJson());
        }

        /** The block first - placing it seeds the type's empty block entity - then the kept one over it. */
        void putWith(BlockWriter writer) {
            writer.block(blockId, rotation, filler);
            if (components != null) {
                writer.entity(blockId, rotation, components);
            }
        }
    }

    /** Writes a block, then a block entity, at one position. */
    interface BlockWriter {
        void block(String blockId, int rotation, int filler);

        void entity(String blockId, int rotation, BsonDocument components);
    }

    private static BlockType type(String id) {
        BlockType type = BlockType.getAssetMap().getAsset(id);
        if (type == null) {
            throw new IllegalArgumentException("The journal holds the block " + id + ", which this server has no asset for;"
                    + " install the pack that adds it, or drop the journal of that region.");
        }
        return type;
    }

    /** The block entity at the index as a detached copy (a live one or one of an unloaded chunk), or null. */
    private static Holder<ChunkStore> blockEntity(Ref<ChunkStore> section, int index) {
        BlockComponentSection components = section.getStore().getComponent(section, BlockComponentSection.getComponentType());
        return components == null ? null : components.copyBlockHolder(index);
    }

    /** Replaces the block entity {@link BlockOperations#setBlock} seeded for the type with {@code entity}. */
    private static void setBlockEntity(Ref<ChunkStore> section, int x, int y, int z, BlockType type, int rotation, Holder<ChunkStore> entity) {
        BlockComponentSection components = section.getStore().getComponent(section, BlockComponentSection.getComponentType());
        if (components != null) {
            BlockEntity.setBlockEntity(section.getStore(), section, components, x, y, z, type, rotation, entity);
        }
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

    @Override
    public String getGameVersion() {
        return "hytale-" + ManifestUtil.getImplementationVersion();
    }

    @Override
    public String getSnapshotExtension() {
        return "prefab.json";
    }

    /**
     * The box as a prefab through the server's {@link PrefabStore}, every position of it - air included, so a restore
     * also clears what was built after - with each block's entity when nbt is on. Its corners go to a {@code .box} file beside it: a prefab keeps no world position.
     */
    @Override
    public void writeSnapshot(String world, BlockPos min, BlockPos max, TerrainContent content, File file) throws IOException {
        World at = world(world);
        BlockSelection selection = new BlockSelection();
        selection.setPosition(min.getX(), min.getY(), min.getZ());
        selection.setAnchorAtWorldPos(min.getX(), min.getY(), min.getZ());
        selection.setSelectionArea(new Vector3i(min.getX(), min.getY(), min.getZ()), new Vector3i(max.getX(), max.getY(), max.getZ()));
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    Ref<ChunkStore> section = section(at, x, y, z);
                    BlockSection blocks = section.getStore().getComponent(section, BlockSection.getComponentType());
                    int index = ChunkUtil.indexBlock(x, y, z);
                    int id = blocks.get(index);
                    if (id == emptyId()) {
                        selection.addEmptyAtWorldPos(x, y, z);
                    } else {
                        selection.addBlockAtWorldPos(x, y, z, id, blocks.getRotationIndex(index), blocks.getFiller(index), 0,
                                content.isNbt() ? blockEntity(section, index) : null);
                    }
                }
            }
        }
        if (content.isEntities()) {
            Store<EntityStore> store = at.getEntityStore().getStore();
            //the server's own prefab copy: every entity it marks prefab-copyable, positioned relative to the selection
            BuilderToolsPlugin.forEachCopyableInSelection(at, min.getX(), min.getY(), min.getZ(), max.getX() - min.getX() + 1,
                    max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1, ref -> selection.addEntityFromWorld(store.copyEntity(ref)));
        }
        Path saved = PrefabStore.get().savePrefab(file.toPath(), selection, true);
        if (saved != null && !saved.toAbsolutePath().equals(file.toPath().toAbsolutePath())) {
            Files.move(saved, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(new File(file.getPath() + BOX_SUFFIX).toPath(), Collections.singletonList(min.getX() + " " + min.getY() + " "
                + min.getZ() + " " + max.getX() + " " + max.getY() + " " + max.getZ()), StandardCharsets.UTF_8);
    }

    /**
     * Puts the prefab back position by position - {@link BlockSelection#place} has no budget of its own - then spawns its
     * entities as the server places a prefab's, with {@link AddReason#LOAD}.
     */
    @Override
    public TerrainPlacement readSnapshot(String world, File file) throws IOException {
        String[] box = Files.readAllLines(new File(file.getPath() + BOX_SUFFIX).toPath(), StandardCharsets.UTF_8).get(0).split(" ");
        int minX = Integer.parseInt(box[0]), minY = Integer.parseInt(box[1]), minZ = Integer.parseInt(box[2]);
        int sizeX = Integer.parseInt(box[3]) - minX + 1, sizeY = Integer.parseInt(box[4]) - minY + 1, sizeZ = Integer.parseInt(box[5]) - minZ + 1;
        BlockSelection selection = PrefabStore.get().getPrefab(file.toPath());
        if (selection == null) {
            throw new IOException(file + " does not read as a prefab on this server.");
        }
        selection.setPosition(minX, minY, minZ);
        World at = world(world);
        long volume = (long) sizeX * sizeY * sizeZ;
        long[] next = {0};
        return budget -> {
            for (int placed = 0; placed < budget && next[0] < volume; placed++, next[0]++) {
                long i = next[0];
                int x = minX + (int) (i / ((long) sizeY * sizeZ));
                int y = minY + (int) (i / sizeZ % sizeY);
                int z = minZ + (int) (i % sizeZ);
                BlockSelection.BlockHolder holder = selection.hasBlockAtWorldPos(x, y, z) ? selection.getBlockHolderAtWorldPos(x, y, z) : null;
                if (holder == null || holder.blockId() == emptyId()) {
                    BlockOperations.setBlock(at.getChunkStore(), section(at, x, y, z), x, y, z, emptyId(), BlockType.getAssetMap().getAsset(BlockType.EMPTY_KEY), 0, 0, 0);
                } else {
                    Ref<ChunkStore> section = section(at, x, y, z);
                    BlockType type = BlockType.getAssetMap().getAsset(holder.blockId());
                    BlockOperations.setBlock(at.getChunkStore(), section, x, y, z, holder.blockId(), type, holder.rotation(), holder.filler(), 0);
                    if (holder.holder() != null) {
                        setBlockEntity(section, x, y, z, type, holder.rotation(), holder.holder().clone());
                    }
                }
            }
            if (next[0] < volume) {
                return false;
            }
            Store<EntityStore> store = at.getEntityStore().getStore();
            selection.forEachEntity(saved -> {
                Holder<EntityStore> entity = saved.clone();
                entity.getComponent(TransformComponent.getComponentType()).getPosition().add(minX, minY, minZ);
                store.addEntity(entity, AddReason.LOAD);
            });
            return true;
        };
    }

    private static int emptyId() {
        return BlockType.getAssetMap().getIndex(BlockType.EMPTY_KEY);
    }
}
