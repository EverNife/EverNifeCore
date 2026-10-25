package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.LogicCases;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.conformance.hytale.HyWorld;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The operations engine on a Hytale server: the server exposes no experience nor statistics, so {@code exp-level} and
 * {@code stat} are unsupported here; a gate on {@code health} passes for the synthetic player, a console command runs,
 * a held game mode is given back. Every player case runs on the player's world thread, where the state is read.
 */
public final class HyLogicSuite implements ConformanceSuite {

    private final ECPluginData plugin;

    public HyLogicSuite(ECPluginData plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "logic";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("stock-variables", () -> LogicCases.stockVariables(false)),
                onWorld("gate-health", ctx, player -> () -> LogicCases.gatePasses(plugin, player,
                        "{\"require\": {\"type\": \"compare\", \"variable\": \"health\", \"op\": \">\", \"amount\": 0}}")),
                onWorld("console-command", ctx, player -> () -> LogicCases.consoleCommand(plugin, player, "ecc list")),
                onWorld("held-gamemode-given-back", ctx, player -> () -> LogicCases.heldGameMode(plugin, player)),
                ConformanceCase.of("daily-window-in-server-zone", LogicCases::dailyWindowInTheServerZone));
    }

    private interface PlayerTask {
        Callable<Verdict> on(FPlayer player);
    }

    private static ConformanceCase onWorld(String id, ConformanceContext ctx, PlayerTask task) {
        return HyRegionsSuite.withPlayer(id, ctx, player -> HyWorld.on(HyWorld.worldOf(HyWorld.player(player)), task.on(player)));
    }
}
