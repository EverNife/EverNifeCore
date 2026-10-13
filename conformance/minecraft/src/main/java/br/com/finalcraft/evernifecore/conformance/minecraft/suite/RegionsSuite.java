package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.region.listener.RegionListeners;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.DenyFlag;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.region.usage.UsageWatch;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The region system on a live server: the core's flags registered under the real EverNifeCore, a plugin's own event
 * source putting its listener on the server only while a region needs it, a break inside a denying region cancelled,
 * and a region's {@code deny-message} and own flag settings read back after a restart. Every region a case creates is
 * removed by it, except the one the write half leaves for the read half.
 */
public final class RegionsSuite implements ConformanceSuite {

    private static final String TOKEN_FILE = "regions.token";
    private static final String PERSISTED = "ecc-persist";
    private static final String PERSISTED_MESSAGE = "ECC deny message";
    private static final Duration PERSISTED_INTERVAL = Duration.ofMillis(1234);
    private static final long TIMEOUT_MILLIS = 10_000L;
    /** The two nodes {@code RegionLookup} reads as a bypass; the bot is OP, and OP holds every undeclared node. */
    private static final List<String> BYPASS_NODES = Arrays.asList("evernifecore.region.bypass.*",
            "evernifecore.region.bypass.evernifecore.deny-block-break");

    private final RegionManager regions;
    private final CompletableFuture<Void> loaded;

    /**
     * @param regions the plugin's own manager, opened on enable
     * @param loaded  that manager's {@code load()}, which the read half waits on
     */
    public RegionsSuite(RegionManager regions, CompletableFuture<Void> loaded) {
        this.regions = regions;
        this.loaded = loaded;
    }

    @Override
    public String getId() {
        return "regions";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("core-flags-registered", this::coreFlagsRegistered),
                ConformanceCase.of("plugin-source-follows-need", this::pluginSourceFollowsNeed),
                PlayerCases.later("deny-break-is-cancelled", ctx, this::denyBreakIsCancelled));
    }

    @Override
    public List<ConformanceCase> getWriteCases(ConformanceContext ctx) {
        return Collections.singletonList(ConformanceCase.later("write", () -> write(ctx)));
    }

    @Override
    public List<ConformanceCase> getReadCases(ConformanceContext ctx) {
        return Collections.singletonList(ConformanceCase.later("read", () -> read(ctx)));
    }

    private Verdict coreFlagsRegistered() {
        List<RegionFlag<?>> all = CoreFlags.all();
        long registered = all.stream().filter(RegionFlag::isRegistered).count();
        boolean sameInstance = FlagRegistry.get().find("evernifecore:deny-pvp").orElse(null) == CoreFlags.DENY_PVP;
        ECPluginData owner = CoreFlags.DENY_PVP.getOwner();
        String ownerName = owner == null ? null : owner.getMetaInfo().getName();
        return Verdict.of(registered == all.size() && sameInstance && "EverNifeCore".equals(ownerName),
                "registered=" + registered + "/" + all.size() + " sameInstance=" + sameInstance + " owner=" + ownerName);
    }

    private Verdict pluginSourceFollowsNeed() {
        ECPluginData pd = regions.getOwner();
        EventSource probe = EventSource.register(pd, "probe-source");
        DenyFlag flag = new DenyFlag(FlagKey.of(pd, "deny-probe"), probe) {
        };
        FlagRegistry.get().register(pd, flag);
        ProbeListener listener = new ProbeListener();
        UsageWatch watch = RegionListeners.follow(pd, probe, listener, null);
        Region region = null;
        try {
            boolean offBefore = !isOn(listener) && !EventSourceUsage.get().isNeeded(probe);
            World world = Bukkit.getWorlds().get(0);
            CreationResult created = regions.create("ecc-probe", world.getName(), CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(1, 1, 1)));
            region = created.getRegion();
            if (region == null) {
                return Verdict.fail("ecc-probe refused: " + created.getRefusal());
            }
            region.setFlag(flag, Tristate.TRUE);
            boolean neededWhileHeld = EventSourceUsage.get().isNeeded(probe);
            boolean onWhileNeeded = isOn(listener);
            regions.remove(region);
            region = null;
            boolean unneededAfter = !EventSourceUsage.get().isNeeded(probe);   //the listener itself leaves after the idle time
            return Verdict.of(offBefore && neededWhileHeld && onWhileNeeded && unneededAfter, "source=" + probe + " offBefore=" + offBefore
                    + " neededWhileHeld=" + neededWhileHeld + " onWhileNeeded=" + onWhileNeeded + " unneededAfter=" + unneededAfter);
        } finally {
            if (region != null) {
                regions.remove(region);
            }
            watch.stop();
            listener.unregisterThis();
            FlagRegistry.get().unregister(flag);
            EventSource.unregisterAll(pd);
        }
    }

    private CompletableFuture<Verdict> denyBreakIsCancelled(Player player) {
        Location at = player.getLocation();
        World world = player.getWorld();
        int x = at.getBlockX(), y = at.getBlockY(), z = at.getBlockZ();
        CreationResult created = regions.create("ecc-break", world.getName(), CuboidShape.of(BlockPos.of(x - 2, y - 2, z - 2), BlockPos.of(x + 2, y + 2, z + 2)));
        Region region = created.getRegion();
        if (region == null) {
            return CompletableFuture.completedFuture(Verdict.fail("ecc-break refused: " + created.getRefusal()));
        }
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        //a tick later: the core's BREAK listener comes up through the main thread once the region needs it
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            PermissionAttachment noBypass = player.addAttachment(plugin());
            BYPASS_NODES.forEach(node -> noBypass.setPermission(node, false));
            try {
                boolean insideCancelled = callBreak(world.getBlockAt(x + 1, y - 1, z + 1), player);
                boolean outsideCancelled = callBreak(world.getBlockAt(x + 10, y - 1, z), player);
                answer.complete(Verdict.of(insideCancelled && !outsideCancelled,
                        "insideCancelled=" + insideCancelled + " outsideCancelled=" + outsideCancelled));
            } catch (Throwable thrown) {
                answer.complete(Verdict.fail(Verdict.describe(thrown)));
            } finally {
                player.removeAttachment(noBypass);
                regions.remove(region);
            }
        }, 2L);
        return PlayerCases.within(answer, TIMEOUT_MILLIS);
    }

    private static boolean callBreak(Block block, Player player) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    private CompletableFuture<Verdict> write(ConformanceContext ctx) {
        Region old = regions.get(PERSISTED);
        if (old != null) {
            regions.remove(old);
        }
        World world = Bukkit.getWorlds().get(0);
        CreationResult created = regions.create(PERSISTED, world.getName(), CuboidShape.of(BlockPos.of(100, 0, 100), BlockPos.of(104, 4, 104)));
        Region region = created.getRegion();
        if (region == null) {
            return CompletableFuture.completedFuture(Verdict.fail(PERSISTED + " refused: " + created.getRefusal()));
        }
        region.setFlag(CoreFlags.DENY_MESSAGE, PERSISTED_MESSAGE);
        region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);
        region.editFlagSettings(CoreFlags.DENY_BLOCK_BREAK, (FlagSettings settings) -> settings.setTriggerInterval(PERSISTED_INTERVAL));
        CompletableFuture<Verdict> answer = regions.flush().thenApply(flushed -> {
            try {
                Files.write(tokenFile(ctx).toPath(), region.getId().toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return Verdict.fail("flushed, but the token file failed: " + Verdict.describe(e));
            }
            return Verdict.pass("flushed " + region.getId() + " deny-message='" + PERSISTED_MESSAGE + "' triggerInterval=" + PERSISTED_INTERVAL.toMillis() + "ms");
        }).exceptionally(thrown -> Verdict.fail(Verdict.describe(thrown)));
        return PlayerCases.within(answer, TIMEOUT_MILLIS);
    }

    private CompletableFuture<Verdict> read(ConformanceContext ctx) throws Exception {
        File file = tokenFile(ctx);
        if (!file.isFile()) {
            return CompletableFuture.completedFuture(Verdict.fail("no " + TOKEN_FILE + " - the write half never ran here"));
        }
        String expected = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        CompletableFuture<Verdict> answer = loaded.thenApply(done -> {
            Region region = regions.get(PERSISTED);
            if (region == null) {
                return Verdict.fail(expected + " is not in the index after the restart");
            }
            String message = region.getFlag(CoreFlags.DENY_MESSAGE);
            FlagSettings own = region.getFlagSettings(CoreFlags.DENY_BLOCK_BREAK);
            Long intervalMillis = own == null ? null : own.getTriggerInterval().toMillis();
            boolean ok = expected.equals(region.getId().toString()) && PERSISTED_MESSAGE.equals(message)
                    && Long.valueOf(PERSISTED_INTERVAL.toMillis()).equals(intervalMillis);
            regions.remove(region);
            return Verdict.of(ok, "read " + region.getId() + " deny-message='" + message + "' ownTriggerInterval=" + intervalMillis
                    + "ms (expected " + expected + ", '" + PERSISTED_MESSAGE + "', " + PERSISTED_INTERVAL.toMillis() + "ms)");
        }).exceptionally(thrown -> Verdict.fail(Verdict.describe(thrown)));
        return PlayerCases.within(answer, TIMEOUT_MILLIS);
    }

    private static boolean isOn(ECListener listener) {
        for (RegisteredListener registered : PlayerInteractEvent.getHandlerList().getRegisteredListeners()) {
            if (registered.getListener() == listener) {
                return true;
            }
        }
        return false;
    }

    private static Plugin plugin() {
        return Bukkit.getPluginManager().getPlugin("ECoreConformance");
    }

    private static File tokenFile(ConformanceContext ctx) {
        return new File(ctx.getDataFolder(), TOKEN_FILE);
    }

    /** Hears what the probe source stands for; its presence on the handler list is all the case reads. */
    public static final class ProbeListener implements ECListener {

        @Override
        public boolean silentRegistration() {
            return true;
        }

        @EventHandler
        public void onInteract(PlayerInteractEvent event) {
        }
    }
}
