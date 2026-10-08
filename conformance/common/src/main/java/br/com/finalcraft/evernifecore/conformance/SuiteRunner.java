package br.com.finalcraft.evernifecore.conformance;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Runs one suite, one case at a time, on the server's main thread. Every run comes out three ways: a human line
 * per case (and a summary) for whoever typed the command, a {@code suite_start -> test_result* -> suite_end}
 * marker sequence for a harness reading the log, and the same events in {@code reports/<runId>.ndjson}.
 *
 * <p>PASS and an expected refusal are {@code ok=true}; FAIL and an unexpected refusal are {@code ok=false} with
 * the detail as {@code error}; a SKIPPED case emits no {@code test_result} and only counts in
 * {@code suite_end.skipped}. A case answered on a later tick ({@link ConformanceCase#later}) does not hold the
 * thread: the run resumes through {@code resume} once it completes.</p>
 */
public final class SuiteRunner {

    public static final String SUITE_PREFIX = "ecore-";
    public static final String HUMAN_PREFIX = "[ECoreConformance] ";

    private final MarkerLine markers;
    private final Consumer<String> human;
    private final File dataFolder;
    private final LongSupplier clock;
    private final Supplier<String> runIds;
    private final Executor resume;

    /** @param resume where a run continues after a case answered later - the main thread on a server */
    public SuiteRunner(MarkerLine markers, Consumer<String> human, File dataFolder, LongSupplier clock, Supplier<String> runIds,
                       Executor resume) {
        this.markers = markers;
        this.human = human;
        this.dataFolder = dataFolder;
        this.clock = clock;
        this.runIds = runIds;
        this.resume = resume;
    }

    public static SuiteRunner live(MarkerLine markers, Consumer<String> human, File dataFolder, Executor resume) {
        return new SuiteRunner(markers, human, dataFolder, System::currentTimeMillis, RunIds::next, resume);
    }

    /** Completes with the summary once the last case answered; already complete when every case answers at once. */
    public CompletableFuture<Summary> run(String suiteId, String cid, List<ConformanceCase> cases, String versionLabel) {
        Run run = new Run(suiteId, cid, cases, versionLabel);
        run.next(0);
        return run.done;
    }

    private final class Run {
        private final String suiteId;
        private final String suite;
        private final String cid;
        private final List<ConformanceCase> cases;
        private final String versionLabel;
        private final String runId = runIds.get();
        private final NdjsonReport report;
        private final long suiteStart = clock.getAsLong();
        private final CompletableFuture<Summary> done = new CompletableFuture<>();
        private int passed, failed, skipped;

        Run(String suiteId, String cid, List<ConformanceCase> cases, String versionLabel) {
            this.suiteId = suiteId;
            this.suite = SUITE_PREFIX + suiteId;
            this.cid = cid;
            this.cases = cases;
            this.versionLabel = versionLabel;
            this.report = new NdjsonReport(dataFolder, runId);
            emit(MarkerLine.Json.event("suite_start", suiteStart, cid)
                    .str("runId", runId).str("suite", suite).num("total", cases.size()));
        }

        void next(int index) {
            for (; index < cases.size(); index++) {
                ConformanceCase testCase = cases.get(index);
                long start = clock.getAsLong();
                CompletableFuture<Verdict> pending = start(testCase);
                if (!pending.isDone()) {
                    int answered = index;
                    pending.whenComplete((verdict, error) -> resume.execute(() -> {
                        record(testCase, start, pending);
                        next(answered + 1);
                    }));
                    return;
                }
                record(testCase, start, pending);
            }
            finish();
        }

        private CompletableFuture<Verdict> start(ConformanceCase testCase) {
            try {
                CompletableFuture<Verdict> pending = testCase.start();
                return pending != null ? pending : CompletableFuture.completedFuture(Verdict.fail("the case answered nothing"));
            } catch (Throwable thrown) {
                return CompletableFuture.completedFuture(Verdict.fail(Verdict.describe(thrown)));
            }
        }

        private void record(ConformanceCase testCase, long start, CompletableFuture<Verdict> answered) {
            Verdict verdict;
            try {
                verdict = answered.get();
                if (verdict == null) verdict = Verdict.fail("the case answered nothing");
            } catch (ExecutionException e) {
                verdict = Verdict.fail(Verdict.describe(e.getCause()));
            } catch (Throwable thrown) {
                verdict = Verdict.fail(Verdict.describe(thrown));
            }
            long end = clock.getAsLong();
            human.accept(HUMAN_PREFIX + suiteId + "/" + testCase.getId() + " " + verdict.getKind() + " " + verdict.getDetail());

            if (verdict.getKind() == Verdict.Kind.SKIPPED) {
                skipped++;
                return;
            }
            boolean ok = verdict.isOk();
            if (ok) passed++; else failed++;
            emit(MarkerLine.Json.event("test_result", end, cid)
                    .str("runId", runId).str("suite", suite).str("test", testCase.getId()).bool("ok", ok)
                    .num("durationMs", end - start).str("error", ok ? null : verdict.getDetail()));
        }

        private void finish() {
            long suiteEnd = clock.getAsLong();
            emit(MarkerLine.Json.event("suite_end", suiteEnd, cid)
                    .str("runId", runId).str("suite", suite).num("passed", passed).num("failed", failed).num("skipped", skipped)
                    .num("durationMs", suiteEnd - suiteStart).str("report", report.getRelativePath()));
            report.close();
            human.accept(HUMAN_PREFIX + suiteId + " summary " + passed + "/" + cases.size() + " (" + skipped + " skipped) on " + versionLabel);
            done.complete(new Summary(runId, cases.size(), passed, failed, skipped));
        }

        private void emit(MarkerLine.Json event) {
            markers.emit(event);
            report.write(event.finish());
        }
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
