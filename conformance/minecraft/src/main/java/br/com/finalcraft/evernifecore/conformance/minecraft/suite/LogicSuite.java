package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.LogicCases;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The operations engine on a Bukkit server: every era reads and writes experience, so {@code exp-level} and
 * {@code stat} are supported; a gate passes for the bot, a console command runs (its {@code say} line is in the
 * server log), a held game mode is given back. The bot is op and in creative when the run starts.
 */
public final class LogicSuite implements ConformanceSuite {

    private final ECPluginData plugin;

    public LogicSuite(ECPluginData plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "logic";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("stock-variables", () -> LogicCases.stockVariables(true)),
                PlayerCases.of("gate-permission-and-exp-level", ctx, player -> LogicCases.gatePasses(plugin, ctx.getPlayerOrNull(),
                        "{\"require\": [{\"type\": \"permission\", \"node\": \"evernifecore.conformance\"},"
                                + " {\"type\": \"compare\", \"variable\": \"exp-level\", \"op\": \">=\", \"amount\": 0}]}")),
                PlayerCases.of("exp-level-reads-what-was-set", ctx, player ->
                        LogicCases.variableRoundTrip(plugin, ctx.getPlayerOrNull(), "exp-level", new BigDecimal(5))),
                PlayerCases.of("exp-points-reads-what-was-set", ctx, player ->
                        LogicCases.variableRoundTrip(plugin, ctx.getPlayerOrNull(), "exp-points", new BigDecimal(37))),
                PlayerCases.of("console-command", ctx, player -> LogicCases.consoleCommand(plugin, ctx.getPlayerOrNull(),
                        "say ENCTEST-logic " + UUID.randomUUID().toString().substring(0, 8))),
                PlayerCases.of("held-gamemode-given-back", ctx, player -> LogicCases.heldGameMode(plugin, ctx.getPlayerOrNull())),
                ConformanceCase.of("daily-window-in-server-zone", LogicCases::dailyWindowInTheServerZone));
    }
}
