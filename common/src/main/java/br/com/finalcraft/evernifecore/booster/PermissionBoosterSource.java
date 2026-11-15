package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.player.IOfflinePermissions;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * {@code evernifecore:permission}: for a domain that declared a {@link BoosterDomain#getPermissionPrefix() prefix}, the
 * permission {@code <prefix>.<factor>} ({@code duels.booster.win.2}) boosts the player's gain of that domain by that
 * factor. A platform cannot list a player's permissions, so the source tests the factors the domain declared
 * ({@link BoosterDomain#getPermissionFactors()}) and keeps the largest the player has; any other is never seen. A
 * node counts only when it is set on the player ({@link FPlayer#isPermissionSet}) and granted, so an op without the
 * node gets no booster, and a node negated outright gets none either. A player who is offline (a context whose owner is
 * that player) is read from the platform's {@link IOfflinePermissions}, only from what it holds in memory - never a
 * blocking load; {@link Boosters#loadPermissions} loads them first, and {@link #getUnreadReason} names what was missed.
 */
public final class PermissionBoosterSource implements BoosterSource {

    private final Caller origin;

    PermissionBoosterSource(ECPluginData core) {
        this.origin = Caller.of(core, "permission");
    }

    @Override
    public String getId() {
        return "evernifecore:permission";
    }

    @Override
    public Collection<Booster> boostersFor(ExecutionContext ctx, BoosterDomain domain) {
        String prefix = domain.getPermissionPrefix();
        if (prefix == null) {
            return Collections.emptyList();
        }
        FPlayer player = ctx.getPlayer();
        UUID playerId = player != null ? player.getUniqueId() : offlinePlayer(ctx);
        if (playerId == null) {
            return Collections.emptyList();
        }
        IOfflinePermissions offline = player == null ? EverNifeCore.getPlatform().getOfflinePermissions() : null;
        String base = prefix + ".";
        List<BigDecimal> factors = domain.getPermissionFactors();
        for (int i = factors.size() - 1; i >= 0; i--) { //ascending list: the first held from the top is the largest
            BigDecimal factor = factors.get(i);
            //2.0 and 2 are the same factor, whichever way the node writes it
            String written = base + factor.toPlainString();
            String stripped = base + factor.stripTrailingZeros().toPlainString();
            boolean held = player != null ? holds(player, written) || holds(player, stripped)
                    : offline.holds(playerId, written) || offline.holds(playerId, stripped);
            if (held) {
                return Collections.singletonList(Booster.on(domain).factor(factor)
                        .forOwner(ProgressOwner.player(playerId)).from(origin).build());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public @Nullable String getUnreadReason(ExecutionContext ctx, BoosterDomain domain) {
        UUID playerId = ctx.getPlayer() == null ? offlinePlayer(ctx) : null;
        if (domain.getPermissionPrefix() == null || playerId == null) {
            return null;
        }
        IOfflinePermissions offline = EverNifeCore.getPlatform().getOfflinePermissions();
        if (!offline.isSupported()) {
            return "this server reads no permission of an offline player (on Bukkit LuckPerms does), so " + domain.getPermissionPrefix()
                    + ".<factor> of " + playerId + " was not read";
        }
        if (!offline.isLoaded(playerId)) {
            return "the permissions of the offline player " + playerId + " were not loaded - a reward payment loads them first,"
                    + " any other gain calls Boosters.loadPermissions before resolving";
        }
        return null;
    }

    /** The player a context without an online player is about: its owner, when that owner is a player. */
    private static @Nullable UUID offlinePlayer(ExecutionContext ctx) {
        ProgressOwner owner = ctx.getOwner();
        return owner != null && owner.isKind(ProgressOwner.PLAYER) ? UUID.fromString(owner.getId()) : null;
    }

    private static boolean holds(FPlayer player, String node) {
        return player.isPermissionSet(node) && player.hasPermission(node);
    }

    @Override
    public String toString() {
        return getId();
    }
}
