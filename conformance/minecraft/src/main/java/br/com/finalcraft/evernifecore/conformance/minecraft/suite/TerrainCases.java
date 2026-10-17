package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.region.terrain.McTerrainAccess;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.minecraft.worldedit.WEPlatform;
import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.minecraft.worldedit.region.IFCCuboidRegion;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.PolygonShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.terrain.RegionTerrain;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.math.transform.AffineTransform;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The terrain side of the region system on a live server: a {@code track-changes} journal fed by the real Bukkit events
 * and put back, a snapshot written and restored through the server's WorldEdit, snapshot retention, WorldEdit's own
 * rotated paste and undo through the core's wrappers, and what a journal restore costs a tick. The events are fired
 * through the plugin manager the way the game fires them, and the case then applies the change itself, as the game
 * would after nobody cancelled it. Every case puts its blocks and entities back and removes its region.
 */
final class TerrainCases {

    private static final long TIMEOUT_MILLIS = 20_000L;
    private static final long COST_TIMEOUT_MILLIS = 90_000L;
    private static final int CHEST_DIAMONDS = 3;
    /** Positions the cost case journals: a 100x1x100 slab. */
    private static final int COST_SIDE = 100;
    /** The restore budget of the cost case: the 10 000 positions go back over 100 ticks, the window the tick average spans. */
    private static final int COST_BLOCKS_PER_TICK = 100;
    /** Paper's average tick time covers the last 100 ticks. */
    private static final int COST_WINDOW_TICKS = 100;
    private static final double COST_LIMIT_MILLIS = 5.0;

    private final RegionManager regions;

    TerrainCases(RegionManager regions) {
        this.regions = regions;
    }

    List<ConformanceCase> cases(ConformanceContext ctx) {
        return Arrays.asList(
                PlayerCases.later("terrain-journal-restores", ctx, this::journalRestores),
                PlayerCases.later("terrain-journal-keeps-chest-items", ctx, this::journalKeepsChestItems),
                PlayerCases.later("terrain-journal-entity-back", ctx, this::journalEntityBack),
                PlayerCases.later("terrain-snapshot-restores", ctx, player -> snapshotRestores(player, "ecc-snapshot", 48, TerrainContent.ALL)),
                PlayerCases.later("terrain-snapshot-without-nbt", ctx, player -> snapshotRestores(player, "ecc-snapshot-bare", 54,
                        TerrainContent.of(true, false, true))),
                PlayerCases.later("terrain-snapshot-entities-back", ctx, this::snapshotEntitiesBack),
                PlayerCases.later("terrain-snapshot-polygon-box", ctx, this::snapshotPolygonBox),
                PlayerCases.later("terrain-snapshot-retention", ctx, this::snapshotRetention),
                PlayerCases.later("worldedit-rotated-paste-undo", ctx, this::rotatedPasteUndo),
                PlayerCases.later("terrain-journal-restore-cost", ctx, this::journalRestoreCost));
    }

    // ---------------------------------------------------------------------------------------------- journal --

    /** A stone broken and a dirt placed in a tracked box, through the real events; one block a tick puts every position back. */
    private CompletableFuture<Verdict> journalRestores(Player player) {
        World world = player.getWorld();
        Block c = anchor(player, 30);
        Block stone = c, placed = c.getRelative(1, 0, 0);
        stone.setType(Material.STONE);
        placed.setType(Material.AIR);
        BlockPos min = pos(c).add(-1, -1, -1), max = pos(c).add(2, 1, 1);
        Map<Block, String> before = states(world, min, max);
        return tracked("ecc-journal", world, min, max, TIMEOUT_MILLIS, region -> {
            boolean broke = breakAsTheGame(stone, player);
            boolean put = placeAsTheGame(placed, Material.DIRT, stone, player);
            int recorded = terrain(region).getJournalSize(region);
            return terrain(region).restoreJournal(region, 1).thenCompose(restored -> ticks(1)).thenApply(read -> {
                int back = unchanged(before);
                return Verdict.of(broke && put && recorded == 2 && back == before.size(), "break=" + broke + " place=" + put
                        + " recorded=" + recorded + " back=" + back + "/" + before.size());
            });
        }, () -> {
            stone.setType(Material.AIR);
            placed.setType(Material.AIR);
        });
    }

    /** A chest with diamonds broken in a tracked box comes back with them. */
    private CompletableFuture<Verdict> journalKeepsChestItems(Player player) {
        World world = player.getWorld();
        Block chest = anchor(player, 36);
        chest.setType(Material.CHEST);
        inventory(chest).addItem(new ItemStack(Material.DIAMOND, CHEST_DIAMONDS));
        return tracked("ecc-journal-chest", world, pos(chest).add(-1, -1, -1), pos(chest).add(1, 1, 1), TIMEOUT_MILLIS, region -> {
            boolean broke = breakChestAsTheGame(chest, player);
            int recorded = terrain(region).getJournalSize(region);
            return terrain(region).restoreJournal(region, 1).thenCompose(restored -> ticks(1)).thenApply(read -> {
                boolean isChest = chest.getType() == Material.CHEST;
                int diamonds = isChest ? diamonds(chest) : 0;
                return Verdict.of(broke && recorded == 1 && isChest && diamonds == CHEST_DIAMONDS, "break=" + broke + " recorded="
                        + recorded + " chestBack=" + isChest + " diamonds=" + diamonds + "/" + CHEST_DIAMONDS);
            });
        }, () -> clearChest(chest));
    }

    /**
     * A non-living entity a player breaks in a tracked box comes back with the journal: an armor stand through its death
     * event from 1.8 on, an item frame through {@link HangingBreakEvent} on 1.7.10, which has no armor stand.
     */
    private CompletableFuture<Verdict> journalEntityBack(Player player) {
        World world = player.getWorld();
        Block c = anchor(player, 42);
        boolean frame = MCVersion.isEqual(MCVersion.v1_7_10);
        Block wall = c;
        wall.setType(Material.STONE);
        //1.7.10 hangs a frame on the block it is spawned at, out on a free side; the stand stands on the stone
        Entity entity = frame ? world.spawn(wall.getLocation(), ItemFrame.class)
                : world.spawnEntity(c.getRelative(0, 1, 0).getLocation().add(0.5, 0, 0.5), EntityType.valueOf("ARMOR_STAND"));
        EntityType type = entity.getType();
        Location at = entity.getLocation();
        return tracked("ecc-journal-entity", world, pos(c).add(-1, -1, -1), pos(c).add(1, 2, 2), TIMEOUT_MILLIS, region -> {
            if (frame) {
                Bukkit.getPluginManager().callEvent(new HangingBreakEvent((ItemFrame) entity, HangingBreakEvent.RemoveCause.ENTITY));
            } else {
                Bukkit.getPluginManager().callEvent(deathEvent((LivingEntity) entity));
            }
            entity.remove();
            return ticks(2).thenCompose(gone -> {
                int before = alive(world, type, at).size();
                return terrain(region).restoreJournal(region, 1).thenCompose(restored -> ticks(2)).thenApply(read -> {
                    int back = alive(world, type, at).size();
                    return Verdict.of(before == 0 && back == 1, "entity=" + type + " via=" + (frame ? "HangingBreakEvent" : "EntityDeathEvent")
                            + " aliveAfterBreak=" + before + " back=" + back + "/1");
                });
            });
        }, () -> {
            entity.remove();
            alive(world, type, at).forEach(Entity::remove);
            wall.setType(Material.AIR);
        });
    }

    // -------------------------------------------------------------------------------------------- snapshots --

    /**
     * A stone 3x3x3 box with a chest of diamonds at its heart, saved with {@code content}, wrecked, and put back ten blocks
     * a tick from the snapshot: every block comes back, and the diamonds come back only when the snapshot kept nbt and the
     * server's WorldEdit carries tile entities ({@link #worldEditCarriesTileEntities()}).
     */
    private CompletableFuture<Verdict> snapshotRestores(Player player, String name, int dx, TerrainContent content) {
        World world = player.getWorld();
        Block c = anchor(player, dx);
        BlockPos min = pos(c).add(-1, -1, -1), max = pos(c).add(1, 1, 1);
        List<Block> box = blocks(world, min, max);
        box.forEach(block -> block.setType(block.equals(c) ? Material.CHEST : Material.STONE));
        inventory(c).addItem(new ItemStack(Material.DIAMOND, CHEST_DIAMONDS));
        int expectedDiamonds = content.isNbt() && worldEditCarriesTileEntities() ? CHEST_DIAMONDS : 0;
        return inRegion(name, world, min, max, TIMEOUT_MILLIS, region -> {}, region -> terrain(region).snapshot(region, content).thenCompose(file -> {
            clearChest(c);
            box.forEach(block -> block.setType(Material.AIR));
            return terrain(region).restoreSnapshot(region, 0, 10).thenCompose(placed -> ticks(1)).thenApply(read -> {
                int stone = 0;
                for (Block block : box) {
                    stone += block.getType() == Material.STONE ? 1 : 0;
                }
                boolean isChest = c.getType() == Material.CHEST;
                int diamonds = isChest ? diamonds(c) : 0;
                return Verdict.of(stone == 26 && isChest && diamonds == expectedDiamonds, "file=" + file.getName() + " content=" + content
                        + " stoneBack=" + stone + "/26 chestBack=" + isChest + " diamonds=" + diamonds + " (expected " + expectedDiamonds + ")");
            });
        }), () -> {
            clearChest(c);
            box.forEach(block -> block.setType(Material.AIR));
        });
    }

    /**
     * Whether the server's WorldEdit reads and writes tile entity data, which it does only through a Bukkit adapter. On
     * crucible-1.7.10 WorldEdit 6.1.9 ships a CraftBukkit_v1_7_R4 adapter, but Crucible's NMS is not CraftBukkit's, and
     * WorldEdit says so at boot: "This WorldEdit version does not fully support your version of Bukkit." A snapshot
     * there keeps the chest and loses its items. Every other flavor names its adapter ("Using ... as the Bukkit adapter").
     */
    private static boolean worldEditCarriesTileEntities() {
        return !MCVersion.isEqual(MCVersion.v1_7_10);
    }

    /**
     * A stone floor with a stone at its center and one entity on it, saved, the entity removed and the box wrecked; the
     * snapshot puts the blocks back and spawns the entity again: an armor stand from 1.8 on, an item frame hung on the
     * stone on 1.7.10, which has no armor stand.
     */
    private CompletableFuture<Verdict> snapshotEntitiesBack(Player player) {
        World world = player.getWorld();
        Block c = anchor(player, 72);
        BlockPos min = pos(c).add(-1, -1, -1), max = pos(c).add(1, 1, 1);
        List<Block> box = blocks(world, min, max);
        box.forEach(block -> block.setType(block.getY() < c.getY() || block.equals(c) ? Material.STONE : Material.AIR));
        boolean frame = MCVersion.isEqual(MCVersion.v1_7_10);
        Entity entity = frame ? world.spawn(c.getLocation(), ItemFrame.class)
                : world.spawnEntity(c.getRelative(0, 1, 0).getLocation().add(0.5, 0, 0.5), EntityType.valueOf("ARMOR_STAND"));
        EntityType type = entity.getType();
        Map<Block, String> before = states(world, min, max);
        return inRegion("ecc-snapshot-entities", world, CuboidShape.of(min, max), TIMEOUT_MILLIS, region -> {}, region -> {
            int put = inBox(world, type, min, max).size();
            return terrain(region).snapshot(region).thenCompose(file -> {
                inBox(world, type, min, max).forEach(Entity::remove);
                box.forEach(block -> block.setType(Material.AIR));
                return ticks(1);
            }).thenCompose(wrecked -> {
                int afterRemove = inBox(world, type, min, max).size();
                return terrain(region).restoreSnapshot(region, 0, 10).thenCompose(placed -> ticks(2)).thenApply(read -> {
                    int back = inBox(world, type, min, max).size();
                    int blocksBack = unchanged(before);
                    return Verdict.of(put == 1 && afterRemove == 0 && back == put && blocksBack == before.size(), "entity=" + type
                            + " entitiesPut=" + put + " afterRemove=" + afterRemove + " entitiesBack=" + back + "/" + put
                            + " blocksBack=" + blocksBack + "/" + before.size());
                });
            });
        }, () -> {
            entity.remove();
            inBox(world, type, min, max).forEach(Entity::remove);
            box.forEach(block -> block.setType(Material.AIR));
        });
    }

    /**
     * A right-triangle region (legs of 5, two layers) whose bounding box is all stone; the snapshot is taken, every block of
     * the box turned to dirt, and the snapshot restored. Only measures how many blocks outside the region but inside the box
     * the restore changed: a snapshot holds the box, not the shape.
     */
    private CompletableFuture<Verdict> snapshotPolygonBox(Player player) {
        World world = player.getWorld();
        Block c = anchor(player, 78);
        PolygonShape triangle = PolygonShape.of(Arrays.asList(pos(c), pos(c).add(4, 0, 0), pos(c).add(0, 0, 4)), c.getY(), c.getY() + 1);
        BlockPos min = pos(c), max = pos(c).add(4, 1, 4);
        List<Block> box = blocks(world, min, max);
        box.forEach(block -> block.setType(Material.STONE));
        return inRegion("ecc-snapshot-polygon", world, triangle, TIMEOUT_MILLIS, region -> {}, region -> terrain(region).snapshot(region).thenCompose(file -> {
            box.forEach(block -> block.setType(Material.DIRT));
            return terrain(region).restoreSnapshot(region, 0, 10);
        }).thenCompose(placed -> ticks(1)).thenApply(read -> {
            int inside = 0, insideBack = 0, outside = 0, outsideAltered = 0;
            for (Block block : box) {
                boolean back = block.getType() == Material.STONE;
                if (triangle.contains(block.getX(), block.getY(), block.getZ())) {
                    inside++;
                    insideBack += back ? 1 : 0;
                } else {
                    outside++;
                    outsideAltered += back ? 1 : 0;
                }
            }
            return Verdict.of(insideBack == inside, "box=" + box.size() + " insideBack=" + insideBack + "/" + inside
                    + " outsideAltered=" + outsideAltered + "/" + outside + " (blocks outside the region the restore changed)");
        }), () -> box.forEach(block -> block.setType(Material.AIR)));
    }

    /** With a retention of 2, three snapshots leave the two newest on disk, the oldest gone with its side files. */
    private CompletableFuture<Verdict> snapshotRetention(Player player) {
        World world = player.getWorld();
        Block c = anchor(player, 60);
        c.setType(Material.STONE);
        File[] taken = new File[3];
        int[] retention = new int[1];
        return inRegion("ecc-retention", world, pos(c), pos(c), TIMEOUT_MILLIS, region -> {
            retention[0] = terrain(region).getSnapshotRetention();
            terrain(region).setSnapshotRetention(2);
        }, region -> terrain(region).snapshot(region).thenCompose(first -> {
            taken[0] = first;
            return terrain(region).snapshot(region);
        }).thenCompose(second -> {
            taken[1] = second;
            return terrain(region).snapshot(region);
        }).thenApply(third -> {
            taken[2] = third;
            List<File> kept = terrain(region).getSnapshots(region);
            File oldest = taken[0];
            String[] sideFiles = oldest.getParentFile().list((dir, file) -> file.startsWith(oldest.getName()));
            boolean oldestGone = !oldest.exists() && (sideFiles == null || sideFiles.length == 0);
            return Verdict.of(kept.size() == 2 && kept.get(0).equals(taken[2]) && kept.get(1).equals(taken[1]) && oldestGone,
                    "kept=" + names(kept) + " taken=" + names(Arrays.asList(taken)) + " oldestGone=" + oldestGone);
        }), () -> {
            if (retention[0] > 0) {
                regions.getTerrain().setSnapshotRetention(retention[0]);
            }
            c.setType(Material.AIR);
        });
    }

    // ------------------------------------------------------------------------------------------- worldedit --

    /**
     * An L of three stones and a gold marker copied into a clipboard and pasted turned 90 degrees ({@code rotateY(90)}),
     * over a dirt floor: the marker lands where the rotation sends it, and undoing the paste gives the floor back.
     */
    private CompletableFuture<Verdict> rotatedPasteUndo(Player player) {
        World world = player.getWorld();
        Block src = anchor(player, 66);
        Block dst = src.getRelative(0, 0, 8);
        List<Block> shape = Arrays.asList(src, src.getRelative(1, 0, 0), src.getRelative(2, 0, 0), src.getRelative(2, 0, 1),
                src.getRelative(0, 0, 1), src.getRelative(1, 0, 1));
        for (int i = 0; i < shape.size(); i++) {
            shape.get(i).setType(i < 3 ? Material.STONE : i == 3 ? Material.GOLD_BLOCK : Material.AIR);
        }
        BlockPos floorMin = pos(dst).add(-3, 0, -3), floorMax = pos(dst).add(3, 0, 3);
        List<Block> floor = blocks(world, floorMin, floorMax);
        floor.forEach(block -> block.setType(Material.DIRT));
        Map<Block, String> before = states(world, floorMin, floorMax);
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        try {
            answer.complete(WorldEditPaste.rotateAndUndo(world, src, dst, before));
        } catch (Throwable thrown) {
            answer.complete(Verdict.fail(Verdict.describe(thrown)));
        } finally {
            shape.forEach(block -> block.setType(Material.AIR));
            floor.forEach(block -> block.setType(Material.AIR));
        }
        return answer;
    }

    /** The WorldEdit side, loaded only when the case runs; only API that WorldEdit 6 and 7 share. */
    private static final class WorldEditPaste {

        static Verdict rotateAndUndo(World world, Block src, Block dst, Map<Block, String> floorBefore) {
            if (!WEPlatform.isPresent()) {
                return Verdict.fail("WorldEdit is not on this server, or the core's adapter for it did not load");
            }
            WEPlatform worldEdit = WEPlatform.getInstance();
            IFCCuboidRegion area = worldEdit.createCuboidRegion(pos(src), pos(src).add(2, 0, 1));
            FCBlockArrayClipboard clipboard = worldEdit.createBlockArrayClipboard(area);
            worldEdit.getOperationManager().forwardExtentCopy(world, area, clipboard);
            //WorldEdit's rotateY matrix sends an offset (x, z) from the origin (the box's minimum) to (z, -x)
            EditSession paste = clipboard.paste(world, pos(dst), true, false, new AffineTransform().rotateY(90));
            Block marker = dst.getRelative(1, 0, -2);
            boolean markerThere = marker.getType() == Material.GOLD_BLOCK;
            boolean stonesThere = dst.getType() == Material.STONE && dst.getRelative(0, 0, -1).getType() == Material.STONE
                    && dst.getRelative(0, 0, -2).getType() == Material.STONE;
            boolean airThere = dst.getRelative(1, 0, 0).getType() == Material.AIR && dst.getRelative(1, 0, -1).getType() == Material.AIR;
            Block unrotated = dst.getRelative(2, 0, 1);
            String unrotatedHolds = unrotated.getType().name();
            EditSession undo = WorldEdit.getInstance().getEditSessionFactory().getEditSession(new BukkitWorld(world), -1);
            paste.undo(undo);
            int floorBack = unchanged(floorBefore);
            return Verdict.of(markerThere && stonesThere && airThere && unrotatedHolds.equals("DIRT") && floorBack == floorBefore.size(),
                    "rotateY(90) marker at dst+(1,0,-2)=" + markerThere + " stones=" + stonesThere + " air=" + airThere
                            + " unrotatedSpotHolds=" + unrotatedHolds + " undoFloorBack=" + floorBack + "/" + floorBefore.size());
        }
    }

    // ------------------------------------------------------------------------------------------------- cost --

    /**
     * 10 000 positions of a slab journaled through {@link RegionTerrain#recordChange}, the path every listener takes, then
     * Paper's average tick time over the 100 idle ticks before the restore against the 100 ticks the restore spans at
     * {@link #COST_BLOCKS_PER_TICK} a tick. Paper only: no other flavor answers a tick time.
     */
    private CompletableFuture<Verdict> journalRestoreCost(Player player) {
        if (!"Paper".equals(Bukkit.getName()) || !Bukkit.getBukkitVersion().startsWith("1.21.1-")) {
            return CompletableFuture.completedFuture(Verdict.skipped("the tick cost is measured on paper-1.21.1 only, the flavor whose"
                    + " server answers getAverageTickTime(); this is " + Bukkit.getName() + " " + Bukkit.getBukkitVersion()));
        }
        World world = player.getWorld();
        Location at = player.getLocation();
        int y = Math.min(at.getBlockY() + 25, world.getMaxHeight() - 10);
        BlockPos min = BlockPos.of(at.getBlockX() - COST_SIDE / 2, y, at.getBlockZ() - COST_SIDE / 2);
        BlockPos max = min.add(COST_SIDE - 1, 0, COST_SIDE - 1);
        List<Block> slab = blocks(world, min, max);
        return tracked("ecc-cost", world, min, max, COST_TIMEOUT_MILLIS, region -> {
            String worldName = world.getName();
            for (Block block : slab) {
                int x = block.getX(), z = block.getZ();
                RegionTerrain.recordChange(worldName, x, y, z,
                        content -> EverNifeCore.getPlatform().getTerrainAccess().captureBlock(worldName, x, y, z, content));
                block.setType(Material.STONE);
            }
            int recorded = terrain(region).getJournalSize(region);
            double[] idle = new double[1];
            return ticks(COST_WINDOW_TICKS + 1).thenCompose(waited -> {
                idle[0] = averageTickMillis();
                return terrain(region).restoreJournal(region, COST_BLOCKS_PER_TICK);
            }).thenCompose(restored -> ticks(1).thenApply(read -> {
                double restoring = averageTickMillis();
                int back = 0;
                for (Block block : slab) {
                    back += block.getType() == Material.STONE ? 0 : 1;
                }
                double cost = restoring - idle[0];
                return Verdict.of(recorded == slab.size() && restored == slab.size() && back == slab.size() && cost <= COST_LIMIT_MILLIS,
                        String.format("recorded=%d restored=%d back=%d B=%d/tick avgTick idle=%.3fms restoring=%.3fms diff=%.3fms (limit %.1fms,"
                                        + " Paper getAverageTickTime over %d ticks)", recorded, restored, back, COST_BLOCKS_PER_TICK, idle[0],
                                restoring, cost, COST_LIMIT_MILLIS, COST_WINDOW_TICKS));
            }));
        }, () -> slab.forEach(block -> {
            if (block.getType() == Material.STONE) {
                block.setType(Material.AIR);
            }
        }));
    }

    /** Paper's average tick time in milliseconds; reached by reflection, the Spigot API carries no tick time. */
    private static double averageTickMillis() {
        try {
            return ((Number) Bukkit.getServer().getClass().getMethod("getAverageTickTime").invoke(Bukkit.getServer())).doubleValue();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("This Paper server answers no getAverageTickTime(): " + Verdict.describe(e), e);
        }
    }

    // ---------------------------------------------------------------------------------------------- helpers --

    /** {@link #inRegion} for a region with {@code track-changes} on, its body run once the journal listener is up. */
    private CompletableFuture<Verdict> tracked(String name, World world, BlockPos min, BlockPos max, long timeoutMillis,
                                               ThrowingBody body, Runnable cleanup) {
        return inRegion(name, world, min, max, timeoutMillis, region -> region.getManager().getTerrain().track(region),
                //two ticks: the core's journal listener comes up through the main thread once the region needs it
                region -> ticks(2).thenCompose(ready -> {
                    try {
                        return body.run(region);
                    } catch (Exception thrown) {
                        CompletableFuture<Verdict> failed = new CompletableFuture<>();
                        failed.completeExceptionally(thrown);
                        return failed;
                    }
                }), cleanup);
    }

    interface ThrowingBody {
        CompletableFuture<Verdict> run(Region region) throws Exception;
    }

    /**
     * Creates the region, runs {@code body}, and whatever happens - a throw, a timeout - puts the world back with
     * {@code cleanup} on the main thread, deletes the region's snapshots and removes the region.
     */
    private CompletableFuture<Verdict> inRegion(String name, World world, BlockPos min, BlockPos max, long timeoutMillis, Consumer<Region> setup,
                                                Function<Region, CompletableFuture<Verdict>> body, Runnable cleanup) {
        return inRegion(name, world, CuboidShape.of(min, max), timeoutMillis, setup, body, cleanup);
    }

    private CompletableFuture<Verdict> inRegion(String name, World world, Shape shape, long timeoutMillis, Consumer<Region> setup,
                                                Function<Region, CompletableFuture<Verdict>> body, Runnable cleanup) {
        CreationResult created = regions.create(name, world.getName(), shape);
        Region region = created.getRegion();
        if (region == null) {
            cleanup.run();
            return CompletableFuture.completedFuture(Verdict.fail(name + " refused: " + created.getRefusal()));
        }
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        answer.whenComplete((verdict, failed) -> onMain(() -> {
            try {
                cleanup.run();
            } finally {
                deleteSnapshots(region);
                regions.remove(region);
            }
        }));
        try {
            setup.accept(region);
            body.apply(region).whenComplete((verdict, failed) -> answer.complete(failed == null ? verdict : Verdict.fail(Verdict.describe(
                    failed.getCause() != null && failed instanceof java.util.concurrent.CompletionException ? failed.getCause() : failed))));
        } catch (Throwable thrown) {
            answer.complete(Verdict.fail(Verdict.describe(thrown)));
        }
        return PlayerCases.within(answer, timeoutMillis);
    }

    private static void deleteSnapshots(Region region) {
        List<File> snapshots = region.getManager().getTerrain().getSnapshots(region);
        if (snapshots.isEmpty()) {
            return;
        }
        File folder = snapshots.get(0).getParentFile();
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        folder.delete();
    }

    /** The break as the game does it: the event first, the block gone only if nobody cancelled it. */
    private static boolean breakAsTheGame(Block block, Player player) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        block.setType(Material.AIR);
        return true;
    }

    /** {@link #breakAsTheGame} for a chest; emptied first, as the game drops the contents before the block goes. */
    private static boolean breakChestAsTheGame(Block chest, Player player) {
        BlockBreakEvent event = new BlockBreakEvent(chest, player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        clearChest(chest);
        return true;
    }

    /** The place as the game does it: the block is set first, then the event carries the state it replaced. */
    private static boolean placeAsTheGame(Block block, Material type, Block against, Player player) {
        BlockState replaced = block.getState();
        block.setType(type);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replaced, against, new ItemStack(type), player, true);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            replaced.update(true, false);
            return false;
        }
        return true;
    }

    /** An entity's death event; the damage source joined its constructor in 1.20.5. */
    private static EntityDeathEvent deathEvent(LivingEntity entity) throws ReflectiveOperationException {
        List<ItemStack> drops = new ArrayList<>();
        for (Constructor<?> constructor : EntityDeathEvent.class.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 2 && parameters[1] == List.class) {
                return (EntityDeathEvent) constructor.newInstance(entity, drops);
            }
        }
        Class<?> damageType = Class.forName("org.bukkit.damage.DamageType");
        Class<?> damageSource = Class.forName("org.bukkit.damage.DamageSource");
        Object builder = damageSource.getMethod("builder", damageType).invoke(null, damageType.getField("GENERIC").get(null));
        Object source = Class.forName("org.bukkit.damage.DamageSource$Builder").getMethod("build").invoke(builder);
        return EntityDeathEvent.class.getConstructor(LivingEntity.class, damageSource, List.class).newInstance(entity, source, drops);
    }

    private static List<Entity> alive(World world, EntityType type, Location at) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (entity.getType() == type && !entity.isDead() && entity.getLocation().distance(at) < 1.5) {
                found.add(entity);
            }
        }
        return found;
    }

    /** The live entities of {@code type} standing in the box, both corners inclusive. */
    private static List<Entity> inBox(World world, EntityType type, BlockPos min, BlockPos max) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            Location at = entity.getLocation();
            if (entity.getType() == type && !entity.isDead() && at.getBlockX() >= min.getX() && at.getBlockX() <= max.getX()
                    && at.getBlockY() >= min.getY() && at.getBlockY() <= max.getY() && at.getBlockZ() >= min.getZ() && at.getBlockZ() <= max.getZ()) {
                found.add(entity);
            }
        }
        return found;
    }

    private static Inventory inventory(Block block) {
        return ((InventoryHolder) block.getState()).getInventory();
    }

    private static int diamonds(Block chest) {
        int count = 0;
        for (ItemStack item : inventory(chest).getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /** Empties and removes a chest, so no item drops into the world; any other block is just set to air. */
    private static void clearChest(Block block) {
        if (block.getState() instanceof InventoryHolder) {
            inventory(block).clear();
        }
        block.setType(Material.AIR);
    }

    /** A block 20 above the player (capped below the build limit), {@code dx} blocks east. */
    private static Block anchor(Player player, int dx) {
        Location at = player.getLocation();
        World world = player.getWorld();
        return world.getBlockAt(at.getBlockX() + dx, Math.min(at.getBlockY() + 20, world.getMaxHeight() - 20), at.getBlockZ());
    }

    private static List<Block> blocks(World world, BlockPos min, BlockPos max) {
        List<Block> all = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    all.add(world.getBlockAt(x, y, z));
                }
            }
        }
        return all;
    }

    /** Every block of the box as the core captures it, nbt left out. */
    private static Map<Block, String> states(World world, BlockPos min, BlockPos max) {
        Map<Block, String> states = new LinkedHashMap<>();
        for (Block block : blocks(world, min, max)) {
            states.put(block, McTerrainAccess.capture(block.getState(), TerrainContent.of(true, false, false)));
        }
        return states;
    }

    /** How many blocks of {@code before} hold the same state now. */
    private static int unchanged(Map<Block, String> before) {
        int same = 0;
        for (Map.Entry<Block, String> entry : before.entrySet()) {
            same += McTerrainAccess.capture(entry.getKey().getState(), TerrainContent.of(true, false, false)).equals(entry.getValue()) ? 1 : 0;
        }
        return same;
    }

    private static List<String> names(List<File> files) {
        List<String> names = new ArrayList<>();
        for (File file : files) {
            names.add(file == null ? "null" : file.getName());
        }
        return names;
    }

    private static RegionTerrain terrain(Region region) {
        return region.getManager().getTerrain();
    }

    private static BlockPos pos(Block block) {
        return BlockPos.of(block.getX(), block.getY(), block.getZ());
    }

    private static CompletableFuture<Void> ticks(long ticks) {
        CompletableFuture<Void> passed = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskLater(plugin(), () -> passed.complete(null), ticks);
        return passed;
    }

    private static void onMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin(), task);
        }
    }

    private static Plugin plugin() {
        return Bukkit.getPluginManager().getPlugin("ECoreConformance");
    }
}
