package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.action.EventSource;

import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A yes/no flag. Both the default and the value that wins among equally specific layers
 * ({@link FlagResolvers#preferAmongEquals}) are the flag's own call.
 */
public class TristateFlag extends Flag<Tristate> {

    private final Tristate winner;

    public TristateFlag(FlagKey key, Set<EventSource> needs, Tristate defaultValue, Tristate winner) {
        this(key, needs, defaultValue, winner, null);
    }

    /** @param parent the flag this one refines, see {@link Flag#getParent()}; null for none */
    public TristateFlag(FlagKey key, Set<EventSource> needs, Tristate defaultValue, Tristate winner, @Nullable Flag<Tristate> parent) {
        super(key, FlagTypes.TRISTATE, needs, Objects.requireNonNull(defaultValue, () -> "Flag " + key + " needs a default:"
                + " pass the value it has where no region defines it."), parent);
        this.winner = Objects.requireNonNull(winner, () -> "Flag " + key + " needs a winner: pass the value that wins when"
                + " regions of the same priority disagree.");
    }

    public Tristate getWinner() {
        return winner;
    }

    /** The winner when one of the equally specific leading layers holds it; otherwise the first. */
    @Override
    public FlagResolver<Tristate> getResolver() {
        return FlagResolvers.preferAmongEquals(winner);
    }

    @Override
    public List<FlagResolver<Tristate>> getAcceptedResolvers() {
        return Arrays.asList(getResolver(), FlagResolvers.firstDefined());
    }
}
