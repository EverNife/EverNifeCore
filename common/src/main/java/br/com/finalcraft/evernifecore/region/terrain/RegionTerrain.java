package br.com.finalcraft.evernifecore.region.terrain;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.RegionScheduler;
import br.com.finalcraft.evernifecore.region.event.RegionTerrainEvent;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.TrackChangesSettings;
import br.com.finalcraft.evernifecore.region.shape.BoundingBox;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The terrain side of one owner's regions: the change journal a {@code track-changes} region keeps, and its budgeted
 * restore. Nothing here takes part in protection. Platform listeners feed it through {@link #recordChange}, after the
 * change is certain to happen; the journal keeps the original state of each position - its first change wins.
 */
public final class RegionTerrain {

    private static final long DISK_WRITE_DELAY_MILLIS = 1000;
    private static final String JOURNAL_FILE = "journal.json";

    private final RegionManager manager;
    private final Map<String, TerrainJournal> journals = new ConcurrentHashMap<>();
    private final AtomicBoolean writeScheduled = new AtomicBoolean();
    private volatile int snapshotRetention = 3;

    /** Internal: one per {@link RegionManager}, reached through {@link RegionManager#getTerrain()}. */
    public RegionTerrain(RegionManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    // ------------------------------------------------------------------
    //  Tracking
    // ------------------------------------------------------------------

    /** Turns {@code track-changes} on in {@code region} with the default settings: in memory, blocks + nbt + entities. */
    public void track(Region region) {
        track(region, new TrackChangesSettings());
    }

    /** Turns {@code track-changes} on in {@code region} with {@code settings} as the region's own. */
    public void track(Region region, TrackChangesSettings settings) {
        requireOwn(region);
        region.setFlagSettings(CoreFlags.TRACK_CHANGES, settings);
        region.setFlag(CoreFlags.TRACK_CHANGES, Tristate.TRUE);
    }

    public boolean isTracking(Region region) {
        return region.getFlag(CoreFlags.TRACK_CHANGES) == Tristate.TRUE;
    }

    /** How many positions the journal of {@code region} would put back. */
    public int getJournalSize(Region region) {
        TerrainJournal journal = journals.get(region.getId().getName());
        return journal == null ? 0 : journal.size();
    }

    /**
     * Records the change about to happen at a position in every tracking region there. {@code captureOriginal} reads the
     * state the position has BEFORE the change, with the content the region keeps; it runs only when some region
     * tracks the position and has not recorded it yet. World thread.
     */
    public static void recordChange(String world, int x, int y, int z, Function<TerrainContent, String> captureOriginal) {
        for (Region region : RegionLookup.get().at(world, x, y, z)) {
            RegionTerrain terrain = region.getManager().getTerrain();
            if (!terrain.isTracking(region)) continue;
            TerrainContent content = settingsOf(region).getContent();
            if (!content.isBlocks()) continue;
            TerrainJournal journal = terrain.journalOf(region);
            if (journal.contains(x, y, z)) continue;
            journal.record(x, y, z, captureOriginal.apply(content));
            terrain.onRecorded(region);
        }
    }

    /**
     * Records a non-player entity a covered action is about to remove, in every tracking region that keeps entities
     * and holds its position. {@code capture} reads it as the platform restores it. World thread.
     */
    public static void recordEntityRemoval(String world, double x, double y, double z, Function<TerrainContent, String> capture) {
        String state = null;
        for (Region region : RegionLookup.get().at(world, x, y, z)) {
            RegionTerrain terrain = region.getManager().getTerrain();
            if (!terrain.isTracking(region)) continue;
            TerrainContent content = settingsOf(region).getContent();
            if (!content.isEntities()) continue;
            if (state == null) {
                state = capture.apply(content);
            }
            terrain.journalOf(region).recordEntity(state);
            terrain.onRecorded(region);
        }
    }

    // ------------------------------------------------------------------
    //  Restore
    // ------------------------------------------------------------------

    /**
     * Puts back every position of the journal of {@code region}, the most recent first change first, at most
     * {@code blocksPerTick} per tick on the world's thread; then spawns the recorded entities and empties the journal.
     * What a stop leaves undone - a failure, the manager closing - stays in the journal for the next restore.
     *
     * @return completes with how many positions were put back
     */
    public CompletableFuture<Integer> restoreJournal(Region region, int blocksPerTick) {
        requireOwn(region);
        requireBudget(blocksPerTick);
        TerrainJournal journal = journals.get(region.getId().getName());
        if (journal == null || journal.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        String world = region.getWorld();
        return restore(region, true, blocksPerTick, () -> journal.size() + " position(s) stay in its journal", (access, budget) -> {
            int restored = 0;
            for (; restored < budget; restored++) {
                TerrainJournal.Entry last = journal.peekLast();
                if (last == null) break;
                access.restoreBlock(world, last.x, last.y, last.z, last.state);
                journal.removeLast();
            }
            if (journal.size() == 0) {
                for (String entity : journal.takeEntities()) {
                    access.restoreEntity(world, entity);
                }
            }
            return restored;
        }, () -> journal.size() == 0);
    }

    // ------------------------------------------------------------------
    //  Snapshots
    // ------------------------------------------------------------------

    /** How many snapshots of each region are kept; older ones are deleted as new ones are taken. 3 unless set. */
    public int getSnapshotRetention() {
        return snapshotRetention;
    }

    public void setSnapshotRetention(int snapshotRetention) {
        if (snapshotRetention < 1) {
            throw new IllegalArgumentException("snapshotRetention is " + snapshotRetention + "; keep at least the snapshot just taken.");
        }
        this.snapshotRetention = snapshotRetention;
    }

    /** {@link #snapshot(Region, TerrainContent)} with blocks, nbt and entities. */
    public CompletableFuture<File> snapshot(Region region) {
        return snapshot(region, TerrainContent.ALL);
    }

    /**
     * Saves the bounding box of {@code region} as {@code <owner data>/regions/terrain/<region>/<stamp>.<ext>} on the
     * world's thread, with the game version beside it, keeping the {@link #getSnapshotRetention()} newest and deleting
     * the older ones.
     *
     * @return completes with the file written
     */
    public CompletableFuture<File> snapshot(Region region, TerrainContent content) {
        requireOwn(region);
        IPlatform platform = EverNifeCore.getPlatform();
        BoundingBox box = region.getShape().getBounds();
        File[] written = new File[1];
        long[] took = new long[1];
        return platform.runOnWorldThread(region.getWorld(), () -> {
            long start = System.nanoTime();
            ITerrainAccess access = platform.getTerrainAccess();
            File folder = terrainFolder(region);
            long stamp = Math.max(System.currentTimeMillis(), newestStamp(region) + 1);   //two snapshots in one millisecond stay apart
            File file = new File(folder, stamp + "." + access.getSnapshotExtension());
            try {
                Files.createDirectories(folder.toPath());
                access.writeSnapshot(region.getWorld(), new BlockPos(box.getMinX(), box.getMinY(), box.getMinZ()),
                        new BlockPos(box.getMaxX(), box.getMaxY(), box.getMaxZ()), content, file);
                Files.write(versionFile(file).toPath(), access.getGameVersion().getBytes(StandardCharsets.UTF_8));
            } catch (IOException failed) {
                throw new UncheckedIOException("The snapshot of region " + region.getId() + " could not be written to " + file, failed);
            }
            List<File> kept = getSnapshots(region);
            for (File old : kept.subList(Math.min(snapshotRetention, kept.size()), kept.size())) {
                deleteSnapshot(old);
            }
            written[0] = file;
            took[0] = System.nanoTime() - start;
        }).thenApply(ignored -> {
            ECEventBus.global().postIfListened(RegionTerrainEvent.class, () -> new RegionTerrainEvent(region, RegionTerrainEvent.Type.SNAPSHOT,
                    false, 0, took[0]));
            return written[0];
        });
    }

    /** The snapshot files of {@code region}, newest first. */
    public List<File> getSnapshots(Region region) {
        String extension = "." + EverNifeCore.getPlatform().getTerrainAccess().getSnapshotExtension();
        File[] files = terrainFolder(region).listFiles((dir, name) -> stampOf(name) > 0 && name.endsWith(extension));
        List<File> snapshots = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        snapshots.sort(Comparator.comparingLong((File file) -> stampOf(file.getName())).reversed());
        return snapshots;
    }

    /**
     * Puts back the {@code index}-th newest snapshot of {@code region} (0 = the newest), at most {@code blocksPerTick}
     * blocks per tick on the world's thread. The file keeps the whole bounding box, but only the blocks and entities
     * the region's shape holds go back, so a polygon never rewrites the corners of its box. A snapshot taken on another
     * game version is refused.
     *
     * @return completes with how many blocks were written - the positions outside the shape are not counted
     */
    public CompletableFuture<Integer> restoreSnapshot(Region region, int index, int blocksPerTick) {
        requireOwn(region);
        requireBudget(blocksPerTick);
        List<File> snapshots = getSnapshots(region);
        if (index < 0 || index >= snapshots.size()) {
            throw new IllegalArgumentException("Region " + region.getId() + " has " + snapshots.size() + " snapshot(s), so there is no"
                    + " snapshot #" + index + " - take one with snapshot(region) first, or pick an index below " + snapshots.size() + ".");
        }
        File file = snapshots.get(index);
        requireSameGameVersion(file);
        String world = region.getWorld();
        Shape shape = region.getShape();
        TerrainPlacement[] placement = new TerrainPlacement[1];
        long[] next = {0};
        boolean[] placed = new boolean[1];
        return restore(region, false, blocksPerTick, () -> "the snapshot " + file.getName() + " was put back only in part", (access, budget) -> {
            if (placement[0] == null) {
                try {
                    placement[0] = access.readSnapshot(world, file);
                } catch (IOException failed) {
                    throw new UncheckedIOException("The snapshot " + file + " does not read", failed);
                }
            }
            TerrainPlacement snapshot = placement[0];
            BlockPos min = snapshot.getMin();
            BlockPos max = snapshot.getMax();
            int sizeY = max.getY() - min.getY() + 1;
            int sizeZ = max.getZ() - min.getZ() + 1;
            long volume = (long) (max.getX() - min.getX() + 1) * sizeY * sizeZ;
            int written = 0;
            for (; written < budget && next[0] < volume; next[0]++) {
                long i = next[0];
                int x = min.getX() + (int) (i / ((long) sizeY * sizeZ));
                int y = min.getY() + (int) (i / sizeZ % sizeY);
                int z = min.getZ() + (int) (i % sizeZ);
                if (!shape.contains(x, y, z)) continue;
                snapshot.placeBlock(x, y, z);
                written++;
            }
            if (next[0] >= volume) {
                List<BlockPos> entities = snapshot.getEntityPositions();
                for (int e = 0; e < entities.size(); e++) {
                    BlockPos at = entities.get(e);
                    if (shape.contains(at.getX(), at.getY(), at.getZ())) {
                        snapshot.placeEntity(e);
                    }
                }
                placed[0] = true;
            }
            return written;
        }, () -> placed[0]);
    }

    // ------------------------------------------------------------------
    //  Budgeted restore
    // ------------------------------------------------------------------

    /** One tick of a restore: places at most {@code budget} blocks and answers how many. */
    private interface RestoreTick {
        int place(ITerrainAccess access, int budget);
    }

    private CompletableFuture<Integer> restore(Region region, boolean journal, int blocksPerTick, Supplier<String> leftOver,
                                               RestoreTick tick, BooleanSupplier finished) {
        CompletableFuture<Integer> done = new CompletableFuture<>();
        ECEventBus.global().postIfListened(RegionTerrainEvent.class, () -> new RegionTerrainEvent(region, RegionTerrainEvent.Type.RESTORE_START, journal));
        CompletableFuture<Integer> ended = done.whenComplete((count, error) -> ECEventBus.global().postIfListened(RegionTerrainEvent.class,
                () -> new RegionTerrainEvent(region, RegionTerrainEvent.Type.RESTORE_END, journal)));
        step(region, journal, blocksPerTick, 0, leftOver, tick, finished, done, false);
        return ended;
    }

    /** One tick of a restore: the first runs now (inline when already on the world's thread), each next one a tick later. */
    private void step(Region region, boolean journal, int blocksPerTick, int restoredSoFar, Supplier<String> leftOver, RestoreTick tick,
                      BooleanSupplier finished, CompletableFuture<Integer> done, boolean nextTick) {
        IPlatform platform = EverNifeCore.getPlatform();
        int[] restored = {restoredSoFar};
        CompletableFuture<Void> ran;
        try {
            Runnable work = () -> {
                if (manager.isClosed()) {
                    throw new IllegalStateException("The RegionManager of " + region.getId() + " closed during the restore; " + leftOver.get() + ".");
                }
                long start = System.nanoTime();
                int placed = tick.place(platform.getTerrainAccess(), blocksPerTick);
                long took = System.nanoTime() - start;
                restored[0] += placed;
                ECEventBus.global().postIfListened(RegionTerrainEvent.class, () -> new RegionTerrainEvent(region,
                        RegionTerrainEvent.Type.RESTORE_TICK, journal, placed, took));
            };
            ran = nextTick ? platform.runOnWorldThreadNextTick(region.getWorld(), work) : platform.runOnWorldThread(region.getWorld(), work);
        } catch (RuntimeException failed) {   //a platform already on the world thread runs the step inline and throws here
            ran = new CompletableFuture<>();
            ran.completeExceptionally(failed);
        }
        ran.whenComplete((ignored, error) -> {
            if (error != null) {
                scheduleDiskWrite();
                done.completeExceptionally(error);
            } else if (finished.getAsBoolean()) {
                scheduleDiskWrite();
                done.complete(restored[0]);
            } else {
                step(region, journal, blocksPerTick, restored[0], leftOver, tick, finished, done, true);
            }
        });
    }

    private static void requireBudget(int blocksPerTick) {
        if (blocksPerTick < 1) {
            throw new IllegalArgumentException("blocksPerTick is " + blocksPerTick + "; a restore puts back at least one block per tick.");
        }
    }

    /** Block and tile entity formats change between game versions, so a snapshot only goes back on the one that took it. */
    private static void requireSameGameVersion(File snapshot) {
        String running = EverNifeCore.getPlatform().getTerrainAccess().getGameVersion();
        File version = versionFile(snapshot);
        String taken;
        try {
            taken = version.isFile() ? new String(Files.readAllBytes(version.toPath()), StandardCharsets.UTF_8).trim() : null;
        } catch (IOException unreadable) {
            throw new UncheckedIOException("The game version of the snapshot " + snapshot + " does not read from " + version, unreadable);
        }
        if (!running.equals(taken)) {
            throw new IllegalStateException("The snapshot " + snapshot.getName() + " was taken on game version " + (taken == null
                    ? "unknown (no " + version.getName() + " beside it)" : taken) + " and this server runs " + running + "; a snapshot"
                    + " only restores on the version that took it - restore it on that version, or take a new snapshot here.");
        }
    }

    private static File versionFile(File snapshot) {
        return new File(snapshot.getPath() + ".version");
    }

    private long newestStamp(Region region) {
        List<File> snapshots = getSnapshots(region);
        return snapshots.isEmpty() ? 0 : stampOf(snapshots.get(0).getName());
    }

    /** Deletes a snapshot and every side file the platform wrote next to it - each name starts with its stamp. */
    private static void deleteSnapshot(File snapshot) {
        String stamp = stampOf(snapshot.getName()) + ".";
        File[] siblings = snapshot.getParentFile().listFiles((dir, name) -> name.startsWith(stamp));
        if (siblings != null) {
            for (File file : siblings) {
                file.delete();
            }
        }
    }

    /** The stamp a snapshot's name starts with; 0 for any other file (the journal). */
    private static long stampOf(String name) {
        int dot = name.indexOf('.');
        try {
            return Long.parseLong(dot < 0 ? name : name.substring(0, dot));
        } catch (NumberFormatException notASnapshot) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    //  Persistence
    // ------------------------------------------------------------------

    /** Internal: reads the disk journals of freshly loaded regions; the manager calls it after a load. */
    public void loadJournals(Collection<Region> regions) {
        ObjectMapper mapper = mapper();
        for (Region region : regions) {
            File file = journalFile(region);
            if (!file.isFile()) continue;
            try {
                journals.put(region.getId().getName(), mapper.readValue(file, TerrainJournal.class).reindexed());
            } catch (IOException unreadable) {
                manager.getOwner().getLog().warning("The terrain journal of region {} does not read ({}); it stays on disk untouched"
                        + " and the region starts with an empty journal.", region.getId(), unreadable.getMessage());
            }
        }
    }

    /** Internal: writes every changed journal kept on {@link JournalPersistence#DISK}; the manager calls it on flush. */
    public void writeDirty() {
        writeScheduled.set(false);
        ObjectMapper mapper = mapper();
        for (Region region : manager.getAll()) {
            TerrainJournal journal = journals.get(region.getId().getName());
            if (journal == null || !journal.dirty) continue;
            if (settingsOf(region).getPersistence() != JournalPersistence.DISK) {
                journal.dirty = false;
                continue;
            }
            File file = journalFile(region);
            try {
                synchronized (journal) {
                    journal.dirty = false;
                    if (journal.isEmpty()) {
                        Files.deleteIfExists(file.toPath());
                        continue;
                    }
                    Files.createDirectories(file.getParentFile().toPath());
                    File temp = new File(file.getParentFile(), JOURNAL_FILE + ".tmp");
                    mapper.writeValue(temp, journal);
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException failed) {
                journal.dirty = true;
                manager.getOwner().getLog().warning("The terrain journal of region {} could not be written to {} ({}); the next flush"
                        + " tries again.", region.getId(), file, failed.getMessage());
            }
        }
    }

    private TerrainJournal journalOf(Region region) {
        return journals.computeIfAbsent(region.getId().getName(), name -> new TerrainJournal());
    }

    private void onRecorded(Region region) {
        if (settingsOf(region).getPersistence() == JournalPersistence.DISK) {
            scheduleDiskWrite();
        }
    }

    private void scheduleDiskWrite() {
        if (writeScheduled.compareAndSet(false, true)) {
            RegionScheduler.scheduleAsync(this::writeDirty, DISK_WRITE_DELAY_MILLIS);
        }
    }

    private File journalFile(Region region) {
        return new File(terrainFolder(region), JOURNAL_FILE);
    }

    private File terrainFolder(Region region) {
        return new File(manager.getOwner().getMetaInfo().getDataFolder(), "regions/terrain/" + region.getId().getName());
    }

    private static TrackChangesSettings settingsOf(Region region) {
        return region.getEffectiveFlagSettings(CoreFlags.TRACK_CHANGES);
    }

    private static ObjectMapper mapper() {
        return ConfigFactory.inMemoryCodec().getObjectMapper();
    }

    private void requireOwn(Region region) {
        if (region.getManager() != manager) {
            throw new IllegalArgumentException("Region " + region.getId() + " belongs to another owner's RegionManager - use"
                    + " region.getManager().getTerrain().");
        }
    }
}
