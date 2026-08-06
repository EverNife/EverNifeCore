package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import org.bukkit.entity.Player;

/**
 * A {@link GuiView} built straight onto a test's own surface and scheduler.
 *
 * <p>{@code GuiView} already takes both as constructor arguments - they are the seams the framework
 * was designed around - but only {@code GuiViews.open} calls that constructor, and it hands over the
 * Bukkit implementations. This lives in the same package so a test can hand over doubles instead,
 * which is what makes a whole screen drivable tick by tick with no server anywhere.</p>
 *
 * <p>The view produced is <b>not</b> registered in {@link GuiViews}: nothing routes a click event to
 * it, and the framework's own shutdown does not know about it. A test about clicks or about
 * {@code closeAll} opens through the real entry point instead.</p>
 */
public final class DetachedViews {

    private DetachedViews() {

    }

    /** Builds the view and runs the same first pass an open does: declare, draw, write. */
    public static GuiView open(Gui gui, Player viewer, GuiSurface surface, GuiScheduler scheduler) {
        GuiView view = new GuiView(gui, viewer, surface, scheduler, gui.getTitle());
        view.start();
        view.render();
        view.commitNow();
        return view;
    }

    /** Tears the view down exactly as a close event would. */
    public static void release(GuiView view, CloseReason reason) {
        view.release(reason);
    }

}
