package br.com.finalcraft.evernifecore.player.state;

import java.util.Objects;

/**
 * Something that holds a slot of a player's state in {@link HeldPlayerState} - a flag, a quest stage, a screen - and
 * gives it back when its scope ends. Two holders with the same id are the same holder.
 */
public interface StateHolder {

    /** Unique among holders: {@code evernifecore:game-mode}, {@code finalquests:tutorial/stage:mine}. */
    String getHolderId();

    /** A holder known only by its id. */
    static StateHolder of(String holderId) {
        Objects.requireNonNull(holderId, "holderId");
        return new StateHolder() {
            @Override
            public String getHolderId() {
                return holderId;
            }

            @Override
            public String toString() {
                return holderId;
            }
        };
    }
}
