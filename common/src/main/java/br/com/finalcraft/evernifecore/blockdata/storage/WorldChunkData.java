package br.com.finalcraft.evernifecore.blockdata.storage;

import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.everydatabase.manager.cache.IDirtyable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The chunk-grained persisted entity: one row/document/file per {@code (world, chunk)} holding a
 * {@code block -> value} map, keyed by {@code "<world>/<chunkX>/<chunkZ>"} so the same layout works on every
 * backend. Block keys are the serialized
 * {@link br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos} ({@code "x|y|z"}).
 *
 * <p>Write-back entity: the instance living in the manager's cache IS the state. Mutate it through
 * {@link #putValue}/{@link #removeValue} (both flag it dirty) while holding its monitor, and let the flush
 * persist a {@link #copyForSave()} taken under that same monitor - which is what keeps an encode from
 * iterating a map another writer is mutating.
 *
 * <p>{@code O} is erased at the class level, so a plain Jackson read hands back raw {@code LinkedHashMap}
 * values; {@link WorldChunkDataCodec} re-binds the map to the concrete type on decode.
 *
 * @param <O> the block-value type of the owning manager
 */
public class WorldChunkData<O> implements IDirtyable {

    /**
     * The reserved key of the grid sentinel - the one entity of a collection that holds no blocks and
     * carries {@link #getGridChunkSize()} instead. It has no world segment, so {@link #worldOf} and
     * {@link #chunkPosOf} reject it; a scan skips it through {@link #isMetaKey(String)}.
     */
    public static final String META_KEY = "$meta";

    private String chunkKey;
    private Map<String, O> values = new LinkedHashMap<>();

    /**
     * The chunk size the collection was written with. Present only on the {@link #META_KEY} sentinel, hence
     * omitted from the encoded form (rather than written as an explicit null) on every real chunk.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer gridChunkSize;

    @JsonIgnore
    private transient volatile boolean dirty = false;

    public WorldChunkData() {
    }

    public WorldChunkData(String chunkKey) {
        this.chunkKey = chunkKey;
    }

    /** The sentinel entity of a collection whose chunks are {@code gridChunkSize} blocks wide. */
    public static <O> WorldChunkData<O> metaSentinel(int gridChunkSize) {
        WorldChunkData<O> sentinel = new WorldChunkData<>(META_KEY);
        sentinel.setGridChunkSize(gridChunkSize);
        return sentinel;
    }

    public String getChunkKey() {
        return chunkKey;
    }

    public void setChunkKey(String chunkKey) {
        this.chunkKey = chunkKey;
    }

    /** The LIVE block map - read or mutate it only under this entity's monitor, else {@link #snapshotValues()}. */
    public Map<String, O> getValues() {
        return values;
    }

    public void setValues(Map<String, O> values) {
        this.values = values != null ? values : new LinkedHashMap<>();
    }

    /** The grid chunk size this collection was written with, or {@code null} on anything but the sentinel. */
    public Integer getGridChunkSize() {
        return gridChunkSize;
    }

    public void setGridChunkSize(Integer gridChunkSize) {
        this.gridChunkSize = gridChunkSize;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Block-map mutation (dirty-tracking)
    // -----------------------------------------------------------------------------------------------------------------

    /** The value stored for a serialized block position, or {@code null} when absent. */
    public O getValue(String blockKey) {
        return values.get(blockKey);
    }

    /**
     * Stores (or replaces) the value for a serialized block position and flags this chunk dirty.
     *
     * @return the value that was stored there, or {@code null} when the block was absent
     */
    public O putValue(String blockKey, O value) {
        O previous = values.put(blockKey, value);
        markDirty();
        return previous;
    }

    /**
     * Removes the value for a serialized block position, flagging this chunk dirty only if one was there.
     *
     * @return the removed value, or {@code null} when the block was absent
     */
    public O removeValue(String blockKey) {
        O removed = values.remove(blockKey);
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    /** Whether this chunk holds no block values (its backing entity can be deleted). */
    @JsonIgnore
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** A shallow copy of the block map - take it under this entity's monitor to get a consistent one. */
    public Map<String, O> snapshotValues() {
        return new LinkedHashMap<>(values);
    }

    /**
     * A detached copy carrying this entity's key and a {@link #snapshotValues() snapshot} of its blocks: what
     * the flush encodes, so the cached instance stays the live one the writers hold.
     */
    public WorldChunkData<O> copyForSave() {
        WorldChunkData<O> copy = new WorldChunkData<>(chunkKey);
        copy.setValues(snapshotValues());
        copy.setGridChunkSize(gridChunkSize);
        return copy;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Key encode/decode ("<world>/<chunkX>/<chunkZ>")
    // -----------------------------------------------------------------------------------------------------------------

    public static String keyOf(String worldName, ChunkPos chunkPos) {
        return worldName + "/" + chunkPos.getX() + "/" + chunkPos.getZ();
    }

    /**
     * The world segment of a chunk key: everything before the two coordinates, found by walking the last two
     * separators back with {@code lastIndexOf}, so a world whose own name contains {@code '/'} round-trips.
     */
    public static String worldOf(String chunkKey) {
        int chunkZAt = chunkKey.lastIndexOf('/');
        int chunkXAt = chunkZAt > 0 ? chunkKey.lastIndexOf('/', chunkZAt - 1) : -1;
        if (chunkXAt <= 0) {
            throw notAChunkKey(chunkKey);
        }
        return chunkKey.substring(0, chunkXAt);
    }

    /** The chunk coordinates of a chunk key - the two segments {@link #worldOf(String)} leaves out. */
    public static ChunkPos chunkPosOf(String chunkKey) {
        int chunkZAt = chunkKey.lastIndexOf('/');
        int chunkXAt = chunkZAt > 0 ? chunkKey.lastIndexOf('/', chunkZAt - 1) : -1;
        if (chunkXAt <= 0) {
            throw notAChunkKey(chunkKey);
        }
        int x = Integer.parseInt(chunkKey.substring(chunkXAt + 1, chunkZAt));
        int z = Integer.parseInt(chunkKey.substring(chunkZAt + 1));
        return ChunkPos.of(x, z);
    }

    /** Whether a key is reserved for store metadata instead of naming a chunk - see {@link #META_KEY}. */
    public static boolean isMetaKey(String key) {
        return key != null && key.startsWith(META_KEY);
    }

    private static IllegalArgumentException notAChunkKey(String key) {
        return new IllegalArgumentException("'" + key + "' is not a chunk key. The format is"
                + " \"<world>/<chunkX>/<chunkZ>\", so a key needs a non-empty world name and two '/'"
                + " separators. The reserved '" + META_KEY + "' key has neither - drop it with"
                + " WorldChunkData.isMetaKey(String) before parsing keys that came from a scan.");
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
