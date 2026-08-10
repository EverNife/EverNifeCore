package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.icons.DefaultIcons;
import br.com.finalcraft.evernifecore.minecraft.gui.icons.EnumStainedGlassPane;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.GuiLayout;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.IconData;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Layouts;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiGeometry;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.view.ClickContext;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * "Are you sure?", as a screen the whole server shares instead of one every plugin writes again.
 *
 * <pre>{@code
 * ctx.open(ConfirmGui.of("§eSell it for §6$500§e?"))
 *    .thenAccept(yes -> { if (yes) sell(); });
 * }</pre>
 *
 * <p>The answer comes back through the chain, so denying gives the player back the screen they came
 * from, on the page and with the filter they left it on. Walking away instead of answering cancels the
 * future, so nothing runs on an answer nobody gave.</p>
 *
 * <p>What it looks like is the admin's: {@code ConfirmLayout.yml} restyles the two buttons, the
 * background and the size, and the plugin keeps saying only what it is asking.</p>
 */
public final class ConfirmGui extends ResultGui<Boolean, ConfirmGui.ConfirmLayout> {

    @FCLocale(lang = LocaleType.EN_US, text = "§cThis one is serious - hold SHIFT and click to confirm.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cEssa é séria - segure SHIFT e clique para confirmar.")
    private static LocaleMessage HOLD_SHIFT_TO_CONFIRM;

    private boolean dangerous = false;

    private ConfirmGui(ConfirmLayout layout, String question) {
        super(layout);
        title(question);
        icon(l -> l.CONFIRM).onClick(this::onConfirm);
        icon(l -> l.DENY).onClick(context -> context.back(Boolean.FALSE));
    }

    /** Asks {@code question}, which is also what the window is titled. */
    @Nonnull
    public static ConfirmGui of(@Nonnull String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("A confirmation has to say what it is asking about: the "
                    + "question is the title of the window, and without it the player reads two buttons "
                    + "and no reason.");
        }
        return new ConfirmGui(Layouts.of(ConfirmLayout.class), question);
    }

    /**
     * Shows what the question is about, in the middle of the screen - wherever the middle is on the
     * window the admin's file asked for.
     */
    @Nonnull
    public ConfirmGui display(@Nullable ItemStack item) {
        if (item != null) {
            GuiGeometry geometry = getGeometry();
            icon(Slots.at(geometry.getRows() / 2 + 1, geometry.getWidth() / 2 + 1), Icon.of(item));
        }
        return this;
    }

    /** Makes confirming take a shift-click, for the answer that cannot be taken back. */
    @Nonnull
    public ConfirmGui dangerous() {
        this.dangerous = true;
        return this;
    }

    private void onConfirm(ClickContext context) {
        if (dangerous && !context.getClickType().isShiftClick()) {
            Player viewer = context.getViewer();
            if (viewer != null && HOLD_SHIFT_TO_CONFIRM != null) {
                HOLD_SHIFT_TO_CONFIRM.send(FCBukkitUtil.adapt(viewer));
            }
            return;
        }
        context.back(Boolean.TRUE);
    }

    /** What a confirmation looks like, and the file the admin restyles it in. */
    @GuiLayout(title = "§8Are you sure?", rows = 3)
    public static class ConfirmLayout extends LayoutBase {

        @IconData(slot = {11})
        public Icon CONFIRM = DefaultIcons.confirm();

        @IconData(slot = {15})
        public Icon DENY = DefaultIcons.deny();

        @IconData(slot = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14,
                16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26}, background = true)
        public Icon BACKGROUND = EnumStainedGlassPane.GRAY.asFactory().asIcon();

    }

}
