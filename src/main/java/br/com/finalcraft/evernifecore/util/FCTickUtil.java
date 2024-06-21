package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.atomic.AtomicLong;

public class FCTickUtil {

    private static AtomicLong tickCount = new AtomicLong(1);

    static {
        new BukkitRunnable(){
            @Override
            public void run() {
                tickCount.incrementAndGet();
            }
        }.runTaskLaterAsynchronously(EverNifeCore.instance, 1);
    }

    public static long getTickCount(){
        return tickCount.get();
    }

}
