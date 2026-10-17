package br.com.finalcraft.evernifecore.minecraft.region.terrain;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.minecraft.worldedit.WEPlatform;
import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.format.IFCClipboardFormat;
import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.format.IFCClipboardManager;
import br.com.finalcraft.evernifecore.minecraft.worldedit.region.IFCCuboidRegion;
import br.com.finalcraft.evernifecore.region.terrain.ITerrainAccess;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
import br.com.finalcraft.evernifecore.region.terrain.TerrainPlacement;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.util.io.Closer;
import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Block and entity access by position on Bukkit. A block is its {@code BlockData} text from 1.13 on and
 * {@code MATERIAL:data} before, followed by {@code |nbt=} and the tile entity's SNBT when nbt is kept. An entity is
 * {@code TYPE|x,y,z,yaw,pitch|snbt}. Tile entities Bukkit does not wrap (some modded blocks on a legacy hybrid) keep
 * only their block.
 */
public final class McTerrainAccess implements ITerrainAccess {

    private static final String NBT_SEPARATOR = "|nbt=";
    private static final String ENTITIES_SUFFIX = ".entities";
    private static final boolean BLOCK_DATA = MCVersion.isHigherEquals(MCDetailedVersion.v1_13_R1);
    private static final boolean HANGING_SPAWNS_ON_ITS_WALL = MCVersion.isLower(MCDetailedVersion.v1_8_R1);
    //what makes a respawned entity a copy instead of the same one, or a dead one
    private static final Set<String> ENTITY_KEYS_NOT_RESTORED = new HashSet<>(Arrays.asList("UUID", "UUIDMost", "UUIDLeast",
            "Pos", "Motion", "Health", "DeathTime", "HurtTime", "Dimension", "WorldUUIDMost", "WorldUUIDLeast"));

    @Override
    public String captureBlock(String world, int x, int y, int z, TerrainContent content) {
        return capture(world(world).getBlockAt(x, y, z).getState(), content);
    }

    /** The state of {@code state} as {@link #restoreBlock} reads it back - for a block that has already changed in the world. */
    @SuppressWarnings("deprecation")   //the raw data byte is the only block state before 1.13
    public static String capture(BlockState state, TerrainContent content) {
        String block = BLOCK_DATA ? BlockDataText.of(state) : state.getType().name() + ":" + state.getRawData();
        if (content.isNbt() && isTileEntity(state)) {
            return block + NBT_SEPARATOR + NBT.get(state, (ReadableNBT nbt) -> nbt.toString());
        }
        return block;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void restoreBlock(String world, int x, int y, int z, String state) {
        int separator = state.indexOf(NBT_SEPARATOR);
        String block = separator < 0 ? state : state.substring(0, separator);
        Block at = world(world).getBlockAt(x, y, z);
        if (BLOCK_DATA) {
            BlockDataText.set(at, block);
        } else {
            int colon = block.lastIndexOf(':');
            Material material = Material.getMaterial(block.substring(0, colon));
            if (material == null) {
                throw new IllegalArgumentException("The journal holds the block " + block + ", a material this server does not know;"
                        + " a terrain state only restores on the server version that captured it.");
            }
            BlockState fresh = at.getState();
            fresh.setType(material);
            fresh.setRawData(Byte.parseByte(block.substring(colon + 1)));
            fresh.update(true, false);
        }
        if (separator >= 0) {
            ReadWriteNBT saved = NBT.parseNBT(state.substring(separator + NBT_SEPARATOR.length()));
            NBT.modify(at.getState(), (ReadWriteNBT nbt) -> {
                nbt.mergeCompound(saved);
            });
        }
    }

    @Override
    public List<String> captureEntities(String world, BlockPos min, BlockPos max) {
        List<String> inside = new ArrayList<>();
        for (Entity entity : world(world).getEntities()) {
            Location at = entity.getLocation();
            if (!(entity instanceof Player) && at.getBlockX() >= min.getX() && at.getBlockX() <= max.getX() && at.getBlockY() >= min.getY()
                    && at.getBlockY() <= max.getY() && at.getBlockZ() >= min.getZ() && at.getBlockZ() <= max.getZ()) {
                inside.add(capture(entity));
            }
        }
        return inside;
    }

    /** The entity as {@link #restoreEntity} spawns it again. */
    public static String capture(Entity entity) {
        Location at = entity.getLocation();
        return entity.getType().name() + "|" + at.getX() + "," + at.getY() + "," + at.getZ() + "," + at.getYaw() + "," + at.getPitch()
                + "|" + NBT.get(entity, (ReadableNBT nbt) -> nbt.toString());
    }

    @Override
    public void restoreEntity(String world, String state) {
        String[] parts = state.split("\\|", 3);
        String[] xyz = parts[1].split(",");
        Location at = new Location(world(world), Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]),
                Float.parseFloat(xyz[3]), Float.parseFloat(xyz[4]));
        EntityType type = EntityType.valueOf(parts[0]);
        ReadWriteNBT saved = NBT.parseNBT(parts[2]);
        Entity spawned = at.getWorld().spawnEntity(spawnPoint(type, at, saved.getInteger("TileX"), saved.getInteger("TileY"),
                saved.getInteger("TileZ"), HANGING_SPAWNS_ON_ITS_WALL), type);
        ENTITY_KEYS_NOT_RESTORED.forEach(saved::removeKey);
        NBT.modify(spawned, (ReadWriteNBT nbt) -> {
            nbt.mergeCompound(saved);
        });
    }

    /**
     * Where Bukkit spawns {@code type} so it lands at {@code at}. Before 1.8 a frame or painting is spawned at the block it
     * hangs on - its {@code TileX/Y/Z} then - and the spawn refuses the free block the entity itself stands in.
     */
    static Location spawnPoint(EntityType type, Location at, Integer tileX, Integer tileY, Integer tileZ, boolean hangingSpawnsOnItsWall) {
        if (hangingSpawnsOnItsWall && tileX != null && tileY != null && tileZ != null && type.getEntityClass() != null
                && Hanging.class.isAssignableFrom(type.getEntityClass())) {
            return new Location(at.getWorld(), tileX, tileY, tileZ, at.getYaw(), at.getPitch());
        }
        return at;
    }

    /** Whether the state carries a tile entity: every era wraps one in a subclass of the plain {@code CraftBlockState}. */
    private static boolean isTileEntity(BlockState state) {
        return !state.getClass().getSimpleName().equals("CraftBlockState");
    }

    /** The 1.13+ block state; loaded only behind {@link #BLOCK_DATA}. */
    private static final class BlockDataText {
        static String of(BlockState state) {
            return state.getBlockData().getAsString();
        }

        static void set(Block block, String text) {
            block.setBlockData(Bukkit.createBlockData(text), false);
        }
    }

    private static World world(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            throw new IllegalArgumentException("No world named " + name + " is loaded; load it before touching its terrain.");
        }
        return world;
    }

    @Override
    public String getGameVersion() {
        return "minecraft-" + Bukkit.getBukkitVersion();
    }

    /** WorldEdit 7 (1.13+) writes the Sponge {@code .schem}, WorldEdit 6 the MCEdit {@code .schematic}. */
    @Override
    public String getSnapshotExtension() {
        return BLOCK_DATA ? "schem" : "schematic";
    }

    /**
     * The box through WorldEdit's own copy and format, without the tile entities' data when nbt is off; the entities go
     * to a {@code .entities} file beside it, one per line.
     */
    @Override
    public void writeSnapshot(String world, BlockPos min, BlockPos max, TerrainContent content, File file) throws IOException {
        WorldEditSnapshots.write(world(world), min, max, content.isNbt(), file);
        if (content.isEntities()) {
            Files.write(new File(file.getPath() + ENTITIES_SUFFIX).toPath(), captureEntities(world, min, max), StandardCharsets.UTF_8);
        }
    }

    @Override
    public TerrainPlacement readSnapshot(String world, File file) throws IOException {
        File entities = new File(file.getPath() + ENTITIES_SUFFIX);
        List<String> kept = entities.isFile() ? Files.readAllLines(entities.toPath(), StandardCharsets.UTF_8) : new ArrayList<>();
        TerrainPlacement blocks = WorldEditSnapshots.read(world(world), file);
        return budget -> {
            if (!blocks.placeNext(budget)) {
                return false;
            }
            for (String entity : kept) {
                restoreEntity(world, entity);
            }
            kept.clear();
            return true;
        };
    }

    /** The WorldEdit side; loaded only once {@link WEPlatform#isPresent()} answered true. */
    private static final class WorldEditSnapshots {

        static WEPlatform withWorldEdit() {
            if (!WEPlatform.isPresent()) {
                throw new IllegalStateException("A region snapshot on Bukkit is written by WorldEdit, and WorldEdit is not on this"
                        + " server - install WorldEdit, or keep the region's changes with the track-changes flag and put them back"
                        + " with RegionTerrain.restoreJournal.");
            }
            return WEPlatform.getInstance();
        }

        static void write(World world, BlockPos min, BlockPos max, boolean nbt, File file) throws IOException {
            WEPlatform worldEdit = withWorldEdit();
            IFCCuboidRegion region = worldEdit.createCuboidRegion(min, max);
            FCBlockArrayClipboard clipboard = worldEdit.createBlockArrayClipboard(region);
            worldEdit.getOperationManager().forwardExtentCopy(world, region, clipboard);
            if (!nbt) {
                clipboard.clearNbtData();
            }
            IFCClipboardManager formats = worldEdit.getClipboardManager();
            try (Closer closer = Closer.create()) {
                OutputStream out = closer.register(new FileOutputStream(file));
                (BLOCK_DATA ? formats.SCHEM : formats.SCHEMATIC).getWriterAndWrite(out, clipboard.getHandle(), closer);
            }
        }

        static TerrainPlacement read(World world, File file) throws IOException {
            WEPlatform worldEdit = withWorldEdit();
            IFCClipboardFormat format = worldEdit.getClipboardManager().getFormat(file);
            if (format == null) {
                throw new IOException(file + " is no schematic this WorldEdit reads; a snapshot restores only on the server version"
                        + " and WorldEdit that wrote it.");
            }
            FCBlockArrayClipboard clipboard;
            try (InputStream in = new FileInputStream(file)) {
                clipboard = format.getReaderAndRead(in);
            }
            BlockPos min = clipboard.getMinimumPoint();
            BlockPos size = clipboard.getDimensions();
            long volume = (long) size.getX() * size.getY() * size.getZ();
            com.sk89q.worldedit.world.World target = worldEdit.adaptWorld(world);
            long[] next = {0};
            return budget -> {
                for (int placed = 0; placed < budget && next[0] < volume; placed++, next[0]++) {
                    long i = next[0];
                    BlockPos at = new BlockPos(min.getX() + (int) (i / ((long) size.getY() * size.getZ())),
                            min.getY() + (int) (i / size.getZ() % size.getY()), min.getZ() + (int) (i % size.getZ()));
                    try {
                        worldEdit.setBlock(target, at, clipboard.getBlock(at), false);
                    } catch (WorldEditException refused) {
                        throw new IllegalStateException("WorldEdit refused to set " + at + " of " + world.getName() + ": " + refused.getMessage(), refused);
                    }
                }
                return next[0] >= volume;
            };
        }
    }
}
