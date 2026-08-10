package br.com.finalcraft.evernifecore.minecraft.itemstack.itembuilder;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.view.ClickContext;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemBase;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ParsedBlock;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import jakarta.annotation.Nonnull;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FCItemBuilder extends FCBaseItemBuilder<FCItemBuilder> {

    public FCItemBuilder(@Nonnull ItemStack itemStack) {
        super(ItemBase.of(itemStack));
    }

    public FCItemBuilder(@Nonnull ItemBase base) {
        super(base);
    }

    public FCItemBuilder(@Nonnull ItemBase base, @Nonnull ParsedBlock block) {
        super(base, block);
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
     * Builds the item and hands it to a class that wraps one.
     *
     * @param itemStackHolderClass a class with a constructor taking a single {@link ItemStack}
     * @return a new instance of it, holding the item this recipe builds
     */
    @Nonnull
    public <ItemStackHolder> ItemStackHolder as(Class<ItemStackHolder> itemStackHolderClass) {
        return FCReflectionUtil.getConstructors().getConstructor(itemStackHolderClass, ItemStack.class)
                .newInstance(this.build());
    }

    /**
     * Runs {@code apply} over this recipe, so a block of staging can live inside a chain.
     *
     * @return this builder
     */
    @Nonnull
    public FCItemBuilder apply(@Nonnull Consumer<FCItemBuilder> apply){
        apply.accept(this);
        return this;
    }

    /**
     * Runs {@code apply} over this recipe only when {@code condition} answers true.
     *
     * @param condition asked once, here; a condition answering null counts as false
     * @return this builder
     */
    @Nonnull
    public FCItemBuilder applyIf(@Nonnull Supplier<Boolean> condition, @Nonnull Consumer<FCItemBuilder> apply){
        if (Boolean.TRUE.equals(condition.get())){
            apply.accept(this);
        }
        return this;
    }

    /**
     * Starts the item over from {@code materialOrMinecraftIdentifier}, but only if this server has
     * it - which is how an icon names a modded look and still works where the mod is absent.
     *
     * @param materialOrMinecraftIdentifier a bukkit name or a namespaced identifier
     * @return The FCItemBuilder object.
     */
    @Nonnull
    public FCItemBuilder applyMaterialIfExists(@Nonnull String materialOrMinecraftIdentifier){
        try {
            ItemBase candidate = ItemBase.ofIdentifier(materialOrMinecraftIdentifier);
            candidate.resolve();
            setBase(candidate);
        } catch (Exception | LinkageError absentHere) {
            //asking "if it exists" means the answer "it does not" is ordinary, not a failure
        }
        return this;
    }

    /**
     * Reads the item this recipe builds back into item-data lines.
     *
     * <p>It goes through the built item on purpose. Formatting the staged edits directly would be
     * cheaper and would be a second way of writing the same text - and two ways of writing it is
     * exactly how the key of a concept and the shape of its value used to drift apart.</p>
     *
     * @return one line per concept the built item carries
     */
    @Nonnull
    public List<String> toDataPart(){
        return ItemEngine.get().read(this.build()).getLines();
    }
}
