package br.com.finalcraft.evernifecore.pageviewer;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.fancytext.MessageScope;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.pageviewer.nav.PageNavigation;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageTheme;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Locales;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
 * descends into every piece of a chain, and a value carrying regex or closure characters lands
 * literally.
 *
 * <p>It also pins where each of the two levels is paid for: a {@code row} key costs one call per
 * line however many people read it, and a {@code viewer} key costs one per line per reader - and
 * neither costs anything at all where the line does not cite it.</p>
 */
@ECoreTest
class PageViewerContractTest {

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

    /** A page with no chrome and no navigation: what reaches the sender is the entries and nothing else. */
    private static PageViewer.IBuilder<String> bareOf(List<String> entries) {
        return PageViewer.of(String.class)
                .source(() -> entries)
                .unlimitedEntries()
                .theme(PageTheme.none())
                .navigation(PageNavigation.none());
    }

    @Test
    void aPlaceholderIsResolvedInTheHoverAndInTheClickToo() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        bareOf(Collections.singletonList("alpha"))
                .setFormatLine(new FancySegment("§aline")
                        .setHover("§7tip: ${hover_value}")
                        .setClickSuggest("/say ${click_value}"))
                .addRowPlaceholder("hover_value", entry -> "HOVERED-" + entry)
                .addRowPlaceholder("click_value", entry -> "CLICKED-" + entry)
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

        bareOf(Collections.singletonList("alpha"))
                .setFormatLine(FancyText.of("§7first ").append("§asecond ${deep}"))
                .addRowPlaceholder("deep", entry -> "DEEP-" + entry)
                .build()
                .send(console);

        assertEquals(1, console.getMessages().size(), "unexpected messages: " + console.getMessages());
        String line = console.getMessages().get(0);
        assertTrue(line.contains("first "), "the first piece is missing: " + line);
        assertTrue(line.contains("second DEEP-alpha"),
                "a key cited only in the second piece of a chain was not resolved: " + line);
    }

    @Test
    void theLineNumberIsOneBasedAndTheValueComesFromTheOrder() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.of(String.class)
                .source(() -> Arrays.asList("alpha", "beta", "gamma"))
                .unlimitedEntries()
                .orderBy(entry -> entry.toUpperCase(Locale.ROOT)).ascending()
                .setFormatLine("#${number}:${value}")
                .theme(PageTheme.none())
                .navigation(PageNavigation.none())
                .build()
                .send(console);

        assertEquals(Arrays.asList("#1:ALPHA", "#2:BETA", "#3:GAMMA"), console.getMessages());
    }

    @Test
    void aValueCarryingRegexAndClosureCharactersIsInjectedLiterally() {
        TestCommandSender console = new TestCommandSender("CONSOLE");
        String hostile = "a$b\\c%pct%d${unknown}e";

        bareOf(Collections.singletonList("alpha"))
                .setFormatLine("[${raw}]")
                .addRowPlaceholder("raw", entry -> hostile)
                .build()
                .send(console);

        assertEquals(Collections.singletonList("[" + hostile + "]"), console.getMessages());
    }

    @Test
    void anInjectedValueIsNotScannedAgainForOtherKeys() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        bareOf(Collections.singletonList("alpha"))
                .setFormatLine("[${outer}]")
                //what "outer" resolves to happens to spell another declared key: it is a value, not
                //text to be substituted again, so it must survive verbatim whatever the key order is
                .addRowPlaceholder("outer", entry -> "${inner}")
                .addRowPlaceholder("inner", entry -> "SHOULD-NOT-APPEAR")
                .build()
                .send(console);

        assertEquals(Collections.singletonList("[${inner}]"), console.getMessages());
    }

    @Test
    void aKeyDeclaredOnThePageWinsOverTheFrameworkWideOne() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer<String> viewer = bareOf(Collections.singletonList("alpha"))
                .setFormatLine("/${label} entry")
                .addRowPlaceholder("label", entry -> "pagelabel")
                .build();

        //A page can be re-sent later, from another command's scope, and must still say what it was
        //built to say - which is why the page's own declaration is baked in before any render.
        try (MessageScope scope = MessageScope.open(CommandPath.ofRoot("scopelabel"))) {
            viewer.send(console);
        }

        assertEquals(Collections.singletonList("/pagelabel entry"), console.getMessages());
    }

    @Test
    void aValueKeyTheCallerDeclaredWinsOverTheOrder() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.of(String.class)
                .source(() -> Collections.singletonList("alpha"))
                .unlimitedEntries()
                .orderBy(entry -> "FROM-ORDER").ascending()
                .setFormatLine("${value}")
                .theme(PageTheme.none())
                .navigation(PageNavigation.none())
                .addRowPlaceholder("value", entry -> "FROM-CALLER")
                .build()
                .send(console);

        assertEquals(Collections.singletonList("FROM-CALLER"), console.getMessages());
    }

    @Test
    void aPlayerKeyTheCallerDeclaredWinsOverTheAutomaticOne() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.of(FPlayer.class)
                .source(() -> Collections.<FPlayer>singletonList(new TestFPlayerSender("Steve")))
                .unlimitedEntries()
                .setFormatLine("${player}")
                .theme(PageTheme.none())
                .navigation(PageNavigation.none())
                .addRowPlaceholder("player", entry -> "FROM-CALLER")
                .build()
                .send(console);

        assertEquals(Collections.singletonList("FROM-CALLER"), console.getMessages());
    }

    // The page TARGETS players, so it answers for ${player} - and goes on answering for it when the
    // list comes back empty, which is the whole point of asking the type instead of the first entry.
    @Test
    void thePlayerKeyIsAnsweredForBecauseThePageTargetsPlayers() {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.of(FPlayer.class)
                .source(() -> Collections.<FPlayer>singletonList(new TestFPlayerSender("Steve")))
                .unlimitedEntries()
                .setFormatLine("${player}")
                .theme(PageTheme.none())
                .navigation(PageNavigation.none())
                .build()
                .send(console);

        assertEquals(Collections.singletonList("Steve"), console.getMessages());
    }

    @Test
    void pageSizeSlicesThePage() {
        TestCommandSender paged = new TestCommandSender("PAGED");

        bareOf(Arrays.asList("e1", "e2", "e3", "e4", "e5"))
                .setFormatLine("${entry}")
                .addRowPlaceholder("entry", entry -> entry)
                .setPageSize(2)
                .build()
                .send(Integer.valueOf(2), paged);

        assertEquals(Arrays.asList("e3", "e4"), paged.getMessages(), "page 2 of size 2");
    }

    @Test
    void eachRecipientReceivesEveryLineExactlyOnce() {
        TestCommandSender first = new TestCommandSender("FIRST");
        TestCommandSender second = new TestCommandSender("SECOND");

        bareOf(Arrays.asList("alpha", "beta", "gamma"))
                .setFormatLine("§7#  ${number}:   §a${entry}")
                .addRowPlaceholder("entry", entry -> entry)
                .build()
                .send(first, second);

        for (TestCommandSender recipient : Arrays.asList(first, second)) {
            String who = recipient.getName();
            assertEquals(1, occurrences(recipient.getMessages(), "alpha"), "line 'alpha' for " + who);
            assertEquals(1, occurrences(recipient.getMessages(), "beta"), "line 'beta' for " + who);
            assertEquals(1, occurrences(recipient.getMessages(), "gamma"), "line 'gamma' for " + who);
            assertEquals(3, recipient.getMessages().size(),
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

    // ------------------------------------------------------------------
    //  the two levels
    // ------------------------------------------------------------------

    @Test
    void aRowPlaceholderIsResolvedOncePerLineAndNotOncePerRecipient() {
        AtomicInteger calls = new AtomicInteger();
        TestCommandSender first = new TestCommandSender("FIRST");
        TestCommandSender second = new TestCommandSender("SECOND");

        bareOf(Arrays.asList("alpha", "beta"))
                //cited twice on the same line on purpose: one line still costs one invocation
                .setFormatLine(new FancySegment("§aline ${shared}").setHover("§7tip ${shared}"))
                .addRowPlaceholder("shared", entry -> {
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
    void aViewerPlaceholderIsResolvedOncePerLinePerRecipient() {
        AtomicInteger calls = new AtomicInteger();
        TestCommandSender first = new TestCommandSender("FIRST");
        TestCommandSender second = new TestCommandSender("SECOND");

        bareOf(Arrays.asList("alpha", "beta"))
                //cited twice again: the memo is per render, so the second mention is free
                .setFormatLine(new FancySegment("§aline ${who}").setHover("§7tip ${who}"))
                .addViewerPlaceholder("who", (entry, reader) -> {
                    calls.incrementAndGet();
                    return reader.getName() + "-" + entry;
                })
                .build()
                .send(first, second);

        assertEquals(4, calls.get(),
                "two lines and two recipients cost four invocations at the viewer level");
        assertTrue(first.getMessages().get(0).contains("FIRST-alpha"), "" + first.getMessages());
        assertTrue(second.getMessages().get(0).contains("SECOND-alpha"), "" + second.getMessages());
    }

    @Test
    void aRegisteredButUnreferencedPlaceholderFunctionIsNeverInvoked() {
        AtomicInteger hiddenRowCalls = new AtomicInteger();
        AtomicInteger hiddenViewerCalls = new AtomicInteger();
        AtomicInteger shownCalls = new AtomicInteger();
        TestCommandSender console = new TestCommandSender("CONSOLE");

        bareOf(Arrays.asList("alpha", "beta"))
                //the format line cites ${shown} (and ${number}), but neither hidden key
                .setFormatLine("§7#  ${number}:   §a${shown}")
                .addRowPlaceholder("hidden_row", entry -> { hiddenRowCalls.incrementAndGet(); return "hidden"; })
                .addViewerPlaceholder("hidden_viewer", (entry, reader) -> { hiddenViewerCalls.incrementAndGet(); return "hidden"; })
                .addRowPlaceholder("shown", entry -> { shownCalls.incrementAndGet(); return String.valueOf(entry); })
                .build()
                .send(console);

        assertEquals(0, hiddenRowCalls.get(),
                "a row placeholder the line never cites must never be invoked");
        assertEquals(0, hiddenViewerCalls.get(),
                "a viewer placeholder the line never cites must never be invoked");
        assertEquals(2, shownCalls.get(), "one call per line for the key the line does cite");
    }

    // ------------------------------------------------------------------
    //  language
    // ------------------------------------------------------------------

    @Test
    void twoReadersOfDifferentLanguagesShareTheOrderAndTheRowValuesButNotTheWords() {
        LocaleMessage line = Locales.message(harness.ecPluginData, "PageViewerContract.LINE",
                LocaleType.EN_US, "§7#${number} balance of ${entry}: ${amount}",
                LocaleType.PT_BR, "§7#${number} saldo de ${entry}: ${amount}");

        AtomicInteger amountCalls = new AtomicInteger();

        try (Locales locales = Locales.perPlayerLocale(tempDir)) {
            TestFPlayerSender english = locales.reader("Steve", LocaleType.EN_US);
            TestFPlayerSender brazilian = locales.reader("Petrus", LocaleType.PT_BR);

            PageViewer.of(String.class)
                    .source(() -> Arrays.asList("gamma", "alpha", "beta"))
                    .unlimitedEntries()
                    .orderBy(entry -> entry).ascending()
                    .setFormatLine(line)
                    .theme(PageTheme.none())
                    .navigation(PageNavigation.none())
                    .addRowPlaceholder("entry", entry -> entry)
                    .addRowPlaceholder("amount", entry -> {
                        amountCalls.incrementAndGet();
                        return entry.length() * 10;
                    })
                    .build()
                    .send(english, brazilian);

            assertEquals(Arrays.asList(
                    "§7#1 balance of alpha: 50",
                    "§7#2 balance of beta: 40",
                    "§7#3 balance of gamma: 50"), english.getMessages());

            assertEquals(Arrays.asList(
                    "§7#1 saldo de alpha: 50",
                    "§7#2 saldo de beta: 40",
                    "§7#3 saldo de gamma: 50"), brazilian.getMessages());

            assertEquals(3, amountCalls.get(),
                    "the row values are shared: two readers of three lines still cost three calls");
        }
    }

    // ------------------------------------------------------------------
    //  concurrency
    // ------------------------------------------------------------------

    @Test
    void twoConcurrentSendsNeverMixOneSnapshotWithAnother() throws Exception {
        AtomicInteger generation = new AtomicInteger();

        //Every read of the source produces a whole new set of entries, all stamped with the same
        //generation - so a page holding lines from two reads is visible as two stamps in one send.
        PageViewer<String> viewer = PageViewer.of(String.class)
                .source(() -> {
                    int stamp = generation.incrementAndGet();
                    List<String> entries = new ArrayList<>();
                    for (int index = 0; index < 40; index++) {
                        entries.add("g" + stamp);
                    }
                    return entries;
                })
                .unlimitedEntries()
                .setPageSize(100)
                .theme(PageTheme.none())
                .navigation(PageNavigation.none())
                .cache(CachePolicy.none())
                .setFormatLine("${entry}")
                .addRowPlaceholder("entry", entry -> entry)
                .build();

        List<Thread> threads = new ArrayList<>();
        List<TestCommandSender> readers = Collections.synchronizedList(new ArrayList<TestCommandSender>());

        for (int index = 0; index < 8; index++) {
            TestCommandSender reader = new TestCommandSender("READER-" + index);
            readers.add(reader);
            threads.add(new Thread(() -> viewer.send(reader)));
        }
        for (Thread thread : threads) thread.start();
        for (Thread thread : threads) thread.join();

        for (TestCommandSender reader : readers) {
            assertEquals(1, new HashSet<>(reader.getMessages()).size(),
                    "a single send must never mix two reads of the source: " + reader.getMessages());
            assertEquals(40, reader.getMessages().size(), "" + reader.getMessages());
        }
    }
}
