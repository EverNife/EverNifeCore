package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import java.util.ArrayList;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.TickTimes;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.conformance.hytale.HyWorld;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.hytale.region.HySelectionItems;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.event.RegionTerrainEvent;
import br.com.finalcraft.evernifecore.region.event.RegionLeaveEvent;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.EntryDenySettings;
import br.com.finalcraft.evernifecore.region.flag.core.TrackChangesSettings;
import br.com.finalcraft.evernifecore.region.selection.SelectionSpec;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.region.session.RevertStrategy;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.PolygonShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.terrain.JournalPersistence;
import br.com.finalcraft.evernifecore.region.terrain.RegionTerrain;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.region.visual.VisualStyle;
import com.hypixel.hytale.component.Holder;
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
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
                withPlayer("outline-is-sent", ctx, this::outlineIsSent),
                withPlayer("deny-item-drop-inside-only", ctx, player -> itemDeniedInsideOnly(player, "ecc-item-drop", CoreFlags.DENY_ITEM_DROP,
                        () -> new DropItemEvent.Drop(new ItemStack(SOLID), 1F))),
                withPlayer("deny-item-pickup-inside-only", ctx, player -> itemDeniedInsideOnly(player, "ecc-item-pickup", CoreFlags.DENY_ITEM_PICKUP,
                        () -> new InteractivelyPickupItemEvent(new ItemStack(SOLID)))),
                withPlayer("terrain-journal-restores", ctx, this::terrainJournalRestores),
                withPlayer("terrain-prefab-restores", ctx, this::terrainPrefabRestores),
                withPlayer("terrain-journal-keeps-chest-items", ctx, this::terrainJournalKeepsChestItems),
                withPlayer("terrain-prefab-keeps-chest-items", ctx, this::terrainPrefabKeepsChestItems),
                withPlayer("terrain-prefab-without-nbt", ctx, this::terrainPrefabWithoutNbt),
                withPlayer("terrain-prefab-retention", ctx, this::terrainPrefabRetention),
                withPlayer("terrain-snapshot-polygon-box", ctx, this::terrainSnapshotPolygonBox),
                withPlayer("terrain-cost-grid", ctx, GRID_TIMEOUT_SECONDS, this::terrainCostGrid));
    }

    /** A case that needs the run's player; skipped without one, failed past {@link #TIMEOUT_SECONDS}. */
    static ConformanceCase withPlayer(String id, ConformanceContext ctx, Function<FPlayer, CompletableFuture<Verdict>> body) {
        return withPlayer(id, ctx, TIMEOUT_SECONDS, body);
    }

    /** {@link #withPlayer(String, ConformanceContext, Function)} failed past {@code timeoutSeconds} instead. */
    static ConformanceCase withPlayer(String id, ConformanceContext ctx, long timeoutSeconds, Function<FPlayer, CompletableFuture<Verdict>> body) {
        return ConformanceCase.later(id, () -> {
            FPlayer player = ctx.getPlayerOrNull();
            if (player == null) {
                return CompletableFuture.completedFuture(Verdict.skipped(ConformanceContext.NEEDS_A_PLAYER));
            }
            return body.apply(player).orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .exceptionally(failed -> Verdict.fail(Verdict.describe(failed instanceof java.util.concurrent.CompletionException
                            && failed.getCause() != null ? failed.getCause() : failed)));
        });
    }

    /** Creates a cuboid region with {@code flags}, runs {@code body}, and removes the region whatever happened. */
    static CompletableFuture<Verdict> inRegion(RegionManager regions, String name, String world, BlockPos a, BlockPos b, Consumer<Region> flags,
                                               Function<Region, CompletableFuture<Verdict>> body) {
        return inRegion(regions, name, world, CuboidShape.of(a, b), flags, body);
    }

    /** {@link #inRegion(RegionManager, String, String, BlockPos, BlockPos, Consumer, Function)} for any shape. */
    static CompletableFuture<Verdict> inRegion(RegionManager regions, String name, String world, Shape shape, Consumer<Region> flags,
                                               Function<Region, CompletableFuture<Verdict>> body) {
        CreationResult created = regions.create(name, world, shape);
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

    /**
     * The player drops or picks up an item (the game's own event, fired at the player): with {@code flag} on a region
     * 20 blocks off it goes through, with it on a region around the player it is cancelled.
     */
    private CompletableFuture<Verdict> itemDeniedInsideOnly(FPlayer fplayer, String name, RegionFlag<Tristate> flag, Supplier<EcsEvent> event) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i feet = block(at, 0, 0, 0);
            Vector3i away = block(at, 20, 0, 0);
            boolean[] outside = new boolean[1];
            return inRegion(regions, name + "-away", world.getName(), pos(away).add(-2, -2, -2), pos(away).add(2, 3, 2),
                    region -> region.setFlag(flag, Tristate.TRUE),
                    region -> HyWorld.on(world, () -> {
                        outside[0] = firedCancelled(player.getReference(), event.get());
                        return Verdict.pass("");
                    })).thenCompose(ignored -> inRegion(regions, name, world.getName(), pos(feet).add(-2, -2, -2), pos(feet).add(2, 3, 2),
                    region -> region.setFlag(flag, Tristate.TRUE),
                    region -> HyWorld.on(world, () -> {
                        boolean inside = firedCancelled(player.getReference(), event.get());
                        return Verdict.of(inside && !outside[0], "insideCancelled=" + inside + " outsideCancelled=" + outside[0]);
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

    /**
     * Three rock blocks in a {@code track-changes} region, each hit by a break the server's Systems hear and then
     * cleared as the break would; the journal, restored two blocks a tick, puts all three back.
     */
    private CompletableFuture<Verdict> terrainJournalRestores(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i c = block(at, 6, 4, 0);
            return HyWorld.chunksLoaded(world, c.x - 1, c.z - 1, c.x + 3, c.z + 1).thenCompose(loaded -> HyWorld.on(world, () -> {
                for (int x = 0; x < 3; x++) HyWorld.setBlock(world, c.x + x, c.y, c.z, SOLID);
                return null;
            })).thenCompose(built -> inRegion(regions, "ecc-journal", world.getName(), pos(c).add(-1, -1, -1), pos(c).add(3, 1, 1),
                    region -> region.getManager().getTerrain().track(region),
                    region -> HyWorld.on(world, () -> {
                        for (int x = 0; x < 3; x++) {
                            Vector3i at3 = new Vector3i(c.x + x, c.y, c.z);
                            breakCancelled(player.getReference(), world, at3);
                            HyWorld.setBlock(world, at3.x, at3.y, at3.z, BlockType.EMPTY_KEY);
                        }
                        return region.getManager().getTerrain().getJournalSize(region);
                    }).thenCompose(recorded -> region.getManager().getTerrain().restoreJournal(region, 2)
                            .thenCompose(restored -> HyWorld.on(world, () -> {
                                int back = 0;
                                for (int x = 0; x < 3; x++) {
                                    back += SOLID.equals(BlockType.getAssetMap().getAsset(HyWorld.blockId(world, c.x + x, c.y, c.z)).getId()) ? 1 : 0;
                                    HyWorld.setBlock(world, c.x + x, c.y, c.z, BlockType.EMPTY_KEY);
                                }
                                return Verdict.of(recorded == 3 && back == 3, "recorded=" + recorded + " restored=" + restored + " back=" + back + "/3");
                            })))));
        });
    }

    /** A hollow 3x3x3 rock cube saved as a prefab, cleared, and put back ten blocks a tick from the prefab file. */
    private CompletableFuture<Verdict> terrainPrefabRestores(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i c = block(at, 6, 8, 0);
            int[] entitiesTaken = new int[1];
            return HyWorld.chunksLoaded(world, c.x - 1, c.z - 1, c.x + 1, c.z + 1).thenCompose(loaded -> HyWorld.on(world, () -> {
                cube(world, c, SOLID);
                NPCPlugin.get().spawnNPC(world.getEntityStore().getStore(), "Sheep", null, new Vector3d(c.x + 0.5, c.y, c.z + 0.5),
                        new Rotation3f(0F, 0F, 0F));
                entitiesTaken[0] = copyableIn(world, c).size();
                return null;
            })).thenCompose(built -> inRegion(regions, "ecc-prefab", world.getName(), pos(c).add(-1, -1, -1), pos(c).add(1, 1, 1),
                    region -> { },
                    region -> region.getManager().getTerrain().snapshot(region)
                            .thenCompose(file -> HyWorld.on(world, () -> {
                                cube(world, c, BlockType.EMPTY_KEY);
                                copyableIn(world, c).forEach(entity -> entity.getStore().removeEntity(entity, RemoveReason.REMOVE));
                                return file;
                            }))
                            .thenCompose(file -> region.getManager().getTerrain().restoreSnapshot(region, 0, 10)
                                    .thenCompose(placed -> HyWorld.on(world, () -> {
                                        int back = 0;
                                        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                                            int id = HyWorld.blockId(world, c.x + x, c.y + y, c.z + z);
                                            back += id > 0 && SOLID.equals(BlockType.getAssetMap().getAsset(id).getId()) ? 1 : 0;
                                        }
                                        List<Ref<EntityStore>> entitiesBack = copyableIn(world, c);
                                        entitiesBack.forEach(entity -> entity.getStore().removeEntity(entity, RemoveReason.REMOVE));
                                        cube(world, c, BlockType.EMPTY_KEY);
                                        return Verdict.of(back == 26 && file.getName().endsWith(".prefab.json") && entitiesTaken[0] == 1
                                                        && entitiesBack.size() == 1, "file=" + file.getName() + " back=" + back + "/26 entities="
                                                + entitiesBack.size() + "/" + entitiesTaken[0] + " (a Sheep)");
                                    })))));
        });
    }

    /** How many items the chest cases put in their chest. */
    private static final int CHEST_ITEMS = 7;

    /**
     * A chest with {@link #CHEST_ITEMS} items in a {@code track-changes} region, broken by the player as the server's
     * Systems hear it and then cleared; the journal puts the chest back with its items.
     */
    private CompletableFuture<Verdict> terrainJournalKeepsChestItems(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i c = block(at, 6, 4, 3);
            String[] chest = new String[1];
            return placeChest(world, c, chest).thenCompose(put -> inRegion(regions, "ecc-journal-chest", world.getName(),
                    pos(c).add(-1, -1, -1), pos(c).add(1, 1, 1), region -> region.getManager().getTerrain().track(region),
                    region -> HyWorld.on(world, () -> {
                        breakCancelled(player.getReference(), world, c);
                        HyWorld.setBlock(world, c.x, c.y, c.z, BlockType.EMPTY_KEY);
                        return chestItems(world, c);
                    }).thenCompose(cleared -> region.getManager().getTerrain().restoreJournal(region, 1)
                            .thenCompose(restored -> HyWorld.ticks(world, 2))
                            .thenCompose(waited -> HyWorld.on(world, () -> chestVerdict(world, c, chest[0], put, cleared))))));
        });
    }

    /** The same chest saved as a prefab, cleared, and put back from the prefab file with its items. */
    private CompletableFuture<Verdict> terrainPrefabKeepsChestItems(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i c = block(at, 6, 8, 3);
            String[] chest = new String[1];
            return placeChest(world, c, chest).thenCompose(put -> inRegion(regions, "ecc-prefab-chest", world.getName(),
                    pos(c).add(-1, -1, -1), pos(c).add(1, 1, 1), region -> { },
                    region -> region.getManager().getTerrain().snapshot(region)
                            .thenCompose(file -> HyWorld.on(world, () -> {
                                HyWorld.setBlock(world, c.x, c.y, c.z, BlockType.EMPTY_KEY);
                                return chestItems(world, c);
                            }))
                            .thenCompose(cleared -> region.getManager().getTerrain().restoreSnapshot(region, 0, 10)
                                    .thenCompose(placed -> HyWorld.ticks(world, 2))
                                    .thenCompose(waited -> HyWorld.on(world, () -> chestVerdict(world, c, chest[0], put, cleared))))));
        });
    }

    /** A chest saved as a prefab with nbt off, cleared, and put back: the chest returns without its items. */
    private CompletableFuture<Verdict> terrainPrefabWithoutNbt(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i c = block(at, 6, 16, 3);
            String[] chest = new String[1];
            return placeChest(world, c, chest).thenCompose(put -> inRegion(regions, "ecc-prefab-chest-no-nbt", world.getName(),
                    pos(c).add(-1, -1, -1), pos(c).add(1, 1, 1), region -> { },
                    region -> region.getManager().getTerrain().snapshot(region, TerrainContent.of(true, false, true))
                            .thenCompose(file -> HyWorld.on(world, () -> {
                                HyWorld.setBlock(world, c.x, c.y, c.z, BlockType.EMPTY_KEY);
                                return chestItems(world, c);
                            }))
                            .thenCompose(cleared -> region.getManager().getTerrain().restoreSnapshot(region, 0, 10)
                                    .thenCompose(placed -> HyWorld.ticks(world, 2))
                                    .thenCompose(waited -> HyWorld.on(world, () -> {
                                        int back = chestItems(world, c);
                                        boolean chestBack = chest[0].equals(BlockType.getAssetMap().getAsset(HyWorld.blockId(world, c.x, c.y, c.z)).getId());
                                        HyWorld.setBlock(world, c.x, c.y, c.z, BlockType.EMPTY_KEY);
                                        return Verdict.of(put == CHEST_ITEMS && cleared == -1 && chestBack && back == 0, "chest=" + chest[0]
                                                + " itemsPut=" + put + " afterClear=" + cleared + " chestBack=" + chestBack + " itemsBack=" + back
                                                + " (nbt off: the chest comes back empty)");
                                    })))));
        });
    }

    /** Reads the chest back at {@code c}, clears it, and passes when it holds every item it was given. */
    private static Verdict chestVerdict(World world, Vector3i c, String chest, int put, int afterClear) {
        int back = chestItems(world, c);
        boolean chestBack = chest.equals(BlockType.getAssetMap().getAsset(HyWorld.blockId(world, c.x, c.y, c.z)).getId());
        HyWorld.setBlock(world, c.x, c.y, c.z, BlockType.EMPTY_KEY);
        return Verdict.of(put == CHEST_ITEMS && afterClear == -1 && chestBack && back == put, "chest=" + chest + " itemsPut=" + put
                + " afterClear=" + afterClear + " chestBack=" + chestBack + " itemsBack=" + back + "/" + put);
    }

    /** Three snapshots of one region with a retention of 2: the two newest stay, the oldest and its side files go. */
    private CompletableFuture<Verdict> terrainPrefabRetention(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i c = block(at, 6, 12, 3);
            List<File> taken = new ArrayList<>();
            return HyWorld.chunksLoaded(world, c.x - 1, c.z - 1, c.x + 1, c.z + 1).thenCompose(loaded ->
                    inRegion(regions, "ecc-prefab-retention", world.getName(), pos(c), pos(c).add(1, 1, 1), region -> { }, region -> {
                        int retention = region.getManager().getTerrain().getSnapshotRetention();
                        region.getManager().getTerrain().setSnapshotRetention(2);
                        return region.getManager().getTerrain().snapshot(region).thenCompose(first -> {
                            taken.add(first);
                            return region.getManager().getTerrain().snapshot(region);
                        }).thenCompose(second -> {
                            taken.add(second);
                            return region.getManager().getTerrain().snapshot(region);
                        }).thenApply(third -> {
                            taken.add(third);
                            List<File> kept = region.getManager().getTerrain().getSnapshots(region);
                            File oldest = taken.get(0);
                            boolean oldestGone = !oldest.exists() && !new File(oldest.getPath() + ".box").exists()
                                    && !new File(oldest.getPath() + ".version").exists();
                            boolean newestKept = kept.size() == 2 && kept.get(0).equals(taken.get(2)) && kept.get(1).equals(taken.get(1));
                            return Verdict.of(oldestGone && newestKept, "taken=3 kept=" + kept.size() + " newestTwoKept=" + newestKept
                                    + " oldestAndSideFilesGone=" + oldestGone);
                        }).whenComplete((verdict, failed) -> region.getManager().getTerrain().setSnapshotRetention(retention));
                    }));
        });
    }

    /**
     * A right-triangle region (legs of 5, two layers) whose bounding box is all rock; the prefab is taken, the box emptied,
     * and the prefab put back: every block of the triangle is rock again and none outside it - the prefab holds the box,
     * the restore only the shape.
     */
    private CompletableFuture<Verdict> terrainSnapshotPolygonBox(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i c = block(at, 10, 4, -6);
            PolygonShape triangle = PolygonShape.of(Arrays.asList(pos(c), pos(c).add(4, 0, 0), pos(c).add(0, 0, 4)), c.y, c.y + 1);
            return HyWorld.chunksLoaded(world, c.x, c.z, c.x + 4, c.z + 4).thenCompose(loaded -> HyWorld.on(world, () -> {
                box(world, c, SOLID);
                return null;
            })).thenCompose(built -> inRegion(regions, "ecc-prefab-polygon", world.getName(), triangle, region -> { },
                    region -> region.getManager().getTerrain().snapshot(region)
                            .thenCompose(file -> HyWorld.on(world, () -> {
                                box(world, c, BlockType.EMPTY_KEY);
                                return file;
                            }))
                            .thenCompose(file -> region.getManager().getTerrain().restoreSnapshot(region, 0, 10))
                            .thenCompose(placed -> HyWorld.on(world, () -> {
                                int inside = 0, insideBack = 0, outside = 0, outsideAltered = 0;
                                for (int x = 0; x <= 4; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 4; z++) {
                                    int id = HyWorld.blockId(world, c.x + x, c.y + y, c.z + z);
                                    boolean back = id > 0 && SOLID.equals(BlockType.getAssetMap().getAsset(id).getId());
                                    if (triangle.contains(c.x + x, c.y + y, c.z + z)) {
                                        inside++;
                                        insideBack += back ? 1 : 0;
                                    } else {
                                        outside++;
                                        outsideAltered += back ? 1 : 0;
                                    }
                                }
                                box(world, c, BlockType.EMPTY_KEY);
                                return Verdict.of(insideBack == inside && outsideAltered == 0, "box=50 insideBack=" + insideBack + "/" + inside + " outsideAltered="
                                        + outsideAltered + "/" + outside + " (blocks outside the region the restore changed)");
                            }))));
        });
    }

    /** The 5x2x5 box from {@code c} filled with {@code block}. */
    private static void box(World world, Vector3i c, String block) {
        for (int x = 0; x <= 4; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 4; z++) {
            HyWorld.setBlock(world, c.x + x, c.y + y, c.z + z, block);
        }
    }

    /** The boxes of the cost grid: {side, blocks restored per tick}, the same budget for the journal and the prefab. */
    private static final int[][] GRID = {{16, 100}, {64, 1000}};
    private static final long GRID_TIMEOUT_SECONDS = 600;
    /** Long enough for the core's delayed disk write of a journal (one second after its first change) to finish. */
    private static final int JOURNAL_WRITE_TICKS = 90;

    /**
     * The cost grid: a 16^3 box restored 100 blocks a tick and a 64^3 box restored 1 000 a tick, each through the journal
     * with 10% and with 100% of it changed, then through a prefab. The changes are journaled through
     * {@link RegionTerrain#recordChange}, the path every terrain System takes; each restore tick is timed by the core
     * itself ({@link RegionTerrainEvent.Type#RESTORE_TICK}), the same on every platform, and the world's own tick
     * metric is read beside it. Passes when every block comes back; the milliseconds are the report.
     */
    private CompletableFuture<Verdict> terrainCostGrid(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            List<String> lines = new ArrayList<>();
            boolean[] allBack = {true};
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (int[] box : GRID) {
                chain = chain.thenCompose(previous -> gridBox(world, at, box[0], box[1]).thenAccept(verdict -> {
                    allBack[0] &= verdict.isOk();
                    lines.add(verdict.getDetail());
                }));
            }
            return chain.thenApply(done -> Verdict.of(allBack[0], String.join(" || ", lines)));
        });
    }

    /** One box of the grid, its region tracked on disk so the journal's bytes can be read; emptied whatever happens. */
    private CompletableFuture<Verdict> gridBox(World world, Vector3d at, int side, int budget) {
        int y = Math.min((int) Math.floor(at.y) + 30, RegionGridOptions.HYTALE.getMaxHeight() - side - 5);
        //the small box stands east of the big one, so each box's area is its own
        int x = side == GRID[1][0] ? (int) Math.floor(at.x) - side / 2 : (int) Math.floor(at.x) + GRID[1][0];
        Vector3i min = new Vector3i(x, y, (int) Math.floor(at.z) - side / 2);
        int volume = side * side * side;
        return everyChunkLoaded(world, min.x, min.z, min.x + side - 1, min.z + side - 1).thenCompose(loaded -> HyWorld.on(world, () -> {
            fill(world, min, side, volume, SOLID);
            return null;
        })).thenCompose(built -> inRegion(regions, "ecc-grid-" + side, world.getName(), pos(min), pos(min).add(side - 1, side - 1, side - 1),
                region -> region.getManager().getTerrain().track(region, new TrackChangesSettings().setPersistence(JournalPersistence.DISK)),
                region -> gridJournal(world, region, min, side, volume / 10, budget)
                        .thenCompose(tenth -> gridJournal(world, region, min, side, volume, budget)
                                .thenCompose(all -> gridPrefab(world, region, min, side, budget)
                                        .thenApply(prefab -> Verdict.of(tenth.isOk() && all.isOk() && prefab.isOk(), "box=" + side + "^3(" + volume
                                                + ") B=" + budget + "/tick journal10%{" + tenth.getDetail() + "} journal100%{" + all.getDetail()
                                                + "} prefab{" + prefab.getDetail() + "}"))))))
                .whenComplete((verdict, failed) -> HyWorld.on(world, () -> {
                    fill(world, min, side, volume, BlockType.EMPTY_KEY);
                    return null;
                }));
    }

    /** Journals the first {@code changes} positions, empties them, writes the journal to disk, and restores it. */
    private CompletableFuture<Verdict> gridJournal(World world, Region region, Vector3i min, int side, int changes, int budget) {
        String name = world.getName();
        File journal = new File(regions.getOwner().getMetaInfo().getDataFolder(), "regions/terrain/" + region.getId().getName() + "/journal.json");
        return HyWorld.on(world, () -> {
            long start = System.nanoTime();
            forEach(min, side, changes, (x, y, z) -> RegionTerrain.recordChange(name, x, y, z,
                    content -> EverNifeCore.getPlatform().getTerrainAccess().captureBlock(name, x, y, z, content)));
            double recordMillis = (System.nanoTime() - start) / 1_000_000.0;
            fill(world, min, side, changes, BlockType.EMPTY_KEY);
            return recordMillis;
        }).thenCompose(recordMillis -> HyWorld.ticks(world, JOURNAL_WRITE_TICKS).thenApply(written -> {
            //the core writes a disk journal a second after its first change, off the world's thread: that write is over now
            region.getManager().getTerrain().writeDirty();
            return String.format(Locale.ROOT, "changes=%d record=%.1fms (%.4fms/change) disk=%dB", changes, recordMillis, recordMillis / changes,
                    journal.length());
        })).thenCompose(recorded -> timedRestore(world, region, true, () -> region.getManager().getTerrain().restoreJournal(region, budget))
                .thenCompose(restore -> HyWorld.on(world, () -> {
                    int back = solid(world, min, side, changes);
                    return Verdict.of(back == changes, recorded + " restore{" + restore + "} back=" + back + "/" + changes);
                })));
    }

    /** Saves the box as a prefab, empties it, and puts the prefab back. */
    private CompletableFuture<Verdict> gridPrefab(World world, Region region, Vector3i min, int side, int budget) {
        int volume = side * side * side;
        TickTimes write = new TickTimes();
        ECEventSubscription<RegionTerrainEvent> written = ECEventBus.global().subscribe(RegionTerrainEvent.class, event -> {
            if (event.getRegion() == region && event.getType() == RegionTerrainEvent.Type.SNAPSHOT) write.add(event.getNanos());
        });
        return region.getManager().getTerrain().snapshot(region).whenComplete((file, failed) -> written.unsubscribe())
                .thenCompose(file -> HyWorld.on(world, () -> {
                    long sideBytes = 0;
                    String stamp = file.getName().substring(0, file.getName().indexOf('.') + 1);
                    File[] siblings = file.getParentFile().listFiles((dir, sibling) -> sibling.startsWith(stamp) && !sibling.equals(file.getName()));
                    for (File sibling : siblings == null ? new File[0] : siblings) sideBytes += sibling.length();
                    fill(world, min, side, volume, BlockType.EMPTY_KEY);
                    return String.format(Locale.ROOT, "write=%.1fms file=%dB side=%dB", write.getWorkMillis(), file.length(), sideBytes);
                }))
                .thenCompose(saved -> timedRestore(world, region, false, () -> region.getManager().getTerrain().restoreSnapshot(region, 0, budget))
                        .thenCompose(restore -> HyWorld.on(world, () -> {
                            int back = solid(world, min, side, volume);
                            return Verdict.of(back == volume, saved + " restore{" + restore + "} back=" + back + "/" + volume);
                        })));
    }

    /**
     * Runs a restore, timing each of its ticks through the core's {@link RegionTerrainEvent.Type#RESTORE_TICK}, and
     * the world's ticks it spanned with the median tick the world measured itself, idle before and while restoring.
     */
    private static CompletableFuture<String> timedRestore(World world, Region region, boolean journal, Supplier<CompletableFuture<Integer>> restore) {
        TickTimes times = new TickTimes();
        ECEventSubscription<RegionTerrainEvent> ticks = ECEventBus.global().subscribe(RegionTerrainEvent.class, event -> {
            if (event.getRegion() == region && event.getType() == RegionTerrainEvent.Type.RESTORE_TICK && event.isJournal() == journal) {
                times.add(event.getNanos());
            }
        });
        double idle = HyWorld.medianTickMillis(world, 30);
        long firstTick = world.getTick();
        long start = System.nanoTime();
        return restore.get().whenComplete((count, failed) -> ticks.unsubscribe()).thenApply(count -> {
            long serverTicks = world.getTick() - firstTick;
            double restoring = HyWorld.medianTickMillis(world, (int) Math.max(1, Math.min(serverTicks, Integer.MAX_VALUE)));
            return times.describe((System.nanoTime() - start) / 1_000_000.0) + " serverTicks=" + serverTicks
                    + String.format(Locale.ROOT, " world{tickMedian idle=%.3fms restoring=%.3fms}", idle, restoring);
        });
    }

    private interface BlockVisitor {
        void visit(int x, int y, int z);
    }

    /** The first {@code count} positions of the box from {@code min}, x slab by x slab. */
    private static void forEach(Vector3i min, int side, int count, BlockVisitor visitor) {
        for (int i = 0; i < count; i++) {
            visitor.visit(min.x + i / (side * side), min.y + i / side % side, min.z + i % side);
        }
    }

    private static void fill(World world, Vector3i min, int side, int count, String block) {
        forEach(min, side, count, (x, y, z) -> HyWorld.setBlock(world, x, y, z, block));
    }

    /** How many of the first {@code count} positions hold {@link #SOLID}. */
    private static int solid(World world, Vector3i min, int side, int count) {
        int[] solid = {0};
        int id = BlockType.getAssetMap().getIndex(SOLID);
        forEach(min, side, count, (x, y, z) -> solid[0] += HyWorld.blockId(world, x, y, z) == id ? 1 : 0);
        return solid[0];
    }

    /** {@link HyWorld#chunksLoaded} for every chunk the area touches, not just its corners. */
    private static CompletableFuture<Void> everyChunkLoaded(World world, int minX, int minZ, int maxX, int maxZ) {
        List<CompletableFuture<Void>> loads = new ArrayList<>();
        for (int x = minX; x <= maxX + 31; x += 32) {
            for (int z = minZ; z <= maxZ + 31; z += 32) {
                int cx = Math.min(x, maxX), cz = Math.min(z, maxZ);
                loads.add(HyWorld.chunksLoaded(world, cx, cz, cx, cz));
            }
        }
        return CompletableFuture.allOf(loads.toArray(new CompletableFuture<?>[0]));
    }

    /**
     * Puts the first chest the server knows at {@code c} and {@link #CHEST_ITEMS} rock in it; completes with how many
     * the chest holds afterwards, {@code chest[0]} naming the block used.
     */
    private static CompletableFuture<Integer> placeChest(World world, Vector3i c, String[] chest) {
        return HyWorld.chunksLoaded(world, c.x - 1, c.z - 1, c.x + 1, c.z + 1).thenCompose(loaded -> HyWorld.on(world, () -> {
            chest[0] = chestType();
            HyWorld.setBlock(world, c.x, c.y, c.z, chest[0]);
            return null;
        })).thenCompose(placed -> HyWorld.ticks(world, 2)).thenCompose(waited -> HyWorld.on(world, () -> {
            ItemContainerBlock container = chestAt(world, c);
            if (container == null || container.getItemContainer() == null) {
                throw new IllegalStateException(chest[0] + " at " + c + " has no item container after placing it");
            }
            container.getItemContainer().addItemStack(new ItemStack(SOLID, CHEST_ITEMS));
            return chestItems(world, c);
        }));
    }

    /** The block whose default block entity holds items, a chest by name when there is one. */
    private static String chestType() {
        String found = null;
        for (BlockType type : BlockType.getAssetMap().getAssetMap().values()) {
            Holder<ChunkStore> entity = type.getBlockEntity();
            if (entity == null || entity.getComponent(ItemContainerBlock.getComponentType()) == null) continue;
            if (type.getId().contains("Chest")) return type.getId();
            if (found == null) found = type.getId();
        }
        if (found == null) {
            throw new IllegalStateException("no block type on this server has an item container");
        }
        return found;
    }

    /** The item container of the block at {@code c}, live or still pending in its section; null when there is none. */
    private static ItemContainerBlock chestAt(World world, Vector3i c) {
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(c.x, c.y, c.z);
        BlockComponentSection components = section == null ? null : section.getStore().getComponent(section, BlockComponentSection.getComponentType());
        if (components == null) return null;
        int index = ChunkUtil.indexBlock(c.x, c.y, c.z);
        Ref<ChunkStore> live = components.getBlockReference(index);
        if (live != null) return live.getStore().getComponent(live, ItemContainerBlock.getComponentType());
        Holder<ChunkStore> pending = components.getBlockHolder(index);
        return pending == null ? null : pending.getComponent(ItemContainerBlock.getComponentType());
    }

    /** How many items the chest at {@code c} holds; -1 when no chest stands there. */
    private static int chestItems(World world, Vector3i c) {
        ItemContainerBlock container = chestAt(world, c);
        if (container == null || container.getItemContainer() == null) return -1;
        ItemContainer items = container.getItemContainer();
        int count = 0;
        for (short slot = 0; slot < items.getCapacity(); slot++) {
            ItemStack stack = items.getItemStack(slot);
            count += stack == null ? 0 : stack.getQuantity();
        }
        return count;
    }

    /** The entities a prefab of the 3x3x3 box around {@code c} would copy, as the server collects them. */
    private static List<Ref<EntityStore>> copyableIn(World world, Vector3i c) {
        List<Ref<EntityStore>> found = new ArrayList<>();
        BuilderToolsPlugin.forEachCopyableInSelection(world, c.x - 1, c.y - 1, c.z - 1, 3, 3, 3, found::add);
        return found;
    }

    /** A 3x3x3 cube of {@code block} around {@code c}, its center left empty. */
    private static void cube(World world, Vector3i c, String block) {
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            HyWorld.setBlock(world, c.x + x, c.y + y, c.z + z, x == 0 && y == 0 && z == 0 ? BlockType.EMPTY_KEY : block);
        }
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
     * A sheep spawned inside {@code deny-spawn-animal} is gone before the next tick, one outside stays: the creature a
     * region denies is removed when it is added, so no tick ever shows it. The detail carries the ticks it was seen.
     */
    private CompletableFuture<Verdict> deniedSpawnNeverTicks(FPlayer fplayer) {
        PlayerRef player = HyWorld.player(fplayer);
        World world = HyWorld.worldOf(player);
        return HyWorld.on(world, () -> HyWorld.position(player)).thenCompose(at -> {
            Vector3i inside = block(at, -6, 0, 6);
            return inRegion(regions, "ecc-spawn", world.getName(), pos(inside).add(-2, -3, -2), pos(inside).add(2, 4, 2),
                    region -> region.setFlag(CoreFlags.DENY_SPAWN_ANIMAL, Tristate.TRUE),
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
