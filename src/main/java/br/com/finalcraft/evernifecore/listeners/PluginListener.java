package br.com.finalcraft.evernifecore.listeners;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.listeners.bossshop.BossShopListener;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.server.PluginEnableEvent;

public class PluginListener implements ECListener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("BossShopPro")){
            if (NMSUtils.get() != null){
                EverNifeCore.info("Found BossShopPro, registering 'nbt' tag!");
                ECListener.register(EverNifeCore.instance, BossShopListener.class);
            }else {
                EverNifeCore.info("Found BossShopPro, but NMS not found for this server version [" + MCVersion.getCurrent() +" ] !");
            }
            return;
        }
    }

}
