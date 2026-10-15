package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.conformance.hytale.HyWorld;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import com.hypixel.hytale.builtin.triggervolumes.EntityTargetType;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.effect.builtin.rules.NoBuildRule;
import com.hypixel.hytale.builtin.triggervolumes.effect.builtin.rules.NoDestroyRule;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.builtin.triggervolumes.shape.BoxShape;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * What the regions cost the server, measured on the world's own tick length (the work of a tick, without the sleep
 * that paces it): the move System with one player while a region needs MOVE, and the two ways of predicting a denial on
 * the client - ours (the server cancels, the client rolls back) and the game's native one (the regions published as
 * TriggerVolume rules, which the server ships to the client). Every case passes once measured; the numbers are the
 * evidence a decision is taken on.
 */
public final class HyCostSuite implements ConformanceSuite {

    /** Ticks per sample: the median of this many tick lengths. */
    public static final int TICKS = 200;
    public static final int REGIONS = 100;
    /** The native prediction is dropped past either: a publication slower than this, or a tick this much heavier. */
    public static final double MAX_PUBLISH_MS = 5.0;
    public static final double MAX_TICK_MS = 0.1;

    private final RegionManager regions;

    public HyCostSuite(RegionManager regions) {
        this.regions = regions;
    }

    @Override
    public String getId() {
        return "cost";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                HyRegionsSuite.withPlayer("move-system-per-tick", ctx, this::moveSystemPerTick),
                HyRegionsSuite.withPlayer("client-prediction", ctx, this::clientPrediction));
    }

    /** The median tick length while nothing changes, after the metrics are reset. */
    private static CompletableFuture<Double> sample(World world) {
        return HyWorld.on(world, () -> {
            world.clearMetrics();
            return null;
        }).thenCompose(cleared -> HyWorld.ticks(world, TICKS + 2)).thenCompose(done -> HyWorld.on(world, () -> HyWorld.medianTickMillis(world, TICKS)));
    }

    /** R3: one player ticking, with a region that needs MOVE far away from him and without it. */
    private CompletableFuture<Verdict> moveSystemPerTick(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return sample(world).thenCompose(idle -> {
            CreationResult created = regions.create("ecc-move-cost", world.getName(), CuboidShape.of(BlockPos.of(5000, 0, 5000), BlockPos.of(5010, 200, 5010)));
            Region region = created.getRegion();
            if (region == null) {
                return CompletableFuture.completedFuture(Verdict.fail("ecc-move-cost refused: " + created.getRefusal()));
            }
            region.setFlag(CoreFlags.DENY_ENTRY, Tristate.TRUE);
            return sample(world).whenComplete((v, t) -> regions.remove(region)).thenApply(moving -> Verdict.pass(String.format(
                    "players=1 medianTick idle=%.4fms withMoveNeeded=%.4fms delta=%.4fms ticks=%d", idle, moving, moving - idle, TICKS)));
        });
    }

    /**
     * D5: {@link #REGIONS} regions with {@code deny-block-break} + {@code deny-block-place} around the player, as our
     * regions and as native volumes with the game's NoDestroy/NoBuild rules: the publication time of each and the tick
     * it costs, each against the same idle tick. Native is kept only within {@link #MAX_PUBLISH_MS} and {@link #MAX_TICK_MS}.
     */
    private CompletableFuture<Verdict> clientPrediction(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> sample(world).thenCompose(idle -> {
            List<Region> ours = new ArrayList<>();
            double[] ownPublish = new double[1];
            return HyWorld.on(world, () -> {
                long start = System.nanoTime();
                for (int i = 0; i < REGIONS; i++) {
                    BlockPos min = cell(at, i);
                    Region region = regions.create("ecc-cost-" + i, world.getName(), CuboidShape.of(min, min.add(7, 15, 7))).getRegion();
                    region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);
                    region.setFlag(CoreFlags.DENY_BLOCK_PLACE, Tristate.TRUE);
                    ours.add(region);
                }
                ownPublish[0] = (System.nanoTime() - start) / 1_000_000.0;
                return null;
            }).thenCompose(created -> sample(world)).whenComplete((v, t) -> ours.forEach(regions::remove))
                    .thenCompose(ownTick -> {
                        Supplier<TriggerVolumeManager> manager = () -> world.getEntityStore().getStore()
                                .getResource(TriggerVolumesPlugin.get().getManagerResourceType());
                        double[] nativePublish = new double[1];
                        return HyWorld.on(world, () -> {
                            long start = System.nanoTime();
                            for (int i = 0; i < REGIONS; i++) {
                                BlockPos min = cell(at, i);
                                VolumeEntry volume = new VolumeEntry("ecc-cost-" + i, world.getName(), new Vector3d(min.getX(), min.getY(), min.getZ()),
                                        new BoxShape(new Vector3d(0, 0, 0), new Vector3d(8, 16, 8)), new ArrayList<>(),
                                        EnumSet.allOf(EntityTargetType.class), true);
                                volume.getRules().add(new NoDestroyRule());
                                volume.getRules().add(new NoBuildRule());
                                manager.get().register(volume.getId(), volume);
                            }
                            manager.get().markSpatialDirty();
                            nativePublish[0] = (System.nanoTime() - start) / 1_000_000.0;
                            return manager.get().getVolumes().size();
                        }).thenCompose(registered -> sample(world).whenComplete((v, t) -> world.execute(() -> {
                            for (int i = 0; i < REGIONS; i++) manager.get().unregister("ecc-cost-" + i);
                        })).thenApply(nativeTick -> {
                            boolean kept = nativePublish[0] <= MAX_PUBLISH_MS && nativeTick - idle <= MAX_TICK_MS;
                            return Verdict.pass(String.format("regions=%d idleTick=%.4fms own{publish=%.3fms tick=%.4fms delta=%.4fms}"
                                            + " native{volumes=%d publish=%.3fms tick=%.4fms delta=%.4fms} native=%s",
                                    REGIONS, idle, ownPublish[0], ownTick, ownTick - idle, registered, nativePublish[0], nativeTick,
                                    nativeTick - idle, kept ? "kept" : "dropped"));
                        }));
                    });
        }));
    }

    /** The i-th 8x8 cell of a 10x10 grid centered on the player, from 8 blocks under him. */
    private static BlockPos cell(Vector3d at, int i) {
        return BlockPos.of((int) Math.floor(at.x) - 40 + (i % 10) * 8, (int) Math.floor(at.y) - 8, (int) Math.floor(at.z) - 40 + (i / 10) * 8);
    }
}
