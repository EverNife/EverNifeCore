package br.com.finalcraft.evernifecore.worlddata.manager.storage;

import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.everydatabase.manager.cache.IDirtyable;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The chunk-grained persisted entity of the {@code SVDataManager}: one row/document/file per
 * {@code (world, chunk)} holding a {@code block -> value} map. Keyed by a String
 * {@code "<world>/<chunkX>/<chunkZ>"} so the same layout works on every backend.
 *
 * <p>The concrete value type {@code O} of a given manager is erased at the class level (the field is
 * {@code Map<String, Object>}); the manager's {@link WorldChunkDataCodec} restores each value to the
 * concrete {@code O} on decode. Keys are the serialized {@link br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos}
 * ({@code "x|y|z"}).
 *
 * <p>Write-back entity: mutate the map through {@link #putValue}/{@link #removeValue} (both flag it
 * dirty) and let the {@code CachingManager} flush the dirty set in a batch.
 */
public class WorldChunkData implements IDirtyable {

    private String chunkKey;
    private Map<String, Object> values = new LinkedHashMap<>();

    @JsonIgnore
    private transient volatile boolean dirty = false;

    public WorldChunkData() {
    }

    public WorldChunkData(String chunkKey) {
        this.chunkKey = chunkKey;
    }

    public String getChunkKey() {
        return chunkKey;
    }

    public void setChunkKey(String chunkKey) {
        this.chunkKey = chunkKey;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values != null ? values : new LinkedHashMap<>();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Block-map mutation (dirty-tracking)
    // -----------------------------------------------------------------------------------------------------------------

    /** The value stored for a serialized block position, or {@code null} when absent. */
    public Object getValue(String blockKey) {
        return values.get(blockKey);
    }

    /** Stores (or replaces) the value for a serialized block position and flags this chunk dirty. */
    public void putValue(String blockKey, Object value) {
        values.put(blockKey, value);
        markDirty();
    }

    /** Removes the value for a serialized block position (if present) and flags this chunk dirty. */
    public void removeValue(String blockKey) {
        if (values.remove(blockKey) != null) {
            markDirty();
        }
    }

    /** Whether this chunk holds no block values (its backing entity can be deleted). */
    @JsonIgnore
    public boolean isEmpty() {
        return values.isEmpty();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Key encode/decode ("<world>/<chunkX>/<chunkZ>")
    // -----------------------------------------------------------------------------------------------------------------

    public static String keyOf(String worldName, ChunkPos chunkPos) {
        return worldName + "/" + chunkPos.getX() + "/" + chunkPos.getZ();
    }

    public static String worldOf(String chunkKey) {
        return chunkKey.substring(0, chunkKey.indexOf('/'));
    }

    public static ChunkPos chunkPosOf(String chunkKey) {
        int p2 = chunkKey.lastIndexOf('/');
        int p1 = chunkKey.lastIndexOf('/', p2 - 1);
        int x = Integer.parseInt(chunkKey.substring(p1 + 1, p2));
        int z = Integer.parseInt(chunkKey.substring(p2 + 1));
        return ChunkPos.of(x, z);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  IDirtyable
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void markClean() {
        this.dirty = false;
    }

    @Override
    public void markDirty() {
        this.dirty = true;
    }
}
