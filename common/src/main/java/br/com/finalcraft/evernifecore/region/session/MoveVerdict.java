package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** What the movement tracker says about a move: let it happen, or put the player at {@link #getRevertTo()} instead. */
public final class MoveVerdict {

    private static final MoveVerdict ALLOWED = new MoveVerdict(null);

    private final @Nullable WorldLocPos revertTo;

    private MoveVerdict(@Nullable WorldLocPos revertTo) {
        this.revertTo = revertTo;
    }

    public static MoveVerdict allowed() {
        return ALLOWED;
    }

    public static MoveVerdict revert(WorldLocPos to) {
        return new MoveVerdict(Objects.requireNonNull(to, "to"));
    }

    public boolean isAllowed() {
        return revertTo == null;
    }

    /** Where the platform puts the player - keeping their facing; null when the move is allowed. */
    public @Nullable WorldLocPos getRevertTo() {
        return revertTo;
    }

    @Override
    public String toString() {
        return revertTo == null ? "allowed" : "revert to " + revertTo;
    }
}
