package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupMember;
import br.com.finalcraft.evernifecore.group.JoinCause;
import jakarta.annotation.Nullable;

import java.util.UUID;

/** A player became a member. */
public class GroupMemberJoinedEvent extends GroupEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GroupMemberJoinedEvent.class);
    }

    private final UUID player;
    private final @Nullable FPlayer fPlayer;
    private final JoinCause cause;
    private final GroupMember member;

    public GroupMemberJoinedEvent(Group group, UUID player, @Nullable FPlayer fPlayer, JoinCause cause, GroupMember member) {
        super(group);
        this.player = player;
        this.fPlayer = fPlayer;
        this.cause = cause;
        this.member = member;
    }

    public UUID getPlayer() {
        return player;
    }

    /** The player when the join came through them; null for the owner's {@code add}. */
    public @Nullable FPlayer getFPlayer() {
        return fPlayer;
    }

    public JoinCause getCause() {
        return cause;
    }

    public GroupMember getMember() {
        return member;
    }
}
