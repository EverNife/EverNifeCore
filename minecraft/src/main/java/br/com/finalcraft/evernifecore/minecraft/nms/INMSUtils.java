package br.com.finalcraft.evernifecore.minecraft.nms;

import br.com.finalcraft.evernifecore.minecraft.nms.data.IMCMaterialRegistry;
import br.com.finalcraft.evernifecore.minecraft.nms.data.IMcBlockWrapper;
import br.com.finalcraft.evernifecore.minecraft.nms.data.IMcItemWrapper;
import br.com.finalcraft.evernifecore.minecraft.nms.data.oredict.IMCOreRegistry;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface INMSUtils {
    String getItemRegistryName(ItemStack item);

    String getEntityRegistryName(Entity entity);

    ItemStack getItemFromMinecraftIdentifier(String minecraftIdentifier);

    String getLocalizedName(ItemStack itemStack);

    ItemStack asBukkitItemStack(Object mcItemStack);

    Object asMinecraftItemStack(ItemStack itemStack);

    String serializeItemStack(ItemStack itemStack);

    World asBukkitWorld(Object minecraftWorld);

    Object asMinecraftWorld(World bukkitWorld);

    void autoRespawnOnDeath(Player player);

    boolean isTool(ItemStack itemStack);

    boolean isSword(ItemStack itemStack);

    boolean isArmor(ItemStack itemStack);

    boolean isHelmet(ItemStack itemStack);

    boolean isChestplate(ItemStack itemStack);

    boolean isLeggings(ItemStack itemStack);

    boolean isBoots(ItemStack itemStack);

    boolean isFakePlayer(Player player);

    Entity asBukkitEntity(Object minecraftEntity);

    Object asMinecraftEntity(Entity entity);

    ItemStack validateItemStackHandle(ItemStack itemStack);

    default IMCMaterialRegistry<IMcBlockWrapper> getBlockRegistry() {
        throw new UnsupportedOperationException("Not implemented");
    }

    default IMCMaterialRegistry<IMcItemWrapper> getItemRegistry() {
        throw new UnsupportedOperationException("Not implemented");
    }

    default IMCOreRegistry getOreRegistry() {
        throw new UnsupportedOperationException("Not implemented");
    }
}