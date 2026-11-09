package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.logic.reward.core.CoreRewardPieces;

import java.util.UUID;

/** What becomes of an eligible member who is offline when the rule pays. Any thread. */
public interface Absence extends RewardPiece {

    /** {@link #DELIVER}: see {@link Absence#deliver()}; {@link #FORFEIT}: nothing; {@link #REDISTRIBUTE}: the share goes to the online eligible. */
    enum Outcome { DELIVER, FORFEIT, REDISTRIBUTE }

    Outcome onAbsent(UUID member, RewardContext ctx);

    @Override
    default Role getRole() {
        return Role.ABSENCE;
    }

    /**
     * What of the share can wait, already divided, waits as a pending delivery of the member ({@code Deliveries}), claimed on
     * the next login; a {@code command} follows its own {@code offline}, which by default runs it now.
     */
    static Absence deliver() {
        return CoreRewardPieces.DELIVER;
    }

    /** The share is lost. */
    static Absence forfeit() {
        return CoreRewardPieces.FORFEIT;
    }

    /** The share is split among the online eligible by the rule's own distribution; lost when nobody eligible is online. */
    static Absence redistribute() {
        return CoreRewardPieces.REDISTRIBUTE;
    }
}
