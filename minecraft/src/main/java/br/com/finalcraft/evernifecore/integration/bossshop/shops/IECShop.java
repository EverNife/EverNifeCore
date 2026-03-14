package br.com.finalcraft.evernifecore.integration.bossshop.shops;

import org.black_ixx.bossshop.core.BSBuy;
import org.black_ixx.bossshop.core.BSShop;
import org.black_ixx.bossshop.core.BSShopHolder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface IECShop {

    public ItemStack finalizeTranslateItemStack(BSBuy buy, BSShop shop, BSShopHolder holder, ItemStack translated_item, Player target, boolean final_version);

}
