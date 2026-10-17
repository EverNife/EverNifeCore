package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.TickTimes;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.region.terrain.McTerrainAccess;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.minecraft.worldedit.WEPlatform;
import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.minecraft.worldedit.region.IFCCuboidRegion;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.event.RegionTerrainEvent;
import br.com.finalcraft.evernifecore.region.flag.core.TrackChangesSettings;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.PolygonShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.terrain.JournalPersistence;
import br.com.finalcraft.evernifecore.region.terrain.RegionTerrain;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.math.transform.AffineTransform;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
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
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The terrain side of the region system on a live server: a {@code track-changes} journal fed by the real Bukkit events
 * and put back, a snapshot written and restored through the server's WorldEdit, snapshot retention, WorldEdit's own
 * rotated paste and undo through the core's wrappers, and what each mode costs a tick on a small and a big box. The events are fired
 * through the plugin manager the way the game fires them, and the case then applies the change itself, as the game
 * would after nobody cancelled it. Every case puts its blocks and entities back and removes its region.
 */
final class TerrainCases {

    private static final long TIMEOUT_MILLIS = 20_000L;
    private static final int CHEST_DIAMONDS = 3;
    /** The boxes of the cost grid: {side, blocks restored per tick}, the same budget for the journal and the snapshot. */
    private static final int[][] GRID = {{16, 100}, {64, 1000}};
    private static final long GRID_TIMEOUT_MILLIS = 600_000L;
    /** Long enough for the core's delayed disk write of a journal (one second after its first change) to finish. */
    private static final int JOURNAL_WRITE_TICKS = 60;
    /** Paper's average tick time covers the last 100 ticks. */
    private static final int COST_WINDOW_TICKS = 100;

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
                PlayerCases.later("terrain-snapshot-keeps-sign-text", ctx, this::snapshotKeepsSignText),
                PlayerCases.later("terrain-snapshot-entities-back", ctx, this::snapshotEntitiesBack),
                PlayerCases.later("terrain-snapshot-polygon-box", ctx, this::snapshotPolygonBox),
                PlayerCases.later("terrain-snapshot-retention", ctx, this::snapshotRetention),
                PlayerCases.later("worldedit-rotated-paste-undo", ctx, this::rotatedPasteUndo),
                PlayerCases.later("terrain-cost-grid", ctx, this::costGrid));
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
     * a tick from the snapshot: every block comes back, and the diamonds come back only when the snapshot kept nbt. On
     * 1.7.10 that takes WorldEdit Forge in mods/ beside the plugin, which the matrix routes there.
     */
    private CompletableFuture<Verdict> snapshotRestores(Player player, String name, int dx, TerrainContent content) {
        World world = player.getWorld();
        Block c = anchor(player, dx);
        BlockPos min = pos(c).add(-1, -1, -1), max = pos(c).add(1, 1, 1);
        List<Block> box = blocks(world, min, max);
        box.forEach(block -> block.setType(block.equals(c) ? Material.CHEST : Material.STONE));
        inventory(c).addItem(new ItemStack(Material.DIAMOND, CHEST_DIAMONDS));
        int expectedDiamonds = content.isNbt() ? CHEST_DIAMONDS : 0;
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
     * A sign with two lines of text on a stone floor, saved with nbt, wrecked and put back: the sign returns with its text,
     * the second tile entity a snapshot has to carry besides the chest.
     */
    private CompletableFuture<Verdict> snapshotKeepsSignText(Player player) {
        World world = player.getWorld();
        Block c = anchor(player, 84);
        BlockPos min = pos(c).add(-1, -1, -1), max = pos(c).add(1, 1, 1);
        List<Block> box = blocks(world, min, max);
        Material signType = Material.getMaterial("OAK_SIGN") != null ? Material.getMaterial("OAK_SIGN") : Material.getMaterial("SIGN_POST");
        box.forEach(block -> block.setType(block.getY() < c.getY() ? Material.STONE : Material.AIR));
        c.setType(signType);
        Sign sign = (Sign) c.getState();
        sign.setLine(0, "ecc");
        sign.setLine(1, "snapshot");
        sign.update(true, false);
        return inRegion("ecc-snapshot-sign", world, min, max, TIMEOUT_MILLIS, region -> {}, region -> terrain(region).snapshot(region, TerrainContent.ALL)
                .thenCompose(file -> {
                    box.forEach(block -> block.setType(Material.AIR));
                    return terrain(region).restoreSnapshot(region, 0, 10).thenCompose(placed -> ticks(1)).thenApply(read -> {
                        BlockState back = c.getState();
                        String lines = back instanceof Sign ? ((Sign) back).getLine(0) + "|" + ((Sign) back).getLine(1) : null;
                        return Verdict.of("ecc|snapshot".equals(lines), "file=" + file.getName() + " block=" + c.getType() + " lines=" + lines
                                + " (expected ecc|snapshot)");
                    });
                }), () -> box.forEach(block -> block.setType(Material.AIR)));
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
     * the box turned to dirt, and the snapshot restored: every block of the triangle is stone again and none outside it -
     * the snapshot holds the box, the restore only the shape.
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
            return Verdict.of(insideBack == inside && outsideAltered == 0, "box=" + box.size() + " insideBack=" + insideBack + "/" + inside
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
            EditSession undo = WorldEdit.getInstance().getEditSessionFactory().getEditSession(worldEdit.adaptWorld(world), -1);
            paste.undo(undo);
            int floorBack = unchanged(floorBefore);
            return Verdict.of(markerThere && stonesThere && airThere && unrotatedHolds.equals("DIRT") && floorBack == floorBefore.size(),
                    "rotateY(90) marker at dst+(1,0,-2)=" + markerThere + " stones=" + stonesThere + " air=" + airThere
                            + " unrotatedSpotHolds=" + unrotatedHolds + " undoFloorBack=" + floorBack + "/" + floorBefore.size());
        }
    }

    // ------------------------------------------------------------------------------------------------- cost --

    /**
     * The cost grid: a 16^3 box restored 100 blocks a tick and a 64^3 box restored 1 000 a tick, each through the journal
     * with 10% and with 100% of it changed, then through a snapshot. The changes are journaled through
     * {@link RegionTerrain#recordChange}, the path every listener takes; each restore tick is timed by the core itself
     * ({@link RegionTerrainEvent.Type#RESTORE_TICK}), the same on every platform. Passes when every block comes back; the
     * milliseconds are the report. On Paper the big box also reads {@code getAverageTickTime()} as a check of the method.
     */
    private CompletableFuture<Verdict> costGrid(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        List<String> lines = new ArrayList<>();
        boolean[] allBack = {true};
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int[] box : GRID) {
            chain = chain.thenCompose(previous -> gridBox(world, at, box[0], box[1]).thenAccept(verdict -> {
                allBack[0] &= verdict.isOk();
                lines.add(verdict.getDetail());
            }));
        }
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        chain.whenComplete((done, failed) -> answer.complete(failed != null ? Verdict.fail(Verdict.describe(failed)) : Verdict.of(allBack[0],
                String.join(" || ", lines))));
        return PlayerCases.within(answer, GRID_TIMEOUT_MILLIS);
    }

    /** One box of the grid, its region tracked on disk so the journal's bytes can be read. */
    private CompletableFuture<Verdict> gridBox(World world, Location at, int side, int budget) {
        int y = Math.min(at.getBlockY() + 30, world.getMaxHeight() - side - 5);
        //the small box stands east of the big one: a box's cleanup may run after the next box is built
        int x = side == GRID[1][0] ? at.getBlockX() - side / 2 : at.getBlockX() + GRID[1][0];
        BlockPos min = BlockPos.of(x, y, at.getBlockZ() - side / 2), max = min.add(side - 1, side - 1, side - 1);
        List<Block> box = blocks(world, min, max);
        box.forEach(block -> block.setType(Material.STONE));
        boolean paper = isPaper();
        return inRegion("ecc-grid-" + side, world, CuboidShape.of(min, max), GRID_TIMEOUT_MILLIS,
                region -> terrain(region).track(region, new TrackChangesSettings().setPersistence(JournalPersistence.DISK)),
                region -> gridJournal(region, box.subList(0, box.size() / 10), budget, false)
                        .thenCompose(tenth -> gridJournal(region, box, budget, paper && side == GRID[1][0])
                                .thenCompose(all -> gridSnapshot(region, box, budget, paper && side == GRID[1][0])
                                        .thenApply(snapshot -> Verdict.of(tenth.isOk() && all.isOk() && snapshot.isOk(), "box=" + side + "^3("
                                                + box.size() + ") B=" + budget + "/tick journal10%{" + tenth.getDetail() + "} journal100%{"
                                                + all.getDetail() + "} snapshot{" + snapshot.getDetail() + "}")))),
                () -> box.forEach(block -> block.setType(Material.AIR)));
    }

    /** Journals {@code changed}, empties it, writes the journal to disk, and restores it; passes when every block is stone again. */
    private CompletableFuture<Verdict> gridJournal(Region region, List<Block> changed, int budget, boolean paperCheck) {
        String world = region.getWorld();
        long start = System.nanoTime();
        for (Block block : changed) {
            int x = block.getX(), y = block.getY(), z = block.getZ();
            RegionTerrain.recordChange(world, x, y, z, content -> EverNifeCore.getPlatform().getTerrainAccess().captureBlock(world, x, y, z, content));
        }
        double recordMillis = (System.nanoTime() - start) / 1_000_000.0;
        changed.forEach(block -> block.setType(Material.AIR));
        File journal = new File(regions.getOwner().getMetaInfo().getDataFolder(), "regions/terrain/" + region.getId().getName() + "/journal.json");
        String[] recorded = new String[1];
        //the core writes a disk journal a second after its first change, off the main thread: wait that write out
        return ticks(JOURNAL_WRITE_TICKS).thenCompose(written -> {
            terrain(region).writeDirty();
            recorded[0] = String.format(Locale.ROOT, "changes=%d record=%.1fms (%.4fms/change) disk=%dB", changed.size(), recordMillis,
                    recordMillis / changed.size(), journal.length());
            return timedRestore(region, true, paperCheck, () -> terrain(region).restoreJournal(region, budget));
        }).thenApply(restore -> {
            int back = stone(changed);
            return Verdict.of(back == changed.size(), recorded[0] + " restore{" + restore + "} back=" + back + "/" + changed.size() + missing(changed));
        });
    }

    /** Snapshots the box, empties it, and restores the snapshot; passes when every block is stone again. */
    private CompletableFuture<Verdict> gridSnapshot(Region region, List<Block> box, int budget, boolean paperCheck) {
        TickTimes write = new TickTimes();
        ECEventSubscription<RegionTerrainEvent> written = ECEventBus.global().subscribe(RegionTerrainEvent.class, event -> {
            if (event.getRegion() == region && event.getType() == RegionTerrainEvent.Type.SNAPSHOT) write.add(event.getNanos());
        });
        return terrain(region).snapshot(region).whenComplete((file, failed) -> written.unsubscribe()).thenCompose(file -> {
            long fileBytes = file.length(), sideBytes = 0;
            String stamp = file.getName().substring(0, file.getName().indexOf('.') + 1);
            File[] siblings = file.getParentFile().listFiles((dir, name) -> name.startsWith(stamp) && !name.equals(file.getName()));
            for (File side : siblings == null ? new File[0] : siblings) sideBytes += side.length();
            String saved = String.format(Locale.ROOT, "write=%.1fms file=%dB side=%dB", write.getWorkMillis(), fileBytes, sideBytes);
            box.forEach(block -> block.setType(Material.AIR));
            return timedRestore(region, false, paperCheck, () -> terrain(region).restoreSnapshot(region, 0, budget)).thenApply(restore -> {
                int back = stone(box);
                return Verdict.of(back == box.size(), saved + " restore{" + restore + "} back=" + back + "/" + box.size() + missing(box));
            });
        });
    }

    /**
     * Runs a restore, timing each of its ticks through the core's {@link RegionTerrainEvent.Type#RESTORE_TICK}. With
     * {@code paperCheck}, Paper's average tick over the 100 idle ticks before is set against the 100 restoring ticks at
     * its end: their difference is the restore's share of a tick, which the core's own mean of those ticks should match.
     */
    private CompletableFuture<String> timedRestore(Region region, boolean journal, boolean paperCheck, Supplier<CompletableFuture<Integer>> restore) {
        double[] idle = {Double.NaN};
        CompletableFuture<Void> ready = paperCheck ? ticks(COST_WINDOW_TICKS + 1).thenRun(() -> idle[0] = averageTickMillis())
                : CompletableFuture.completedFuture(null);
        return ready.thenCompose(waited -> {
            TickTimes times = new TickTimes();
            ECEventSubscription<RegionTerrainEvent> ticks = ECEventBus.global().subscribe(RegionTerrainEvent.class, event -> {
                if (event.getRegion() == region && event.getType() == RegionTerrainEvent.Type.RESTORE_TICK && event.isJournal() == journal) {
                    times.add(event.getNanos());
                }
            });
            int[] serverTicks = {0};
            BukkitTask ticker = Bukkit.getScheduler().runTaskTimer(plugin(), () -> serverTicks[0]++, 1, 1);
            long start = System.nanoTime();
            return restore.get().whenComplete((count, failed) -> {
                ticks.unsubscribe();
                ticker.cancel();
            }).thenApply(count -> {
                String line = times.describe((System.nanoTime() - start) / 1_000_000.0) + " serverTicks=" + serverTicks[0];
                if (!paperCheck) return line;
                double restoring = averageTickMillis(), share = restoring - idle[0], core = times.getMeanOfLastMillis(COST_WINDOW_TICKS);
                return line + String.format(Locale.ROOT, " paper{avgTick idle=%.3fms restoring=%.3fms share=%.3fms coreMeanLast%d=%.3fms"
                        + " ratio=%.2f}", idle[0], restoring, share, COST_WINDOW_TICKS, core, core / share);
            });
        });
    }

    private static boolean isPaper() {
        return "Paper".equals(Bukkit.getName());
    }

    /** Where the blocks that are not stone lie, and what the first one is; empty when all are stone. */
    private static String missing(List<Block> blocks) {
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        String first = null;
        for (Block block : blocks) {
            if (block.getType() == Material.STONE) continue;
            if (first == null) first = block.getType().name();
            int[] at = {block.getX(), block.getY(), block.getZ()};
            for (int i = 0; i < 3; i++) {
                lo[i] = Math.min(lo[i], at[i]);
                hi[i] = Math.max(hi[i], at[i]);
            }
        }
        return first == null ? "" : " missing{first=" + first + " from=" + lo[0] + "," + lo[1] + "," + lo[2] + " to=" + hi[0] + "," + hi[1] + "," + hi[2] + "}";
    }

    private static int stone(List<Block> blocks) {
        int stone = 0;
        for (Block block : blocks) stone += block.getType() == Material.STONE ? 1 : 0;
        return stone;
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
