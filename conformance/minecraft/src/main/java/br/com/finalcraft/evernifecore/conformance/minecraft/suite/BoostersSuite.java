package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.booster.BoosterDomain;
import br.com.finalcraft.evernifecore.booster.Boosters;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.reward.RewardContext;
import br.com.finalcraft.evernifecore.logic.reward.RewardOccasion;
import br.com.finalcraft.evernifecore.logic.reward.RewardReceipt;
import br.com.finalcraft.evernifecore.logic.reward.RewardScene;
import br.com.finalcraft.evernifecore.logic.reward.RewardTable;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A permission booster for a player who is not online, read from the server's real permission store: with LuckPerms,
 * a player who never joined gets {@value #NODE} written to its storage, and a reward paid to them while offline -
 * boosted by {@code ecc:win} - freezes the base times 2 on its receipt line. Without LuckPerms the same payment keeps
 * the base and the explanation says the offline permissions were not read. Each case is SKIPPED on the other branch.
 * The {@code money} effect's currency is never registered: only the receipt is read.
 */
public final class BoostersSuite implements ConformanceSuite {

    private static final String NODE = "ecc.booster.win.2";
    private static final BigDecimal BASE = new BigDecimal(500);
    private static final long TIMEOUT_MILLIS = 15_000;

    private final ECPluginData plugin;
    private BoosterDomain win;
    private RewardOccasion occasion;

    public BoostersSuite(ECPluginData plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "boosters";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.later("offline-permission-luckperms", () -> {
                    if (!luckPerms()) return CompletableFuture.completedFuture(Verdict.skipped("LuckPerms is not installed here"));
                    return PlayerCases.within(offlineWithLuckPerms(), TIMEOUT_MILLIS);
                }),
                ConformanceCase.later("offline-permission-unread", () -> {
                    if (luckPerms()) return CompletableFuture.completedFuture(Verdict.skipped("LuckPerms is installed here"));
                    return PlayerCases.within(offlineWithoutStore(), TIMEOUT_MILLIS);
                }));
    }

    private CompletableFuture<Verdict> offlineWithLuckPerms() {
        UUID player = UUID.randomUUID();
        //LuckPerms keeps a user it just loaded in memory for a while (cleanupUser does not drop it at once), so whether
        //the payment had to load them is reported, not required
        return LuckPermsStore.grantAndForget(player, NODE).thenCompose(forgotten -> pay(player).thenApply(receipt -> {
            RewardReceipt.Line line = receipt.getStep(0).getLine(player);
            boolean ok = line != null && line.getStatus() == RewardReceipt.Status.DELIVERED
                    && line.getMultiplier().compareTo(new BigDecimal(2)) == 0 && line.getAmount().compareTo(BASE.multiply(new BigDecimal(2))) == 0;
            return Verdict.of(ok, "out-of-memory-before=" + forgotten + " line=" + line);
        }));
    }

    private CompletableFuture<Verdict> offlineWithoutStore() {
        UUID player = UUID.randomUUID();
        return pay(player).thenApply(receipt -> {
            RewardReceipt.Line line = receipt.getStep(0).getLine(player);
            String explained = domain().resolve(player).explain().getText();
            boolean ok = !EverNifeCore.getPlatform().getOfflinePermissions().isSupported() && line != null
                    && line.getMultiplier().compareTo(BigDecimal.ONE) == 0 && line.getAmount().compareTo(BASE) == 0
                    && explained.contains("reads no permission of an offline player");
            return Verdict.of(ok, "line=" + line + " explain=" + explained.replace('\n', '|'));
        });
    }

    private CompletableFuture<RewardReceipt> pay(UUID player) {
        RewardOccasion won = occasion();
        RewardScene scene = RewardScene.builder(won).participant(player, "ecc-offline", BigDecimal.ONE).build();
        RewardTable table;
        try {
            table = RewardTable.parse(plugin, won, new ObjectMapper().readTree(
                    "[{\"effects\": [{\"type\": \"money\", \"amount\": " + BASE + ", \"currency\": \"ecc:points\"}]}]"));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return table.pay(RewardContext.builder(Caller.of(plugin, "conformance/boosters"), ProgressOwner.global())
                .scene(scene).boostedBy(domain()).build());
    }

    private synchronized BoosterDomain domain() {
        if (win == null) {
            win = Boosters.findDomain(plugin.getMetaInfo().getName() + ":win")
                    .orElseGet(() -> BoosterDomain.of(plugin, "win").permission("ecc.booster.win", 2).register());
        }
        return win;
    }

    private synchronized RewardOccasion occasion() {
        if (occasion == null) {
            occasion = RewardOccasion.builder(plugin, "ecc-duel").phase("round").build();
        }
        return occasion;
    }

    private static boolean luckPerms() {
        return Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }

    /** Every LuckPerms class is touched only from here, after the plugin is known to be enabled. */
    private static final class LuckPermsStore {

        /** Writes {@code node} to the stored user and unloads them; completes with whether they are out of memory. */
        static CompletableFuture<Boolean> grantAndForget(UUID player, String node) {
            UserManager users = LuckPermsProvider.get().getUserManager();
            return users.loadUser(player).thenCompose(user -> {
                user.data().add(Node.builder(node).build());
                return users.saveUser(user).thenApply(saved -> user);
            }).thenApply((User user) -> {
                users.cleanupUser(user);
                return users.getUser(player) == null;
            });
        }
    }
}
