package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.logic.Condition;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Who a TRANSIENT group holds without anyone joining by hand: a fixed list or a condition over the online players,
 * taken once at creation ({@link #snapshot}) or followed while the group lives ({@link #live}). Immutable.
 */
public final class Roster {

    private final Set<UUID> members;
    private final @Nullable Condition who;
    private final boolean live;

    private Roster(Set<UUID> members, @Nullable Condition who, boolean live) {
        this.members = members;
        this.who = who;
        this.live = live;
    }

    /** These players, added at creation. */
    public static Roster snapshot(Collection<UUID> members) {
        return new Roster(Collections.unmodifiableSet(new LinkedHashSet<>(members)), null, false);
    }

    /** The online players {@code who} passes for, tested once at creation. */
    public static Roster snapshot(Condition who) {
        return new Roster(Collections.<UUID>emptySet(), Objects.requireNonNull(who, "who"), false);
    }

    /**
     * The online players {@code who} passes for, while the group lives: tested at every sweep, a player who starts
     * passing joins through the join {@link Edge} (one the edge refused is tried again only after failing once), one
     * who stops passing leaves with {@link LeaveCause#ROSTER}.
     */
    public static Roster live(Condition who) {
        return new Roster(Collections.<UUID>emptySet(), Objects.requireNonNull(who, "who"), true);
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public @Nullable Condition getCondition() {
        return who;
    }

    public boolean isLive() {
        return live;
    }
}
