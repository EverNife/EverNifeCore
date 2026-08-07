package br.com.finalcraft.evernifecore.minecraft.gui.icons;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.itemstack.itembuilder.FCItemBuilder;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
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

    public ItemStack getItemStack(){
        ItemStack itemStack = new ItemStack(material);
        if (damage != 0) itemStack.setDurability(damage);
        return itemStack;
    }

    /** This pane as an icon, which is the form a screen's background is written in. */
    public Icon asIcon(){
        return asFactory().asIcon();
    }
}
