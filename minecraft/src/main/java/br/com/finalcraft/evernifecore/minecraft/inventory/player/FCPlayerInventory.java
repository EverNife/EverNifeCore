package br.com.finalcraft.evernifecore.minecraft.inventory.player;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.minecraft.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.minecraft.inventory.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.minecraft.inventory.extrainvs.ExtraInvManager;
import br.com.finalcraft.evernifecore.minecraft.inventory.extrainvs.factory.IExtraInvFactory;
import br.com.finalcraft.everyconfig.binding.ConfigContext;
import br.com.finalcraft.everyconfig.binding.ConfigLifecycle;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Persists as {@code {helmet, chestplate, leggings, boots, inventory, extra.<id>}}. The four armor pieces and
 * the main {@link GenericInventory} are bound as ordinary fields ({@link JsonAutoDetect} exposes them since the
 * class has getters but no setters); the factory-driven {@code extra.<id>} map is handled in the
 * {@link ConfigLifecycle} hooks, where each factory's own {@link IExtraInvFactory#onConfigLoad} stays
 * polymorphic.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class FCPlayerInventory implements ConfigLifecycle {

    protected ItemStack helmet;
    protected ItemStack chestplate;
    protected ItemStack leggings;
    protected ItemStack boots;
    protected GenericInventory inventory = new GenericInventory(); //0-35
    @JsonIgnore
    protected List<ExtraInv> extraInvs = new ArrayList<>();

    public FCPlayerInventory() {
        this(null, null, null, null, new GenericInventory());
    }

    public FCPlayerInventory(Collection<ItemInSlot> inventoryContent) {
        this(null, null, null, null, new GenericInventory(inventoryContent));
    }

    public FCPlayerInventory(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, GenericInventory inventory) {
        this(helmet, chestplate, leggings, boots, inventory, new ArrayList<>());
    }

    public FCPlayerInventory(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, GenericInventory inventory, List<ExtraInv> extraInvs) {
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.inventory = inventory;
        this.extraInvs = extraInvs;
    }

    public FCPlayerInventory(Player player) {
        this(player, ExtraInvManager.getAllFactories());
    }

    public FCPlayerInventory(Player player, @Nullable Collection<IExtraInvFactory<?>> inventoryFactories) {
        PlayerInventory playerInventory = player.getInventory();
        for (int index = 0; index < 36; index++){
            ItemStack itemStack = playerInventory.getItem(index);
            if (itemStack != null){
                inventory.setItem(index, itemStack.clone());
            }
        }

        this.helmet = playerInventory.getHelmet() != null ? playerInventory.getHelmet().clone() : null;
        this.chestplate = playerInventory.getChestplate() != null ? playerInventory.getChestplate() : null;
        this.leggings = playerInventory.getLeggings() != null ? playerInventory.getLeggings() : null;
        this.boots = playerInventory.getBoots() != null ? playerInventory.getBoots() : null;

        if (inventoryFactories != null){
            for (IExtraInvFactory<?> factory : inventoryFactories) {
                try {
                    ExtraInv extraInv = factory.extractFromPlayer(player);
                    extraInvs.add(extraInv);
                }catch (Exception e){
                    EverNifeCore.getLog().info("Failed to extract ExtraInv(" + factory.getId() + ") from " + player.getName());
                    e.printStackTrace();
                }
            }
        }

    }

    public ItemStack getHelmet() {
        return helmet;
    }

    public ItemStack getChestplate() {
        return chestplate;
    }

    public ItemStack getLeggings() {
        return leggings;
    }

    public ItemStack getBoots() {
        return boots;
    }

    public GenericInventory getInventory() {
        return inventory;
    }

    @JsonIgnore
    public List<ExtraInv> getExtraInvs(){
        return extraInvs;
    }

    public ExtraInv getExtraInv(String extraInvId){
        return extraInvs.stream()
                .filter(extraInv -> extraInv.getFactory().getId().equals(extraInvId))
                .findFirst()
                .orElse(null);
    }

    public void restoreTo(Player player){
        restoreTo(player, ExtraInvManager.getAllFactories());
    }

    public void restoreTo(Player player, @Nullable Collection<IExtraInvFactory<?>> inventoryFactories) {
        PlayerInventory playerInventory = player.getInventory();

        ItemStack[] inventoryContent = new ItemStack[36];
        for (ItemInSlot itemInSlot : inventory.getItems()) {
            inventoryContent[itemInSlot.getSlot()] = itemInSlot.getItemStack().clone();
        }
        playerInventory.setContents(inventoryContent);

        for (IExtraInvFactory factory : inventoryFactories) {
            // We need to ge all factories, rather than use 'this.getExtraInvs()'
            // because if there is a factory that is not present on 'this.extraInvs()',
            // it means that we need to erase that extraInv on the player
            try {
                ExtraInv extraInv = this.getExtraInv(factory.getId());
                if (extraInv == null){
                    extraInv = factory.createEmptyExtraInv();
                }
                factory.applyToPlayer(player, extraInv);
            }catch (Throwable e){
                EverNifeCore.getLog().info("Failed to restore ExtraInv(" + factory.getId() + ") into " + player.getName());
                e.printStackTrace();
            }
        }

        playerInventory.setHelmet(this.getHelmet() == null ?  null : this.getHelmet().clone());
        playerInventory.setChestplate(this.getChestplate() == null ?  null : this.getChestplate().clone());
        playerInventory.setLeggings(this.getLeggings() == null ?  null : this.getLeggings().clone());
        playerInventory.setBoots(this.getBoots() == null ?  null : this.getBoots().clone());
    }

    // ==================== config lifecycle ====================

    /** Write each extra inventory under {@code extra.<id>}; its slot map routes through {@link GenericInventory}.
     *  Runs post-save so {@code extra} lands after the bound fields, matching the legacy key order. */
    @Override
    public void postSave(ConfigContext context) {
        for (ExtraInv extraInv : extraInvs) {
            context.section().setValue("extra." + extraInv.getFactory().getId(), extraInv);
        }
    }

    /** Rebuild the extras from {@code extra.<id>} through each factory's own polymorphic
     *  {@link IExtraInvFactory#onConfigLoad}. */
    @Override
    public void postLoad(ConfigContext context) {
        for (String extraInvKey : context.section().getKeys("extra")) {
            ConfigSection extraInvSection = context.section().getConfigSection("extra." + extraInvKey);
            try {
                IExtraInvFactory factory = ExtraInvManager.getFactory(extraInvKey);
                if (factory == null) {
                    continue;
                }
                extraInvs.add(factory.onConfigLoad(extraInvSection));
            } catch (Throwable e) {
                EverNifeCore.getLog().info("Failed to load ExtraInv(" + extraInvKey + ") at " + extraInvSection.getPath());
                e.printStackTrace();
            }
        }
    }
}
