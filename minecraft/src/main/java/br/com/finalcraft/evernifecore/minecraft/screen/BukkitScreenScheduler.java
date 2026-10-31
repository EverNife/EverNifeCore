package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.minecraft.scheduler.McFCScheduler;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.screen.Cancellable;
import br.com.finalcraft.evernifecore.screen.ScreenScheduler;
import org.bukkit.scheduler.BukkitTask;

/** The chest renderer's clock: the server's main-thread scheduler, in ticks. */
public final class BukkitScreenScheduler implements ScreenScheduler {

    public static final BukkitScreenScheduler INSTANCE = new BukkitScreenScheduler();

    private BukkitScreenScheduler() {

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

    @Override
    public boolean isRenderThread() {
        return FCBukkitUtil.isMainThread();
    }

}
