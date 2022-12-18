package br.com.finalcraft.evernifecore.inventory.player;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Salvable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.inventory.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.inventory.extrainvs.ExtraInvManager;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.IExtraInvFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FCPlayerInventory implements Salvable {

    protected ItemStack helmet;
    protected ItemStack chestplate;
    protected ItemStack leggings;
    protected ItemStack boots;
    protected GenericInventory inventory = new GenericInventory(); //0-35
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

        for (IExtraInvFactory iventoryFactory : ExtraInvManager.getAllFactories()) {
            ExtraInv extraInv = iventoryFactory.getPlayerExtraInv(player);
            extraInvs.add(extraInv);
        }
    }

    @Override
    public void onConfigSave(ConfigSection section) {
        section.setValue(null);

        section.setValue("helmet", helmet);
        section.setValue("chestplate", chestplate);
        section.setValue("leggings", leggings);
        section.setValue("boots", boots);
        section.setValue("inventory", inventory);
        section.setValue("extra", null); //Clear content before saving it

        for (ExtraInv extraInv : extraInvs) {
            String extraInvID = extraInv.getFactory().getId();
            section.setValue("extra." + extraInvID, extraInv);
        }
    }

    @Loadable
    public static FCPlayerInventory onConfigLoad(ConfigSection section){
        ItemStack helmet = section.getLoadable("helmet",ItemStack.class);
        ItemStack chestplate = section.getLoadable("chestplate",ItemStack.class);
        ItemStack leggings = section.getLoadable("leggings",ItemStack.class);
        ItemStack boots = section.getLoadable("boots",ItemStack.class);
        GenericInventory inventory = section.getLoadable("inventory", GenericInventory.class);
        if (inventory == null){
            inventory = new GenericInventory();
        }

        List<ExtraInv> extraInvList = new ArrayList<>();
        for (String extraInvKey : section.getKeys("extra")) {
            ConfigSection extraInvSection = section.getConfigSection("extra." + extraInvKey);
            try {
                IExtraInvFactory factory = ExtraInvManager.getFactory(extraInvKey);
                if (factory == null){
                    continue;
                }

                ExtraInv extraInv = factory.loadExtraInv(extraInvSection);
                extraInvList.add(extraInv);
            }catch (Exception e){
                EverNifeCore.info("Failed to load ExtraInv(" + extraInvKey + ") at " + extraInvSection.toString());
                e.printStackTrace();
            }
        }

        return new FCPlayerInventory(helmet, chestplate, leggings, boots, inventory, extraInvList);
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
        PlayerInventory playerInventory = player.getInventory();

        ItemStack[] inventoryContent = new ItemStack[36];
        for (ItemInSlot itemInSlot : inventory.getItems()) {
            inventoryContent[itemInSlot.getSlot()] = itemInSlot.getItemStack().clone();
        }
        playerInventory.setContents(inventoryContent);

        for (IExtraInvFactory factory : ExtraInvManager.getAllFactories()) {
            // We need to ge all factories, rather than use 'this.getExtraInvs()'
            // because if there is a factory that is not present on 'this.extraInvs()',
            // it means that we need to erase that extraInv on the player

            ExtraInv extraInv = this.getExtraInv(factory.getId());
            if (extraInv == null){
                extraInv = factory.createEmptyExtraInv();
            }
            factory.setPlayerExtraInv(player, extraInv);
        }

        playerInventory.setHelmet(this.getHelmet() == null ?  null : this.getHelmet().clone());
        playerInventory.setChestplate(this.getChestplate() == null ?  null : this.getChestplate().clone());
        playerInventory.setLeggings(this.getLeggings() == null ?  null : this.getLeggings().clone());
        playerInventory.setBoots(this.getBoots() == null ?  null : this.getBoots().clone());
    }
}