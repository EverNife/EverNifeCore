package br.com.finalcraft.evernifecore.minecraft.itemstack.itembuilder;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.view.ClickContext;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import jakarta.annotation.Nonnull;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FCItemBuilder extends FCBaseItemBuilder<FCItemBuilder> {

    public FCItemBuilder(@Nonnull ItemStack itemStack) {
        super(itemStack);
    }

    /**
     * Ends the chain as an {@link Icon}: the item plus everything a gui needs to place it and react
     * to it. This is the single icon type - there is no separate "gui item".
     */
    @Nonnull
    public Icon asIcon() {
        return new Icon(build());
    }

    /** Ends the chain as an {@link Icon} that answers a click. */
    @Nonnull
    public Icon onClick(@Nonnull Consumer<ClickContext> onClick) {
        return asIcon().onClick(onClick);
    }

    /** Ends the chain as an {@link Icon} that redraws itself every {@code ticks} - pair it with
     *  {@link Icon#render(Consumer)}. */
    @Nonnull
    public Icon every(long ticks) {
        return asIcon().every(ticks);
    }

    /**
     * Returns an ItemStackHolder object that contains the ItemStack of this ItemBuilder.
     * An ItemStackHolder is any object that has a Construtor that has a sole argument
     * of an ItemStack
     *
     * @return An instance of the requested ItemStackHolder class
     */
    @Nonnull
    public <ItemStackHolder> ItemStackHolder as(Class<ItemStackHolder> itemStackHolderClass) {
        return FCReflectionUtil.getConstructors().getConstructor(itemStackHolderClass, ItemStack.class)
                .newInstance(this.build());
    }

    /**
     * "This function applies a consumer to the builder and returns the builder."
     *
     * The `apply` function is a very useful function that allows you to apply a consumer to the builder
     *
     * @param apply The function that will be applied to the builder.
     * @return The FCItemBuilder object.
     */
    @Nonnull
    public FCItemBuilder apply(@Nonnull Consumer<FCItemBuilder> apply){
        apply.accept(this);
        return this;
    }

    /**
     * "If the condition is true, apply the consumer to the builder."
     *
     * The `applyIf` function is a very useful function that allows you to apply a consumer to the builder only if a
     * condition is true
     *
     * @param condition A supplier that returns a boolean.
     * @param apply The consumer that will be applied to the builder if the condition is true.
     * @return The FCItemBuilder object.
     */
    @Nonnull
    public FCItemBuilder applyIf(@Nonnull Supplier<Boolean> condition, @Nonnull Consumer<FCItemBuilder> apply){
        if (condition.get() == true){
            apply.accept(this);
        }
        return this;
    }

    /**
     * Applies the material of the given [Material or Minecraft identifier] to the builder if it exists.
     *
     * @param materialOrMinecraftIdentifier The Minecraft identifier of the material.
     * @return The FCItemBuilder object.
     */
    @Nonnull
    public FCItemBuilder applyMaterialIfExists(@Nonnull String materialOrMinecraftIdentifier){
        try {
            return this.changeItemStack(FCItemFactory.from(materialOrMinecraftIdentifier).build());
        }catch (Exception ignored){

        }
        return this;
    }


    /**
     * Read the ItemStack to a DataPart String List
     *
     * @return A list of strings.
     */
    @Nonnull
    public List<String> toDataPart(){
        return ItemDataPart.readItem(this.build());
    }
}
