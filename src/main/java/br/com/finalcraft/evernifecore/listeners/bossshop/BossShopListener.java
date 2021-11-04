package br.com.finalcraft.evernifecore.listeners.bossshop;

import br.com.finalcraft.evernifecore.integration.datapart.ItemDataPartNBT;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.black_ixx.bossshop.events.BSRegisterTypesEvent;
import org.black_ixx.bossshop.managers.item.ItemDataPart;
import org.bukkit.event.EventHandler;

public class BossShopListener implements ECListener {

    private final ItemDataPartNBT DATAPART_NBT = new ItemDataPartNBT();

    @EventHandler
    public void onRegisterTypesEvent(BSRegisterTypesEvent event) {
        ItemDataPart.registerType(DATAPART_NBT);
    }

}
