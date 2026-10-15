package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.region.listener.RegionContexts;
import br.com.finalcraft.evernifecore.minecraft.region.listener.RegionListeners;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.event.RegionLeaveEvent;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.FluidContext;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.DenyFlag;
import br.com.finalcraft.evernifecore.region.flag.core.EntryDenySettings;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.region.session.RevertStrategy;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.region.usage.UsageWatch;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The region system on a live server: the core's flags registered under the real EverNifeCore, a plugin's own event
 * source putting its listener on the server only while a region needs it, a break inside a denying region cancelled,
 * a bot found inside a region that starts denying entry sent out, a real TNT held whole by {@code deny-explosion} and
 * cut at a {@code deny-block-break} border, the registry's own block and entity keys, outside water kept out by a FLUID
 * flag, and a region's {@code deny-message} and own flag settings read back after a restart. Every region a case
 * creates is removed by it, except the one the write half leaves for the read half.
 */
public final class RegionsSuite implements ConformanceSuite {

    private static final String TOKEN_FILE = "regions.token";
    private static final String PERSISTED = "ecc-persist";
    private static final String PERSISTED_MESSAGE = "ECC deny message";
    private static final Duration PERSISTED_INTERVAL = Duration.ofMillis(1234);
    private static final long TIMEOUT_MILLIS = 10_000L;
    /** The longest the probe waits for its source to go idle; the core's default idle time is minutes. */
    private static final Duration PROBE_IDLE_CAP = Duration.ofSeconds(1);
    /** How long past the idle wait the listener may still take to leave: the hop to the main thread. */
    private static final long OFF_GRACE_MILLIS = 2_000L;
    /** The two nodes {@code RegionLookup} reads as a bypass; the bot is OP, and OP holds every undeclared node. */
    private static final List<String> BYPASS_NODES = Arrays.asList("evernifecore.region.bypass.*",
            "evernifecore.region.bypass.evernifecore.deny-block-break");

    /** The two nodes {@code RegionLookup} reads as a bypass of {@code deny-entry}. */
    private static final List<String> ENTRY_BYPASS_NODES = Arrays.asList("evernifecore.region.bypass.*",
            "evernifecore.region.bypass.evernifecore.deny-entry");

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
                ConformanceCase.later("plugin-source-follows-need", this::pluginSourceFollowsNeed),
                PlayerCases.later("deny-break-is-cancelled", ctx, this::denyBreakIsCancelled),
                PlayerCases.later("expel-found-inside", ctx, this::expelFoundInside),
                PlayerCases.later("deny-explosion-keeps-every-block", ctx, this::denyExplosionKeepsEveryBlock),
                PlayerCases.later("explosion-on-a-border", ctx, this::explosionOnABorder),
                ConformanceCase.of("block-key-is-the-registrys", this::blockKeyIsTheRegistrys),
                PlayerCases.later("tnt-entity-key", ctx, this::tntEntityKey),
                PlayerCases.later("fluid-stays-out", ctx, this::fluidStaysOut));
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

    /**
     * The listener comes up with the need and leaves once the source has been idle. The idle wait is the core's own
     * ({@link EventSourceUsage#getIdleTime()}), capped at {@link #PROBE_IDLE_CAP} for the probe's one transition so the
     * case does not hold the run for minutes; the cap is put back as soon as the wait is scheduled.
     */
    private CompletableFuture<Verdict> pluginSourceFollowsNeed() {
        ECPluginData pd = regions.getOwner();
        EventSource probe = EventSource.register(pd, "probe-source");
        DenyFlag flag = new DenyFlag(FlagKey.of(pd, "deny-probe"), probe) {
        };
        FlagRegistry.get().register(pd, flag);
        ProbeListener listener = new ProbeListener();
        UsageWatch watch = RegionListeners.follow(pd, probe, listener, null);
        Runnable cleanup = () -> {
            watch.stop();
            listener.unregisterThis();
            FlagRegistry.get().unregister(flag);
            EventSource.unregisterAll(pd);
        };
        EventSourceUsage usage = EventSourceUsage.get();
        boolean offBefore = !isOn(listener) && !usage.isNeeded(probe);
        World world = Bukkit.getWorlds().get(0);
        CreationResult created = regions.create("ecc-probe", world.getName(), CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(1, 1, 1)));
        Region region = created.getRegion();
        if (region == null) {
            cleanup.run();
            return CompletableFuture.completedFuture(Verdict.fail("ecc-probe refused: " + created.getRefusal()));
        }
        region.setFlag(flag, Tristate.TRUE);
        boolean neededWhileHeld = usage.isNeeded(probe);
        boolean onWhileNeeded = isOn(listener);
        Duration configuredIdle = usage.getIdleTime();
        Duration idle = configuredIdle.compareTo(PROBE_IDLE_CAP) < 0 ? configuredIdle : PROBE_IDLE_CAP;
        usage.setIdleTime(idle);
        try {
            regions.remove(region);   //the unneeded transition schedules its idle wait here, with the idle time set now
        } finally {
            usage.setIdleTime(configuredIdle);
        }
        boolean unneededAfter = !usage.isNeeded(probe);
        String detail = "source=" + probe + " offBefore=" + offBefore + " neededWhileHeld=" + neededWhileHeld
                + " onWhileNeeded=" + onWhileNeeded + " unneededAfter=" + unneededAfter;
        boolean onSoFar = offBefore && neededWhileHeld && onWhileNeeded && unneededAfter;
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        long deadline = System.currentTimeMillis() + idle.toMillis() + OFF_GRACE_MILLIS;
        BukkitTask[] poll = new BukkitTask[1];
        //polls each tick: the listener leaves on the main thread once the idle wait has run out
        poll[0] = Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            boolean offAfter = !isOn(listener);
            if (!offAfter && System.currentTimeMillis() < deadline) {
                return;
            }
            poll[0].cancel();
            cleanup.run();
            answer.complete(Verdict.of(onSoFar && offAfter, detail + " offAfter=" + offAfter + " idle=" + idle.toMillis() + "ms"));
        }, 1L, 1L);
        return PlayerCases.within(answer, idle.toMillis() + OFF_GRACE_MILLIS + TIMEOUT_MILLIS);
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

    /**
     * A region that starts denying entry around the standing bot, with {@code expelFoundInside}, sends it to its fixed
     * point; the bot goes back where the case found it once the region is gone.
     */
    private CompletableFuture<Verdict> expelFoundInside(Player player) {
        Location start = player.getLocation();
        World world = player.getWorld();
        int x = start.getBlockX(), y = start.getBlockY(), z = start.getBlockZ();
        int tx = x + 12;
        Location target = new Location(world, tx + 0.5, world.getHighestBlockYAt(tx, z), z + 0.5);
        PermissionAttachment noBypass = player.addAttachment(plugin());
        ENTRY_BYPASS_NODES.forEach(node -> noBypass.setPermission(node, false));
        Region[] expelling = new Region[1];
        MoveCause[] cause = new MoveCause[1];
        ECEventSubscription<RegionLeaveEvent> leaves = ECEventBus.global().subscribe(RegionLeaveEvent.class, leave -> {
            if (leave.getRegion() == expelling[0]) {
                cause[0] = leave.getCause();
            }
        });
        CreationResult created = regions.create("ecc-expel", world.getName(), CuboidShape.of(BlockPos.of(x - 2, y - 2, z - 2), BlockPos.of(x + 2, y + 3, z + 2)));
        Region region = created.getRegion();
        if (region == null) {
            leaves.unsubscribe();
            player.removeAttachment(noBypass);
            return CompletableFuture.completedFuture(Verdict.fail("ecc-expel refused: " + created.getRefusal()));
        }
        expelling[0] = region;
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        try {
            EntryDenySettings settings = new EntryDenySettings();
            settings.setExpelFoundInside(true);
            settings.setRevert(RevertStrategy.fixed(WorldLocPos.of(target.getX(), target.getY(), target.getZ(), world.getName())));
            region.setFlagSettings(CoreFlags.DENY_ENTRY, settings);
            region.setFlag(CoreFlags.DENY_ENTRY, Tristate.TRUE);
        } catch (Throwable thrown) {
            answer.complete(Verdict.fail(Verdict.describe(thrown)));
        }
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            try {
                Location now = player.getLocation();
                boolean inside = region.getShape().contains(now.getX(), now.getY(), now.getZ());
                boolean atTarget = Math.abs(now.getX() - target.getX()) < 1 && Math.abs(now.getZ() - target.getZ()) < 1;
                answer.complete(Verdict.of(!inside && atTarget, "start=" + x + "," + y + "," + z + " target=" + tx + "," + target.getBlockY() + "," + z
                        + " standsAt=" + now.getBlockX() + "," + now.getBlockY() + "," + now.getBlockZ() + " insideAfter=" + inside + " leaveCause=" + cause[0]));
            } catch (Throwable thrown) {
                answer.complete(Verdict.fail(Verdict.describe(thrown)));
            } finally {
                leaves.unsubscribe();
                regions.remove(region);
                player.removeAttachment(noBypass);
                player.teleport(start);
            }
        }, 10L);
        return PlayerCases.within(answer, TIMEOUT_MILLIS);
    }

    /**
     * A 5x5x5 dirt cube, hollow at its center, high above the player (out of the blast's reach of them), with a region
     * over the cube or over its west half ({@code westHalfOnly}) holding {@code flag}; a TNT with a one-tick fuse goes off
     * in the hollow, and {@code verdict} reads the cube once the blast is over.
     */
    private CompletableFuture<Verdict> blast(Player player, String name, DenyFlag flag, boolean westHalfOnly, BlastVerdict verdict) {
        World world = player.getWorld();
        Location at = player.getLocation();
        int cx = at.getBlockX() + 20, cy = Math.min(at.getBlockY() + 30, world.getMaxHeight() - 10), cz = at.getBlockZ();
        CreationResult created = regions.create(name, world.getName(),
                CuboidShape.of(BlockPos.of(cx - 2, cy - 2, cz - 2), BlockPos.of(westHalfOnly ? cx - 1 : cx + 2, cy + 2, cz + 2)));
        Region region = created.getRegion();
        if (region == null) {
            return CompletableFuture.completedFuture(Verdict.fail(name + " refused: " + created.getRefusal()));
        }
        region.setFlag(flag, Tristate.TRUE);
        List<Block> cube = new ArrayList<>();
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int y = cy - 2; y <= cy + 2; y++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    boolean hollow = x == cx && y == cy && z == cz;
                    block.setType(hollow ? Material.AIR : Material.DIRT);
                    if (!hollow) {
                        cube.add(block);
                    }
                }
            }
        }
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        //a tick for the core's listener to come up with the need, then the TNT
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            try {
                TNTPrimed tnt = world.spawn(new Location(world, cx + 0.5, cy, cz + 0.5), TNTPrimed.class);
                tnt.setFuseTicks(1);
            } catch (Throwable thrown) {
                regions.remove(region);
                answer.complete(Verdict.fail(Verdict.describe(thrown)));
                return;
            }
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                try {
                    answer.complete(verdict.read(cube, cx));
                } catch (Throwable thrown) {
                    answer.complete(Verdict.fail(Verdict.describe(thrown)));
                } finally {
                    regions.remove(region);
                    cube.forEach(block -> block.setType(Material.AIR));
                }
            }, 20L);
        }, 2L);
        return PlayerCases.within(answer, TIMEOUT_MILLIS);
    }

    /** Reads the cube {@link #blast} left. */
    private interface BlastVerdict {
        Verdict read(List<Block> cube, int centerX);
    }

    private static int dirtLeft(List<Block> cube, boolean west, int centerX) {
        int left = 0;
        for (Block block : cube) {
            if ((block.getX() < centerX) == west && block.getType() == Material.DIRT) {
                left++;
            }
        }
        return left;
    }

    private static int count(List<Block> cube, boolean west, int centerX) {
        int count = 0;
        for (Block block : cube) {
            if ((block.getX() < centerX) == west) {
                count++;
            }
        }
        return count;
    }

    private CompletableFuture<Verdict> denyExplosionKeepsEveryBlock(Player player) {
        return blast(player, "ecc-boom-denied", CoreFlags.DENY_EXPLOSION, false, (cube, cx) -> {
            int left = 0;
            for (Block block : cube) {
                left += block.getType() == Material.DIRT ? 1 : 0;
            }
            return Verdict.of(left == cube.size(), "dirt left " + left + "/" + cube.size());
        });
    }

    /** The region holds the cube's west half (x < center): the blast keeps all of it and breaks into the east half. */
    private CompletableFuture<Verdict> explosionOnABorder(Player player) {
        return blast(player, "ecc-boom-border", CoreFlags.DENY_BLOCK_BREAK, true, (cube, cx) -> {
            int westLeft = dirtLeft(cube, true, cx), westAll = count(cube, true, cx);
            int eastLeft = dirtLeft(cube, false, cx), eastAll = count(cube, false, cx);
            return Verdict.of(westLeft == westAll && eastLeft < eastAll,
                    "inside (west) left " + westLeft + "/" + westAll + ", outside (east) left " + eastLeft + "/" + eastAll);
        });
    }

    /**
     * Block keys come from the block registry: a vanilla block is {@code minecraft:<name>}, redstone dust on the ground
     * is its block ({@code redstone_wire}, its item is {@code redstone}), and a block with metadata variants answers its
     * one block name before 1.13 (wool) and each flattened block after.
     */
    private Verdict blockKeyIsTheRegistrys() {
        boolean flattened = MCVersion.isHigherEquals(MCVersion.v1_13);
        String tnt = RegionContexts.key(Material.TNT).toString();
        String wire = RegionContexts.key(Material.REDSTONE_WIRE).toString();
        String wool = RegionContexts.key(Material.valueOf(flattened ? "RED_WOOL" : "WOOL")).toString();
        String expectedWool = flattened ? "minecraft:red_wool" : "minecraft:wool";
        return Verdict.of("minecraft:tnt".equals(tnt) && "minecraft:redstone_wire".equals(wire) && expectedWool.equals(wool),
                "tnt=" + tnt + " redstone_wire=" + wire + " wool=" + wool + " (expected " + expectedWool + ")");
    }

    /** A primed TNT's key as the entity registry names it: 1.7.10 writes {@code PrimedTnt} with no namespace. */
    private CompletableFuture<Verdict> tntEntityKey(Player player) {
        World world = player.getWorld();
        Location above = player.getLocation().add(0, 40, 0);
        TNTPrimed tnt = world.spawn(above, TNTPrimed.class);
        try {
            String key = RegionContexts.key(tnt).toString();
            String expected = MCVersion.isEqual(MCVersion.v1_7_10) ? "minecraft:PrimedTnt" : "minecraft:tnt";
            return CompletableFuture.completedFuture(Verdict.of(expected.equals(key), "key=" + key + " (expected " + expected + ")"));
        } finally {
            tnt.remove();
        }
    }

    /**
     * A FLUID flag that keeps outside liquids out - the shape of FinalProtection's {@code deny-fluid-flow}, which this
     * plugin cannot reach: water poured west of a region's border spreads on its floor and stops at the border.
     */
    private CompletableFuture<Verdict> fluidStaysOut(Player player) {
        ECPluginData pd = regions.getOwner();
        DenyFlag[] self = new DenyFlag[1];
        self[0] = new DenyFlag(FlagKey.of(pd, "deny-probe-flow"), EventSource.FLUID) {
            @Override
            public boolean applies(ActionContext context) {
                if (!(context instanceof FluidContext)) {
                    return false;
                }
                WorldLocPos from = ((FluidContext) context).getFrom();
                return RegionLookup.get().resolve(self[0], from.getWorldName(), from.getX(), from.getY(), from.getZ(), null) != Tristate.TRUE;
            }
        };
        DenyFlag denyFlow = self[0];
        FlagRegistry.get().register(pd, denyFlow);
        World world = player.getWorld();
        Location at = player.getLocation();
        int cx = at.getBlockX() - 20, cy = Math.min(at.getBlockY() + 30, world.getMaxHeight() - 10), cz = at.getBlockZ();
        CreationResult created = regions.create("ecc-pond", world.getName(), CuboidShape.of(BlockPos.of(cx, cy - 10, cz - 6), BlockPos.of(cx + 6, cy + 3, cz + 6)));
        Region region = created.getRegion();
        if (region == null) {
            FlagRegistry.get().unregister(denyFlow);
            return CompletableFuture.completedFuture(Verdict.fail("ecc-pond refused: " + created.getRefusal()));
        }
        region.setFlag(denyFlow, Tristate.TRUE);
        List<Block> floor = new ArrayList<>();
        for (int x = cx - 6; x <= cx + 6; x++) {
            for (int z = cz - 6; z <= cz + 6; z++) {
                Block block = world.getBlockAt(x, cy, z);
                block.setType(Material.STONE);
                floor.add(block);
                if (Math.abs(x - cx) == 6 || Math.abs(z - cz) == 6) {   //a rim, so no water falls off the floor
                    Block rim = world.getBlockAt(x, cy + 1, z);
                    rim.setType(Material.STONE);
                    floor.add(rim);
                }
            }
        }
        Runnable cleanup = () -> {
            for (int x = cx - 8; x <= cx + 8; x++) {
                for (int y = cy - 12; y <= cy + 2; y++) {
                    for (int z = cz - 8; z <= cz + 8; z++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.isLiquid() || floor.contains(block)) {
                            block.setType(Material.AIR);
                        }
                    }
                }
            }
            regions.remove(region);
            FlagRegistry.get().unregister(denyFlow);
        };
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        //a tick for the core's FLUID listener to come up with the need, then the water, then time to spread
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            world.getBlockAt(cx - 3, cy + 1, cz).setType(Material.WATER);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                try {
                    boolean flowedOutside = world.getBlockAt(cx - 1, cy + 1, cz).isLiquid();
                    int inside = 0;
                    for (int x = cx; x <= cx + 5; x++) {
                        for (int z = cz - 5; z <= cz + 5; z++) {
                            inside += world.getBlockAt(x, cy + 1, z).isLiquid() ? 1 : 0;
                        }
                    }
                    answer.complete(Verdict.of(flowedOutside && inside == 0, "flowedOutside=" + flowedOutside + " liquidInside=" + inside));
                } catch (Throwable thrown) {
                    answer.complete(Verdict.fail(Verdict.describe(thrown)));
                } finally {
                    cleanup.run();
                }
            }, 60L);
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
