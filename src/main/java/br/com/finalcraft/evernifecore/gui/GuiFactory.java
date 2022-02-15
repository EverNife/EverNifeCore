package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.gui.custom.GuiComplex;
import br.com.finalcraft.evernifecore.gui.item.GuiItemComplex;
import br.com.finalcraft.evernifecore.gui.util.EnumStainedGlassPane;
import br.com.finalcraft.evernifecore.itembuilder.FCItemFactory;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Material;

public class GuiFactory {

    //TODO Remove this
    public static GuiComplex create(){

        GuiComplex guiComplex = GuiComplex
                .complex()
                .disableAllInteractions()
                .title("Xablau")
                .rows(6)
                .create();


        int count = -1;
        for (EnumStainedGlassPane glassPane : EnumStainedGlassPane.values()) {
            count++;
            GuiItemComplex WHOOL = FCItemFactory.from(Material.STONE)
                    .applyIf(() -> MCVersion.isBellow1_13(), builder -> builder.material("WHOOL"))
                    .applyIf(() -> !MCVersion.isBellow1_13(), builder -> builder.material(Material.WHITE_WOOL))
                    .name("Vai Brasil")
                    .asGuiItemComplex()
                    .setUpdateInterval(count + 1)
                    .setOnItemUpdate(context -> {
                        context.getGuiItem().updateItemStack(builder -> builder
                                .material(Material.getMaterial("STAINED_GLASS_PANE"))
                                .durability(FCBukkitUtil.getRandom().nextInt(12))
                                .build()
                        );
                    });
            guiComplex.setItem(
                    count,
                    WHOOL
            );
        }

        return guiComplex;
    }


}
