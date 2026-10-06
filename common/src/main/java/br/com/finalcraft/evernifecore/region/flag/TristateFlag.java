package br.com.finalcraft.evernifecore.region.flag;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A yes/no flag. Both the default and the value that wins a tie are the flag's own call. */
public class TristateFlag extends RegionFlag<Tristate> {

    private final Tristate winner;

    public TristateFlag(FlagKey key, Set<EventSource> needs, Tristate defaultValue, Tristate winner) {
        super(key, FlagTypes.TRISTATE, needs, Objects.requireNonNull(defaultValue, () -> "Flag " + key + " needs a default:"
                + " pass the value it has where no region defines it."));
        this.winner = Objects.requireNonNull(winner, () -> "Flag " + key + " needs a winner: pass the value that wins when"
                + " regions of the same priority disagree.");
    }

    public Tristate getWinner() {
        return winner;
    }

    /** The winner when any tied value is the winner; otherwise the first. */
    @Override
    public Tristate resolveTie(List<Tristate> values) {
        return values.contains(winner) ? winner : values.get(0);
    }
}
