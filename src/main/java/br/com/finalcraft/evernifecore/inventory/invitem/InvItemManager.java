package br.com.finalcraft.evernifecore.inventory.invitem;

import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.inventory.invitem.imp.InvItemDraconicChest;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class InvItemManager {

    public static HashMap<Material, InvItem> INVITEM_MAP = new HashMap<>();
    static {
        if (EverForgeLibIntegration.draconicLoaded) {
            InvItemDraconicChest invItemDraconicChest = new InvItemDraconicChest();
            getInvItemMap().put(invItemDraconicChest.getMaterial(), invItemDraconicChest);
        }
    }

    public static HashMap<Material, InvItem> getInvItemMap() {
        return INVITEM_MAP;
    }

    public static @Nullable InvItem of(Material material){
        return INVITEM_MAP.get(material);
    }

    //Used less often, no need to have a map for this
    public static @Nullable InvItem of(String invItemId){
        return INVITEM_MAP.values().stream()
                .filter(invItem -> invItem.getId().equals(invItemId))
                .findFirst()
                .orElse(null);
    }
}
