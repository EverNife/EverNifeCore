package br.com.finalcraft.evernifecore.pageviewer;

import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.fancytext.MessageScope;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.TestCommandSender;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a page owes its recipients, asserted on the rendered text that reaches them rather than on any
 * key, map or token shape: a substitution reaches the hover and the click as well as the text, it
 * descends into every piece of a chain, it is computed once per line and shared by every recipient,
 * and a value carrying regex or closure characters lands literally.
 */
class PageViewerContractTest {

    @BeforeAll
    static void installTestPlatform() {
        TestPlatformFixture.ensureInstalled();
    }

    //PageViewer's default header calls getChatAdapter().straightLineOf while the builder is being
    //constructed, which the plain no-op fixture answers with null; the harness installs a real one.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("PageViewerContract", tempDir);
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    @Test
    void aPlaceholderIsResolvedInTheHoverAndInTheClickToo() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.targeting(String.class)
                .withSuplier(() -> Collections.singletonList("alpha"))
                .extracting(entry -> entry)
                .setComparator(null)
                .setFormatHeader(Collections.<FancyText>emptyList())
                .setFormatLine(new FancySegment("§aline")
                        .setHover("§7tip: ${hover_value}")
                        .setClickSuggest("/say ${click_value}"))
                .setNextAndPreviousPageButton(false)
                .addPlaceholder("hover_value", entry -> "HOVERED-" + entry)
                .addPlaceholder("click_value", entry -> "CLICKED-" + entry)
                .build()
                .send(console);

        String hover = console.hoverTextOfMessageContaining("line");
        assertNotNull(hover, "the line should carry a hover: " + console.getMessages());
        assertTrue(hover.contains("HOVERED-alpha"), "hover was not resolved: " + hover);
        assertFalse(hover.contains("hover_value"), "hover kept the unresolved token: " + hover);

        assertEquals("/say CLICKED-alpha", console.clickValueOfMessageContaining("line"),
                "click value was not resolved");
    }

    @Test
    void aKeyCitedOnlyInTheSecondPieceOfAChainIsResolved() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.targeting(String.class)
                .withSuplier(() -> Collections.singletonList("alpha"))
                .extracting(entry -> entry)
                .setComparator(null)
                .setFormatHeader(Collections.<FancyText>emptyList())
                .setFormatLine(FancyText.of("§7first ").append("§asecond ${deep}"))
                .setNextAndPreviousPageButton(false)
                .addPlaceholder("deep", entry -> "DEEP-" + entry)
                .build()
                .send(console);

        assertEquals(1, console.getMessages().size(), "unexpected messages: " + console.getMessages());
        String line = console.getMessages().get(0);
        assertTrue(line.contains("first "), "the first piece is missing: " + line);
        assertTrue(line.contains("second DEEP-alpha"),
                "a key cited only in the second piece of a chain was not resolved: " + line);
    }

    @Test
    void theLineNumberIsOneBasedAndTheValueComesFromTheExtractor() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.targeting(String.class)
                .withSuplier(() -> Arrays.asList("alpha", "beta", "gamma"))
                .extracting(entry -> entry.toUpperCase(Locale.ROOT))
                .setComparator(null)
                .setFormatHeader(Collections.<FancyText>emptyList())
                .setFormatLine("#${number}:${value}")
                .setNextAndPreviousPageButton(false)
                .build()
                .send(console);

        assertEquals(Arrays.asList("#1:ALPHA", "#2:BETA", "#3:GAMMA"), console.getMessages());
    }

    @Test
    void aValueCarryingRegexAndClosureCharactersIsInjectedLiterally() {
        TestCommandSender console = new TestCommandSender("CONSOLE");
        String hostile = "a$b\\c%pct%d${unknown}e";

        PageViewer.targeting(String.class)
                .withSuplier(() -> Collections.singletonList("alpha"))
                .extracting(entry -> entry)
                .setComparator(null)
                .setFormatHeader(Collections.<FancyText>emptyList())
                .setFormatLine("[${raw}]")
                .setNextAndPreviousPageButton(false)
                .addPlaceholder("raw", entry -> hostile)
                .build()
                .send(console);

        assertEquals(Collections.singletonList("[" + hostile + "]"), console.getMessages());
    }

    @Test
    void aPlaceholderIsResolvedOncePerLineAndNotOncePerRecipient() {
        AtomicInteger calls = new AtomicInteger();
        TestCommandSender first = new TestCommandSender("FIRST");
        TestCommandSender second = new TestCommandSender("SECOND");

        PageViewer.targeting(String.class)
                .withSuplier(() -> Arrays.asList("alpha", "beta"))
                .extracting(entry -> entry)
                .setComparator(null)
                .setFormatHeader(Collections.<FancyText>emptyList())
                //cited twice on the same line on purpose: one line still costs one invocation
                .setFormatLine(new FancySegment("§aline ${shared}").setHover("§7tip ${shared}"))
                .setNextAndPreviousPageButton(false)
                .addPlaceholder("shared", entry -> {
                    calls.incrementAndGet();
                    return "V-" + entry;
                })
                .build()
                .send(first, second);

        assertEquals(2, calls.get(),
                "two lines and two recipients must cost two invocations - one per line");
        assertEquals(first.getMessages(), second.getMessages(),
                "both recipients must see the same values");
        assertTrue(first.getMessages().get(0).contains("V-alpha"),
                "unexpected first line: " + first.getMessages());
        assertTrue(first.getMessages().get(1).contains("V-beta"),
                "unexpected second line: " + first.getMessages());
    }

    @Test
    void anInjectedValueIsNotScannedAgainForOtherKeys() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.targeting(String.class)
                .withSuplier(() -> Collections.singletonList("alpha"))
                .extracting(entry -> entry)
                .setComparator(null)
                .setFormatHeader(Collections.<FancyText>emptyList())
                .setFormatLine("[${outer}]")
                .setNextAndPreviousPageButton(false)
                //what "outer" resolves to happens to spell another declared key: it is a value, not
                //text to be substituted again, so it must survive verbatim whatever the key order is
                .addPlaceholder("outer", entry -> "${inner}")
                .addPlaceholder("inner", entry -> "SHOULD-NOT-APPEAR")
                .build()
                .send(console);

        assertEquals(Collections.singletonList("[${inner}]"), console.getMessages());
    }

    @Test
    void aKeyDeclaredOnThePageWinsOverTheFrameworkWideOne() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer<String, String> viewer = PageViewer.targeting(String.class)
                .withSuplier(() -> Collections.singletonList("alpha"))
                .extracting(entry -> entry)
                .setComparator(null)
                .setFormatHeader(Collections.<FancyText>emptyList())
                .setFormatLine("/${label} entry")
                .setNextAndPreviousPageButton(false)
                .addPlaceholder("label", entry -> "pagelabel")
                .build();

        //A page can be re-sent later, from another command's scope, and must still say what it was
        //built to say - which is why the page's own declaration is baked in before any render.
        try (MessageScope scope = MessageScope.open("scopelabel", null)) {
            viewer.send(console);
        }

        assertEquals(Collections.singletonList("/pagelabel entry"), console.getMessages());
    }

    @Test
    void pageSizeSlicesThePageAndAnUnboundedLineEndKeepsEveryEntry() {
        TestCommandSender paged = new TestCommandSender("PAGED");

        PageViewer.targeting(String.class)
                .withSuplier(() -> Arrays.asList("e1", "e2", "e3", "e4", "e5"))
                .extracting(entry -> entry)
                .setComparator(null)
                .setFormatHeader(Collections.<FancyText>emptyList())
                .setFormatLine("${value}")
                .setPageSize(2)
                .setNextAndPreviousPageButton(false)
                .build()
                .send(Integer.valueOf(2), paged);

        assertEquals(Arrays.asList("e3", "e4"), paged.getMessages(), "page 2 of size 2");

        //The default lineEnd caps how many lines are cached at all, so the tail of a longer list is
        //unreachable by any page until setLineEnd(-1) lifts the cap - which is what /oredictinfo uses.
        List<String> sixty = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            sixty.add("e" + i);
        }

        TestCommandSender capped = new TestCommandSender("CAPPED");
        PageViewer.targeting(String.class)
                .withSuplier(() -> sixty)
                .extracting(entry -> entry)
                .setComparator(null)
                .setFormatHeader(Collections.<FancyText>emptyList())
                .setFormatLine("${value}")
                .setPageSize(100)
                .setNextAndPreviousPageButton(false)
                .build()
                .send(capped);

        assertEquals(50, capped.getMessages().size(), "the default lineEnd caps the page at 50 lines");

        TestCommandSender unbounded = new TestCommandSender("UNBOUNDED");
        PageViewer.targeting(String.class)
                .withSuplier(() -> sixty)
                .extracting(entry -> entry)
                .setComparator(null)
                .setFormatHeader(Collections.<FancyText>emptyList())
                .setFormatLine("${value}")
                .setPageSize(100)
                .setLineEnd(-1)
                .setNextAndPreviousPageButton(false)
                .build()
                .send(unbounded);

        assertEquals(60, unbounded.getMessages().size(), "setLineEnd(-1) keeps every entry");
        assertEquals("e60", unbounded.getMessages().get(59));
    }
}
