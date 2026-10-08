package br.com.finalcraft.evernifecore.conformance;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Runs one suite, one case at a time, on the caller's thread - the server's main thread, which the command
 * already is. Every run comes out three ways: a human line per case (and a summary) for whoever typed the
 * command, a {@code suite_start -> test_result* -> suite_end} marker sequence for a harness reading the log, and
 * the same events in {@code reports/<runId>.ndjson}.
 *
 * <p>PASS and an expected refusal are {@code ok=true}; FAIL and an unexpected refusal are {@code ok=false} with
 * the detail as {@code error}; a SKIPPED case emits no {@code test_result} and only counts in
 * {@code suite_end.skipped}.</p>
 */
public final class SuiteRunner {

    public static final String SUITE_PREFIX = "ecore-";
    public static final String HUMAN_PREFIX = "[ECoreConformance] ";

    private final MarkerLine markers;
    private final Consumer<String> human;
    private final File dataFolder;
    private final LongSupplier clock;
    private final Supplier<String> runIds;

    public SuiteRunner(MarkerLine markers, Consumer<String> human, File dataFolder, LongSupplier clock, Supplier<String> runIds) {
        this.markers = markers;
        this.human = human;
        this.dataFolder = dataFolder;
        this.clock = clock;
        this.runIds = runIds;
    }

    public static SuiteRunner live(MarkerLine markers, Consumer<String> human, File dataFolder) {
        return new SuiteRunner(markers, human, dataFolder, System::currentTimeMillis, RunIds::next);
    }

    public Summary run(String suiteId, String cid, List<ConformanceCase> cases, String versionLabel) {
        String runId = runIds.get();
        String suite = SUITE_PREFIX + suiteId;
        NdjsonReport report = new NdjsonReport(dataFolder, runId);
        long suiteStart = clock.getAsLong();

        emit(report, MarkerLine.Json.event("suite_start", suiteStart, cid)
                .str("runId", runId).str("suite", suite).num("total", cases.size()));

        int passed = 0, failed = 0, skipped = 0;
        for (ConformanceCase testCase : cases) {
            long start = clock.getAsLong();
            Verdict verdict;
            try {
                verdict = testCase.run();
            } catch (Throwable thrown) {
                verdict = Verdict.fail(Verdict.describe(thrown));
            }
            long end = clock.getAsLong();
            human.accept(HUMAN_PREFIX + suiteId + "/" + testCase.getId() + " " + verdict.getKind() + " " + verdict.getDetail());

            if (verdict.getKind() == Verdict.Kind.SKIPPED) {
                skipped++;
                continue;
            }
            boolean ok = verdict.isOk();
            if (ok) passed++; else failed++;
            emit(report, MarkerLine.Json.event("test_result", end, cid)
                    .str("runId", runId).str("suite", suite).str("test", testCase.getId()).bool("ok", ok)
                    .num("durationMs", end - start).str("error", ok ? null : verdict.getDetail()));
        }

        long suiteEnd = clock.getAsLong();
        emit(report, MarkerLine.Json.event("suite_end", suiteEnd, cid)
                .str("runId", runId).str("suite", suite).num("passed", passed).num("failed", failed).num("skipped", skipped)
                .num("durationMs", suiteEnd - suiteStart).str("report", report.getRelativePath()));
        report.close();

        human.accept(HUMAN_PREFIX + suiteId + " summary " + passed + "/" + cases.size() + " (" + skipped + " skipped) on " + versionLabel);
        return new Summary(runId, cases.size(), passed, failed, skipped);
    }

    private void emit(NdjsonReport report, MarkerLine.Json event) {
        markers.emit(event);
        report.write(event.finish());
    }

    public static final class Summary {
        public final String runId;
        public final int total;
        public final int passed;
        public final int failed;
        public final int skipped;

        Summary(String runId, int total, int passed, int failed, int skipped) {
            this.runId = runId;
            this.total = total;
            this.passed = passed;
            this.failed = failed;
            this.skipped = skipped;
        }
    }
}
