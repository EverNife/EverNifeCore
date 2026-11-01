package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.testing.RendererConformance;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The page renderer keeps every promise the screen SPI asks of a renderer, scenario by scenario. */
class HyPageRendererConformanceTest {

    /** The scenarios {@link RendererConformance} runs, each reported as a test of its own. */
    private static final String[] SCENARIOS = {
            "open-registers-view", "open-posts-event", "click-routes-by-id-and-gesture", "patch-batches-per-tick",
            "patch-touches-only-dirty-elements", "close-runs-onclose-once", "close-posts-event-with-reason",
            "prompt-sets-aside-and-restores", "nav-push-pop-hands-value", "capability-refusal-teaches",
            "title-change-when-declared", "label-draws-its-text", "choice-disabled-carries-reason",
            "progress-reports-ratio", "input-asks-through-a-prompt", "list-pages-with-arrows"};

    @TempDir
    Path tempDir;

    @TestFactory
    List<DynamicTest> thePagePassesTheRendererConformanceSuite() {
        List<String> failures;
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            failures = RendererConformance.check(pages.renderer, pages.screens);
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (String scenario : SCENARIOS) {
            tests.add(DynamicTest.dynamicTest(scenario, () -> assertEquals(Collections.emptyList(), failuresOf(failures, scenario))));
        }
        //a scenario the suite gained after this list was written still fails here
        tests.add(DynamicTest.dynamicTest("no-failure-at-all", () -> assertEquals(Collections.emptyList(), failures)));
        return tests;
    }

    private static List<String> failuresOf(List<String> failures, String scenario) {
        List<String> mine = new ArrayList<>();
        for (String failure : failures) {
            if (failure.startsWith(scenario + ":")) {
                mine.add(failure);
            }
        }
        return mine;
    }

}
