package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.conformance.hytale.HyWorld;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.hytale.region.HySelectionItems;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.event.RegionLeaveEvent;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.EntryDenySettings;
import br.com.finalcraft.evernifecore.region.selection.SelectionSpec;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.region.session.RevertStrategy;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.region.visual.VisualStyle;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.component.system.ICancellableEcsEvent;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.ExplosionConfig;
import com.hypixel.hytale.server.core.entity.ExplosionUtils;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The region system on a live Hytale server, with the synthetic player of the run as the one who acts: the BREAK
 * System deciding only while a region needs it, a break by the player and by an NPC denied inside and free outside, a
 * real explosion kept out of a {@code deny-block-break} half, a {@code deny-entry} reverted, the wand closing a shape,
 * a denied spawn removed before it ticks, and the outline sent. Messages the player got are read from outside the
 * server (the driver reads {@code mc_player}); the cases name the texts it looks for.
 */
public final class HyRegionsSuite implements ConformanceSuite {

    public static final String ENTRY_MESSAGE = "ECC entry denied";
    public static final String BREAK_MESSAGE = "ECC break denied";
    /** How many times {@code deny-message-per-hit} hits the same block; the driver counts the messages it sent. */
    public static final int HITS = 5;
    private static final String SOLID = "Rock_Stone";
    private static final long TIMEOUT_SECONDS = 30;

    private final RegionManager regions;

    public HyRegionsSuite(RegionManager regions) {
        this.regions = regions;
    }

    @Override
    public String getId() {
        return "regions";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                withPlayer("gate-follows-need", ctx, this::gateFollowsNeed),
                withPlayer("break-by-npc", ctx, this::breakByNpc),
                withPlayer("explosion-keeps-inside", ctx, this::explosionKeepsInside),
                withPlayer("deny-entry-reverts", ctx, this::denyEntryReverts),
                withPlayer("expel-found-inside", ctx, this::expelFoundInside),
                withPlayer("deny-message-per-hit", ctx, this::denyMessagePerHit),
                withPlayer("wand-closes-a-shape", ctx, this::wandClosesAShape),
                withPlayer("denied-spawn-never-ticks", ctx, this::deniedSpawnNeverTicks),
                withPlayer("outline-is-sent", ctx, this::outlineIsSent));
    }

    /** A case that needs the run's player; skipped without one, failed past {@link #TIMEOUT_SECONDS}. */
    static ConformanceCase withPlayer(String id, ConformanceContext ctx, Function<FPlayer, CompletableFuture<Verdict>> body) {
        return ConformanceCase.later(id, () -> {
            FPlayer player = ctx.getPlayerOrNull();
            if (player == null) {
                return CompletableFuture.completedFuture(Verdict.skipped(ConformanceContext.NEEDS_A_PLAYER));
            }
            return body.apply(player).orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(failed -> Verdict.fail(Verdict.describe(failed instanceof java.util.concurrent.CompletionException
                            && failed.getCause() != null ? failed.getCause() : failed)));
        });
    }

    /** Creates a cuboid region with {@code flags}, runs {@code body}, and removes the region whatever happened. */
    static CompletableFuture<Verdict> inRegion(RegionManager regions, String name, String world, BlockPos a, BlockPos b, Consumer<Region> flags,
                                               Function<Region, CompletableFuture<Verdict>> body) {
        CreationResult created = regions.create(name, world, CuboidShape.of(a, b));
        Region region = created.getRegion();
        if (region == null) {
            return CompletableFuture.completedFuture(Verdict.fail(name + " refused: " + created.getRefusal()));
        }
        CompletableFuture<Verdict> answer;
        try {
            flags.accept(region);
            answer = body.apply(region);
        } catch (Throwable thrown) {
            answer = CompletableFuture.failedFuture(thrown);
        }
        return answer.whenComplete((verdict, failed) -> regions.remove(region));
    }

    static Vector3i block(Vector3d at, int dx, int dy, int dz) {
        return new Vector3i((int) Math.floor(at.x) + dx, (int) Math.floor(at.y) + dy, (int) Math.floor(at.z) + dz);
    }

    static BlockPos pos(Vector3i at) {
        return BlockPos.of(at.x, at.y, at.z);
    }

    private static BlockType typeAt(World world, Vector3i at) {
        int id = HyWorld.blockId(world, at.x, at.y, at.z);
        BlockType type = id < 0 ? null : BlockType.getAssetMap().getAsset(id);
        return type == null ? BlockType.EMPTY : type;
    }

    /** Fires {@code event} at {@code actor} as the game does and reads it after every System; on the world's thread. */
    private static boolean firedCancelled(Ref<EntityStore> actor, EcsEvent event) {
        actor.getStore().invoke(actor, event);
        return ((ICancellableEcsEvent) event).isCancelled();
    }

    private static boolean breakCancelled(Ref<EntityStore> actor, World world, Vector3i at) {
        return firedCancelled(actor, new BreakBlockEvent(null, at, typeAt(world, at)));
    }

    // ---------------------------------------------------------------------------------------------- cases --

    private CompletableFuture<Verdict> gateFollowsNeed(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i inside = block(at, 3, -1, 0);
            Vector3i outside = block(at, 20, -1, 0);
            return HyWorld.on(world, () -> new boolean[]{EventSourceUsage.get().isNeeded(EventSource.BREAK),
                    breakCancelled(player.getReference(), world, inside)}).thenCompose(before ->
                    inRegion(regions, "ecc-gate", world.getName(), pos(inside).add(-1, -1, -1), pos(inside).add(1, 1, 1),
                            region -> region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE),
                            region -> HyWorld.on(world, () -> {
                                boolean needed = EventSourceUsage.get().isNeeded(EventSource.BREAK);
                                boolean in = breakCancelled(player.getReference(), world, inside);
                                boolean out = breakCancelled(player.getReference(), world, outside);
                                return Verdict.of(!before[0] && !before[1] && needed && in && !out,
                                        "withoutRegion{needed=" + before[0] + " cancelled=" + before[1] + "} withRegion{needed=" + needed
                                                + " insideCancelled=" + in + " outsideCancelled=" + out + "}");
                            })));
        });
    }

    private CompletableFuture<Verdict> breakByNpc(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i inside = block(at, 3, -1, 0);
            Vector3i outside = block(at, 20, -1, 0);
            return inRegion(regions, "ecc-npc-break", world.getName(), pos(inside).add(-1, -1, -1), pos(inside).add(1, 1, 1),
                    region -> region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE),
                    region -> HyWorld.on(world, () -> {
                        Store<EntityStore> store = world.getEntityStore().getStore();
                        Ref<EntityStore> sheep = NPCPlugin.get().spawnNPC(store, "Sheep", null, new Vector3d(at.x + 2, at.y, at.z + 2),
                                new Rotation3f(0F, 0F, 0F)).first();
                        try {
                            boolean in = breakCancelled(sheep, world, inside);
                            boolean out = breakCancelled(sheep, world, outside);
                            return Verdict.of(in && !out, "npc=Sheep insideCancelled=" + in + " outsideCancelled=" + out);
                        } finally {
                            if (sheep.isValid()) {
                                store.removeEntity(sheep, RemoveReason.REMOVE);
                            }
                        }
                    }));
        });
    }

    /**
     * A 5x5x5 rock cube, hollow at its center, over the player's head, with {@code deny-block-break} on its west half;
     * the game's own generic explosion goes off in the hollow. Inside the region every block stays; outside some break.
     */
    private CompletableFuture<Verdict> explosionKeepsInside(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i c = block(at, 12, 12, 0);
            //the cube stands 12 blocks off: its chunk may still be unloaded, and an unloaded block neither sets nor reads
            return HyWorld.chunksLoaded(world, c.x - 2, c.z - 2, c.x + 2, c.z + 2).thenCompose(loaded -> HyWorld.on(world, () -> {
                for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                    HyWorld.setBlock(world, c.x + x, c.y + y, c.z + z, x == 0 && y == 0 && z == 0 ? BlockType.EMPTY_KEY : SOLID);
                }
                return null;
            })).thenCompose(built -> inRegion(regions, "ecc-blast", world.getName(), BlockPos.of(c.x - 2, c.y - 2, c.z - 2),
                    BlockPos.of(c.x - 1, c.y + 2, c.z + 2), region -> region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE),
                    region -> HyWorld.on(world, () -> {
                        ExplosionUtils.performExplosion(Damage.NULL_SOURCE, new Vector3d(c.x + 0.5, c.y + 0.5, c.z + 0.5),
                                new Rotation3f(0F, 0F, 0F), genericExplosion(), null, world.getEntityStore().getStore(),
                                world.getChunkStore().getStore());
                        return null;
                    }).thenCompose(blown -> HyWorld.ticks(world, 10)).thenCompose(waited -> HyWorld.on(world, () -> {
                        int west = 0, east = 0;
                        for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                            for (int x = -2; x <= -1; x++) west += HyWorld.blockId(world, c.x + x, c.y + y, c.z + z) > 0 ? 1 : 0;
                            for (int x = 1; x <= 2; x++) east += HyWorld.blockId(world, c.x + x, c.y + y, c.z + z) > 0 ? 1 : 0;
                        }
                        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                            HyWorld.setBlock(world, c.x + x, c.y + y, c.z + z, BlockType.EMPTY_KEY);
                        }
                        return Verdict.of(west == 50 && east < 50, "insideKept=" + west + "/50 outsideKept=" + east + "/50");
                    }))));
        });
    }

    /** The config of the game's generic explosion asset, which keeps it in a field with no getter. */
    private static ExplosionConfig genericExplosion() throws ReflectiveOperationException {
        Interaction asset = Interaction.getAssetMap().getAsset("Explode_Generic");
        Field config = asset.getClass().getDeclaredField("config");
        config.setAccessible(true);
        return (ExplosionConfig) config.get(asset);
    }

    private CompletableFuture<Verdict> denyEntryReverts(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(start -> {
            Vector3i center = block(start, 8, 0, 0);
            BlockPos min = pos(center).add(-2, -3, -2);
            BlockPos max = pos(center).add(2, 4, 2);
            return inRegion(regions, "ecc-entry", world.getName(), min, max, region -> {
                region.setFlag(CoreFlags.DENY_ENTRY, Tristate.TRUE);
                region.setFlag(CoreFlags.DENY_MESSAGE, ENTRY_MESSAGE);
            }, region -> {
                //the client answers every teleport; one still pending would keep the move System off the player
                boolean[] pendingAtStart = new boolean[1];
                return HyWorld.on(world, () -> pendingAtStart[0] = HyWorld.isTeleportPending(player))
                        .thenCompose(read -> HyWorld.ticks(world, 3))   //the move System seeds the player on the first tick MOVE is needed
                        .thenCompose(seeded -> HyWorld.on(world, () -> {
                            HyWorld.teleport(player, world, new Vector3d(center.x + 0.5, start.y, center.z + 0.5));
                            return null;
                        }))
                        .thenCompose(sent -> HyWorld.ticks(world, 3))
                        .thenCompose(landed -> HyWorld.on(world, () -> HyWorld.isTeleportPending(player)))
                        .thenCompose(pendingAfterMove -> HyWorld.ticks(world, 10).thenCompose(waited -> HyWorld.on(world, () -> {
                            Vector3d now = HyWorld.position(player);
                            boolean inside = region.getShape().contains(now.x, now.y, now.z);
                            return Verdict.of(!pendingAtStart[0] && !pendingAfterMove && !inside, "sentTo=" + center + " standsAt=" + fmt(now)
                                    + " insideAfter=" + inside + " start=" + fmt(start) + " pendingAtStart=" + pendingAtStart[0]
                                    + " pendingAfterMove=" + pendingAfterMove
                                    + " message='" + ENTRY_MESSAGE + "'");
                        })));
            });
        });
    }

    /**
     * A region that starts denying entry around the standing player, with {@code expelFoundInside}, sends them to its
     * fixed point; the player is put back where the case found them once the region is gone.
     */
    private CompletableFuture<Verdict> expelFoundInside(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(start -> {
            Vector3i here = block(start, 0, 0, 0);
            Vector3d target = new Vector3d(here.x + 8.5, start.y, here.z + 0.5);
            Region[] expelling = new Region[1];
            MoveCause[] cause = new MoveCause[1];
            ECEventSubscription<RegionLeaveEvent> leaves = ECEventBus.global().subscribe(RegionLeaveEvent.class, leave -> {
                if (leave.getRegion() == expelling[0]) {
                    cause[0] = leave.getCause();
                }
            });
            return inRegion(regions, "ecc-expel", world.getName(), pos(here).add(-2, -3, -2), pos(here).add(2, 4, 2), region -> {
                expelling[0] = region;
                EntryDenySettings settings = new EntryDenySettings();
                settings.setExpelFoundInside(true);
                settings.setRevert(RevertStrategy.fixed(WorldLocPos.of(target.x, target.y, target.z, world.getName())));
                region.setFlagSettings(CoreFlags.DENY_ENTRY, settings);
                region.setFlag(CoreFlags.DENY_ENTRY, Tristate.TRUE);
            }, region -> HyWorld.ticks(world, 30).thenCompose(waited -> HyWorld.on(world, () -> {
                Vector3d now = HyWorld.position(player);
                boolean inside = region.getShape().contains(now.x, now.y, now.z);
                boolean atTarget = Math.abs(now.x - target.x) < 1 && Math.abs(now.z - target.z) < 1;
                boolean pending = HyWorld.isTeleportPending(player);
                return Verdict.of(!inside && atTarget && !pending, "start=" + fmt(start) + " target=" + fmt(target) + " standsAt=" + fmt(now)
                        + " insideAfter=" + inside + " pendingAfter=" + pending + " leaveCause=" + cause[0]);
            }))).whenComplete((verdict, failed) -> leaves.unsubscribe()).thenCompose(verdict -> HyWorld.on(world, () -> {
                HyWorld.teleport(player, world, start);
                return verdict;
            })).thenCompose(verdict -> HyWorld.ticks(world, 10).thenApply(back -> verdict));
        });
    }

    /** {@link #HITS} hits on one denied block, a tick apart: each cancelled; the driver counts the deny messages sent. */
    private CompletableFuture<Verdict> denyMessagePerHit(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i target = block(at, -3, -1, 0);
            return inRegion(regions, "ecc-hits", world.getName(), pos(target).add(-1, -1, -1), pos(target).add(1, 1, 1), region -> {
                region.setFlag(CoreFlags.DENY_BLOCK_BREAK, Tristate.TRUE);
                region.setFlag(CoreFlags.DENY_MESSAGE, BREAK_MESSAGE);
            }, region -> {
                int[] cancelled = new int[1];
                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                for (int hit = 0; hit < HITS; hit++) {
                    chain = chain.thenCompose(previous -> HyWorld.on(world, () -> {
                        if (firedCancelled(player.getReference(), new DamageBlockEvent(null, target, typeAt(world, target), 1F, 1F))) {
                            cancelled[0]++;
                        }
                        return null;
                    })).thenCompose(hitDone -> HyWorld.ticks(world, 1));
                }
                return chain.thenApply(done -> Verdict.of(cancelled[0] == HITS, "hits=" + HITS + " cancelled=" + cancelled[0]
                        + " message='" + BREAK_MESSAGE + "'"));
            });
        });
    }

    /**
     * The wand the core hands out, read back from the player's inventory with its mark; two left clicks and a crouched
     * right click with it in hand close a cuboid, and every click is the selection's - cancelled, never a break.
     */
    private CompletableFuture<Verdict> wandClosesAShape(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        CompletableFuture<Shape> confirmed = new CompletableFuture<>();
        return HyWorld.on(world, () -> {
            Vector3d at = HyWorld.position(player);
            SelectionTool.start(fplayer, SelectionSpec.cuboid(), confirmed::complete);
            ItemStack wand = findWand((HytaleFPlayer) fplayer);
            if (wand == null) {
                SelectionTool.cancelAll();
                return Verdict.fail("no marked wand in the inventory after SelectionTool.start");
            }
            Ref<EntityStore> ref = player.getReference();
            Vector3i a = block(at, 2, -1, 2);
            Vector3i b = block(at, 4, 1, 4);
            boolean leftA = firedCancelled(ref, new DamageBlockEvent(wand, a, typeAt(world, a), 1F, 1F));
            boolean leftB = firedCancelled(ref, new BreakBlockEvent(wand, b, typeAt(world, b)));
            MovementStatesComponent states = ref.getStore().getComponent(ref, MovementStatesComponent.getComponentType());
            boolean crouched = states.getMovementStates().crouching;
            states.getMovementStates().crouching = true;
            InteractionContext context = InteractionContext.withoutEntity();
            context.setHeldItem(wand);
            boolean confirm;
            try {
                confirm = firedCancelled(ref, new UseBlockEvent.Pre(InteractionType.Secondary, context, b, typeAt(world, b)));
            } finally {
                states.getMovementStates().crouching = crouched;
            }
            Shape shape = confirmed.getNow(null);
            String bounds = shape instanceof CuboidShape cuboid ? cuboid.getMin() + ".." + cuboid.getMax() : String.valueOf(shape);
            boolean right = shape instanceof CuboidShape cuboid && cuboid.getMin().equals(pos(a)) && cuboid.getMax().equals(pos(b));
            if (shape == null) {
                SelectionTool.cancelAll();
            }
            return Verdict.of(leftA && leftB && confirm && right, "wand=" + wand.getItemId() + " marked=true leftCancelled=" + leftA + "/" + leftB
                    + " confirmCancelled=" + confirm + " shape=" + bounds);
        });
    }

    private static ItemStack findWand(HytaleFPlayer player) {
        for (int section : new int[]{InventoryComponent.HOTBAR_SECTION_ID, InventoryComponent.STORAGE_SECTION_ID}) {
            ItemContainer container = player.getInventorySection(section);
            if (container == null) continue;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack item = container.getItemStack(slot);
                if (HySelectionItems.isSelectionTool(item)) {
                    container.removeItemStackFromSlot(slot);
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * A sheep spawned inside {@code deny-animal-spawn} is gone before the next tick, one outside stays: the creature a
     * region denies is removed when it is added, so no tick ever shows it. The detail carries the ticks it was seen.
     */
    private CompletableFuture<Verdict> deniedSpawnNeverTicks(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i inside = block(at, -6, 0, 6);
            return inRegion(regions, "ecc-spawn", world.getName(), pos(inside).add(-2, -3, -2), pos(inside).add(2, 4, 2),
                    region -> region.setFlag(CoreFlags.DENY_ANIMAL_SPAWN, Tristate.TRUE),
                    region -> HyWorld.on(world, () -> {
                        Store<EntityStore> store = world.getEntityStore().getStore();
                        //null: the spawn call itself already found the creature gone
                        var denied = NPCPlugin.get().spawnNPC(store, "Sheep", null,
                                new Vector3d(inside.x + 0.5, at.y, inside.z + 0.5), new Rotation3f(0F, 0F, 0F));
                        var allowed = NPCPlugin.get().spawnNPC(store, "Sheep", null,
                                new Vector3d(at.x - 20, at.y, at.z), new Rotation3f(0F, 0F, 0F));
                        return new Object[]{denied == null ? null : denied.first(), allowed == null ? null : allowed.first()};
                    }).thenCompose(spawned -> {
                        @SuppressWarnings("unchecked") Ref<EntityStore> denied = (Ref<EntityStore>) spawned[0];
                        @SuppressWarnings("unchecked") Ref<EntityStore> allowed = (Ref<EntityStore>) spawned[1];
                        int[] ticksSeen = new int[1];
                        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                        for (int tick = 0; tick < 5; tick++) {
                            chain = chain.thenCompose(previous -> HyWorld.ticks(world, 1)).thenCompose(ticked -> HyWorld.on(world, () -> {
                                if (denied != null && denied.isValid()) ticksSeen[0]++;
                                return null;
                            }));
                        }
                        return chain.thenCompose(done -> HyWorld.on(world, () -> {
                            boolean allowedAlive = allowed != null && allowed.isValid();
                            Store<EntityStore> store = world.getEntityStore().getStore();
                            if (allowedAlive) store.removeEntity(allowed, RemoveReason.REMOVE);
                            if (denied != null && denied.isValid()) store.removeEntity(denied, RemoveReason.REMOVE);
                            return Verdict.of(ticksSeen[0] == 0 && allowedAlive, "removal-on-add: spawnReturnedNothing=" + (denied == null)
                                    + " ticksSeen=" + ticksSeen[0] + "/5 outsideAlive=" + allowedAlive);
                        }));
                    }));
        });
    }

    private CompletableFuture<Verdict> outlineIsSent(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> {
            Vector3d at = HyWorld.position(player);
            List<WorldLocPos> points = Arrays.asList(WorldLocPos.of(at.x + 2, at.y, at.z, world.getName()),
                    WorldLocPos.of(at.x + 2, at.y, at.z + 2, world.getName()), WorldLocPos.of(at.x, at.y, at.z + 2, world.getName()));
            EverNifeCore.getPlatform().getOutlineRenderer().render(fplayer, points, VisualStyle.OWN);
            return Verdict.pass("points=" + points.size() + " style=" + VisualStyle.OWN + " (the driver reads the particle packets)");
        });
    }

    static String fmt(Vector3d at) {
        return String.format("%.1f,%.1f,%.1f", at.x, at.y, at.z);
    }
}
