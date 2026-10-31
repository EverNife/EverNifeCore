package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;

/** Whose counter an offer's limit is kept under. */
public enum LimitScope {

    /** The trading player's own; the context's owner when it has no player but is a player owner. */
    PLAYER,
    /** The context's owner, which must be a group - {@code ctx.derive().owner(ProgressOwner.group(id))}. */
    GROUP,
    /** One counter for the whole server. */
    GLOBAL;

    /** @throws IllegalStateException when {@code ctx} has no owner of this scope, saying how to build one */
    public ProgressOwner ownerOf(ExecutionContext ctx) {
        switch (this) {
            case GLOBAL:
                return ProgressOwner.global();
            case GROUP:
                if (!ctx.getOwner().isKind(ProgressOwner.GROUP)) {
                    throw new IllegalStateException("The limit of " + ctx.getCaller() + " is counted per group, and the context's owner is "
                            + ctx.getOwner() + ". Build the context with .owner(ProgressOwner.group(groupId)).");
                }
                return ctx.getOwner();
            default:
                if (ctx.getPlayer() != null) {
                    return ProgressOwner.player(ctx.getPlayer().getUniqueId());
                }
                if (!ctx.getOwner().isKind(ProgressOwner.PLAYER)) {
                    throw new IllegalStateException("The limit of " + ctx.getCaller() + " is counted per player, and the context has no"
                            + " player and the owner " + ctx.getOwner() + ". Build it with .player(player) or .owner(ProgressOwner.player(uuid)).");
                }
                return ctx.getOwner();
        }
    }
}
