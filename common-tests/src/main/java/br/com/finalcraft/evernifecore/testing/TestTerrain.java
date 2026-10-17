package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.terrain.ITerrainAccess;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
import br.com.finalcraft.evernifecore.region.terrain.TerrainPlacement;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A world of blocks and entities in memory, as {@link Platforms#terrain()} answers {@code getTerrainAccess()}. A block
 * is a name plus an optional nbt text; an entity is a name at a block position. Unset positions are {@code air}.
 *
 * <pre>{@code
 * TestTerrain terrain = platform.getTestTerrain();
 * terrain.setBlock("world", 1, 64, 1, "chest", "items=diamond");
 * terrain.spawn("world", "armor_stand", 1, 65, 1);
 * }</pre>
 */
public final class TestTerrain implements ITerrainAccess {

    private static final String NBT_SEPARATOR = "|nbt=";

    private final Map<String, String> blocks = new HashMap<>();
    private final Map<String, String> nbt = new HashMap<>();
    private final List<String> entities = new ArrayList<>();
    private final List<String> restoredPositions = new ArrayList<>();
    private int restores;
    private int failAfterRestores = -1;
    private String gameVersion = DEFAULT_GAME_VERSION;

    /** The game version a fresh terrain reports. */
    public static final String DEFAULT_GAME_VERSION = "test-1";

    public void setBlock(String world, int x, int y, int z, String block) {
        setBlock(world, x, y, z, block, null);
    }

    /** Sets the block and its tile-entity data; a null {@code data} clears it. */
    public void setBlock(String world, int x, int y, int z, String block, String data) {
        blocks.put(key(world, x, y, z), block);
        if (data == null) {
            nbt.remove(key(world, x, y, z));
        } else {
            nbt.put(key(world, x, y, z), data);
        }
    }

    public String getBlock(String world, int x, int y, int z) {
        return blocks.getOrDefault(key(world, x, y, z), "air");
    }

    /** The tile-entity data at the position; null when none. */
    public String getNbt(String world, int x, int y, int z) {
        return nbt.get(key(world, x, y, z));
    }

    /** Puts an entity in the world, as {@code name@x,y,z}. */
    public void spawn(String world, String name, int x, int y, int z) {
        entities.add(world + ":" + name + "@" + x + "," + y + "," + z);
    }

    /** Takes an entity out, as a kill does; false when it was not there. */
    public boolean kill(String world, String name, int x, int y, int z) {
        return entities.remove(world + ":" + name + "@" + x + "," + y + "," + z);
    }

    /** The entities of {@code world}, each as {@code name@x,y,z}. */
    public List<String> getEntities(String world) {
        List<String> inWorld = new ArrayList<>();
        for (String entity : entities) {
            if (entity.startsWith(world + ":")) {
                inWorld.add(entity.substring(world.length() + 1));
            }
        }
        return inWorld;
    }

    /** The state {@link #captureEntities} gives for an entity, for a test that records its removal by hand. */
    public static String entityState(String name, int x, int y, int z) {
        return name + "@" + x + "," + y + "," + z;
    }

    /** Every position {@code restoreBlock} wrote, in order, as {@code world:x,y,z}. */
    public List<String> getRestoredPositions() {
        return restoredPositions;
    }

    /** Empties the world and forgets what was restored and any scripted failure - a fresh terrain for the next test. */
    public TestTerrain clear() {
        blocks.clear();
        nbt.clear();
        entities.clear();
        restoredPositions.clear();
        restores = 0;
        failAfterRestores = -1;
        gameVersion = DEFAULT_GAME_VERSION;
        return this;
    }

    /** How many blocks were restored so far. */
    public int getRestores() {
        return restores;
    }

    /** Makes every {@code restoreBlock} after the first {@code count} throw - a restore cut short. */
    public TestTerrain failAfterRestores(int count) {
        this.failAfterRestores = count;
        return this;
    }

    @Override
    public String captureBlock(String world, int x, int y, int z, TerrainContent content) {
        String data = getNbt(world, x, y, z);
        return getBlock(world, x, y, z) + (content.isNbt() && data != null ? NBT_SEPARATOR + data : "");
    }

    @Override
    public void restoreBlock(String world, int x, int y, int z, String state) {
        if (failAfterRestores >= 0 && restores >= failAfterRestores) {
            throw new IllegalStateException("TestTerrain was told to fail after " + failAfterRestores + " restores");
        }
        int separator = state.indexOf(NBT_SEPARATOR);
        if (separator < 0) {
            setBlock(world, x, y, z, state, null);
        } else {
            setBlock(world, x, y, z, state.substring(0, separator), state.substring(separator + NBT_SEPARATOR.length()));
        }
        restoredPositions.add(key(world, x, y, z));
        restores++;
    }

    @Override
    public List<String> captureEntities(String world, BlockPos min, BlockPos max) {
        List<String> inside = new ArrayList<>();
        for (String entity : getEntities(world)) {
            String[] xyz = entity.substring(entity.indexOf('@') + 1).split(",");
            int x = Integer.parseInt(xyz[0]);
            int y = Integer.parseInt(xyz[1]);
            int z = Integer.parseInt(xyz[2]);
            if (x >= min.getX() && x <= max.getX() && y >= min.getY() && y <= max.getY() && z >= min.getZ() && z <= max.getZ()) {
                inside.add(entity);
            }
        }
        return inside;
    }

    @Override
    public void restoreEntity(String world, String state) {
        entities.add(world + ":" + state);
    }

    /** The server "upgraded": snapshots taken before carry the old version. */
    public TestTerrain setGameVersion(String gameVersion) {
        this.gameVersion = gameVersion;
        return this;
    }

    @Override
    public String getGameVersion() {
        return gameVersion;
    }

    @Override
    public String getSnapshotExtension() {
        return "txt";
    }

    /** One line per block of the box ({@code B x y z state}), then one per entity inside it ({@code E state}). */
    @Override
    public void writeSnapshot(String world, BlockPos min, BlockPos max, TerrainContent content, File file) throws IOException {
        List<String> lines = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    lines.add("B " + x + " " + y + " " + z + " " + captureBlock(world, x, y, z, content));
                }
            }
        }
        if (content.isEntities()) {
            for (String entity : captureEntities(world, min, max)) {
                lines.add("E " + entity);
            }
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }

    @Override
    public TerrainPlacement readSnapshot(String world, File file) throws IOException {
        Map<String, String> states = new HashMap<>();
        List<String> kept = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] max = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("B ")) {
                String[] parts = line.split(" ", 5);
                for (int axis = 0; axis < 3; axis++) {
                    int value = Integer.parseInt(parts[axis + 1]);
                    min[axis] = Math.min(min[axis], value);
                    max[axis] = Math.max(max[axis], value);
                }
                states.put(parts[1] + " " + parts[2] + " " + parts[3], parts[4]);
            } else {
                String entity = line.substring(2);
                String[] xyz = entity.substring(entity.indexOf('@') + 1).split(",");
                kept.add(entity);
                positions.add(BlockPos.of(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
            }
        }
        return new TerrainPlacement() {
            @Override
            public BlockPos getMin() {
                return BlockPos.of(min[0], min[1], min[2]);
            }

            @Override
            public BlockPos getMax() {
                return BlockPos.of(max[0], max[1], max[2]);
            }

            @Override
            public void placeBlock(int x, int y, int z) {
                restoreBlock(world, x, y, z, states.getOrDefault(x + " " + y + " " + z, "air"));
            }

            @Override
            public List<BlockPos> getEntityPositions() {
                return positions;
            }

            @Override
            public void placeEntity(int index) {
                restoreEntity(world, kept.get(index));
            }
        };
    }

    private static String key(String world, int x, int y, int z) {
        return world + ":" + x + "," + y + "," + z;
    }
}
