package br.com.finalcraft.evernifecore.inventory.player;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.yaml.helper.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.helper.Salvable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.inventory.data.ItemSlot;
import br.com.finalcraft.evernifecore.inventory.player.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.inventory.player.extrainvs.ExtraInvType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class PlayerInventory implements Salvable {

    protected ItemStack helmet;
    protected ItemStack chestplate;
    protected ItemStack leggings;
    protected ItemStack boots;
    protected List<ItemSlot> inventory = new ArrayList<>(); //0-35
    protected HashMap<ExtraInvType, ExtraInv> extraInvMap = new HashMap<>();

    public PlayerInventory() {
        this(null, null, null, null, new ArrayList<>());
    }

    public PlayerInventory(List<ItemSlot> inventory) {
        this(null, null, null, null, inventory);
    }

    public PlayerInventory(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, List<ItemSlot> inventory) {
        this(helmet, chestplate, leggings, boots, inventory, new ArrayList<>());
    }

    public PlayerInventory(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, List<ItemSlot> inventory, List<ExtraInv> extraInvList) {
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

    public PlayerInventory(Player player) {
        org.bukkit.inventory.PlayerInventory playerInventory = player.getInventory();
        for (int index = 0; index < 36; index++){
            ItemStack itemStack = playerInventory.getItem(index);
            if (itemStack != null){
                ItemSlot itemSlot = new ItemSlot(index,itemStack.clone());
                inventory.add(itemSlot);
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
                List<ItemSlot> itemSlotList = ItemSlot.fromStackList(extraInvItems);
                extraInv.getItemSlotList().addAll(itemSlotList);
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
        for (ItemSlot itemSlot : inventory) {
            section.setValue("inventory." + itemSlot.getSlot(), itemSlot.getItemStack());
        }

        section.setValue("extra", null); //Clear content before saving it
        for (ExtraInv extraInv : extraInvMap.values()) {
            for (ItemSlot itemSlot : extraInv.getItemSlotList()) {
                section.setValue("extra." + extraInv.getName() + "." + itemSlot.getSlot(), itemSlot.getItemStack());
            }
        }
    }

    @Loadable
    public static PlayerInventory onConfigLoad(ConfigSection section){
        ItemStack helmet = section.getLoadable("helmet",ItemStack.class);
        ItemStack chestplate = section.getLoadable("chestplate",ItemStack.class);
        ItemStack leggings = section.getLoadable("leggings",ItemStack.class);
        ItemStack boots = section.getLoadable("boots",ItemStack.class);
        List<ItemSlot> inventory = new ArrayList<>();
        for (String key : section.getKeys("inventory")) {
            try {
                Integer slot = Integer.parseInt(key);
                ItemStack itemStack = section.getLoadable("inventory." + slot, ItemStack.class);
                ItemSlot itemSlot = new ItemSlot(slot,itemStack);
                inventory.add(itemSlot);
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
                        ItemSlot itemSlot = new ItemSlot(slot,itemStack);
                        extraInv.getItemSlotList().add(itemSlot);
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
        return new PlayerInventory(helmet,chestplate,leggings,boots,inventory,extraInvList);
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

    public List<ItemSlot> getInventory() {
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
        for (ItemSlot itemSlot : this.inventory) {
            if (index == itemSlot.getSlot()){
                return itemSlot.getItemStack();
            }
        }
        return null;
    }

    public void restoreTo(Player player){
        org.bukkit.inventory.PlayerInventory playerInventory = player.getInventory();

        ItemStack[] inventoryContent = new ItemStack[36];
        for (ItemSlot itemSlot : inventory) {
            inventoryContent[itemSlot.getSlot()] = itemSlot.getItemStack().clone();
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