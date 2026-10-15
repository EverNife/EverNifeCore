package br.com.finalcraft.evernifecore.region.terrain;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.RegionScheduler;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.TrackChangesSettings;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

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
        if (blocksPerTick < 1) {
            throw new IllegalArgumentException("blocksPerTick is " + blocksPerTick + "; a restore puts back at least one block per tick.");
        }
        CompletableFuture<Integer> done = new CompletableFuture<>();
        TerrainJournal journal = journals.get(region.getId().getName());
        if (journal == null || journal.isEmpty()) {
            done.complete(0);
            return done;
        }
        step(region, journal, blocksPerTick, 0, done);
        return done;
    }

    private void step(Region region, TerrainJournal journal, int blocksPerTick, int restoredSoFar, CompletableFuture<Integer> done) {
        IPlatform platform = EverNifeCore.getPlatform();
        String world = region.getWorld();
        int[] restored = {restoredSoFar};
        CompletableFuture<Void> ran;
        try {
            ran = platform.runOnWorldThread(world, () -> {
                if (manager.isClosed()) {
                    throw new IllegalStateException("The RegionManager of " + region.getId() + " closed during the restore; "
                            + journal.size() + " position(s) stay in its journal.");
                }
                ITerrainAccess access = platform.getTerrainAccess();
                for (int i = 0; i < blocksPerTick; i++) {
                    TerrainJournal.Entry last = journal.peekLast();
                    if (last == null) break;
                    access.restoreBlock(world, last.x, last.y, last.z, last.state);
                    journal.removeLast();
                    restored[0]++;
                }
                if (journal.size() == 0) {
                    for (String entity : journal.takeEntities()) {
                        access.restoreEntity(world, entity);
                    }
                }
            });
        } catch (RuntimeException failed) {   //a platform already on the world thread runs the step inline and throws here
            ran = new CompletableFuture<>();
            ran.completeExceptionally(failed);
        }
        ran.whenComplete((ignored, error) -> {
            if (error != null) {
                scheduleDiskWrite();
                done.completeExceptionally(error);
            } else if (journal.size() == 0) {
                scheduleDiskWrite();
                done.complete(restored[0]);
            } else {
                platform.runOnMainThreadNextTick(() -> step(region, journal, blocksPerTick, restored[0], done));
            }
        });
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
        return new File(manager.getOwner().getMetaInfo().getDataFolder(),
                "regions/terrain/" + region.getId().getName() + "/" + JOURNAL_FILE);
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
