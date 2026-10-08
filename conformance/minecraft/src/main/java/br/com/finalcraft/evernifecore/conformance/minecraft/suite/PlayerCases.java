package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.Callable;

/** Cases that act on the {@code --player}: without one they are SKIPPED, and nothing Bukkit is touched. */
final class PlayerCases {

    private PlayerCases() {
    }

    interface PlayerBody {
        Verdict run(Player player) throws Exception;
    }

    static ConformanceCase of(String id, ConformanceContext ctx, PlayerBody body) {
        return ConformanceCase.of(id, () -> {
            FPlayer named = ctx.getPlayerOrNull();
            if (named == null) {
                return Verdict.skipped(ConformanceContext.NEEDS_A_PLAYER);
            }
            Player player = Bukkit.getPlayer(named.getUniqueId());
            if (player == null) {
                return Verdict.fail("player " + named.getName() + " is not online");
            }
            return body.run(player);
        });
    }

    /** {@link Verdict#since} with the version a feature starts at, written as the server reports it. */
    static Verdict since(MCDetailedVersion since, Callable<Verdict> body) {
        return Verdict.since(MCVersion.isHigherEquals(since), since.getLowestRelease(), body);
    }
}
