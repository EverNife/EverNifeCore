package br.com.finalcraft.evernifecore.gui.util;

import br.com.finalcraft.evernifecore.gui.layout.LayoutIcon;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.itemstack.itembuilder.FCItemBuilder;
import br.com.finalcraft.evernifecore.version.MCVersion;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum EnumStainedGlassPane {
    WHITE('7'),     //0
    ORANGE('6'),    //1
    MAGENTA('d'),   //2
    LIGHT_BLUE('b'),//3
    YELLOW('e'),    //4
    LIME('a'),      //5
    PINK('c'),      //6
    GRAY('7'),      //7
    LIGHT_GRAY('7'),//8
    CYAN('3'),      //9
    PURPLE('d'),    //10
    BLUE('9'),      //11
    BROWN('c'),     //12
    GREEN('2'),     //13
    RED('c'),       //14
    BLACK('7'),     //15
    ;

    private final Material material;
    private final short damage;
    private final char colorCode;
    private transient LayoutIcon layoutIcon = null;
    private transient GuiItem guiItem = null;

    EnumStainedGlassPane(char colorCode) {
        if (MCVersion.isHigherEquals(MCVersion.v1_13)) {
            int firstGlassPaneOrdinal = Material.WHITE_STAINED_GLASS_PANE.ordinal();
            this.material = Material.values()[firstGlassPaneOrdinal + this.ordinal()];
            this.damage = 0;
        }else {
            this.material = Material.matchMaterial("STAINED_GLASS_PANE");
            this.damage = (short) this.ordinal();
        }
        this.colorCode = colorCode;
    }

    public Material getMaterial() {
        return material;
    }

    public short getDamage() {
        return damage;
    }

    public char getColorCode() {
        return colorCode;
    }

    public FCItemBuilder asFactory(){
        return FCItemFactory.from(this.material)
                .applyIf(() -> damage != 0, fcItemBuilder -> fcItemBuilder.durability(this.damage));
    }

    public LayoutIcon asLayout(){
        if (layoutIcon == null) {
            layoutIcon = asFactory().displayName("§" + this.colorCode + "§l❂").asLayout();
        }
        return layoutIcon;
    }

    public GuiItem asBackground(){
        if (guiItem == null) {
            guiItem = asFactory().displayName("§" + this.colorCode + "§l❂").asGuiItem();
            guiItem.setAction(event -> event.setCancelled(true));
        }
        return guiItem;
    }

    public ItemStack getItemStack(){
        ItemStack itemStack = new ItemStack(material);
        if (damage != 0) itemStack.setDurability(damage);
        return itemStack;
    }
}
