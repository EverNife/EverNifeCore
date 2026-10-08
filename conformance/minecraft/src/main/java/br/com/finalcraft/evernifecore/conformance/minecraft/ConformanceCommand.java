package br.com.finalcraft.evernifecore.conformance.minecraft;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.MarkerLine;
import br.com.finalcraft.evernifecore.conformance.RunIds;
import br.com.finalcraft.evernifecore.conformance.SuiteRunner;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.GuiSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.SelectionClickSuite;
import br.com.finalcraft.evernifecore.conformance.minecraft.suite.TextMetricsSuite;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@code /ecc run|write|read <suite|all>}: runs suites on this server and reports every case to the sender, to the
 * log as a human line, and to the log again as an {@code ENCTEST} marker. {@code write} and {@code read} are the
 * two halves of the suites whose subject has to survive a restart. {@code /ecc gui <screen> <player>} opens one
 * sample screen of the {@code gui} suite and leaves it open, to be read from outside the server; {@code click} and
 * {@code text} prepare what the {@code selection-click} and {@code text-metrics} suites need from a real client.
 */
@FinalCMD(aliases = {"ecoreconformance", "ecc"}, permission = "evernifecore.conformance")
public class ConformanceCommand {

    private final List<ConformanceSuite> suites;
    private final File dataFolder;
    private final Supplier<String> versionLabel;
    private final Consumer<String> log;
    private final Executor mainThread;
    private final MarkerLine markers;

    /**
     * @param versionLabel asked only when a suite runs, for its summary line
     * @param log          the plugin logger's {@code info}: markers have to reach the server log one line each
     * @param mainThread   where a run resumes after a case that answered on a later tick
     */
    public ConformanceCommand(List<ConformanceSuite> suites, File dataFolder, Supplier<String> versionLabel, Consumer<String> log,
                              Executor mainThread) {
        this.suites = suites;
        this.dataFolder = dataFolder;
        this.versionLabel = versionLabel;
        this.log = log;
        this.mainThread = mainThread;
        this.markers = new MarkerLine(log);
    }

    @FinalCMD.SubCMD(subcmd = "list")
    public void list(FCommandSender sender) {
        ConformanceContext ctx = new ConformanceContext(null, dataFolder, "");
        for (ConformanceSuite suite : suites) {
            sender.sendMessage(SuiteRunner.HUMAN_PREFIX + suite.getId() + " cases=" + suite.getCases(ctx).size()
                    + " write=" + suite.getWriteCases(ctx).size() + " read=" + suite.getReadCases(ctx).size());
        }
    }

    @FinalCMD.SubCMD(subcmd = "run")
    public void run(FCommandSender sender, @Arg("<suite>") String suite,
                    @Arg.Flag("--player") FPlayer player, @Arg.Flag("--cid") String cid) {
        execute(sender, suite, player, cid, "run", ConformanceSuite::getCases);
    }

    @FinalCMD.SubCMD(subcmd = "write")
    public void write(FCommandSender sender, @Arg("<suite>") String suite,
                      @Arg.Flag("--player") FPlayer player, @Arg.Flag("--cid") String cid) {
        execute(sender, suite, player, cid, "write", ConformanceSuite::getWriteCases);
    }

    @FinalCMD.SubCMD(subcmd = "read")
    public void read(FCommandSender sender, @Arg("<suite>") String suite,
                     @Arg.Flag("--player") FPlayer player, @Arg.Flag("--cid") String cid) {
        execute(sender, suite, player, cid, "read", ConformanceSuite::getReadCases);
    }

    @FinalCMD.SubCMD(subcmd = "gui")
    public void gui(FCommandSender sender, @Arg("<screen>") String screen, @Arg("<player>") FPlayer player) {
        if (!GuiSuite.getScreenIds().contains(screen)) {
            sender.sendMessage(SuiteRunner.HUMAN_PREFIX + "unknown screen '" + screen + "' - known: " + GuiSuite.getScreenIds());
            return;
        }
        Player viewer = online(sender, player, "gui " + screen);
        if (viewer == null) return;
        GuiSuite.open(screen, viewer).whenComplete((view, refused) -> sender.sendMessage(SuiteRunner.HUMAN_PREFIX + "gui " + screen
                + (refused == null ? " opened for " + viewer.getName() : " refused: " + Verdict.describe(refused))));
    }

    /** Hands {@code player} the selection wand and records their block clicks for the {@code selection-click} suite. */
    @FinalCMD.SubCMD(subcmd = "click")
    public void click(FCommandSender sender, @Arg("<player>") FPlayer player) {
        Player clicker = online(sender, player, "click");
        if (clicker == null) return;
        SelectionClickSuite.arm(clicker);
        sender.sendMessage(SuiteRunner.HUMAN_PREFIX + "click armed for " + clicker.getName() + " - wand given, click a block");
    }

    /** Sends {@code player} the two chat lines of known width the {@code text-metrics} suite measures. */
    @FinalCMD.SubCMD(subcmd = "text")
    public void text(FCommandSender sender, @Arg("<player>") FPlayer player) {
        Player reader = online(sender, player, "text");
        if (reader == null) return;
        sender.sendMessage(SuiteRunner.HUMAN_PREFIX + "text sent to " + reader.getName() + ": " + TextMetricsSuite.sendLines(reader));
    }

    private static Player online(FCommandSender sender, FPlayer player, String subcommand) {
        Player found = Bukkit.getPlayer(player.getUniqueId());
        if (found == null) {
            sender.sendMessage(SuiteRunner.HUMAN_PREFIX + subcommand + " needs " + player.getName() + " online");
        }
        return found;
    }

    private void execute(FCommandSender sender, String requested, FPlayer player, String cid, String half,
                         BiFunction<ConformanceSuite, ConformanceContext, List<ConformanceCase>> casesOf) {
        List<ConformanceSuite> chosen = pick(requested);
        if (chosen.isEmpty()) {
            sender.sendMessage(SuiteRunner.HUMAN_PREFIX + "unknown suite '" + requested + "' - known: " + getIds() + " or all");
            return;
        }
        ConformanceContext ctx = new ConformanceContext(player, dataFolder, versionLabel.get());
        SuiteRunner runner = SuiteRunner.live(markers, line -> {
            sender.sendMessage(line);
            //the console already echoes what it is sent into the log
            if (sender.isPlayer()) log.accept(line);
        }, dataFolder, mainThread);
        String correlation = cid == null ? RunIds.nextCid() : cid;

        //one suite after the other, also when a case of the previous one answered on a later tick
        CompletableFuture<?> chain = CompletableFuture.completedFuture(null);
        int ran = 0;
        for (ConformanceSuite suite : chosen) {
            List<ConformanceCase> cases = casesOf.apply(suite, ctx);
            if (cases.isEmpty()) continue;
            chain = chain.thenCompose(previous -> runner.run(suite.getId(), correlation, cases, ctx.getVersionLabel()));
            ran++;
        }
        if (ran == 0) {
            sender.sendMessage(SuiteRunner.HUMAN_PREFIX + "'" + requested + "' has no " + half + " half - suites with one: "
                    + suites.stream().filter(s -> !casesOf.apply(s, ctx).isEmpty()).map(ConformanceSuite::getId).collect(Collectors.toList()));
        }
    }

    private List<ConformanceSuite> pick(String requested) {
        if ("all".equalsIgnoreCase(requested)) return suites;
        for (ConformanceSuite suite : suites) {
            if (suite.getId().equalsIgnoreCase(requested)) return Collections.singletonList(suite);
        }
        return Collections.emptyList();
    }

    private List<String> getIds() {
        List<String> ids = new ArrayList<>();
        for (ConformanceSuite suite : suites) ids.add(suite.getId());
        return ids;
    }
}
