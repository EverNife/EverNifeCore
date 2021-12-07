package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.gui.custom.GuiComplex;
import br.com.finalcraft.evernifecore.gui.item.GuiItemComplex;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class GuiFactory {

    public static GuiComplex create(){

        GuiComplex guiComplex = GuiComplex
                .complex()
                .disableAllInteractions()
                .title("Xablau")
                .rows(6)
                .create();


        GuiItemComplex RANDOM_STONE =
                new GuiItemComplex(ItemBuilder.from(Material.STONE).name("Vai Brasil").build())
                .setUpdateInterval(2)
                .setOnItemUpdate((player, guiItemComplex) -> {
                    ItemStack itemStack = guiItemComplex.getItemStack();
                    itemStack.setType(Material.getMaterial("STAINED_GLASS_PANE"));
                    itemStack.setDurability((short) FCBukkitUtil.getRandom().nextInt(12));
                    String itemName = FCItemUtils.getDisplayName(itemStack);
                    FCItemUtils.setDisplayName(
                            itemStack,
                            FCColorUtil.getRandomColor() + "§l" + ChatColor.stripColor(itemName)
                    );
                });

        for (int i = 1; i < 6; i++) {
            guiComplex.setItem(
                    i,
                    i,
                    RANDOM_STONE
            );
        }

        return guiComplex;
    }


}
