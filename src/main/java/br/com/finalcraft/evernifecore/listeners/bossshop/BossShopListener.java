package br.com.finalcraft.evernifecore.listeners.bossshop;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.integration.BossShopIntegration;
import br.com.finalcraft.evernifecore.integration.bossshop.customizer.ECItemStackTranslator;
import br.com.finalcraft.evernifecore.integration.bossshop.datapart.ItemDataPartNBT;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.reflection.FieldAccessor;
import org.black_ixx.bossshop.core.BSBuy;
import org.black_ixx.bossshop.events.BSLoadShopItemsEvent;
import org.black_ixx.bossshop.events.BSRegisterTypesEvent;
import org.black_ixx.bossshop.inbuiltaddons.advancedshops.BSBuyAdvanced;
import org.black_ixx.bossshop.managers.ClassManager;
import org.black_ixx.bossshop.managers.config.BSConfigShop;
import org.black_ixx.bossshop.managers.item.ItemDataPart;
import org.black_ixx.bossshop.managers.item.ItemStackTranslator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BossShopListener implements ECListener {

    private final ItemDataPartNBT DATAPART_NBT = new ItemDataPartNBT();
    private ECItemStackTranslator EC_ITEM_STACK_TRANSLATOR = new ECItemStackTranslator();

    @EventHandler
    public void onRegisterTypes(BSRegisterTypesEvent event) {
        EverNifeCore.info("Registering BossShop Custom ItemDataPartNBT");
        ItemDataPart.registerType(DATAPART_NBT);

        //This event is fired right after the plugin is reloaded, nice time to Override the ItemStackTranslator
        EverNifeCore.info("Replacing BossShopPro ItemStackTranslator");
        FieldAccessor<ItemStackTranslator> translatorField = FCReflectionUtil.getField(ClassManager.class, "itemstackTranslator");
        translatorField.set(ClassManager.manager, EC_ITEM_STACK_TRANSLATOR);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBSLoadShopItem(BSLoadShopItemsEvent event) {

        if (!(event.getShop() instanceof BSConfigShop)){
            return;
        }

        BSConfigShop configShop = (BSConfigShop) event.getShop();

        ConfigurationSection config = configShop.getConfigurationSection();
        
        List<BSBuy> allItems = new ArrayList<>(configShop.getItems()); //Clone to prevent ConcurrentModificationException

        for (BSBuy bsBuy : allItems) {

            if (!(bsBuy instanceof BSBuy) && !(bsBuy instanceof BSBuyAdvanced)){ //Ignore other types of BSBuy (custom plugins?)
                continue;
            }

            ConfigurationSection itemSection = config.getConfigurationSection("shop." + bsBuy.getName());

            if (itemSection == null){
                continue; //If there is no config, we can't do anything
            }

            if (!itemSection.contains("CopyToLocations")) {
                continue; //This item does not need to be cloned
            }

            try {
                List<Integer> integerList = itemSection.getStringList("CopyToLocations").stream().map(Integer::parseInt).collect(Collectors.toList());

                for (Integer integer : integerList) {
                    BSBuy bsBuyClone = BossShopIntegration.cloneBSBuy(bsBuy, bsBuy.getName() + "$C_" + integer);
                    bsBuyClone.setInventoryLocation(integer - 1);
                    configShop.addShopItem(bsBuyClone, bsBuyClone.getItem(),BossShopIntegration.getClassManager());
                }

            }catch (Exception e){
                EverNifeCore.warning("Failed to load [shop." + bsBuy.getName() + ".CopyToLocations] from shop [" + configShop.getShopName() +  "]");
                e.printStackTrace();
            }
        }
    }

}
