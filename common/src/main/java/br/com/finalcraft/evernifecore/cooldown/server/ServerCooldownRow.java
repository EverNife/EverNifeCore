package br.com.finalcraft.evernifecore.cooldown.server;

import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.CooldownEntry;
import br.com.finalcraft.everydatabase.manager.cache.IDirtyable;
import br.com.finalcraft.everydatabase.util.JsonAutoDetectFieldsOnly;
import br.com.finalcraft.everydatabase.versioned.OptimisticLock;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.concurrent.locks.ReentrantLock;

/**
 * One network-wide server cooldown as it is stored: a row of its own, holding the single
 * {@link CooldownEntry} filed under one identifier.
 *
 * <p><b>A row per identifier, not one row holding them all.</b> Every server of the network writes to
 * this collection, so a single row would be one hot key they all contend for - each start of an
 * unrelated cooldown racing every other. Split per identifier, two servers only ever collide when they
 * touch the SAME cooldown, which is precisely the collision that has to be resolved anyway.</p>
 */
@JsonAutoDetectFieldsOnly
public class ServerCooldownRow implements IDirtyable {

    /** The cooldown identifier - this row's storage key. */
    private String identifier;

    /** The stored state. Never null and never swapped out - see {@link #adoptEntryState}. */
    private CooldownEntry entry = new CooldownEntry();

    @OptimisticLock
    private Long lockVersion;

    //volatile: a mutation comes from arbitrary plugin threads while the flush reads and clears the
    //flag on its own - without it a dirty mark could stay invisible to a flush
    @JsonIgnore
    private transient volatile boolean dirty = false;

    /** Guards the conflict-resolution critical section. */
    @JsonIgnore
    private transient ReentrantLock lock = new ReentrantLock();

    ServerCooldownRow() {
        //Jackson no-arg constructor
    }

    ServerCooldownRow(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    /** The state this row stores. Mutate it through a {@link Cooldown} handle, never directly. */
    public CooldownEntry getEntry() {
        return entry;
    }

    // ---- IDirtyable ---------------------------------------------------------------------------

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

    // ---- conflict wiring (called by the flush pipeline, always under the lock) -----------------

    ReentrantLock getLock() {
        return lock;
    }

    /** Resets the optimistic lock so the next write re-creates a row that vanished mid-conflict. */
    void resetLockForRecreate() {
        this.lockVersion = null;
    }

    /** Adopts ONLY the winner's lock version - used when a re-dirtied local state must be kept. */
    void adoptStoredLockVersion(ServerCooldownRow stored) {
        this.lockVersion = stored.lockVersion;
    }

    /**
     * Resolves a concurrent-write race against the state that won it: the two replicas of this
     * cooldown settle by {@link CooldownEntry#latest} - adopted IN PLACE, because the entry instance
     * is shared with every live {@link Cooldown} handle over this row - the winner's lock version is
     * adopted so the retry lands, and the row is re-marked dirty to persist whatever survived.
     */
    void mergeStoredState(ServerCooldownRow stored) {
        this.entry.adoptState(CooldownEntry.latest(this.entry, stored.entry));
        this.lockVersion = stored.lockVersion;
        markDirty();
    }
}
