package br.com.finalcraft.evernifecore.cooldown.server;

import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.GenericCooldown;

/**
 * A cooldown owned by no one in particular but shared by the WHOLE network: it lives as its own row on
 * the shared backend, so every server - whatever its platform - reads and writes the same state. The
 * network counterpart of {@link GenericCooldown}.
 */
public class NetworkCooldown extends Cooldown {

    private final ServerCooldowns store;
    private final ServerCooldownRow row;

    NetworkCooldown(String identifier, ServerCooldownRow row, ServerCooldowns store) {
        super(identifier, row.getEntry());
        this.row = row;
        this.store = store;
        //born PERSISTENT by construction: a network cooldown only means anything if it replicates, and
        //the route to storage is gated on the entry being persistent - a non-persistent one would
        //silently never propagate. Marked directly (not through setPersist, which would file a
        //still-blank row), so the row still only grows once the cooldown is actually started.
        markBornPersistent();
    }

    /**
     * This route's row is a row of the shared collection, written on every mutation that reaches
     * storage - a stop included.
     *
     * <p><b>A stop does NOT drop the row</b>, unlike the local route which simply forgets the
     * cooldown. Replication is what makes an absence ambiguous: on a local store, absent means "never
     * started, or stopped" and reads as free, but on a replicated one it only means "this replica has
     * no opinion", and a peer still holding the old start would win by omission. The zeroed entry a
     * stop leaves behind carries the fact "this was stopped at T" - exactly what makes that peer's
     * older start lose. It is dropped by the retention horizon, not by the stop.</p>
     */
    @Override
    protected void onMutated() {
        store.store(row);
    }
}
