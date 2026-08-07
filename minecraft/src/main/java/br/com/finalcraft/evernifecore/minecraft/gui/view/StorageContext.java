package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * One change to an editable region, as its handler sees it: what the region holds now that the store
 * has been written, and whether this is the last word on it.
 */
public final class StorageContext {

    private final StorageView storage;
    private final List<ItemStack> contents;
    private final boolean last;

    StorageContext(StorageView storage, List<ItemStack> contents, boolean last) {
        this.storage = storage;
        this.contents = contents;
        this.last = last;
    }

    /** The player who has it open, or {@code null} once the view has been released. */
    @Nullable
    public Player getViewer() {
        return storage.getView().getViewer();
    }

    /**
     * The store this change was written into, already holding what {@link #getContents()} answers. It is
     * what tells two regions apart when one handler serves both.
     */
    @Nonnull
    public GenericInventory getBacking() {
        return storage.getBinding().getBacking();
    }

    /**
     * What the region holds, in region order and with empty slots as {@code null}. Copies: writing into
     * this list, or into the stacks in it, reaches neither the store nor the open window.
     */
    @Nonnull
    public List<ItemStack> getContents() {
        return Collections.unmodifiableList(contents);
    }

    /**
     * Whether the screen is going away, making this the last change it will report - the moment a plugin
     * that saves lazily has to flush.
     */
    public boolean isLast() {
        return last;
    }

}
