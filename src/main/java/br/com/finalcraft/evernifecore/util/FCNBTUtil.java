package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.itemstack.nbtutil.TrackedNBTContainer;
import br.com.finalcraft.evernifecore.nbt.NBTPlayer;
import de.tr7zw.changeme.nbtapi.*;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

public class FCNBTUtil {

    public static @Nonnull NBTContainer empyNBT(){
        return new NBTContainer("{}");
    }

    public static @Nonnull NBTContainer getFrom(String nbt){
        return new TrackedNBTContainer(nbt);
    }

    public static @Nonnull NBTPlayer getFrom(Player player){
        return new NBTPlayer(player);
    }

    public static @Nonnull NBTEntity getFrom(Entity entity){
        return new NBTEntity(entity);
    }

    public static @Nonnull NBTItem getFrom(ItemStack itemStack){
        return new NBTItem(itemStack, true);
    }

    public static @Nonnull NBTBlock getFrom(Block block){
        return new NBTBlock(block);
    }

    public static @Nonnull NBTTileEntity getFrom(BlockState tile){
        return new NBTTileEntity(tile);
    }

    public static @Nonnull NBTChunk getFrom(Chunk chunk){
        return new NBTChunk(chunk);
    }

    public static @Nonnull NBTFile getFrom(File file){
        try {
            return new NBTFile(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isEmpty(NBTCompound nbtCompound){
        return nbtCompound == null || nbtCompound.getCompound() == null || nbtCompound.getKeys().isEmpty();
    }
}
