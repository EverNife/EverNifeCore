package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;

import java.util.Arrays;
import java.util.List;

/** The flags EverNifeCore ships. They are also the reference a plugin copies when writing its own. */
public final class StockFlags {

    private StockFlags() {
    }

    /** Registers every stock flag under {@code owner}. */
    public static void registerAll(ECPluginData owner) {
        List<RegionFlag<?>> stock = Arrays.asList(
                new DenyPvpFlag(owner),
                new InvincibleFlag(owner),
                new DenyDamageAnimalsFlag(owner),
                new DenyBlockBreakFlag(owner),
                new DenyFakePlayerBreakFlag(owner),
                new DenyBlockPlaceFlag(owner),
                new DenyInteractFlag(owner),
                new DenyEntryFlag(owner),
                new DenyExitFlag(owner),
                new DenyTeleportInFlag(owner),
                new DenyTeleportOutFlag(owner),
                new DenyMonsterSpawnFlag(owner),
                new DenyAnimalSpawnFlag(owner),
                new DenyEntryWithMessageFlag(owner),
                new GreetingFlag(owner),
                new FarewellFlag(owner),
                new DenyMessageFlag(owner),
                new TeleportPointFlag(owner));
        for (RegionFlag<?> flag : stock) {
            FlagRegistry.get().register(flag);
        }
    }
}
