package br.com.finalcraft.evernifecore.region.terrain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The original states of one region, in the order of their first change; also its file form. Guarded by its own
 * monitor: a record arrives on the world thread while a disk write may read it from another.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
final class TerrainJournal {

    List<Entry> blocks = new ArrayList<>();
    List<String> entities = new ArrayList<>();
    @JsonIgnore
    private final Set<String> positions = new HashSet<>();
    @JsonIgnore
    boolean dirty;

    /** @return false when the position already holds its original state - the first change wins */
    synchronized boolean record(int x, int y, int z, String state) {
        if (!positions.add(key(x, y, z))) {
            return false;
        }
        blocks.add(new Entry(x, y, z, state));
        dirty = true;
        return true;
    }

    synchronized boolean contains(int x, int y, int z) {
        return positions.contains(key(x, y, z));
    }

    synchronized void recordEntity(String state) {
        entities.add(state);
        dirty = true;
    }

    /** The most recent first change, still in the journal until {@link #removeLast()}. */
    synchronized Entry peekLast() {
        return blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
    }

    synchronized void removeLast() {
        Entry last = blocks.remove(blocks.size() - 1);
        positions.remove(key(last.x, last.y, last.z));
        dirty = true;
    }

    synchronized List<String> takeEntities() {
        List<String> taken = new ArrayList<>(entities);
        entities.clear();
        dirty = true;
        return taken;
    }

    synchronized int size() {
        return blocks.size();
    }

    synchronized boolean isEmpty() {
        return blocks.isEmpty() && entities.isEmpty();
    }

    /** Rebuilds the position set after a read from disk. */
    synchronized TerrainJournal reindexed() {
        positions.clear();
        for (Entry entry : blocks) {
            positions.add(key(entry.x, entry.y, entry.z));
        }
        return this;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static final class Entry {
        int x;
        int y;
        int z;
        String state;

        Entry() {
        }

        Entry(int x, int y, int z, String state) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.state = state;
        }
    }
}
