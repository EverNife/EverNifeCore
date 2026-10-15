package br.com.finalcraft.evernifecore.minecraft.region.terrain;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.region.terrain.ITerrainAccess;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
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
import org.bukkit.entity.Player;

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
    private static final boolean BLOCK_DATA = MCVersion.isHigherEquals(MCDetailedVersion.v1_13_R1);
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
        Entity spawned = at.getWorld().spawnEntity(at, EntityType.valueOf(parts[0]));
        ReadWriteNBT saved = NBT.parseNBT(parts[2]);
        ENTITY_KEYS_NOT_RESTORED.forEach(saved::removeKey);
        NBT.modify(spawned, (ReadWriteNBT nbt) -> {
            nbt.mergeCompound(saved);
        });
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
}
