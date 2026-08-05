package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.minecraft.gui.view.ClickContext;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.itemstack.itembuilder.FCItemBuilder;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * One item on a screen, with what happens when it is clicked and who is allowed to see it.
 *
 * <p>This is the single icon type of the framework: the same object serves an icon written by hand,
 * one produced by a list render and one restyled by the admin's YAML. It is the end of the
 * {@code FCItemFactory} chain - {@code FCItemFactory.from(...).displayName(...).onClick(...)}
 * already answers an {@code Icon}.</p>
 *
 * <p>The canonical stack is private and every read hands out a copy, so an icon shared by two
 * viewers cannot be mutated through one of them.</p>
 */
public class Icon {

    private ItemStack itemStack;
    private String permission = "";
    private boolean background = false;
    private Consumer<ClickContext> onClick;
    private long everyTicks = 0L;
    private Consumer<Icon> renderer;

    public Icon(@Nonnull ItemStack itemStack) {
        setItemStack(itemStack);
    }

    @Nonnull
    public static Icon of(@Nonnull ItemStack itemStack) {
        return new Icon(itemStack);
    }

    @Nonnull
    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public void setItemStack(@Nonnull ItemStack itemStack) {
        if (itemStack == null) {
            throw new IllegalArgumentException("An Icon needs an ItemStack. To hide the icon instead, "
                    + "bind it to an empty SlotSet or give it a permission nobody has.");
        }
        this.itemStack = itemStack.clone();
    }

    @Nonnull
    public String getPermission() {
        return permission;
    }

    public void setPermission(@Nullable String permission) {
        this.permission = permission == null ? "" : permission;
    }

    public boolean isBackground() {
        return background;
    }

    public void setBackground(boolean background) {
        this.background = background;
    }

    @Nullable
    public Consumer<ClickContext> getOnClick() {
        return onClick;
    }

    public void setOnClick(@Nullable Consumer<ClickContext> onClick) {
        this.onClick = onClick;
    }

    /** Whether {@code viewer} may see this icon at all. An icon without a permission is public. */
    public boolean isVisibleTo(@Nullable Player viewer) {
        return permission.isEmpty() || (viewer != null && viewer.hasPermission(permission));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Construction chain - the form used inline, where the icon is being built and not read
    // -----------------------------------------------------------------------------------------------------------------

    /** Only this permission's holders see the icon; to everyone else the slot is simply not painted. */
    @Nonnull
    public Icon permission(@Nullable String permission) {
        setPermission(permission);
        return this;
    }

    /** Paints the icon on the background layer, underneath the content of the screen. */
    @Nonnull
    public Icon background() {
        return background(true);
    }

    @Nonnull
    public Icon background(boolean background) {
        setBackground(background);
        return this;
    }

    @Nonnull
    public Icon onClick(@Nullable Consumer<ClickContext> onClick) {
        setOnClick(onClick);
        return this;
    }

    /**
     * Redraws this icon every {@code ticks} while a view showing it is open - {@code every(20)} is
     * once a second, not ten times. The task belongs to that view and is cancelled when it closes;
     * an icon nobody is looking at costs nothing.
     */
    @Nonnull
    public Icon every(long ticks) {
        this.everyTicks = Math.max(0L, ticks);
        return this;
    }

    /** What {@link #every(long)} runs: it edits the icon, and the changed slots are repainted. */
    @Nonnull
    public Icon render(@Nullable Consumer<Icon> renderer) {
        this.renderer = renderer;
        return this;
    }

    public long getEveryTicks() {
        return everyTicks;
    }

    @Nullable
    public Consumer<Icon> getRenderer() {
        return renderer;
    }

    /** Whether this icon redraws itself on a timer, which is what makes a view keep a copy of it. */
    public boolean isAnimated() {
        return everyTicks > 0 && renderer != null;
    }

    public void runRenderer() {
        if (renderer != null) {
            renderer.accept(this);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Editing the item - the same vocabulary as the factory, so a render function needs no second type
    // -----------------------------------------------------------------------------------------------------------------

    /** Runs the full item factory over this icon's stack and keeps the result. */
    @Nonnull
    public Icon edit(@Nonnull Consumer<FCItemBuilder> edit) {
        FCItemBuilder builder = FCItemFactory.from(itemStack);
        edit.accept(builder);
        setItemStack(builder.build());
        return this;
    }

    @Nonnull
    public Icon displayName(@Nonnull String displayName) {
        return edit(builder -> builder.displayName(displayName));
    }

    @Nonnull
    public Icon lore(@Nonnull String... lore) {
        return lore(Arrays.asList(lore));
    }

    @Nonnull
    public Icon lore(@Nonnull List<String> lore) {
        return edit(builder -> builder.lore(lore));
    }

    @Nonnull
    public Icon amount(int amount) {
        return edit(builder -> builder.amount(amount));
    }

    /** An independent copy: same stack, same permission, same handlers, no shared mutable state. */
    @Nonnull
    public Icon copy() {
        Icon copy = new Icon(itemStack);
        copy.permission = this.permission;
        copy.background = this.background;
        copy.onClick = this.onClick;
        copy.everyTicks = this.everyTicks;
        copy.renderer = this.renderer;
        return copy;
    }

    @Override
    public String toString() {
        return "Icon{" + itemStack.getType() + (permission.isEmpty() ? "" : ", permission=" + permission)
                + (background ? ", background" : "") + "}";
    }

}
