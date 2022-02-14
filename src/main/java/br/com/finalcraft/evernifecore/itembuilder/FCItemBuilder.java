package br.com.finalcraft.evernifecore.itembuilder;

import br.com.finalcraft.evernifecore.gui.item.GuiItemComplex;
import dev.triumphteam.gui.builder.item.BaseItemBuilder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class FCItemBuilder extends BaseItemBuilder<FCItemBuilder> {

    protected FCItemBuilder(@NotNull ItemStack itemStack) {
        super(itemStack);
    }

    @NotNull
    public GuiItemComplex asGuiItemComplex() {
        return new GuiItemComplex(build());
    }

    @NotNull
    public FCItemBuilder apply(Consumer<FCItemBuilder> apply){
        apply.accept(this);
        return this;
    }

    @NotNull
    public FCItemBuilder applyIf(Supplier<Boolean> condition, Consumer<FCItemBuilder> apply){
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
}
