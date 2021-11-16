package br.com.finalcraft.evernifecore.integration.bossshop.shops;

import br.com.finalcraft.evernifecore.integration.BossShopIntegration;
import org.black_ixx.bossshop.BossShop;
import org.black_ixx.bossshop.core.BSBuy;
import org.black_ixx.bossshop.core.BSShop;
import org.black_ixx.bossshop.core.BSShopHolder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ECShop extends BSShop implements IECShop {

    public ECShop(String shop_name, String displayname) {
        this(BossShopIntegration.getAPI().getShopHandler().createId(), shop_name, null, true, BossShopIntegration.getBossShopPlugin(), displayname, 0, null);
    }

    public ECShop(int shop_id, String shop_name, String sign_text, boolean needPermToCreateSign, BossShop plugin, String displayname, int manual_inventory_rows, String[] commands) {
        super(shop_id, shop_name, sign_text, needPermToCreateSign, plugin, displayname, manual_inventory_rows, commands);
    }

    public ECShop(int shop_id) {
        super(shop_id);
    }

    @Override
    public void reloadShop() {

    }

    @Override
    public ItemStack finalizeTranslateItemStack(BSBuy buy, BSShop shop, BSShopHolder holder, ItemStack translated_item, Player target, boolean final_version) {
        return translated_item; //By Default, do not change anything
    }
}
