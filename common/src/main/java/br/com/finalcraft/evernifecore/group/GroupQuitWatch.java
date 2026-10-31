package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one quit listener of the group engine: on the bus only while some group has {@link QuitPolicy#LEAVE}, so a
 * server without such a group pays nothing for it.
 */
final class GroupQuitWatch {

    private static final Set<Group> LEAVING = ConcurrentHashMap.newKeySet();
    private static @Nullable ECEventSubscription<ECPlayerQuitEvent> subscription;

    private GroupQuitWatch() {
    }

    /** {@code group} now does (or no longer does) take a quitting member out. */
    static synchronized void track(Group group, boolean leaves) {
        if (leaves && !group.isDissolved()) {
            LEAVING.add(group);
        } else {
            LEAVING.remove(group);
        }
        if (!LEAVING.isEmpty() && subscription == null) {
            subscription = ECEventBus.global().subscribe(ECPlayerQuitEvent.class, event -> onQuit(event.getPlayerData().getUniqueId()));
        } else if (LEAVING.isEmpty() && subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    private static void onQuit(UUID player) {
        for (Group group : new ArrayList<>(LEAVING)) {
            group.remove(player, LeaveCause.QUIT);
        }
    }
}
