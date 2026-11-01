package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who each group with {@code glow-members} TRUE makes glow, kept in step with its online members and its value
 * through the platform's {@link IGroupGlowProvider}. While some group glows, a member who logs in starts glowing and one
 * who quits stops (still online, so the platform can clear what it set on them); with none, nothing listens.
 */
final class GroupGlow {

    private static final Map<Group, Set<UUID>> GLOWING = new ConcurrentHashMap<>();
    /** The groups whose value is TRUE now, online members or not: the ones a login may add a glow to. */
    private static final Set<Group> WANTED = ConcurrentHashMap.newKeySet();
    private static @Nullable ECEventSubscription<ECPlayerFullyLoggedInEvent> loginSubscription;
    private static @Nullable ECEventSubscription<ECPlayerQuitEvent> quitSubscription;
    private static volatile boolean warnedUnsupported;

    private GroupGlow() {
    }

    /** The platform's provider, null where nothing can glow. */
    static @Nullable IGroupGlowProvider provider() {
        return EverNifeCore.getProviders().getBaseProvider().provideOrNull(IGroupGlowProvider.class);
    }

    /** Glows the online members of {@code group} while its value is TRUE, and stops glowing whoever no longer should. */
    static void refresh(Group group) {
        update(group, !group.isDissolved() && group.getRegion() == null
                && group.getEffectiveFlag(CoreFlags.GLOW_MEMBERS) == Tristate.TRUE);
    }

    /** {@code group} glows nobody any more: its manager closed. */
    static void forget(Group group) {
        update(group, false);
    }

    private static synchronized void update(Group group, boolean wanted) {
        Set<UUID> had = GLOWING.get(group);
        if (!wanted && had == null) {
            if (WANTED.remove(group)) {
                listenWhileWanted();
            }
            return;
        }
        IGroupGlowProvider provider = provider();
        if (provider == null) {
            WANTED.remove(group);
            listenWhileWanted();
            if (wanted && !warnedUnsupported) {
                warnedUnsupported = true;
                EverNifeCore.getLog().warning("glow-members is TRUE on group " + group.getId() + ", but this platform cannot make"
                        + " players glow (Bukkit 1.9+ only): the flag has no effect here.");
            }
            GLOWING.remove(group);
            return;
        }
        Set<UUID> now = new LinkedHashSet<>();
        if (wanted) {
            for (FPlayer member : group.getOnlineMembers()) {
                now.add(member.getUniqueId());
                if (had == null || !had.contains(member.getUniqueId())) {
                    provider.glow(member, group);
                }
            }
        }
        for (UUID uuid : had == null ? Collections.<UUID>emptySet() : new HashSet<>(had)) {
            FPlayer player = now.contains(uuid) ? null : EverNifeCore.getPlatform().getPlayer(uuid);
            if (player != null) {
                provider.unglow(player, group);
            }
        }
        if (now.isEmpty()) {
            GLOWING.remove(group);
        } else {
            GLOWING.put(group, now);
        }
        if (wanted) {
            WANTED.add(group);
        } else {
            WANTED.remove(group);
        }
        listenWhileWanted();
    }

    private static void listenWhileWanted() {
        if (!WANTED.isEmpty() && loginSubscription == null) {
            loginSubscription = ECEventBus.global().subscribe(ECPlayerFullyLoggedInEvent.class,
                    event -> onLogin(event.getPlayerData().getUniqueId()));
            quitSubscription = ECEventBus.global().subscribe(ECPlayerQuitEvent.class,
                    event -> onQuit(event.getPlayerData().getUniqueId()));
        } else if (WANTED.isEmpty() && loginSubscription != null) {
            loginSubscription.unsubscribe();
            loginSubscription = null;
            if (quitSubscription != null) {
                quitSubscription.unsubscribe();
                quitSubscription = null;
            }
        }
    }

    private static void onLogin(UUID uuid) {
        for (Group group : new ArrayList<>(WANTED)) {
            if (group.isMember(uuid)) {
                refresh(group);
            }
        }
    }

    private static synchronized void onQuit(UUID uuid) {
        IGroupGlowProvider provider = provider();
        FPlayer player = EverNifeCore.getPlatform().getPlayer(uuid);
        for (Map.Entry<Group, Set<UUID>> entry : new ArrayList<>(GLOWING.entrySet())) {
            if (!entry.getValue().remove(uuid)) {
                continue;
            }
            if (provider != null && player != null) {
                provider.unglow(player, entry.getKey());
            }
            if (entry.getValue().isEmpty()) {
                GLOWING.remove(entry.getKey());
            }
        }
    }
}
