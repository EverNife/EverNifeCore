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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first coverage of {@link PageViewer}: a placeholder registered but never cited by the format
 * line must never have its Function invoked (match-driven), while a cited one is.
 */
class PageViewerPlaceholderLazinessTest {

    @BeforeAll
    static void installTestPlatform() {
        TestPlatformFixture.ensureInstalled();
    }

    //A working chat adapter: PageViewer's header calls getChatAdapter().straightLineOf at build time,
    //which the no-op fixture returns null for.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("PageViewer", tempDir);
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    @Test
    void aRegisteredButUnreferencedPlaceholderFunctionIsNeverInvoked() {
        AtomicInteger hiddenCalls = new AtomicInteger();
        AtomicInteger shownCalls = new AtomicInteger();
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.targeting(String.class)
                .withSuplier(() -> Arrays.asList("alpha", "beta"))
                .extracting(entry -> entry)
                //the format line cites ${shown} (and ${number}), but NOT ${hidden}
                .setFormatLine("§7#  ${number}:   §a${shown}")
                .setNextAndPreviousPageButton(false)
                .addPlaceholder("hidden", entry -> { hiddenCalls.incrementAndGet(); return "hidden"; })
                .addPlaceholder("shown", entry -> { shownCalls.incrementAndGet(); return String.valueOf(entry); })
                .build()
                .send(console);

        assertEquals(0, hiddenCalls.get(),
                "a placeholder Function the line never cites must never be invoked");
        assertTrue(shownCalls.get() >= 1,
                "a placeholder Function the line does cite must be invoked");
    }
}
