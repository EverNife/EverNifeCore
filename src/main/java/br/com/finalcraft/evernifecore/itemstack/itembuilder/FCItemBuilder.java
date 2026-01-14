package br.com.finalcraft.evernifecore.itemstack.itembuilder;

import br.com.finalcraft.evernifecore.gui.item.GuiItemComplex;
import br.com.finalcraft.evernifecore.gui.layout.LayoutIcon;
import br.com.finalcraft.evernifecore.gui.layout.LayoutIconBuilder;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FCItemBuilder extends FCBaseItemBuilder<FCItemBuilder> {

    private transient @Nullable LayoutIcon layout; //This will be present when the FCItemBuilder comes from an LayoutIcon

    public FCItemBuilder(@Nonnull ItemStack itemStack, LayoutIcon layoutIcon) {
        super(itemStack);
        this.layout = layoutIcon;
    }

    public FCItemBuilder(@Nonnull ItemStack itemStack) {
        this(itemStack, null);
    }

    /**
     * Returns a GuiItemComplex object that contains the ItemStack of this ItemBuilder.
     *
     * @return A GuiItemComplex object.
     */
    @Nonnull
    public GuiItemComplex asGuiItemComplex() {
        return new GuiItemComplex(build());
    }

    /**
     * Returns a GuiItem object that contains the ItemStack of this ItemBuilder.
     *
     * @return A GuiItem object.
     */
    @Nonnull
    public GuiItem asGuiItem() {
        return new GuiItem(build());
    }

    /**
     * Returns a GuiItem object that contains the ItemStack of this ItemBuilder.
     *
     * @return A GuiItem object.
     */
    @Nonnull
    public <GI extends GuiItem> GI asGuiItem(Class<GI> customGuiItem) {
        return as(customGuiItem);
    }

    /**
     * Returns a LayoutIcon object that contains the ItemStack of this ItemBuilder.
     *
     * @return A LayoutIcon object
     */
    @Nonnull
    public LayoutIcon asLayout() {
        if (layout != null){
            ItemStack finalStack = this.build();
            return new LayoutIcon(
                    finalStack,
                    layout.getSlot(),
                    layout.isBackground(),
                    layout.getPermission(),
                    FCItemFactory.from(finalStack).toDataPart() //Need to recalculate data-part as it was probably changed on the factory
            );
        }else {
            return new LayoutIcon(
                    this.build(),
                    new int[0],
                    false,
                    "",
                    null //No need to calculate data-part, as this is the creation process, not an edition process
            );
        }
    }

    public LayoutIconBuilder asLayoutBuilder(){
        return this.asLayout().asLayoutBuilder();
    }

    /**
     * Returns an ItemStackHolder object that contains the ItemStack of this ItemBuilder.
     * An ItemStackHolder is any object that has a Construtor that has a sole argument
     * of an ItemStack
     *
     * @return A LayoutIcon object
     */
    @Nonnull
    public <ItemStackHolder> ItemStackHolder as(Class<ItemStackHolder> itemStackHolderClass) {
        return FCReflectionUtil.getConstructor(itemStackHolderClass, ItemStack.class)
                .invoke(this.build());
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
