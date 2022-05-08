package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.nbt.NBTPlayer;
import de.tr7zw.changeme.nbtapi.*;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class FCNBTUtil {

    public static @NotNull NBTPlayer getFrom(Player player){
        return new NBTPlayer(player);
    }

    public static @NotNull NBTEntity getFrom(Entity entity){
        return new NBTEntity(entity);
    }

    public static @NotNull NBTItem getFrom(ItemStack itemStack){
        return new NBTItem(itemStack, true);
    }

    public static @NotNull NBTBlock getFrom(Block block){
        return new NBTBlock(block);
    }

    public static @NotNull NBTTileEntity getFrom(BlockState tile){
        return new NBTTileEntity(tile);
    }

    public static @NotNull NBTChunk getFrom(Chunk chunk){
        return new NBTChunk(chunk);
    }

    public static @NotNull NBTFile getFrom(File file){
        try {
            return new NBTFile(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
