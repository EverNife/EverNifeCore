package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/** Cases that act on the {@code --player}: without one they are SKIPPED, and nothing Bukkit is touched. */
final class PlayerCases {

    private PlayerCases() {
    }

    interface PlayerBody {
        Verdict run(Player player) throws Exception;
    }

    interface LaterPlayerBody {
        CompletableFuture<Verdict> start(Player player) throws Exception;
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

    /** {@link #of} for a case that answers on a later tick; the body owns its timeout ({@link #within}). */
    static ConformanceCase later(String id, ConformanceContext ctx, LaterPlayerBody body) {
        return ConformanceCase.later(id, () -> {
            FPlayer named = ctx.getPlayerOrNull();
            if (named == null) {
                return CompletableFuture.completedFuture(Verdict.skipped(ConformanceContext.NEEDS_A_PLAYER));
            }
            Player player = Bukkit.getPlayer(named.getUniqueId());
            if (player == null) {
                return CompletableFuture.completedFuture(Verdict.fail("player " + named.getName() + " is not online"));
            }
            return body.start(player);
        });
    }

    /** A task that never answers is the defect itself, so the case answers FAIL instead of waiting forever. */
    static CompletableFuture<Verdict> within(CompletableFuture<Verdict> answer, long timeoutMillis) {
        FCScheduler.scheduleAsync(() -> answer.complete(Verdict.fail("no answer within " + timeoutMillis + "ms")), timeoutMillis);
        return answer;
    }

    /** {@link Verdict#since} with the version a feature starts at, written as the server reports it. */
    static Verdict since(MCDetailedVersion since, Callable<Verdict> body) {
        return Verdict.since(MCVersion.isHigherEquals(since), since.getLowestRelease(), body);
    }
}
