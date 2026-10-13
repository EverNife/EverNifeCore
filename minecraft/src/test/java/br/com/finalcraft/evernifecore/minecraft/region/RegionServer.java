package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A Bukkit server with regions on it: the event rig, one region owner, a fake EverNifeCore that owns the core flags
 * and the world {@code "world"} (-64..319). The flags a test registers through {@link #flag} leave with it, and so do
 * its regions.
 */
public final class RegionServer implements AutoCloseable {

    public static final String WORLD = "world";

    public final BukkitEventWorld server;
    public final RegionWorld regions;
    public final ECPluginData core;
    public final World world;
    private final List<Player> players = new ArrayList<>();

    private RegionServer(Path dataFolder) {
        server = BukkitEventWorld.install(dataFolder);
        regions = RegionWorld.open("RegionServerOwner", dataFolder.resolve("regions"));
        IECPluginExtractor serverExtractor = EverNifeCore.getProviders().getBaseProvider().provideOrNull(IECPluginExtractor.class);
        core = Plugins.fakePluginData("EverNifeCore", dataFolder.resolve("core").toFile());
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class, serverExtractor); //the server's plugin keeps answering
        world = server.world(WORLD, -64, 320);
    }

    public static RegionServer open(Path dataFolder) {
        return new RegionServer(dataFolder);
    }

    /** Registers {@code flag} for the length of this server, under the core or the region owner - whichever its key names. */
    public <T, F extends RegionFlag<T>> F flag(F flag) {
        FlagRegistry.get().register(flag.getKey().getOwner().equals(CoreFlags.OWNER) ? core : regions.getOwner(), flag);
        return flag;
    }

    /** A box in {@code "world"} with every flag in {@code denying} set to TRUE. */
    public Region box(String name, int x1, int y1, int z1, int x2, int y2, int z2, TristateFlag... denying) {
        Region region = regions.cuboid(name, WORLD, x1, y1, z1, x2, y2, z2);
        for (TristateFlag flag : denying) {
            region.setFlag(flag, Tristate.TRUE);
        }
        return region;
    }

    public void listen(ECListener listener) {
        server.registerECListener(listener);
    }

    /** Fires {@code event} through the plugin manager - priorities and {@code ignoreCancelled} included - and hands it back. */
    public <E extends Event> E call(E event) {
        server.getPluginManager().callEvent(event);
        return event;
    }

    public Location at(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    public Player player(String name, double x, double y, double z, String... permissions) {
        Player player = server.player(name, at(x, y, z), permissions);
        players.add(player);
        return player;
    }

    /** An entity of {@code type} standing at {@code at}; the builder takes whatever else the test needs it to answer. */
    public static <T extends Entity> Doubles.Builder<T> entity(Class<T> type, EntityType kind, Location at) {
        return Doubles.of(type)
                .returning("getType", kind)
                .on("getLocation", args -> at.clone())
                .returning("getWorld", at.getWorld());
    }

    @Override
    public void close() {
        try {
            //the tracker is global and keys sessions by uuid: a player of this test must not be inside anything in the next
            players.forEach(player -> RegionMovementTracker.get().onQuit(FCBukkitUtil.adapt(player)));
            regions.close();
            FlagRegistry.get().unregisterAll(regions.getOwner());
            FlagRegistry.get().unregisterAll(core);
            Plugins.forget(Plugins.CORE);
        } finally {
            server.close();
        }
    }
}
