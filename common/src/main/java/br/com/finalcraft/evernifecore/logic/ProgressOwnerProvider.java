package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;

import java.util.Collection;
import java.util.Locale;

/**
 * Answers for one owner {@link #getKind() kind}: how to name an owner of it, who its online members are (what
 * {@code target: group|region-players|world-players} applies an effect to) and whether it still exists. Registered with
 * {@link ProgressOwners#register}. Any thread.
 */
public interface ProgressOwnerProvider {

    /** The kind answered, e.g. {@code group}. */
    String getKind();

    /** A readable name of {@code owner} for a player reading {@code locale}. */
    String describe(ProgressOwner owner, Locale locale);

    /** The players online that belong to {@code owner}; empty when none. */
    Collection<FPlayer> getMembers(ProgressOwner owner);

    /** Whether {@code owner} still exists - a dissolved group does not. */
    boolean isAlive(ProgressOwner owner);
}
