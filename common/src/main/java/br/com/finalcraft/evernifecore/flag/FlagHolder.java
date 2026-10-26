package br.com.finalcraft.evernifecore.flag;

import jakarta.annotation.Nullable;

import java.util.Map;

/**
 * Whatever carries flag values: a region and a group of a region (both {@link FlagScope#PLACE}: they speak about a
 * place), or a holder that follows the player wherever they stand ({@link FlagScope#ACTOR}, registered in
 * {@link ActorHolders}).
 */
public interface FlagHolder {

    /** {@code owner:region}, {@code owner:region/group}, or the holder's own id - what explain and info print. */
    String getHolderId();

    /** PLACE for a region and for a group inside one; ACTOR for a holder that follows the player. */
    FlagScope getHolderScope();

    /** Among holders of the same kind, the highest priority is the most specific. */
    int getPriority();

    /** The live value with its envelope; null when unset or expired. */
    <T> @Nullable FlagValue<T> getFlagValue(Flag<T> flag);

    /** A read-only snapshot of every live value. */
    Map<FlagKey, FlagValue<?>> getFlags();

    /** A PLACE holder takes every flag; an ACTOR holder only an ACTOR flag - a deny-block-break on a party means nothing. */
    default boolean accepts(Flag<?> flag) {
        return getHolderScope() == FlagScope.PLACE || flag.getScope() == FlagScope.ACTOR;
    }

    /** Internal: the store under the holder; {@link FlagRegistry} relinks raw values through it. */
    FlagStore getFlagStore();
}
