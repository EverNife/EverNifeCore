package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.logic.reward.core.CoreRewardPieces;

import java.util.UUID;

/** What becomes of an eligible member who is offline when the rule pays. Any thread. */
public interface Absence extends RewardPiece {

    /** {@link #DELIVER}: a pending delivery; {@link #FORFEIT}: nothing; {@link #REDISTRIBUTE}: the share goes to the online eligible. */
    enum Outcome { DELIVER, FORFEIT, REDISTRIBUTE }

    Outcome onAbsent(UUID member, RewardContext ctx);

    @Override
    default Role getRole() {
        return Role.ABSENCE;
    }

    /** The share, already divided, waits as a pending delivery of the member ({@code Deliveries}), claimed on the next login. */
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
