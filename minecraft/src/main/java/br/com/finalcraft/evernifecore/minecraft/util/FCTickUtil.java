package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
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
        }.runTaskTimerAsynchronously(EverNifeCoreBukkitPlugin.instance, 1, 1);
    }

    public static long getTickCount(){
        return tickCount.get();
    }

}
