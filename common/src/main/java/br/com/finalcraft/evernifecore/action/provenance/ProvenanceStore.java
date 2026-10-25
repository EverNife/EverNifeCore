package br.com.finalcraft.evernifecore.action.provenance;

import br.com.finalcraft.evernifecore.blockdata.RefParticipation;
import br.com.finalcraft.evernifecore.blockdata.SVWorldDataManager;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.time.ECClock;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Internal: where a {@link ProvenanceTracker} keeps what it recorded. Blocks go to the owner's storage per chunk, written
 * back on the flush - a placed block is a mutation of the cached chunk, nothing per block reaches the backend. Entities
 * and items live in memory only.
 */
final class ProvenanceStore implements AutoCloseable {

    static final String COLLECTION = "provenance_blocks";
    static final int MAX_ENTITIES = 50_000;

    private final SVWorldDataManager<Provenance> blocks;
    private final Supplier<Duration> retention;
    // ponytail: in-memory only - a mob that outlives a restart reads as NATURAL; a native tag (NBT-API on Bukkit, a
    // component on Hytale) is the upgrade if a product needs it to survive
    private final Map<UUID, Provenance> entities = new LinkedHashMap<UUID, Provenance>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Provenance> eldest) {
            return size() > MAX_ENTITIES;
        }
    };

    ProvenanceStore(ECStorage storage, Supplier<Duration> retention) {
        //ISOLATED: the owner may keep its own SHARED block manager, and the plugin's Ref graph has nothing to find here
        this.blocks = SVWorldDataManager.targeting(Provenance.class)
                .on(storage, COLLECTION)
                .refs(RefParticipation.ISOLATED)
                .build().join();
        this.retention = retention;
    }

    void setBlock(WorldBlockPos pos, Provenance provenance) {
        blocks.setBlock(pos, provenance);
    }

    void removeBlock(WorldBlockPos pos) {
        blocks.removeBlock(pos);
    }

    CompletableFuture<Provenance> getBlock(WorldBlockPos pos) {
        return blocks.getBlock(pos).thenApply(stored -> fresh(pos, stored));
    }

    @Nullable Provenance peekBlock(WorldBlockPos pos) {
        Provenance stored = blocks.peekBlock(pos);
        return stored == null ? null : fresh(pos, stored);
    }

    /** {@code stored}, or NATURAL for no record or one past retention - which is dropped on the way. */
    private Provenance fresh(WorldBlockPos pos, @Nullable Provenance stored) {
        if (stored == null) {
            return Provenance.NATURAL;
        }
        if (stored.getKind() == OriginKind.PLAYER_PLACED && stored.getAt().plus(retention.get()).isBefore(ECClock.now())) {
            blocks.removeBlock(pos);
            return Provenance.NATURAL;
        }
        return stored;
    }

    void setEntity(UUID entity, Provenance provenance) {
        synchronized (entities) {
            entities.put(entity, provenance);
        }
    }

    void forgetEntity(UUID entity) {
        synchronized (entities) {
            entities.remove(entity);
        }
    }

    Provenance getEntity(UUID entity) {
        Provenance found;
        synchronized (entities) {
            found = entities.get(entity);
        }
        return found == null ? Provenance.NATURAL : found;
    }

    CompletableFuture<SVWorldDataManager.FlushReport> flush() {
        return blocks.flush();
    }

    @Override
    public void close() {
        blocks.close();
        synchronized (entities) {
            entities.clear();
        }
    }
}
