package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link GuiView} built straight onto a test's own surface and scheduler.
 *
 * <p>{@code GuiView} already takes both as constructor arguments - they are the seams the framework
 * was designed around - but only {@code GuiViews.open} calls that constructor, and it hands over the
 * Bukkit implementations. This lives in the same package so a test can hand over doubles instead,
 * which is what makes a whole screen drivable tick by tick with no server anywhere.</p>
 *
 * <p>{@link #open} leaves the view out of {@link GuiViews}, so nothing routes an event to it and the
 * framework's own shutdown does not know about it. {@link #register} puts it in, which is what a test
 * that wants both a hand-driven clock and real clicks needs.</p>
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

    /**
     * Puts {@code view} where the listener looks for the screen {@code viewer} has open. The
     * framework's own open is the only other thing that does this, and it always builds a Bukkit
     * container - so a click over any other {@link GuiSurface} has to be registered here.
     */
    public static void register(Player viewer, GuiView view) {
        openViews().put(viewer.getUniqueId(), view);
    }

    /** Tears the view down exactly as a close event would. */
    public static void release(GuiView view, CloseReason reason) {
        view.release(reason);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, GuiView> openViews() {
        try {
            Field field = GuiViews.class.getDeclaredField("OPEN");
            field.setAccessible(true);
            return (Map<UUID, GuiView>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("GuiViews no longer keeps the open views in a static field "
                    + "named OPEN, so a detached view cannot be made reachable by the listener.", e);
        }
    }

}
