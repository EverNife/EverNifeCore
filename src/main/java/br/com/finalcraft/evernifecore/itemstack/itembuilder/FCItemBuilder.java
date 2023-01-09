package br.com.finalcraft.evernifecore.itemstack.itembuilder;

import br.com.finalcraft.evernifecore.gui.item.GuiItemComplex;
import br.com.finalcraft.evernifecore.gui.layout.LayoutIcon;
import br.com.finalcraft.evernifecore.gui.layout.LayoutIconBuilder;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FCItemBuilder extends FCBaseItemBuilder<FCItemBuilder> {

    private transient @Nullable LayoutIcon layout; //This will be present when the FCItemBuilder comes from an LayoutIcon

    public FCItemBuilder(@NotNull ItemStack itemStack, LayoutIcon layoutIcon) {
        super(itemStack);
        this.layout = layoutIcon;
    }

    public FCItemBuilder(@NotNull ItemStack itemStack) {
        this(itemStack, null);
    }

    /**
     * Returns a GuiItemComplex object that contains the ItemStack of this ItemBuilder.
     *
     * @return A GuiItemComplex object.
     */
    @NotNull
    public GuiItemComplex asGuiItemComplex() {
        return new GuiItemComplex(build());
    }

    /**
     * Returns a GuiItem object that contains the ItemStack of this ItemBuilder.
     *
     * @return A GuiItem object.
     */
    @NotNull
    public GuiItem asGuiItem() {
        return new GuiItem(build());
    }

    /**
     * Returns a GuiItem object that contains the ItemStack of this ItemBuilder.
     *
     * @return A GuiItem object.
     */
    @NotNull
    public <GI extends GuiItem> GI asGuiItem(Class<GI> customGuiItem) {
        return as(customGuiItem);
    }

    /**
     * Returns a LayoutIcon object that contains the ItemStack of this ItemBuilder.
     *
     * @return A LayoutIcon object
     */
    @NotNull
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
    @NotNull
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
    @NotNull
    public FCItemBuilder apply(@NotNull Consumer<FCItemBuilder> apply){
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
    @NotNull
    public FCItemBuilder applyIf(@NotNull Supplier<Boolean> condition, @NotNull Consumer<FCItemBuilder> apply){
        if (condition.get() == true){
            apply.accept(this);
        }
        return this;
    }

    /**
     * Read the ItemStack to a DataPart String List
     *
     * @return A list of strings.
     */
    @NotNull
    public List<String> toDataPart(){
        return ItemDataPart.readItem(this.build());
    }
}
