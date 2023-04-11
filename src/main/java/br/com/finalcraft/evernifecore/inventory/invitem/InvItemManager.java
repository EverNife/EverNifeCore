package br.com.finalcraft.evernifecore.inventory.invitem;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class InvItemManager {

    public static HashMap<Material, InvItem> INVITEM_MAP = new HashMap<>();
    public static HashMap<String, InvItem> INVITEM_ID_MAP = new HashMap<>();

    public static void register(InvItem invItem){
        INVITEM_MAP.put(invItem.getMaterial(), invItem);
        INVITEM_ID_MAP.put(invItem.getId().toLowerCase(), invItem);
    }

    public static @Nullable InvItem of(Material material){
        return INVITEM_MAP.get(material);
    }

    public static @Nullable InvItem of(String invItemId){
        return INVITEM_ID_MAP.get(invItemId.toLowerCase());
    }

}
