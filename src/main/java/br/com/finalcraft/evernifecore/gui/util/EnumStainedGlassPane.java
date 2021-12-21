package br.com.finalcraft.evernifecore.gui.util;

import br.com.finalcraft.evernifecore.version.MCVersion;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum EnumStainedGlassPane {
    WHITE('7'),
    ORANGE('6'),
    MAGENTA('d'),
    LIGHT_BLUE('b'),
    YELLOW('e'),
    LIME('a'),
    PINK('c'),
    GRAY('7'),
    LIGHT_GRAY('7'),
    CYAN('3'),
    PURPLE('d'),
    BLUE('9'),
    BROWN('c'),
    GREEN('2'),
    RED('c'),
    BLACK('7'),
    ;

    private final Material material;
    private final short damage;
    private final char colorCode;
    private transient GuiItem guiItem = null;

    EnumStainedGlassPane(char colorCode) {
        if (MCVersion.isCurrentEqualOrHigher(MCVersion.v1_13_R1)) {
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

    public ItemBuilder builder(){
        ItemBuilder itemBuilder = ItemBuilder.from(material);
        if (damage != 0) itemBuilder.durability(damage);
        return itemBuilder;
    }

    public GuiItem asBackground(){
        if (guiItem == null) {
            guiItem = builder().name("§" + this.colorCode + "§l❂").asGuiItem();
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
