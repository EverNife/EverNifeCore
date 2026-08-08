package br.com.finalcraft.evernifecore.pageviewer;

import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.pageviewer.nav.PVExtraMessages;
import br.com.finalcraft.evernifecore.pageviewer.nav.PageNavigation;
import br.com.finalcraft.evernifecore.pageviewer.theme.ClassicPageTheme;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageTheme;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What one read of the source is worth: how long it is reused, how much of it a page keeps, in which
 * order, and how the page owns up to what it had to leave out.
 */
@ECoreTest
class PageSnapshotTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("PageSnapshot", tempDir);
        FCLocaleManager.loadLocale(harness.ecPluginData, ClassicPageTheme.class, PVExtraMessages.class);
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private static List<String> entries(int howMany) {
        List<String> entries = new ArrayList<>(howMany);
        for (int index = 1; index <= howMany; index++) {
            entries.add("e" + index);
        }
        return entries;
    }

    // ------------------------------------------------------------------
    //  the cache
    // ------------------------------------------------------------------

    @Test
    void twoSendsWithinTheTtlConsultTheSourceOnce() {
        AtomicInteger reads = new AtomicInteger();

        PageViewer<String> viewer = PageViewer.of(String.class)
                .source(() -> {
                    reads.incrementAndGet();
                    return Arrays.asList("alpha", "beta");
                })
                .unlimitedEntries()
                .cache(CachePolicy.ttl(Duration.ofMinutes(10)))
                .theme(PageTheme.none())
                .navigation(PageNavigation.none())
                .setFormatLine("${number}")
                .build();

        viewer.send(new TestCommandSender("FIRST"));
        viewer.send(new TestCommandSender("SECOND"));

        assertEquals(1, reads.get(), "a snapshot still inside its ttl must not consult the source again");
    }

    @Test
    void invalidateForcesTheSourceToBeConsultedAgain() {
        AtomicInteger reads = new AtomicInteger();

        PageViewer<String> viewer = PageViewer.of(String.class)
                .source(() -> {
                    reads.incrementAndGet();
                    return Arrays.asList("alpha", "beta");
                })
                .unlimitedEntries()
                .cache(CachePolicy.manual())
                .theme(PageTheme.none())
                .navigation(PageNavigation.none())
                .setFormatLine("${number}")
                .build();

        viewer.send(new TestCommandSender("FIRST"));
        viewer.invalidate();
        viewer.send(new TestCommandSender("SECOND"));

        assertEquals(2, reads.get(), "invalidate() has to make the next send read the source again");
    }

    // ------------------------------------------------------------------
    //  the ceiling
    // ------------------------------------------------------------------

    @Test
    void whatMaxEntriesCutIsAnnouncedInTheHoverOfTheTotalAndCostsNoLineOfChat() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.of(String.class)
                .source(() -> entries(60))
                .maxEntries(50)
                .setPageSize(100)
                .theme(PageTheme.classic().withTotalCount())
                .navigation(PageNavigation.none())
                .setFormatLine("${number}")
                .build()
                .send(console);

        //one rule, one count, fifty entries - and nothing else: the truncation costs no line
        assertEquals(52, console.getMessages().size(), "unexpected page: " + console.getMessages());

        String hover = console.hoverTextOfMessageContaining("From a total of");
        assertNotNull(hover, "the count line should carry the truncation hover: " + console.getMessages());
        assertTrue(hover.contains("50") && hover.contains("60"),
                "the hover has to say how many of how many: " + hover);
    }

    @Test
    void unlimitedEntriesKeepsEveryEntryAndTheHoverAnnouncesNoTruncation() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.of(String.class)
                .source(() -> entries(60))
                .unlimitedEntries()
                .setPageSize(100)
                .theme(PageTheme.classic().withTotalCount())
                .navigation(PageNavigation.none())
                .setFormatLine("${number}")
                .build()
                .send(console);

        assertEquals(62, console.getMessages().size(), "every entry has to be delivered: " + console.getMessages().size());
        assertTrue(console.getMessages().contains("60"), "the sixtieth entry is missing");

        String hover = console.hoverTextOfMessageContaining("From a total of");
        assertNotNull(hover, hover);
        assertFalse(hover.contains("50"), "nothing was cut, so no smaller count may show up: " + hover);
        assertTrue(hover.contains("60"), "" + hover);
    }

    @Test
    void theMaxEntriesFunctionSeesTheRawListAndRunsOncePerSnapshotNotPerReader() {
        AtomicInteger ceilingCalls = new AtomicInteger();
        AtomicReference<Integer> rawSizeSeen = new AtomicReference<>();

        PageViewer.of(String.class)
                .source(() -> entries(10))
                .maxEntries(raw -> {
                    ceilingCalls.incrementAndGet();
                    rawSizeSeen.set(raw.size());
                    return 3;
                })
                .theme(PageTheme.none())
                .navigation(PageNavigation.none())
                .setFormatLine("${number}")
                .build()
                .send(new TestCommandSender("FIRST"), new TestCommandSender("SECOND"));

        assertEquals(1, ceilingCalls.get(), "the ceiling belongs to the snapshot, not to the reader");
        assertEquals(Integer.valueOf(10), rawSizeSeen.get(), "the ceiling is decided against the raw list");
    }

    // ------------------------------------------------------------------
    //  the order
    // ------------------------------------------------------------------

    @Test
    void descendingIsTheOrderThatWasAskedForWithNoHiddenReversal() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.of(String.class)
                .source(() -> Arrays.asList("beta", "alpha", "gamma"))
                .unlimitedEntries()
                .orderBy(entry -> entry).descending()
                .theme(PageTheme.none())
                .navigation(PageNavigation.none())
                .setFormatLine("${value}")
                .build()
                .send(console);

        assertEquals(Arrays.asList("gamma", "beta", "alpha"), console.getMessages());
    }

    @Test
    void ascendingOnNumbersComparesNumericallyAndNotAsText() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.of(String.class)
                .source(() -> Arrays.asList("e2", "e10", "e1"))
                .unlimitedEntries()
                .orderBy(entry -> Integer.valueOf(entry.substring(1))).ascending()
                .theme(PageTheme.none())
                .navigation(PageNavigation.none())
                .setFormatLine("${entry}")
                .addRowPlaceholder("entry", entry -> entry)
                .build()
                .send(console);

        assertEquals(Arrays.asList("e1", "e2", "e10"), console.getMessages());
    }

    @Test
    void withNoOrderTheOutputIsExactlyWhatTheSourceReturned() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.of(String.class)
                .source(() -> Arrays.asList("gamma", "alpha", "beta"))
                .unlimitedEntries()
                .theme(PageTheme.none())
                .navigation(PageNavigation.none())
                .setFormatLine("${entry}")
                .addRowPlaceholder("entry", entry -> entry)
                .build()
                .send(console);

        assertEquals(Arrays.asList("gamma", "alpha", "beta"), console.getMessages());
    }

    // ------------------------------------------------------------------
    //  the empty page
    // ------------------------------------------------------------------

    @Test
    void anEmptyListProducesTheEmptyStateAndNoButtons() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.of(String.class)
                .source(() -> Collections.<String>emptyList())
                .unlimitedEntries()
                .theme(PageTheme.classic())
                .setFormatLine("${number}")
                .build()
                .send(console);

        assertTrue(console.anyMessageContains("Nothing to show"),
                "an empty page has to say so: " + console.getMessages());
        assertFalse(console.anyMessageContains("Page ["),
                "an empty page must not offer buttons pointing at 1/0: " + console.getMessages());
    }
}
