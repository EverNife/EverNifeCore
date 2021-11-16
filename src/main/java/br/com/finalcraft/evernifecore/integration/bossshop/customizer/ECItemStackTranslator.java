package br.com.finalcraft.evernifecore.integration.bossshop.customizer;

import br.com.finalcraft.evernifecore.integration.bossshop.shops.IECShop;
import org.black_ixx.bossshop.core.BSBuy;
import org.black_ixx.bossshop.core.BSShop;
import org.black_ixx.bossshop.core.BSShopHolder;
import org.black_ixx.bossshop.managers.item.ItemStackTranslator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ECItemStackTranslator extends ItemStackTranslator {

    @Override
    public ItemStack translateItemStack(BSBuy buy, BSShop shop, BSShopHolder holder, ItemStack item, Player target, boolean final_version) {
        ItemStack itemStack = super.translateItemStack(buy, shop, holder, item, target, final_version); //Do Default Tranlation

        if (shop instanceof IECShop){ //IF ECshop, fire last customization!
            itemStack = ((IECShop) shop).finalizeTranslateItemStack(buy, shop, holder, item, target, final_version);
        }

        return itemStack;
    }

}
