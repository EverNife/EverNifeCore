package br.com.finalcraft.evernifecore.config.playerinventory;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.playerinventory.extrainvs.ExtraInv;
import br.com.finalcraft.evernifecore.config.playerinventory.extrainvs.ExtraInvType;
import br.com.finalcraft.evernifecore.fcitemstack.FCItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class PlayerInventory implements Config.Salvable {

    protected FCItemStack helmet;
    protected FCItemStack chestplate;
    protected FCItemStack leggings;
    protected FCItemStack boots;
    protected List<ItemSlot> inventory = new ArrayList<>(); //0-35
    protected HashMap<ExtraInvType, ExtraInv> extraInvMap = new HashMap<>();

    public PlayerInventory() {
        this(null, null, null, null, new ArrayList<>());
    }

    public PlayerInventory(List<ItemSlot> inventory) {
        this(null, null, null, null, inventory);
    }

    public PlayerInventory(FCItemStack helmet, FCItemStack chestplate, FCItemStack leggings, FCItemStack boots, List<ItemSlot> inventory) {
        this(helmet, chestplate, leggings, boots, inventory, new ArrayList<>());
    }

    public PlayerInventory(FCItemStack helmet, FCItemStack chestplate, FCItemStack leggings, FCItemStack boots, List<ItemSlot> inventory, List<ExtraInv> extraInvList) {
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
                FCItemStack fcItemStack = new FCItemStack(itemStack);
                ItemSlot itemSlot = new ItemSlot(index,fcItemStack);
                inventory.add(itemSlot);
            }
        }
        this.helmet = playerInventory.getHelmet() != null ? new FCItemStack(playerInventory.getHelmet()) : null;
        this.chestplate = playerInventory.getChestplate() != null ? new FCItemStack(playerInventory.getChestplate()) : null;
        this.leggings = playerInventory.getLeggings() != null ? new FCItemStack(playerInventory.getLeggings()) : null;
        this.boots = playerInventory.getBoots() != null ? new FCItemStack(playerInventory.getBoots()) : null;

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
    public void onConfigSave(Config config, String path) {
        config.setValue(path + ".helmet", helmet);
        config.setValue(path + ".chestplate", chestplate);
        config.setValue(path + ".leggings", leggings);
        config.setValue(path + ".boots", boots);
        for (ItemSlot itemSlot : inventory) {
            config.setValue(path + ".inventory." + itemSlot.getSlot(), itemSlot.getFcItemStack());
        }
        for (ExtraInv extraInv : extraInvMap.values()) {
            for (ItemSlot itemSlot : extraInv.getItemSlotList()) {
                config.setValue(path + ".extra." + extraInv.getName() + "." + itemSlot.getSlot(), itemSlot.getFcItemStack());
            }
        }
    }

    @Config.Loadable
    public static PlayerInventory onConfigLoad(Config config, String path){
        FCItemStack helmet = config.getFCItem(path + ".helmet",null);
        FCItemStack chestplate = config.getFCItem(path + ".chestplate",null);
        FCItemStack leggings = config.getFCItem(path + ".leggings",null);
        FCItemStack boots = config.getFCItem(path + ".boots",null);
        List<ItemSlot> inventory = new ArrayList<>();
        for (String key : config.getKeys(path + ".inventory")) {
            try {
                Integer slot = Integer.parseInt(key);
                FCItemStack fcItemStack = config.getFCItem(path + ".inventory." + slot);
                ItemSlot itemSlot = new ItemSlot(slot,fcItemStack);
                inventory.add(itemSlot);
            }catch (NumberFormatException e){
                e.printStackTrace();
            }
        }

        List<ExtraInv> extraInvList = new ArrayList<>();
        for (String extraInvKey : config.getKeys(path + ".extra")) {
            try {
                ExtraInvType extraInvType = ExtraInvType.valueOf(extraInvKey.toUpperCase());
                if (!extraInvType.isEnabled()) continue;
                ExtraInv extraInv = ExtraInvType.valueOf(extraInvKey.toUpperCase()).createExtraInv();
                for (String itemSlotKey : config.getKeys(path + ".extra." + extraInvKey)) {
                    try {
                        Integer slot = Integer.parseInt(itemSlotKey);
                        FCItemStack fcItemStack = config.getFCItem(path + ".extra." + extraInvKey + "." + slot);
                        ItemSlot itemSlot = new ItemSlot(slot,fcItemStack);
                        extraInv.getItemSlotList().add(itemSlot);
                    }catch (NumberFormatException e){
                        EverNifeCore.info("Failed to load ItemSlot(iteSlot==" + itemSlotKey + ") from [" + (path + ".extra." + extraInvKey ) + "] \n --> " + config.getTheFile().getName());
                        e.printStackTrace();
                    }
                }
                extraInvList.add(extraInv);
            }catch (Exception e){
                EverNifeCore.info("Failed to load ExtraInv(" + extraInvKey + ") from [" + (path + ".extra." + extraInvKey ) + "] \n --> " + config.getTheFile().getName());
                e.printStackTrace();
            }
        }
        return new PlayerInventory(helmet,chestplate,leggings,boots,inventory,extraInvList);
    }

    public FCItemStack getHelmet() {
        return helmet;
    }

    public FCItemStack getChestplate() {
        return chestplate;
    }

    public FCItemStack getLeggings() {
        return leggings;
    }

    public FCItemStack getBoots() {
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

    public FCItemStack getItem(int index){
        for (ItemSlot itemSlot : this.inventory) {
            if (index == itemSlot.slot){
                return itemSlot.getFcItemStack();
            }
        }
        return null;
    }

    public void restoreTo(Player player){
        org.bukkit.inventory.PlayerInventory playerInventory = player.getInventory();

        ItemStack[] inventoryContent = new ItemStack[36];
        for (ItemSlot itemSlot : inventory) {
            inventoryContent[itemSlot.getSlot()] = itemSlot.getFcItemStack().copyItemStack();
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

        playerInventory.setHelmet(this.getHelmet() == null ?  null : this.getHelmet().copyItemStack());
        playerInventory.setChestplate(this.getChestplate() == null ?  null : this.getChestplate().copyItemStack());
        playerInventory.setLeggings(this.getLeggings() == null ?  null : this.getLeggings().copyItemStack());
        playerInventory.setBoots(this.getBoots() == null ?  null : this.getBoots().copyItemStack());
    }
}