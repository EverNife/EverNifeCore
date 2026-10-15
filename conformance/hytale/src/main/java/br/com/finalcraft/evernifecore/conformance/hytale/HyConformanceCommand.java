package br.com.finalcraft.evernifecore.conformance.hytale;

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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@code /ecc run <suite|all> [--player <name>] [--cid <id>]}: runs suites on this server and reports every case to
 * the sender and, as an {@code ENCTEST} marker, to the server's stdout. {@code /ecc list} names the suites.
 */
//"ecc" first: on Hytale the core registers only the first alias as a command
@FinalCMD(aliases = {"ecc", "ecoreconformance"}, permission = "evernifecore.conformance")
public class HyConformanceCommand {

    private final List<ConformanceSuite> suites;
    private final File dataFolder;
    private final Supplier<String> versionLabel;
    private final MarkerLine markers;
    private final Consumer<String> log;
    private final Executor resume;

    /**
     * @param markerSink one call per marker line
     * @param log        the human line of every case, kept in the server log: a sender off the console keeps nothing
     * @param resume     where a run resumes after a case that answered on a later tick
     */
    public HyConformanceCommand(List<ConformanceSuite> suites, File dataFolder, Supplier<String> versionLabel, Consumer<String> markerSink,
                                Consumer<String> log, Executor resume) {
        this.suites = suites;
        this.dataFolder = dataFolder;
        this.versionLabel = versionLabel;
        this.markers = new MarkerLine(markerSink);
        this.log = log;
        this.resume = resume;
    }

    @FinalCMD.SubCMD(subcmd = "list")
    public void list(FCommandSender sender) {
        ConformanceContext ctx = new ConformanceContext(null, dataFolder, "");
        for (ConformanceSuite suite : suites) {
            sender.sendMessage(SuiteRunner.HUMAN_PREFIX + suite.getId() + " cases=" + suite.getCases(ctx).size());
        }
    }

    @FinalCMD.SubCMD(subcmd = "run")
    public void run(FCommandSender sender, @Arg("<suite>") String requested,
                    @Arg.Flag("--player") FPlayer player, @Arg.Flag("--cid") String cid) {
        List<ConformanceSuite> chosen = "all".equalsIgnoreCase(requested) ? suites
                : suites.stream().filter(s -> s.getId().equalsIgnoreCase(requested)).collect(Collectors.toList());
        if (chosen.isEmpty()) {
            sender.sendMessage(SuiteRunner.HUMAN_PREFIX + "unknown suite '" + requested + "' - known: "
                    + suites.stream().map(ConformanceSuite::getId).collect(Collectors.toList()) + " or all");
            return;
        }
        ConformanceContext ctx = new ConformanceContext(player, dataFolder, versionLabel.get());
        SuiteRunner runner = SuiteRunner.live(markers, line -> {
            sender.sendMessage(line);
            log.accept(line);
        }, dataFolder, resume);
        String correlation = cid == null ? RunIds.nextCid() : cid;
        //one suite after the other, also when a case of the previous one answered on a later tick
        CompletableFuture<?> chain = CompletableFuture.completedFuture(null);
        for (ConformanceSuite suite : chosen) {
            List<ConformanceCase> cases = suite.getCases(ctx);
            chain = chain.thenCompose(previous -> runner.run(suite.getId(), correlation, cases, ctx.getVersionLabel()));
        }
    }
}
