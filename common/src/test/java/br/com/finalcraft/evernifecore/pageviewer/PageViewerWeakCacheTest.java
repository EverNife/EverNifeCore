package br.com.finalcraft.evernifecore.pageviewer;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The line cache is weak by design - an unbounded page (setLineEnd(-1)) must be collectable - so
 * nothing may read it back through the {@link java.lang.ref.WeakReference} after publishing it. The
 * hostile moment is reproduced exactly instead of hoped for: the reference is cleared by hand while
 * the page is being built, which is what a collection at any safepoint is allowed to do.
 */
@ECoreTest
class PageViewerWeakCacheTest {


    //PageViewer's default header calls getChatAdapter().straightLineOf while the builder is being
    //constructed, which the plain no-op fixture answers with null; the harness installs a real one.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("PageViewerWeakCache", tempDir);
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    @Test
    void theWholePageIsDeliveredEvenIfTheWeakCacheIsClearedWhileItIsBeingBuilt() {
        TestCommandSender console = new TestCommandSender("CONSOLE");
        AtomicReference<PageViewer<String, String>> viewerRef = new AtomicReference<>();
        AtomicInteger produced = new AtomicInteger();

        PageViewer<String, String> viewer = PageViewer.targeting(String.class)
                .withSuplier(() -> Arrays.asList("alpha", "beta", "gamma"))
                .extracting(entry -> entry)
                .setComparator(null)
                .setFormatHeader(Collections.<FancyText>emptyList())
                .setFormatLine(entry -> {
                    if (produced.incrementAndGet() == 2) {
                        viewerRef.get().pageLinesCache.clear();
                    }
                    return new FancySegment("§a" + entry);
                })
                .setNextAndPreviousPageButton(false)
                .build();
        viewerRef.set(viewer);

        viewer.send(console);

        assertEquals(Arrays.asList("§aalpha", "§abeta", "§agamma"), console.getMessages());
    }
}
