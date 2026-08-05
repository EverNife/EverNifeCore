package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.model.Cancellable;
import br.com.finalcraft.evernifecore.minecraft.scheduler.McFCScheduler;
import org.bukkit.scheduler.BukkitTask;

/** The default {@link GuiScheduler}: the server's main-thread scheduler. */
public final class BukkitGuiScheduler implements GuiScheduler {

    public static final BukkitGuiScheduler INSTANCE = new BukkitGuiScheduler();

    private BukkitGuiScheduler() {

    }

    @Override
    public Cancellable later(long ticks, Runnable task) {
        BukkitTask handle = McFCScheduler.INSTANCE.scheduleSyncInTicks(task, ticks);
        return handle::cancel;
    }

    @Override
    public Cancellable repeat(long ticks, Runnable task) {
        BukkitTask handle = McFCScheduler.INSTANCE.repeatSyncInTicks(task, ticks, ticks);
        return handle::cancel;
    }

}
