package br.com.finalcraft.evernifecore.conformance.minecraft;

import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.SuiteRunner;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /ecc} through the real command framework, with no server: everything a suite cannot reach is skipped. */
class ConformanceCommandTest {

    @TempDir
    Path dataFolder;

    private final List<String> log = new ArrayList<>();
    private FinalCmdTestHarness harness;
    private FinalCMDPluginCommand command;
    private TestCommandSender console;

    @BeforeEach
    void setUp() {
        harness = Commands.harness("Ecc", dataFolder);
        command = harness.register(new ConformanceCommand(Suites.all(), dataFolder.toFile(), () -> "test-server", log::add));
        console = Senders.console().grant("evernifecore.conformance");
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    private List<String> markers(String event) {
        return log.stream().filter(line -> line.startsWith("ENCTEST {") && line.contains("\"e\":\"" + event + "\""))
                .collect(Collectors.toList());
    }

    @Test
    void listNamesEverySuite() {
        harness.dispatch(command, console, "list");

        for (ConformanceSuite suite : Suites.all()) {
            console.assertAnyMessageContains(SuiteRunner.HUMAN_PREFIX + suite.getId() + " cases=");
        }
    }

    @Test
    void anUnknownSuiteIsRefusedNamingTheKnownOnes() {
        harness.dispatch(command, console, "run nope");

        console.assertAnyMessageContains("unknown suite 'nope'");
        for (ConformanceSuite suite : Suites.all()) {
            console.assertAnyMessageContains(suite.getId());
        }
        assertTrue(log.isEmpty(), "nothing ran, so nothing reaches the log: " + log);
    }

    @Test
    void withoutAPlayerThePlayerCasesAreSkippedAndNothingFails() {
        harness.dispatch(command, console, "run items --cid c-42");

        assertEquals(1, markers("suite_start").size(), String.join("\n", log));
        assertTrue(markers("test_result").isEmpty(), String.join("\n", log));
        String end = markers("suite_end").get(0);
        assertTrue(end.contains("\"cid\":\"c-42\"") && end.contains("\"suite\":\"ecore-items\""), end);
        assertTrue(end.contains("\"passed\":0,\"failed\":0,\"skipped\":3,"), end);
        console.assertAnyMessageContains("items/wand SKIPPED needs a player");
    }

    @Test
    void aHalfNoSuiteHasIsSaidSoAndRunsNothing() {
        harness.dispatch(command, console, "write items");

        console.assertAnyMessageContains("'items' has no write half - suites with one: [nbt");
        assertTrue(log.isEmpty(), String.join("\n", log));
    }

    @Test
    void suiteIdsAreUniqueAndLeaveThePrefixToTheRunner() {
        Set<String> seen = new HashSet<>();
        for (ConformanceSuite suite : Suites.all()) {
            assertTrue(seen.add(suite.getId()), "duplicate suite id " + suite.getId());
            assertFalse(suite.getId().startsWith(SuiteRunner.SUITE_PREFIX), suite.getId());
        }
    }
}
