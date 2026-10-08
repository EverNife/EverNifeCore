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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@code /ecc run|write|read <suite|all>}: runs suites on this server and reports every case to the sender, to the
 * log as a human line, and to the log again as an {@code ENCTEST} marker. {@code write} and {@code read} are the
 * two halves of the suites whose subject has to survive a restart.
 */
@FinalCMD(aliases = {"ecoreconformance", "ecc"}, permission = "evernifecore.conformance")
public class ConformanceCommand {

    private final List<ConformanceSuite> suites;
    private final File dataFolder;
    private final Supplier<String> versionLabel;
    private final Consumer<String> log;
    private final MarkerLine markers;

    /**
     * @param versionLabel asked only when a suite runs, for its summary line
     * @param log          the plugin logger's {@code info}: markers have to reach the server log one line each
     */
    public ConformanceCommand(List<ConformanceSuite> suites, File dataFolder, Supplier<String> versionLabel, Consumer<String> log) {
        this.suites = suites;
        this.dataFolder = dataFolder;
        this.versionLabel = versionLabel;
        this.log = log;
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
        }, dataFolder);
        String correlation = cid == null ? RunIds.nextCid() : cid;

        int ran = 0;
        for (ConformanceSuite suite : chosen) {
            List<ConformanceCase> cases = casesOf.apply(suite, ctx);
            if (cases.isEmpty()) continue;
            runner.run(suite.getId(), correlation, cases, ctx.getVersionLabel());
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
