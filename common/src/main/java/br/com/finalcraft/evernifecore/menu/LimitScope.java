package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import jakarta.annotation.Nullable;

/** Whose counter an offer's limit is kept under. */
public enum LimitScope {

    /** The trading player's own; the context's owner when it has no player but is a player owner. */
    PLAYER,
    /** The context's owner, which must be a group - {@code ctx.derive().owner(ProgressOwner.group(id))}. */
    GROUP,
    /** One counter for the whole server. */
    GLOBAL;

    /** The owner whose counter this scope picks out of {@code ctx}; null when {@code ctx} has none of this scope. */
    public @Nullable ProgressOwner findOwner(ExecutionContext ctx) {
        switch (this) {
            case GLOBAL:
                return ProgressOwner.global();
            case GROUP:
                return ctx.getOwner().isKind(ProgressOwner.GROUP) ? ctx.getOwner() : null;
            default:
                if (ctx.getPlayer() != null) {
                    return ProgressOwner.player(ctx.getPlayer().getUniqueId());
                }
                return ctx.getOwner().isKind(ProgressOwner.PLAYER) ? ctx.getOwner() : null;
        }
    }

    /** @throws IllegalStateException when {@code ctx} has no owner of this scope, saying how to build one */
    public ProgressOwner ownerOf(ExecutionContext ctx) {
        ProgressOwner owner = findOwner(ctx);
        if (owner != null) {
            return owner;
        }
        if (this == GROUP) {
            throw new IllegalStateException("The limit of " + ctx.getCaller() + " is counted per group, and the context's owner is "
                    + ctx.getOwner() + ". Build the context with .owner(ProgressOwner.group(groupId)).");
        }
        throw new IllegalStateException("The limit of " + ctx.getCaller() + " is counted per player, and the context has no"
                + " player and the owner " + ctx.getOwner() + ". Build it with .player(player) or .owner(ProgressOwner.player(uuid)).");
    }
}
