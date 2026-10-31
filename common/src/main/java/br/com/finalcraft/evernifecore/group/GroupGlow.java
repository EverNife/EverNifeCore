package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who each group with {@code glow-members} TRUE makes glow, kept in step with its online members and its value
 * through the platform's {@link IGroupGlowProvider}.
 *
 * <p>ponytail: a member who comes online is not glowed until the group's next change; listen to the join if that
 * matters.</p>
 */
final class GroupGlow {

    private static final Map<Group, Set<UUID>> GLOWING = new ConcurrentHashMap<>();
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
            return;
        }
        IGroupGlowProvider provider = provider();
        if (provider == null) {
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
    }
}
