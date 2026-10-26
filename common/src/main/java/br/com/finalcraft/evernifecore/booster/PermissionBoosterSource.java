package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@code evernifecore:permission}: the permission {@code vip.booster.<variable>.<factor>}
 * ({@code vip.booster.money.1.25}) boosts the player's gain by that factor. A platform cannot list a player's
 * permissions, so the source tests the factors of {@code config.yml} {@code Boosters.permission-factors} and keeps the
 * largest the player has; a factor not in that list is never seen.
 */
public final class PermissionBoosterSource implements BoosterSource {

    public static final String PREFIX = "vip.booster.";

    static final List<BigDecimal> DEFAULT_FACTORS = Collections.unmodifiableList(Arrays.asList(
            new BigDecimal("1.1"), new BigDecimal("1.25"), new BigDecimal("1.5"), new BigDecimal("2.0"), new BigDecimal("3.0")));

    private final Caller origin;

    PermissionBoosterSource(ECPluginData core) {
        this.origin = Caller.of(core, "permission");
    }

    @Override
    public String getId() {
        return "evernifecore:permission";
    }

    @Override
    public Collection<Booster> boostersFor(ExecutionContext ctx, String variableId) {
        FPlayer player = ctx.getPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        String base = PREFIX + Boosters.shortForm(variableId) + ".";
        List<BigDecimal> factors = Boosters.getPermissionFactors();
        for (int i = factors.size() - 1; i >= 0; i--) { //ascending list: the first held from the top is the largest
            BigDecimal factor = factors.get(i);
            //2.0 and 2 are the same factor, whichever way the node writes it
            if (player.hasPermission(base + factor.toPlainString()) || player.hasPermission(base + factor.stripTrailingZeros().toPlainString())) {
                return Collections.singletonList(Booster.on(variableId).factor(factor)
                        .forOwner(ProgressOwner.player(player.getUniqueId())).from(origin).build());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return getId();
    }
}
