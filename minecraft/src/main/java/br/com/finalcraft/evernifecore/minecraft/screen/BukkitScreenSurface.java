package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.inventory.ItemStore;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.ElementPatch;
import br.com.finalcraft.evernifecore.screen.GridSurface;
import br.com.finalcraft.evernifecore.screen.RenderedIcon;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.StorageBinding;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One chest window: the Bukkit container a {@link ScreenView} draws through, and the editable areas that live in it.
 *
 * <p>A slot is written as the {@code ItemStack} its {@link RenderedIcon} materializes to ({@link ChestItems}), and that
 * stack is remembered: a click is honoured only on a slot that still shows what was written there, and a resync puts
 * back whatever stopped showing it.</p>
 *
 * <p>An editable area's slots are never drawn here - the player owns them. They are filled from the area's store when
 * the window opens, carried across when the window is replaced, and read back into the store when the view goes.</p>
 */
public final class BukkitScreenSurface extends GridSurface {

    private final ChestScreenRenderer renderer;
    private final ScreenView view;
    private Inventory inventory;
    private final ItemStack[] written;
    private final RenderedIcon[] icons;

    private List<StorageView> storages = new ArrayList<>();
    private Map<StorageBinding, StorageView> storagesByBinding = new IdentityHashMap<>();
    private boolean swapping = false;
    private boolean released = false;

    BukkitScreenSurface(@Nonnull ChestScreenRenderer renderer, @Nonnull ScreenView view, @Nonnull Inventory inventory) {
        super(view.getGrid());
        this.renderer = renderer;
        this.view = view;
        this.inventory = inventory;
        this.written = new ItemStack[view.getGrid().getSize()];
        this.icons = new RenderedIcon[view.getGrid().getSize()];
    }

    @Nonnull
    public Inventory getInventory() {
        return inventory;
    }

    @Nonnull
    public ScreenView getView() {
        return view;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Drawing
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void apply(@Nonnull List<ElementPatch> patch) {
        for (ElementPatch each : patch) {
            if (getGrid().isInside(each.getSlot())) {
                write(each.getSlot(), each.getIcon(), each.isEnabled(), each.getReason(), each.getElementId());
            }
        }
    }

    @Override
    protected void writeSlot(int slot, @Nullable RenderedIcon icon) {
        write(slot, icon, true, null, "slot#" + slot);
    }

    private void write(int slot, @Nullable RenderedIcon icon, boolean enabled, @Nullable String reason, String elementId) {
        ItemStack stack = ChestItems.materialize(icon, enabled, reason, "[" + elementId + "] of [" + view.getScreen().describe() + "]");
        inventory.setItem(slot, stack);
        written[slot] = stack == null ? null : stack.clone();
        icons[slot] = icon;
    }

    @Nullable
    @Override
    protected RenderedIcon readSlot(int slot) {
        ItemStack shown = inventory.getItem(slot);
        if (ItemStore.isEmpty(shown)) {
            return null;
        }
        if (isSameOutput(shown, written[slot])) {
            return icons[slot];
        }
        //somebody else wrote it: an icon of its own native item never equals what the buffer drew
        return new RenderedIcon(ChestItems.specOf(shown), Collections.emptyList(), Collections.emptyList());
    }

    /** Whether {@code slot} still shows what this surface wrote there - the check a click on it has to pass. */
    public boolean isShowingWritten(int slot, @Nullable ItemStack shown) {
        return slot >= 0 && slot < written.length && isSameOutput(shown, written[slot]);
    }

    /** What the container holds at {@code slot} right now. */
    @Nullable
    public ItemStack getItem(int slot) {
        return inventory.getItem(slot);
    }

    /** Writes {@code slot} as an editable area does: straight into the container, outside the buffer. */
    void set(int slot, @Nullable ItemStack item) {
        inventory.setItem(slot, item);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The window
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void setTitle(@Nonnull String title) {
        renderer.swapForTitle(this, title);
    }

    @Override
    public void close(@Nonnull CloseReason reason) {
        Player player = ChestScreenRenderer.playerOf(view);
        if (player != null && player.isOnline()) {
            player.closeInventory();
        }
    }

    /** By reference: two containers with the same contents are still two windows. */
    @Override
    public boolean isBackedBy(@Nullable Object nativeWindow) {
        return nativeWindow != null && nativeWindow == inventory;
    }

    /** True while the container is being replaced for a title change, so the close that causes is not a close. */
    boolean isSwapping() {
        return swapping;
    }

    void setSwapping(boolean swapping) {
        this.swapping = swapping;
    }

    /**
     * Moves everything the window shows - the screen's icons and the editable areas' items alike - into
     * {@code replacement}, which becomes this surface's container. What was written stays written, so nothing is
     * redrawn for it.
     */
    void moveInto(@Nonnull Inventory replacement) {
        int size = Math.min(written.length, replacement.getSize());
        for (int slot = 0; slot < size; slot++) {
            ItemStack held = inventory.getItem(slot);
            replacement.setItem(slot, ItemStore.isEmpty(held) ? null : held.clone());
        }
        this.inventory = replacement;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Editable areas
    // -----------------------------------------------------------------------------------------------------------------

    /** The editable area holding {@code slot}, or {@code null} when the screen draws it. */
    @Nullable
    public StorageView getStorageAt(int slot) {
        StorageBinding binding = view.getStorageAt(slot);
        return binding == null ? null : storagesByBinding.get(binding);
    }

    @Nonnull
    public List<StorageView> getStorages() {
        return Collections.unmodifiableList(storages);
    }

    /** Fills a fresh window from every area's store. */
    void startStorages() {
        for (StorageBinding binding : view.getScreen().getStorages()) {
            List<Integer> inside = new ArrayList<>();
            for (int slot : binding.getSlots().resolve(getGrid()).toArray()) {
                if (getGrid().isInside(slot)) {
                    inside.add(slot);
                }
            }
            int[] slots = new int[inside.size()];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = inside.get(i);
            }
            StorageView storage = new StorageView(view, binding, slots);
            storages.add(storage);
            storagesByBinding.put(binding, storage);
            storage.seed(this);
        }
    }

    /**
     * Takes the areas over from the window this one replaces: a title change and a screen coming back from under another
     * both build a new container, and the items are in the old one.
     */
    void carryStoragesFrom(@Nonnull BukkitScreenSurface previous) {
        this.storages = previous.storages;
        this.storagesByBinding = previous.storagesByBinding;
        for (StorageView storage : storages) {
            storage.carryOver(previous, this);
        }
    }

    /**
     * Reads every area back into its store, last word (one area at a time: a store that throws costs its own contents
     * and nothing else), gives back what the cursor holds, and lets the stores go.
     */
    @Override
    public void released(@Nonnull CloseReason reason) {
        if (released) {
            return;
        }
        released = true;
        renderer.forget(this);
        if (storages.isEmpty()) {
            return;
        }
        for (StorageView storage : storages) {
            try {
                storage.syncNow(true);
            } catch (Throwable e) {
                EverNifeCore.getLog().severe("The editable region [{}] of a screen could not be read back for [{}]",
                        storage.getBinding().getName(), view.getViewerName(), e);
            }
        }
        try {
            returnCarriedItem();
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("What [{}] was holding on the cursor could not be given back", view.getViewerName(), e);
        }
        for (StorageView storage : storages) {
            storage.teardown();
        }
    }

    /**
     * Gives the viewer back whatever they still hold on the cursor. The platform's own answer to a window torn down
     * mid-gesture is to drop the stack on the ground - and to a player already gone, nothing at all - so it is taken
     * off the cursor first and handed over afterwards, which also stops the platform from giving out a second copy.
     */
    private void returnCarriedItem() {
        Player player = ChestScreenRenderer.playerOf(view);
        if (player == null) {
            return;
        }
        ItemStack carried = player.getItemOnCursor();
        if (ItemStore.isEmpty(carried)) {
            return;
        }
        player.setItemOnCursor(new ItemStack(Material.AIR));
        FCBukkitUtil.giveItemsTo(player, carried);
    }

    /** Same item and amount; every way of holding nothing is the same nothing. */
    public static boolean isSameOutput(@Nullable ItemStack current, @Nullable ItemStack rendered) {
        if (ItemStore.isEmpty(current)) {
            return ItemStore.isEmpty(rendered);
        }
        if (ItemStore.isEmpty(rendered)) {
            return false;
        }
        return current.getAmount() == rendered.getAmount() && current.isSimilar(rendered);
    }

}
