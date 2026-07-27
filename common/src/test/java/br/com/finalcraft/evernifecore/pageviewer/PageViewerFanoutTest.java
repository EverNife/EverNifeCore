package br.com.finalcraft.evernifecore.pageviewer;

import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sending one page to several recipients delivers it once to each of them. The count that matters is
 * per recipient, not the total: the fan-out bug this pins handed the whole recipient array to the
 * per-line send inside the per-recipient loop, so every line arrived N times for N recipients - which
 * is invisible with the single recipient of the ordinary command path.
 */
class PageViewerFanoutTest {

    @BeforeAll
    static void installTestPlatform() {
        TestPlatformFixture.ensureInstalled();
    }

    //PageViewer's default header calls getChatAdapter().straightLineOf at build time, which the plain
    //no-op fixture answers with null; the harness installs a working chat adapter.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("PageViewerFanout", tempDir);
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    @Test
    void eachRecipientReceivesEveryLineExactlyOnce() {
        TestCommandSender first = new TestCommandSender("FIRST");
        TestCommandSender second = new TestCommandSender("SECOND");

        PageViewer.targeting(String.class)
                .withSuplier(() -> Arrays.asList("alpha", "beta", "gamma"))
                .extracting(entry -> entry)
                .setComparator(null)
                .setFormatLine("§7#  ${number}:   §a${value}")
                .setNextAndPreviousPageButton(false)
                .build()
                .send(first, second);

        for (TestCommandSender recipient : Arrays.asList(first, second)) {
            String who = recipient.getName();
            assertEquals(1, occurrences(recipient.getMessages(), "alpha"), "line 'alpha' for " + who);
            assertEquals(1, occurrences(recipient.getMessages(), "beta"), "line 'beta' for " + who);
            assertEquals(1, occurrences(recipient.getMessages(), "gamma"), "line 'gamma' for " + who);
            // one header line plus the three entries: nothing else, and nothing twice
            assertEquals(4, recipient.getMessages().size(),
                    "unexpected message count for " + who + ": " + recipient.getMessages());
        }
    }

    private static int occurrences(List<String> messages, String snippet) {
        int count = 0;
        for (String message : messages) {
            if (message.contains(snippet)) count++;
        }
        return count;
    }
}
