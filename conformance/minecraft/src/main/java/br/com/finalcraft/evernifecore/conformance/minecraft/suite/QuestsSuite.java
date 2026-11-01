package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.QuestCases;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The quest engine on a Bukkit server, over the plugin's in-memory engine ({@link QuestCases}): three
 * {@code BlockBreakEvent}s of the player progress {@code ecc-mine} to 3 while BREAK is listened only during it; a stack
 * of 5 dropped at the player's feet, picked up by the server, counts 5; {@code /ecc quest start|progress|info} round
 * trip through the mounted {@code QuestCommandNode}; {@code %ecc_quest_[ecc-cmd]_progress_[tally]%} answers through
 * the real PlaceholderAPI where it is installed.
 */
public final class QuestsSuite implements ConformanceSuite {

    private static final long TIMEOUT_MILLIS = 10_000;
    private static final long PICKUP_WAIT_TICKS = 60;

    private final ECPluginData plugin;

    public QuestsSuite(ECPluginData plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "quests";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                PlayerCases.later("break-block-progresses", ctx, player -> PlayerCases.within(
                        QuestCases.breakBlockProgresses(plugin, FCBukkitUtil.adapt(player), () -> later(5, () -> {
                            Block under = player.getLocation().getBlock().getRelative(0, -2, 0);
                            for (int i = 0; i < QuestCases.MINE_GOAL; i++) {
                                Bukkit.getPluginManager().callEvent(new BlockBreakEvent(under.getRelative(i, 0, 0), player));
                            }
                            return null;
                        })), TIMEOUT_MILLIS)),
                PlayerCases.later("pickup-counts-stack", ctx, player -> PlayerCases.within(
                        QuestCases.pickupCountsStack(plugin, FCBukkitUtil.adapt(player), () -> later(5, () -> {
                            player.getWorld().dropItem(player.getLocation(), new ItemStack(Material.COBBLESTONE, QuestCases.PICKUP_GOAL));
                            return null;
                        }).thenCompose(dropped -> later(PICKUP_WAIT_TICKS, () -> null))), TIMEOUT_MILLIS)),
                PlayerCases.later("commands-round-trip", ctx, player -> PlayerCases.within(
                        QuestCases.commandsRoundTrip(plugin, FCBukkitUtil.adapt(player), "ecc quest",
                                line -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line), read -> later(2, read)), TIMEOUT_MILLIS)),
                PlayerCases.of("placeholder-answers", ctx, this::placeholderAnswers));
    }

    private Verdict placeholderAnswers(Player player) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return Verdict.skipped("PlaceholderAPI is not installed here");
        }
        QuestEngine engine = QuestCases.engine(plugin);
        QuestDefinition counter = QuestCases.quest(engine, QuestCases.COMMAND);
        ProgressOwner owner = ProgressOwner.player(player.getUniqueId());
        QuestCases.restart(engine, FCBukkitUtil.adapt(player), QuestCases.COMMAND).join();
        engine.progress(owner, counter.getId(), "tally", 4).join();
        String placeholder = "%ecc_quest_[" + QuestCases.COMMAND + "]_progress_[tally]%";
        String parsed = EverNifeCore.getPlatform().parse(FCBukkitUtil.adapt(player), placeholder);
        return Verdict.of("4".equals(parsed), placeholder + " -> " + parsed);
    }

    /** {@code task} on the main thread {@code ticks} from now. */
    private static <T> CompletableFuture<T> later(long ticks, Supplier<T> task) {
        CompletableFuture<T> answer = new CompletableFuture<>();
        Plugin self = Bukkit.getPluginManager().getPlugin("ECoreConformance");
        Bukkit.getScheduler().runTaskLater(self, () -> {
            try {
                answer.complete(task.get());
            } catch (RuntimeException failed) {
                answer.completeExceptionally(failed);
            }
        }, ticks);
        return answer;
    }
}
