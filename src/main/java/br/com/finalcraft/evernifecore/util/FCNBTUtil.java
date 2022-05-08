package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.nbt.NBTPlayer;
import de.tr7zw.changeme.nbtapi.*;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;

public class FCNBTUtil {

    public static NBTPlayer getFrom(Player player){
        return new NBTPlayer(player);
    }

    public static NBTEntity getFrom(Entity entity){
        return new NBTEntity(entity);
    }

    public static NBTItem getFrom(ItemStack itemStack){
        return new NBTItem(itemStack, true);
    }

    public static NBTBlock getFrom(Block block){
        return new NBTBlock(block);
    }

    public static NBTTileEntity getFrom(BlockState tile){
        return new NBTTileEntity(tile);
    }

    public static NBTChunk getFrom(Chunk chunk){
        return new NBTChunk(chunk);
    }

    public static NBTFile getFrom(File file){
        try {
            return new NBTFile(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
