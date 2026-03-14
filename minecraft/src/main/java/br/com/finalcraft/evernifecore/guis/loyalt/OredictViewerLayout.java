package br.com.finalcraft.evernifecore.guis.loyalt;

import br.com.finalcraft.evernifecore.gui.builders.PaginatedComplexGuiBuilder;
import br.com.finalcraft.evernifecore.gui.layout.*;
import br.com.finalcraft.evernifecore.gui.util.EnumStainedGlassPane;

@LayoutBaseData(
        title = "➲  §0%oredict_name%",
        guiBuilder = PaginatedComplexGuiBuilder.class
)
public class OredictViewerLayout extends LayoutBase {

    @LayoutIconData(
            slot = {4},
            background = true
    )
    public LayoutIcon INFO_SLOT = DefaultIcons.getInformationButton()
            .asFactory()
            .displayName("§a§l★ §7§lOreDict Info§a§l ★")
            .lore("§7§m----------&7&l< &a&lOreInfo &7&l>&7&m----------&r",
                    "",
                    "§f OreName: §7%oredict_name%",
                    "§f Amount: §7%oredict_amount%",
                    "",
                    "",
                    "&f&7&m-----------------------------------")
            .asLayout();

    // -----------------------------------------------------------------------------------------------------------------
    //  PAGINATION
    // -----------------------------------------------------------------------------------------------------------------

    @LayoutIconData(
            slot = {45}
    )
    public LayoutIcon PREVIOUS_PAGE = DefaultIcons.getPreviousPageButton();

    @LayoutIconData(
            slot = {53}
    )
    public LayoutIcon NEXT_PAGE = DefaultIcons.getNextPageButton();

    // -----------------------------------------------------------------------------------------------------------------
    //  BackGround
    // -----------------------------------------------------------------------------------------------------------------

    @LayoutIconData(
            slot = {36, 37, 46,    52, 43, 44},
            background = true
    )
    public LayoutIcon BLACK_BACKGROUND = EnumStainedGlassPane.BLACK
            .asLayout();

    @LayoutIconData(
            slot = {0, 1, 2, 3, 4, 5, 6, 7, 8},
            background = true
    )
    public LayoutIcon WHITE_BACKGROUND = EnumStainedGlassPane.WHITE
            .asLayout();

}
