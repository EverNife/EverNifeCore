package br.com.finalcraft.evernifecore.probe;

import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCNBTUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
                return verdict(keys > 0 && fire == 200 && read == 200, "keys=" + keys + " fireTicks=" + fire + " Fire=" + read);
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
