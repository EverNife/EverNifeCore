package br.com.finalcraft.evernifecore.inventory.player;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Salvable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.inventory.player.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.inventory.player.extrainvs.ExtraInvType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class FCPlayerInventory implements Salvable {

    protected ItemStack helmet;
    protected ItemStack chestplate;
    protected ItemStack leggings;
    protected ItemStack boots;
    protected List<ItemInSlot> inventory = new ArrayList<>(); //0-35
    protected HashMap<ExtraInvType, ExtraInv> extraInvMap = new HashMap<>();

    public FCPlayerInventory() {
        this(null, null, null, null, new ArrayList<>());
    }

    public FCPlayerInventory(List<ItemInSlot> inventory) {
        this(null, null, null, null, inventory);
    }

    public FCPlayerInventory(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, List<ItemInSlot> inventory) {
        this(helmet, chestplate, leggings, boots, inventory, new ArrayList<>());
    }

    public FCPlayerInventory(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, List<ItemInSlot> inventory, List<ExtraInv> extraInvList) {
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.inventory = inventory;

        if (extraInvList != null){
            for (ExtraInv extraInv : extraInvList) {
                this.extraInvMap.put(extraInv.getType(), extraInv);
            }
        }
    }

    public FCPlayerInventory(Player player) {
        PlayerInventory playerInventory = player.getInventory();
        for (int index = 0; index < 36; index++){
            ItemStack itemStack = playerInventory.getItem(index);
            if (itemStack != null){
                ItemInSlot itemInSlot = new ItemInSlot(index,itemStack.clone());
                inventory.add(itemInSlot);
            }
        }
        this.helmet = playerInventory.getHelmet() != null ? playerInventory.getHelmet().clone() : null;
        this.chestplate = playerInventory.getChestplate() != null ? playerInventory.getChestplate() : null;
        this.leggings = playerInventory.getLeggings() != null ? playerInventory.getLeggings() : null;
        this.boots = playerInventory.getBoots() != null ? playerInventory.getBoots() : null;

        for (ExtraInvType invType : ExtraInvType.values()) {
            if (invType.isEnabled()){
                ExtraInv extraInv = invType.createExtraInv();
                ItemStack[] extraInvItems = extraInv.getPlayerExtraInv(player);
                List<ItemInSlot> itemInSlotList = ItemInSlot.fromStackList(extraInvItems);
                extraInv.getItemSlotList().addAll(itemInSlotList);
                extraInvMap.put(invType, extraInv);
            }
        }
    }


    @Override
    public void onConfigSave(ConfigSection section) {
        section.setValue(null);

        section.setValue("helmet", helmet);
        section.setValue("chestplate", chestplate);
        section.setValue("leggings", leggings);
        section.setValue("boots", boots);

        section.setValue("inventory", null); //Clear content before saving it
        for (ItemInSlot itemInSlot : inventory) {
            section.setValue("inventory." + itemInSlot.getSlot(), itemInSlot.getItemStack());
        }

        section.setValue("extra", null); //Clear content before saving it
        for (ExtraInv extraInv : extraInvMap.values()) {
            for (ItemInSlot itemInSlot : extraInv.getItemSlotList()) {
                section.setValue("extra." + extraInv.getName() + "." + itemInSlot.getSlot(), itemInSlot.getItemStack());
            }
        }
    }

    @Loadable
    public static FCPlayerInventory onConfigLoad(ConfigSection section){
        ItemStack helmet = section.getLoadable("helmet",ItemStack.class);
        ItemStack chestplate = section.getLoadable("chestplate",ItemStack.class);
        ItemStack leggings = section.getLoadable("leggings",ItemStack.class);
        ItemStack boots = section.getLoadable("boots",ItemStack.class);
        List<ItemInSlot> inventory = new ArrayList<>();
        for (String key : section.getKeys("inventory")) {
            try {
                Integer slot = Integer.parseInt(key);
                ItemStack itemStack = section.getLoadable("inventory." + slot, ItemStack.class);
                ItemInSlot itemInSlot = new ItemInSlot(slot,itemStack);
                inventory.add(itemInSlot);
            }catch (NumberFormatException e){
                e.printStackTrace();
            }
        }

        List<ExtraInv> extraInvList = new ArrayList<>();
        for (String extraInvKey : section.getKeys("extra")) {
            try {
                ExtraInvType extraInvType = ExtraInvType.valueOf(extraInvKey.toUpperCase());
                if (!extraInvType.isEnabled()) continue;

                ExtraInv extraInv = ExtraInvType.valueOf(extraInvKey.toUpperCase()).createExtraInv();
                for (String itemSlotKey : section.getKeys("extra." + extraInvKey)) {
                    try {
                        Integer slot = Integer.parseInt(itemSlotKey);
                        ItemStack itemStack = section.getLoadable("extra." + extraInvKey + "." + slot, ItemStack.class);
                        ItemInSlot itemInSlot = new ItemInSlot(slot,itemStack);
                        extraInv.getItemSlotList().add(itemInSlot);
                    }catch (NumberFormatException e){
                        EverNifeCore.info("Failed to load ItemSlot(iteSlot==" + itemSlotKey + ") from " + (section.getPath() + ".extra." + extraInvKey ) + "] \n --> " + section.getConfig().getAbsolutePath());
                        e.printStackTrace();
                    }
                }
                extraInvList.add(extraInv);
            }catch (Exception e){
                EverNifeCore.info("Failed to load ExtraInv(" + extraInvKey + ") from [" + (section.getPath() + ".extra." + extraInvKey ) + "] \n --> " + section.getConfig().getAbsolutePath());
                e.printStackTrace();
            }
        }
        return new FCPlayerInventory(helmet,chestplate,leggings,boots,inventory,extraInvList);
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

    public List<ItemInSlot> getInventory() {
        return inventory;
    }

    public HashMap<ExtraInvType, ExtraInv> getExtraInvMap() {
        return extraInvMap;
    }

    public Collection<ExtraInv> getExtraInvs(){
        return extraInvMap.values();
    }

    public ExtraInv getExtraInv(ExtraInvType invType){
        return extraInvMap.get(invType);
    }

    public ItemStack getItem(int index){
        for (ItemInSlot itemInSlot : this.inventory) {
            if (index == itemInSlot.getSlot()){
                return itemInSlot.getItemStack();
            }
        }
        return null;
    }

    public void restoreTo(Player player){
        org.bukkit.inventory.PlayerInventory playerInventory = player.getInventory();

        ItemStack[] inventoryContent = new ItemStack[36];
        for (ItemInSlot itemInSlot : inventory) {
            inventoryContent[itemInSlot.getSlot()] = itemInSlot.getItemStack().clone();
        }
        playerInventory.setContents(inventoryContent);

        for (ExtraInvType invType : ExtraInvType.values()) {
            if (invType.isEnabled()){
                ExtraInv extraInv = getExtraInv(invType);
                if (extraInv == null){
                    extraInv = invType.createExtraInv();
                }
                extraInv.setPlayerExtraInv(player);
            }
        }

        for (ExtraInv extraInv : this.extraInvMap.values()) {
            extraInv.setPlayerExtraInv(player);
        }

        playerInventory.setHelmet(this.getHelmet() == null ?  null : this.getHelmet().clone());
        playerInventory.setChestplate(this.getChestplate() == null ?  null : this.getChestplate().clone());
        playerInventory.setLeggings(this.getLeggings() == null ?  null : this.getLeggings().clone());
        playerInventory.setBoots(this.getBoots() == null ?  null : this.getBoots().clone());
    }
}