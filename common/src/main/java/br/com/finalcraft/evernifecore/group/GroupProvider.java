package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.flag.FlagHolder;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only answers about one source of groups. Every {@link GroupManager} is one; a plugin wrapping another group
 * plugin (clans, parties) registers its own on {@link GroupLookup#register}. Any thread, no I/O.
 */
public interface GroupProvider {

    Collection<GroupKind> getKinds();

    /** The groups {@code player} is an explicit member of. */
    List<GroupId> getGroupIdsOf(UUID player);

    List<GroupId> getGroupIdsOf(UUID player, GroupKind kind);

    boolean exists(GroupId id);

    /** The explicit members; empty when the group is unknown. */
    Set<UUID> getMembers(GroupId id);

    @Nullable UUID getLeader(GroupId id);

    /** The flag holders that follow {@code player}; only a group the core manages carries flags, so empty by default. */
    default Collection<? extends FlagHolder> getHoldersOf(FPlayer player) {
        return Collections.emptyList();
    }
}
