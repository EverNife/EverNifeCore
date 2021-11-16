package br.com.finalcraft.evernifecore.integration.bossshop.shops;

import br.com.finalcraft.evernifecore.integration.BossShopIntegration;
import org.black_ixx.bossshop.core.BSBuy;
import org.black_ixx.bossshop.core.BSShop;
import org.black_ixx.bossshop.core.BSShopHolder;
import org.black_ixx.bossshop.core.BSShops;
import org.black_ixx.bossshop.managers.config.BSConfigShop;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;

public class ECConfigShop extends BSConfigShop implements IECShop {

    public ECConfigShop(File file) {
        this(BossShopIntegration.getAPI().getShopHandler().createId(), file, null);
    }

    public ECConfigShop(int shop_id, String ymlName, BSShops shophandler) {
        super(shop_id, ymlName, shophandler);
    }

    public ECConfigShop(int shop_id, File f, BSShops shophandler) {
        super(shop_id, f, shophandler);
    }

    public ECConfigShop(int shop_id, File f, BSShops shophandler, ConfigurationSection sectionOptional) {
        super(shop_id, f, shophandler, sectionOptional);
    }

    @Override
    public ItemStack finalizeTranslateItemStack(BSBuy buy, BSShop shop, BSShopHolder holder, ItemStack translated_item, Player target, boolean final_version) {
        return translated_item; //By Default, do not change anything
    }
}
