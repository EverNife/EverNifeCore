package br.com.finalcraft.evernifecore.itemstack.itembuilder;

import br.com.finalcraft.evernifecore.gui.item.GuiItemComplex;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import dev.triumphteam.gui.builder.item.BaseItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FCItemBuilder extends BaseItemBuilder<FCItemBuilder> {

    protected FCItemBuilder(@NotNull ItemStack itemStack) {
        super(NMSUtils.get() != null
                ? NMSUtils.get().validateItemStackHandle(itemStack) //Always build an item with a Valid MCItemStack on it;
                : itemStack);
    }

    @NotNull
    public GuiItemComplex asGuiItemComplex() {
        return new GuiItemComplex(build());
    }

    @NotNull
    public FCItemBuilder apply(@NotNull Consumer<FCItemBuilder> apply){
        apply.accept(this);
        return this;
    }

    @NotNull
    public FCItemBuilder applyIf(@NotNull Supplier<Boolean> condition, @NotNull Consumer<FCItemBuilder> apply){
        if (condition.get() == true){
            apply.accept(this);
        }
        return this;
    }

    @NotNull
    public FCItemBuilder durability(final int durability) {
        itemStack.setDurability((short) durability);
        return this;
    }

    @NotNull
    public FCItemBuilder material(@NotNull Material material) {
        itemStack.setType(material);
        return this;
    }

    @NotNull
    public FCItemBuilder material(@NotNull String material) {
        Material theMaterial = FCInputReader.parseMaterial(material);
        if (theMaterial == null){
            throw new IllegalArgumentException("The materialName '" + material + "' is not a valid Bukkit Material");
        }
        itemStack.setType(theMaterial);
        return this;
    }

    public List<String> toDataPart(){
        return ItemDataPart.readItem(this.build());
    }
}
