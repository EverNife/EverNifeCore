package br.com.finalcraft.evernifecore.conformance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuiteRunnerTest {

    private static final long T = 1700000000000L;

    @TempDir
    Path dataFolder;

    private final List<String> log = new ArrayList<>();
    private final List<String> human = new ArrayList<>();

    private SuiteRunner.Summary run(ConformanceCase... cases) {
        return runIn(dataFolder.toFile(), cases);
    }

    private SuiteRunner.Summary runIn(File folder, ConformanceCase... cases) {
        SuiteRunner runner = new SuiteRunner(new MarkerLine(log::add), human::add, folder, () -> T, () -> "r-1", Runnable::run);
        return runner.run("demo", "c-1", Arrays.asList(cases), "test-version").join();
    }

    private List<String> markers(String event) {
        return log.stream().filter(line -> line.contains("\"e\":\"" + event + "\"")).collect(Collectors.toList());
    }

    @Test
    void passAndAnExpectedRefusalAreOk() {
        SuiteRunner.Summary summary = run(
                ConformanceCase.of("a", () -> Verdict.pass("read 1")),
                ConformanceCase.of("b", () -> Verdict.refused(true, "pdc below 1.14")));

        assertEquals(2, summary.passed, String.join("\n", log));
        assertEquals(0, summary.failed);
        assertTrue(markers("test_result").stream().allMatch(line -> line.contains("\"ok\":true,") && line.endsWith("\"error\":null}")),
                String.join("\n", log));
    }

    @Test
    void failAndAnUnexpectedRefusalAreNotOkAndCarryTheDetailAsError() {
        SuiteRunner.Summary summary = run(
                ConformanceCase.of("a", () -> Verdict.fail("read 2")),
                ConformanceCase.of("b", () -> Verdict.refused(false, "refused on 1.21")));

        assertEquals(2, summary.failed, String.join("\n", log));
        List<String> results = markers("test_result");
        assertTrue(results.get(0).endsWith("\"ok\":false,\"durationMs\":0,\"error\":\"read 2\"}"), results.get(0));
        assertTrue(results.get(1).endsWith("\"ok\":false,\"durationMs\":0,\"error\":\"refused on 1.21\"}"), results.get(1));
    }

    @Test
    void anExceptionInRunIsAFailNamingTheClassAndTheMessage() {
        SuiteRunner.Summary summary = run(ConformanceCase.of("a", () -> {
            throw new IllegalStateException("no world");
        }));

        assertEquals(1, summary.failed);
        assertTrue(markers("test_result").get(0).endsWith("\"error\":\"IllegalStateException: no world\"}"), String.join("\n", log));
    }

    @Test
    void aSkippedCaseEmitsNoTestResultAndCountsInSuiteEnd() {
        SuiteRunner.Summary summary = run(
                ConformanceCase.of("a", () -> Verdict.skipped(ConformanceContext.NEEDS_A_PLAYER)),
                ConformanceCase.of("b", () -> Verdict.pass("x")));

        assertEquals(1, summary.skipped);
        assertEquals(1, markers("test_result").size(), String.join("\n", log));
        assertTrue(markers("suite_end").get(0).contains("\"passed\":1,\"failed\":0,\"skipped\":1,"), String.join("\n", log));
        assertTrue(human.contains("[ECoreConformance] demo/a SKIPPED needs a player"), String.join("\n", human));
    }

    @Test
    void suiteStartTotalIsEveryDeclaredCaseSkippedOnesIncluded() {
        run(ConformanceCase.of("a", () -> Verdict.skipped("no economy")),
                ConformanceCase.of("b", () -> Verdict.pass("x")),
                ConformanceCase.of("c", () -> Verdict.fail("y")));

        assertEquals("ENCTEST {\"v\":1,\"e\":\"suite_start\",\"t\":" + T + ",\"cid\":\"c-1\",\"runId\":\"r-1\",\"suite\":\"ecore-demo\",\"total\":3}",
                log.get(0));
    }

    @Test
    void theMarkerLineHasExactlyTheHarnessShape() {
        run(ConformanceCase.of("item-live", () -> Verdict.pass("slot.k=v")));

        String expected = """
                ENCTEST {"v":1,"e":"test_result","t":1700000000000,"cid":"c-1","runId":"r-1","suite":"ecore-demo","test":"item-live","ok":true,"durationMs":0,"error":null}""";
        assertEquals(expected, log.get(1));
        assertEquals("""
                ENCTEST {"v":1,"e":"suite_end","t":1700000000000,"cid":"c-1","runId":"r-1","suite":"ecore-demo","passed":1,"failed":0,"skipped":0,"durationMs":0,"report":"reports/r-1.ndjson"}""",
                log.get(2));
    }

    @Test
    void aDetailIsEscapedToPureAscii() {
        run(ConformanceCase.of("a", () -> Verdict.fail("não \"leu\" \\ nada")));

        assertTrue(markers("test_result").get(0).endsWith("\"error\":\"n\\u00e3o \\\"leu\\\" \\\\ nada\"}"), String.join("\n", log));
        assertTrue(log.stream().allMatch(line -> line.chars().allMatch(c -> c >= 0x20 && c <= 0x7e)), String.join("\n", log));
    }

    @Test
    void theReportHoldsTheSameEventsAsTheLogWithSuiteEndLast() throws IOException {
        run(ConformanceCase.of("a", () -> Verdict.pass("x")),
                ConformanceCase.of("b", () -> Verdict.skipped("y")),
                ConformanceCase.of("c", () -> Verdict.fail("z")));

        List<String> report = Files.readAllLines(dataFolder.resolve("reports/r-1.ndjson"), StandardCharsets.UTF_8);
        List<String> logged = log.stream().map(line -> line.substring((MarkerLine.PREFIX + " ").length())).collect(Collectors.toList());
        assertEquals(logged, report);
        assertTrue(report.get(report.size() - 1).contains("\"e\":\"suite_end\""), report.toString());
    }

    @Test
    void aReportThatCannotBeWrittenCostsTheReportAndNeverTheMarkers() throws IOException {
        File notAFolder = dataFolder.resolve("occupied").toFile();
        Files.write(notAFolder.toPath(), new byte[0]);

        SuiteRunner.Summary summary = runIn(notAFolder, ConformanceCase.of("a", () -> Verdict.pass("x")));

        assertEquals(1, summary.passed);
        assertEquals(3, log.size(), String.join("\n", log));
        assertTrue(markers("suite_end").get(0).endsWith("\"report\":null}"), String.join("\n", log));
    }

    @Test
    void theHumanLinesNameSuiteCaseKindAndDetailThenTheSummary() {
        run(ConformanceCase.of("a", () -> Verdict.pass("read 1")),
                ConformanceCase.of("b", () -> Verdict.refused(true, "expected=true since=1.14 X: y")));

        assertEquals(Arrays.asList(
                "[ECoreConformance] demo/a PASS read 1",
                "[ECoreConformance] demo/b REFUSED expected=true since=1.14 X: y",
                "[ECoreConformance] demo summary 2/2 (0 skipped) on test-version"), human);
    }

    @Test
    void sinceTurnsAQuietSuccessIntoAFailureWhereTheFeatureIsMissing() {
        Verdict quiet = Verdict.since(false, "1.14", () -> Verdict.pass("k=v"));
        Verdict refused = Verdict.since(false, "1.14", () -> {
            throw new UnsupportedOperationException("no pdc");
        });
        Verdict unexpected = Verdict.since(true, "1.14", () -> {
            throw new UnsupportedOperationException("no pdc");
        });
        Verdict works = Verdict.since(true, "1.14", () -> Verdict.pass("k=v"));

        assertEquals(Verdict.Kind.FAIL, quiet.getKind(), quiet.getDetail());
        assertEquals(Verdict.Kind.REFUSED, refused.getKind());
        assertTrue(refused.isRefusalExpected());
        assertEquals("expected=true since=1.14 UnsupportedOperationException: no pdc", refused.getDetail());
        assertFalse(unexpected.isRefusalExpected());
        assertEquals("k=v expected=false since=1.14", works.getDetail());
    }

    @Test
    void aCaseAnsweredLaterHoldsTheRunUntilItCompletesThenTheRestFollows() {
        CompletableFuture<Verdict> pending = new CompletableFuture<>();
        SuiteRunner runner = new SuiteRunner(new MarkerLine(log::add), human::add, dataFolder.toFile(), () -> T, () -> "r-1", Runnable::run);

        CompletableFuture<SuiteRunner.Summary> done = runner.run("demo", "c-1", Arrays.asList(
                ConformanceCase.later("tick", () -> pending),
                ConformanceCase.of("after", () -> Verdict.pass("x"))), "test-version");

        assertFalse(done.isDone());
        assertEquals(1, log.size(), "only suite_start before the answer: " + log);
        pending.complete(Verdict.pass("ran on the main thread"));
        assertEquals(2, done.join().passed);
        assertTrue(log.get(1).contains("\"test\":\"tick\"") && log.get(2).contains("\"test\":\"after\""), String.join("\n", log));
    }

    @Test
    void runIdsAreSortableTimestampsWithFourHexDigits() {
        String id = RunIds.of(new Date(0), 0xab);
        assertTrue(id.matches("\\d{8}-\\d{6}-00ab"), id);
        assertTrue(RunIds.next().matches("\\d{8}-\\d{6}-[0-9a-f]{4}"));
    }
}
