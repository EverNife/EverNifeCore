package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.everydatabase.util.JsonAutoDetectFieldsOnly;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * The stored value of a single cooldown. The identifier is NOT part of it: an entry is always reached
 * through a key - the id it sits under in a {@link CooldownBucket}, or its own key in a config file.
 *
 * <p>{@link #timeStart} is an absolute epoch ANCHOR, not a deadline, and that is load-bearing: a read
 * may reinterpret the anchor against a duration of its own (asking a 300s cooldown whether 150s have
 * already passed, to let a VIP through early), which an entry holding only the expiry could never
 * answer.</p>
 *
 * <p>Only {@link Cooldown} is meant to mutate one - it is what stamps {@link #updatedAt} and tells the
 * owning storage.</p>
 */
@JsonAutoDetectFieldsOnly
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter(AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
public class CooldownEntry {

    /** Epoch millis this cooldown was last started; {@code 0} is the tombstone a stop leaves behind. */
    private long timeStart;

    /** How long the cooldown nominally lasts, in millis, as given by whoever started it. */
    private long timeDuration;

    /** Epoch millis of the last mutation - the discriminator {@link #latest} decides by. */
    private long updatedAt;

    /** Whether this entry outlives the process. A non-persistent entry never reaches storage. */
    private boolean persist;

    /**
     * When this cooldown nominally ends. Only a merge tiebreak and a retention bound read it - never a
     * cooldown check, which has to re-derive the end from the anchor and the duration the CALLER asks
     * about.
     */
    public long expiry() {
        return timeStart + timeDuration;
    }

    /** An independent copy of this state. */
    public CooldownEntry copy() {
        return new CooldownEntry(timeStart, timeDuration, updatedAt, persist);
    }

    /**
     * Takes on {@code winner}'s whole state IN PLACE - the replication primitive for an entry instance
     * that is shared with live {@link Cooldown} handles and must never be swapped out (a swap would
     * leave those handles mutating a state nothing stores any more). Not a user mutation: it copies
     * the mutation clock instead of stamping it, so only a converged/merged state belongs here.
     */
    public void adoptState(CooldownEntry winner) {
        if (winner == this) {
            return;
        }
        this.timeStart = winner.timeStart;
        this.timeDuration = winner.timeDuration;
        this.updatedAt = winner.updatedAt;
        this.persist = winner.persist;
    }

    /**
     * The winning state between two replicas of the SAME cooldown: last write wins on
     * {@link #updatedAt}, and the remaining fields break a tie in a fixed order so that
     * {@code latest(a, b)} and {@code latest(b, a)} always settle on equal states. That symmetry is the
     * whole point - replicas converge only while the combination does not depend on the order they
     * arrive in.
     *
     * <p>Deliberately not a max over {@link #expiry()}: that would resurrect a cooldown a newer stop
     * has already tombstoned.</p>
     */
    public static CooldownEntry latest(CooldownEntry a, CooldownEntry b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.updatedAt != b.updatedAt) return a.updatedAt > b.updatedAt ? a : b;
        if (a.expiry() != b.expiry()) return a.expiry() > b.expiry() ? a : b;
        if (a.timeStart != b.timeStart) return a.timeStart > b.timeStart ? a : b;
        if (a.persist != b.persist) return a.persist ? a : b;
        return a;
    }
}
