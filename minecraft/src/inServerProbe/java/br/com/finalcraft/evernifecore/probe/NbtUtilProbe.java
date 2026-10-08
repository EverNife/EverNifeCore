package br.com.finalcraft.evernifecore.probe;

import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.NbtDoor;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCNBTUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
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
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Every {@code FCNBTUtil} method on a real server. One line per case: {@code PASS}, {@code FAIL}, or {@code REFUSED}
 * when the method threw - with whether this version was expected to refuse it, so an expected refusal is a pass.
 */
final class NbtUtilProbe {

    private final File dataFolder;
    private final List<String> lines = new ArrayList<>();

    NbtUtilProbe(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    private interface Case {
        String run() throws Exception;
    }

    List<String> run(Player player) {
        boolean pdc = MCVersion.isHigherEquals(MCDetailedVersion.v1_14_R1);
        boolean chunkPdc = MCVersion.isHigherEquals(MCDetailedVersion.v1_16_R3);

        check("text", true, () -> {
            ReadWriteNBT empty = FCNBTUtil.createEmpty();
            boolean wasEmpty = FCNBTUtil.isEmpty(empty) && FCNBTUtil.isEmpty(null);
            empty.setInteger("a", 1);
            ReadWriteNBT back = FCNBTUtil.parse(empty.toString());
            return verdict(wasEmpty && !FCNBTUtil.isEmpty(empty) && back.getInteger("a") == 1,
                    "empty=" + wasEmpty + " parsed=" + back);
        });

        check("item-plain", true, () -> {
            ItemStack plain = new ItemStack(Material.STONE);
            ItemStack carried = FCNBTUtil.modifyItem(plain, nbt -> nbt.setString("k", "v"));
            String onPlain = FCNBTUtil.readItem(plain, nbt -> nbt.hasTag("k") ? nbt.getString("k") : "absent");
            ReadableNBT view = FCNBTUtil.readItem(carried);
            return verdict("v".equals(view.getString("k")) && "absent".equals(onPlain),
                    "returned.k=" + view.getString("k") + " plain.k=" + onPlain + " returned.class=" + carried.getClass().getSimpleName());
        });

        check("item-live", true, () -> {
            int slot = player.getInventory().firstEmpty();
            player.getInventory().setItem(slot, new ItemStack(Material.STONE));
            ItemStack live = player.getInventory().getItem(slot);
            ItemStack returned = FCNBTUtil.modifyItem(live, nbt -> nbt.setString("k", "v"));
            String fresh = FCNBTUtil.readItem(player.getInventory().getItem(slot), nbt -> nbt.getString("k"));
            return verdict(returned == live && "v".equals(fresh), "returned==live=" + (returned == live) + " slot.k=" + fresh);
        });

        check("item-identifier", true, () -> {
            ItemStack fromId = FCItemUtils.fromMinecraftIdentifier("minecraft:stone 1 0 {ecprobe:7}");
            int value = FCNBTUtil.readItem(fromId, nbt -> nbt.hasTag("ecprobe") ? nbt.getInteger("ecprobe") : -1);
            String identifier = FCItemUtils.getMinecraftIdentifier(fromId);
            return verdict(value == 7 && identifier.contains("ecprobe"), "ecprobe=" + value + " identifier=" + identifier);
        });

        Location spot = player.getLocation().clone().add(2, 0, 0);
        Entity pig = spot.getWorld().spawnEntity(spot, EntityType.PIG);
        try {
            check("entity", true, () -> {
                int keys = FCNBTUtil.readEntity(pig, nbt -> nbt.getKeys().size());
                FCNBTUtil.modifyEntity(pig, nbt -> nbt.setShort("Fire", (short) 200));
                int fire = pig.getFireTicks();
                short read = FCNBTUtil.readEntity(pig, nbt -> nbt.getShort("Fire"));
                return verdict(keys > 0 && fire == 200 && read == 200, "keys=" + keys + " fireTicks=" + fire + " Fire=" + read
                        + " valid=" + pig.isValid());
            });
            check("entity-player", true, () -> {
                String name = FCNBTUtil.readEntity(player, nbt -> nbt.hasTag("Pos") ? "has Pos" : "no Pos");
                return verdict("has Pos".equals(name), name);
            });
            check("entity-data", pdc, () -> {
                FCNBTUtil.modifyEntityData(pig, nbt -> nbt.setString("k", "v"));
                String read = FCNBTUtil.readEntityData(pig, nbt -> nbt.getString("k"));
                return verdict("v".equals(read), "k=" + read);
            });
        } finally {
            pig.remove();
        }

        Block block = player.getLocation().getBlock().getRelative(0, 3, 0);
        Material before = block.getType();
        block.setType(Material.CHEST);
        try {
            check("tile", true, () -> {
                BlockState tile = block.getState();
                String id = FCNBTUtil.readTileEntity(tile, nbt -> nbt.getString("id"));
                String name = MCVersion.isHigherEquals(MCVersion.v1_13) ? "{\"text\":\"probe\"}" : "probe";
                FCNBTUtil.modifyTileEntity(tile, nbt -> nbt.setString("CustomName", name));
                String read = FCNBTUtil.readTileEntity(block.getState(), nbt -> nbt.getString("CustomName"));
                return verdict(!id.isEmpty() && read != null && read.contains("probe"), "id=" + id + " CustomName=" + read);
            });
            check("tile-data", pdc, () -> {
                FCNBTUtil.modifyTileEntityData(block.getState(), nbt -> nbt.setString("k", "v"));
                String read = FCNBTUtil.readTileEntityData(block.getState(), nbt -> nbt.getString("k"));
                return verdict("v".equals(read), "k=" + read);
            });
        } finally {
            block.setType(before);
        }

        check("block-data", chunkPdc, () -> {
            FCNBTUtil.modifyBlockData(block, nbt -> nbt.setString("k", "v"));
            String read = FCNBTUtil.readBlockData(block, nbt -> nbt.getString("k"));
            FCNBTUtil.modifyBlockData(block, nbt -> nbt.removeKey("k"));
            boolean cleared = FCNBTUtil.readBlockData(block, nbt -> nbt.getKeys().isEmpty());
            return verdict("v".equals(read) && cleared, "k=" + read + " clearedAfterRemove=" + cleared);
        });

        check("chunk-data", chunkPdc, () -> {
            FCNBTUtil.modifyChunkData(block.getChunk(), nbt -> nbt.setString("k", "v"));
            String read = FCNBTUtil.readChunkData(block.getChunk(), nbt -> nbt.getString("k"));
            FCNBTUtil.modifyChunkData(block.getChunk(), nbt -> nbt.removeKey("k"));
            return verdict("v".equals(read), "k=" + read);
        });

        check("file", true, () -> {
            File file = new File(dataFolder, "probe.dat");
            FCNBTUtil.writeFile(file, FCNBTUtil.parse("{a:1,nested:{b:\"x\"}}"));
            ReadWriteNBT read = FCNBTUtil.readFile(file);
            boolean missingIsEmpty = FCNBTUtil.isEmpty(FCNBTUtil.readFile(new File(dataFolder, "missing.dat")));
            return verdict(read.getInteger("a") == 1 && "x".equals(read.getCompound("nested").getString("b")) && missingIsEmpty,
                    "read=" + read + " missingIsEmpty=" + missingIsEmpty);
        });

        return lines;
    }

    /**
     * Writes through every {@code modify*} onto objects that outlive a restart - a solid block, a chest, a minecart
     * (which never despawns), the player and two items in their inventory - and reads each one back at once.
     * Where everything sits is kept in {@code persist.dat} for {@link #readPersisted}.
     */
    List<String> writePersisted(Player player) {
        boolean pdc = MCVersion.isHigherEquals(MCDetailedVersion.v1_14_R1);
        boolean chunkPdc = MCVersion.isHigherEquals(MCDetailedVersion.v1_16_R3);
        boolean components = MCVersion.isHigherEquals(MCDetailedVersion.v1_20_R4);
        ReadWriteNBT state = FCNBTUtil.createEmpty();
        Block base = player.getLocation().getBlock();
        Block stone = base.getRelative(3, 0, 0);
        Block chest = base.getRelative(3, 0, 2);
        stone.setType(Material.STONE);
        chest.setType(Material.CHEST);
        state.setString("world", base.getWorld().getName());
        state.setIntArray("stone", new int[]{stone.getX(), stone.getY(), stone.getZ()});
        state.setIntArray("chest", new int[]{chest.getX(), chest.getY(), chest.getZ()});

        check("persist-write block-data-stone", chunkPdc, () -> {
            FCNBTUtil.modifyBlockData(stone, nbt -> nbt.setString("k", "stone"));
            String read = FCNBTUtil.readBlockData(stone, nbt -> nbt.getString("k"));
            return verdict("stone".equals(read), "type=" + stone.getType() + " k=" + read);
        });
        check("persist-write block-data-chest", chunkPdc, () -> {
            FCNBTUtil.modifyBlockData(chest, nbt -> nbt.setString("k", "chest"));
            String read = FCNBTUtil.readBlockData(chest, nbt -> nbt.getString("k"));
            return verdict("chest".equals(read), "type=" + chest.getType() + " k=" + read);
        });
        check("persist-write chunk-data", chunkPdc, () -> {
            FCNBTUtil.modifyChunkData(stone.getChunk(), nbt -> nbt.setString("k", "chunk"));
            String read = FCNBTUtil.readChunkData(stone.getChunk(), nbt -> nbt.getString("k"));
            return verdict("chunk".equals(read), "k=" + read);
        });
        check("persist-write tile", true, () -> {
            FCNBTUtil.modifyTileEntity(chest.getState(), nbt -> nbt.setString("CustomName", customName()));
            String read = FCNBTUtil.readTileEntity(chest.getState(), nbt -> nbt.getString("CustomName"));
            return verdict(read != null && read.contains("ecpersist"), "CustomName=" + read);
        });
        check("persist-write tile-data", pdc, () -> {
            FCNBTUtil.modifyTileEntityData(chest.getState(), nbt -> nbt.setString("k", "tile"));
            String read = FCNBTUtil.readTileEntityData(chest.getState(), nbt -> nbt.getString("k"));
            return verdict("tile".equals(read), "k=" + read);
        });

        Entity cart = base.getWorld().spawnEntity(base.getRelative(-3, 0, 0).getLocation().add(0.5, 0, 0.5), EntityType.MINECART);
        state.setString("cart", cart.getUniqueId().toString());
        //never touched by FCNBTUtil: tells a lost write apart from a world that drops the entity on its own
        Entity control = base.getWorld().spawnEntity(base.getRelative(-3, 0, 2).getLocation().add(0.5, 0, 0.5), EntityType.MINECART);
        state.setString("control", control.getUniqueId().toString());
        check("persist-write entity", true, () -> {
            FCNBTUtil.modifyEntity(cart, nbt -> nbt.setString("CustomName", customName()));
            String read = FCNBTUtil.readEntity(cart, nbt -> nbt.getString("CustomName"));
            return verdict(read.contains("ecpersist") && cart.isValid(), "CustomName=" + read + " valid=" + cart.isValid()
                    + " uuid=" + cart.getUniqueId());
        });
        check("persist-write entity-data", pdc, () -> {
            FCNBTUtil.modifyEntityData(cart, nbt -> nbt.setString("k", "cart"));
            String read = FCNBTUtil.readEntityData(cart, nbt -> nbt.getString("k"));
            return verdict("cart".equals(read), "k=" + read);
        });

        check("persist-write entity-player", true, () -> {
            int levelBefore = player.getLevel();
            FCNBTUtil.modifyEntity(player, nbt -> nbt.setInteger("XpLevel", 7));
            int read = FCNBTUtil.readEntity(player, nbt -> nbt.getInteger("XpLevel"));
            return verdict(player.getLevel() == 7 && read == 7, "levelBefore=" + levelBefore + " getLevel=" + player.getLevel()
                    + " XpLevel=" + read + " online=" + player.isOnline());
        });

        check("persist-write item", true, () -> {
            int slot = player.getInventory().firstEmpty();
            player.getInventory().setItem(slot, new ItemStack(Material.STONE));
            FCNBTUtil.modifyItem(player.getInventory().getItem(slot), nbt -> nbt.setString("k", "item"));
            String read = FCNBTUtil.readItem(player.getInventory().getItem(slot), nbt -> nbt.getString("k"));
            state.setInteger("itemSlot", slot);
            return verdict("item".equals(read), "slot=" + slot + " k=" + read);
        });
        check("persist-write components", components, () -> {
            ItemStack built = ItemEngine.get().transform(new ItemStack(Material.STONE),
                    Collections.singletonList("components:{\"minecraft:max_stack_size\":1}"));
            int slot = player.getInventory().firstEmpty();
            player.getInventory().setItem(slot, built);
            ItemStack held = player.getInventory().getItem(slot);
            String read = NbtDoor.components().read(held, nbt -> nbt.toString());
            state.setInteger("componentSlot", slot);
            return verdict(held.getMaxStackSize() == 1 && read.contains("max_stack_size"),
                    "slot=" + slot + " maxStackSize=" + held.getMaxStackSize() + " components=" + read);
        });

        FCNBTUtil.writeFile(new File(dataFolder, "persist.dat"), state);
        lines.add("PASS persist-write state=" + state);
        return lines;
    }

    /** Reads back, after a restart, everything {@link #writePersisted} wrote. */
    List<String> readPersisted(Player player) {
        boolean pdc = MCVersion.isHigherEquals(MCDetailedVersion.v1_14_R1);
        boolean chunkPdc = MCVersion.isHigherEquals(MCDetailedVersion.v1_16_R3);
        boolean components = MCVersion.isHigherEquals(MCDetailedVersion.v1_20_R4);
        ReadWriteNBT state = FCNBTUtil.readFile(new File(dataFolder, "persist.dat"));
        if (FCNBTUtil.isEmpty(state)) {
            lines.add("FAIL persist-read no persist.dat - run nbtpersist write first");
            return lines;
        }
        World world = Bukkit.getWorld(state.getString("world"));
        Block stone = at(world, state.getIntArray("stone"));
        Block chest = at(world, state.getIntArray("chest"));

        check("persist-read block-data-stone", chunkPdc, () -> {
            String read = FCNBTUtil.readBlockData(stone, nbt -> nbt.getString("k"));
            return verdict("stone".equals(read) && stone.getType() == Material.STONE, "type=" + stone.getType() + " k=" + read);
        });
        check("persist-read block-data-chest", chunkPdc, () -> {
            String read = FCNBTUtil.readBlockData(chest, nbt -> nbt.getString("k"));
            return verdict("chest".equals(read), "type=" + chest.getType() + " k=" + read);
        });
        check("persist-read chunk-data", chunkPdc, () -> {
            String read = FCNBTUtil.readChunkData(stone.getChunk(), nbt -> nbt.getString("k"));
            return verdict("chunk".equals(read), "k=" + read);
        });
        check("persist-read tile", true, () -> {
            String read = FCNBTUtil.readTileEntity(chest.getState(), nbt -> nbt.getString("CustomName"));
            return verdict(read != null && read.contains("ecpersist"), "type=" + chest.getType() + " CustomName=" + read);
        });
        check("persist-read tile-data", pdc, () -> {
            String read = FCNBTUtil.readTileEntityData(chest.getState(), nbt -> nbt.getString("k"));
            return verdict("tile".equals(read), "k=" + read);
        });

        UUID cartId = UUID.fromString(state.getString("cart"));
        UUID controlId = UUID.fromString(state.getString("control"));
        Entity found = null;
        boolean controlFound = false;
        StringBuilder loaded = new StringBuilder();
        for (Entity entity : world.getEntities()) {
            found = entity.getUniqueId().equals(cartId) ? entity : found;
            controlFound |= entity.getUniqueId().equals(controlId);
            loaded.append(' ').append(entity.getType()).append('@').append(entity.getLocation().getBlockX()).append(',')
                    .append(entity.getLocation().getBlockZ()).append('/').append(entity.getUniqueId());
        }
        Entity cart = found;
        boolean controlSurvived = controlFound;
        check("persist-read entity-control", true, () -> verdict(controlSurvived, "untouched minecart " + controlId
                + " found=" + controlSurvived));
        check("persist-read entity", true, () -> {
            if (cart == null) {
                return verdict(false, "minecart " + cartId + " not among the loaded entities:" + loaded);
            }
            String read = FCNBTUtil.readEntity(cart, nbt -> nbt.getString("CustomName"));
            return verdict(read.contains("ecpersist"), "CustomName=" + read);
        });
        check("persist-read entity-data", pdc, () -> {
            String read = cart == null ? "no minecart" : FCNBTUtil.readEntityData(cart, nbt -> nbt.getString("k"));
            return verdict("cart".equals(read), "k=" + read);
        });

        check("persist-read entity-player", true, () -> {
            int read = FCNBTUtil.readEntity(player, nbt -> nbt.getInteger("XpLevel"));
            return verdict(player.getLevel() == 7 && read == 7, "getLevel=" + player.getLevel() + " XpLevel=" + read);
        });
        check("persist-read item", true, () -> {
            ItemStack held = player.getInventory().getItem(state.getInteger("itemSlot"));
            String read = held == null ? "empty slot" : FCNBTUtil.readItem(held, nbt -> nbt.getString("k"));
            return verdict("item".equals(read), "slot=" + state.getInteger("itemSlot") + " k=" + read);
        });
        check("persist-read components", components, () -> {
            ItemStack held = player.getInventory().getItem(state.getInteger("componentSlot"));
            String read = held == null ? "empty slot" : NbtDoor.components().read(held, nbt -> nbt.toString());
            return verdict(held != null && held.getMaxStackSize() == 1 && read.contains("max_stack_size"),
                    "maxStackSize=" + (held == null ? -1 : held.getMaxStackSize()) + " components=" + read);
        });
        return lines;
    }

    private static String customName() {
        return MCVersion.isHigherEquals(MCVersion.v1_13) ? "{\"text\":\"ecpersist\"}" : "ecpersist";
    }

    private static Block at(World world, int[] xyz) {
        return world.getBlockAt(xyz[0], xyz[1], xyz[2]);
    }

    private void check(String name, boolean expectedToWork, Case probe) {
        try {
            String result = probe.run();
            String verdict = result.substring(0, 4);
            String detail = result.substring(5);
            lines.add((expectedToWork ? verdict : "FAIL") + " " + name + " " + detail
                    + (expectedToWork ? "" : " (expected a refusal on this version)"));
        } catch (Throwable thrown) {
            lines.add((expectedToWork ? "FAIL" : "PASS") + " " + name + " REFUSED expected=" + !expectedToWork + " "
                    + thrown.getClass().getSimpleName() + ": " + thrown.getMessage());
        }
    }

    private static String verdict(boolean ok, String detail) {
        return (ok ? "PASS" : "FAIL") + " " + detail;
    }
}
