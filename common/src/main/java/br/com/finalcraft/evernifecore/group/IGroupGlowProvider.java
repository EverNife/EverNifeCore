package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;

/**
 * Makes the members of a group with {@code glow-members} TRUE glow, for everyone to see. A platform that can registers
 * one with {@code EverNifeCore.getProviders().getBaseProvider().register(IGroupGlowProvider.class, ...)} (Bukkit 1.9+);
 * without one the flag has no effect and says so once. The core calls it on the thread of the change.
 */
public interface IGroupGlowProvider {

    /** {@code group} now glows {@code player} - called once per group, so a player may glow for several. */
    void glow(FPlayer player, Group group);

    /** {@code group} no longer glows {@code player}; the player stops glowing once no group glows them. */
    void unglow(FPlayer player, Group group);
}
