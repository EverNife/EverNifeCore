package br.com.finalcraft.evernifecore.gui.util;

import br.com.finalcraft.evernifecore.version.MCVersion;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum EnumGlassPane {
    WHITE_STAINED_GLASS_PANE('7'),
    ORANGE_STAINED_GLASS_PANE('6'),
    MAGENTA_STAINED_GLASS_PANE('d'),
    LIGHT_BLUE_STAINED_GLASS_PANE('b'),
    YELLOW_STAINED_GLASS_PANE('e'),
    LIME_STAINED_GLASS_PANE('a'),
    PINK_STAINED_GLASS_PANE('c'),
    GRAY_STAINED_GLASS_PANE('7'),
    LIGHT_GRAY_STAINED_GLASS_PANE('7'),
    CYAN_STAINED_GLASS_PANE('3'),
    PURPLE_STAINED_GLASS_PANE('d'),
    BLUE_STAINED_GLASS_PANE('9'),
    BROWN_STAINED_GLASS_PANE('c'),
    GREEN_STAINED_GLASS_PANE('2'),
    RED_STAINED_GLASS_PANE('c'),
    BLACK_STAINED_GLASS_PANE('7'),
    ;

    private final Material material;
    private final short damage;
    private final char colorCode;
    private transient GuiItem guiItem = null;

    EnumGlassPane(char colorCode) {
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
