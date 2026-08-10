package br.com.finalcraft.evernifecore.pageviewer;

import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.commands.misc.CMDECPage;
import br.com.finalcraft.evernifecore.fancytext.MessageScope;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.pageviewer.nav.PVExtraMessages;
import br.com.finalcraft.evernifecore.pageviewer.nav.PageNavigation;
import br.com.finalcraft.evernifecore.pageviewer.nav.PageSessionManager;
import br.com.finalcraft.evernifecore.pageviewer.theme.ClassicPageTheme;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageTheme;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What clicking a page button costs and what it is allowed to reach: a named page navigates by name
 * and allocates nothing, an anonymous one navigates by a handle that belongs to one reader, and
 * either way the page goes on speaking for the command that produced it.
 */
@ECoreTest
class PageNavigationTest {

    private static final String PAGE_ID = "pagenavigationtest:page";

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private FinalCMDPluginCommand ecpage;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("PageNavigation", tempDir);
        FCLocaleManager.loadLocale(harness.ecPluginData, ClassicPageTheme.class, PVExtraMessages.class);
        ecpage = harness.register(CMDECPage.class);
        PageSessionManager.clear();
    }

    @AfterEach
    void teardown() {
        PageSessionManager.clear();
        PageRegistry.unregister(PAGE_ID);
        if (harness != null) harness.close();
    }

    private static PageViewer.IBuilder<String> pageOf(@Nullable String pageId, List<String> entries) {
        PageViewer.IStepSource<String> step = PageViewer.of(String.class);
        if (pageId != null) {
            step = step.id(pageId);
        }
        return step.source(() -> entries)
                .unlimitedEntries()
                .setPageSize(1)
                .theme(PageTheme.none())
                .setFormatLine("${entry}")
                .addRowPlaceholder("entry", entry -> entry);
    }

    /** The command a page button runs, taken off the page indicator - the only button always present. */
    private static String linkOfTheIndicator(TestFPlayerSender reader) {
        String link = reader.clickValueOfMessageContaining("Page [");
        assertNotNull(link, "the page indicator should carry a click: " + reader.getMessages());
        return link;
    }

    @Test
    void aNamedPageNavigatesByItsNameAndOpensNoSessionAtAll() {
        TestFPlayerSender reader = new TestFPlayerSender("Steve");

        pageOf(PAGE_ID, Arrays.asList("a", "b", "c", "d", "e")).build().send(reader);

        assertEquals("/ecpage " + PAGE_ID + " 1", linkOfTheIndicator(reader));

        for (int page = 1; page <= 5; page++) {
            reader.clearMessages();
            harness.dispatch(ecpage, reader, PAGE_ID + " " + page);
            assertTrue(reader.anyMessageContains("Page [" + page + "/5]"),
                    "page " + page + " was not delivered: " + reader.getMessages());
        }

        assertEquals(0, PageSessionManager.openSessions(),
                "a named page is resolved out of the registry, so navigating it costs no memory");
    }

    @Test
    void anAnonymousPageKeepsOneSessionPerReaderHoweverManyPagesAreTurned() {
        TestFPlayerSender reader = new TestFPlayerSender("Steve");

        pageOf(null, Arrays.asList("a", "b", "c", "d", "e")).build().send(reader);

        String handle = handleOf(linkOfTheIndicator(reader));

        for (int page = 1; page <= 5; page++) {
            reader.clearMessages();
            harness.dispatch(ecpage, reader, handle + " " + page);
            assertTrue(reader.anyMessageContains("Page [" + page + "/5]"),
                    "page " + page + " was not delivered: " + reader.getMessages());
        }

        assertEquals(1, PageSessionManager.openSessions(),
                "one reader turning five pages must leave one session, not five");
        assertNotNull(PageSessionManager.find(handle),
                "the fifth click has to put the session back, or it would expire while it is being read");
    }

    @Test
    void aSessionAnotherReaderOwnsAnswersExactlyLikeOneThatNeverExisted() {
        TestFPlayerSender owner = new TestFPlayerSender("Steve");
        TestFPlayerSender stranger = new TestFPlayerSender("Alex");

        pageOf(null, Arrays.asList("a", "b", "c")).build().send(owner);
        String handle = handleOf(linkOfTheIndicator(owner));

        harness.dispatch(ecpage, stranger, handle + " 2");

        assertTrue(stranger.anyMessageContains("no longer open"),
                "somebody else's handle must not page their view: " + stranger.getMessages());
        assertEquals(1, stranger.getMessages().size(),
                "nothing but the refusal may reach them: " + stranger.getMessages());
    }

    @Test
    void aHandleNobodyHoldsAnymoreTellsTheReaderWhatToDoAboutIt() {
        TestFPlayerSender reader = new TestFPlayerSender("Steve");

        //The shape of a handle whose session is gone - which is what the reader who relogged and
        //pressed the up arrow is holding.
        harness.dispatch(ecpage, reader, UUID.randomUUID() + " 2");

        assertTrue(reader.anyMessageContains("Run the command again"),
                "the refusal has to name the way out: " + reader.getMessages());
    }

    @Test
    void aLineCitingLabelStillNamesTheCommandThatProducedThePageOnPageTwo() {
        TestFPlayerSender reader = new TestFPlayerSender("Steve");

        PageViewer<String> viewer = PageViewer.of(String.class)
                .source(() -> Arrays.asList("a", "b", "c"))
                .unlimitedEntries()
                .setPageSize(1)
                .theme(PageTheme.none())
                .setFormatLine("/${label} entry ${number}")
                .build();

        try (MessageScope scope = MessageScope.open(CommandPath.ofRoot("mylist"))) {
            viewer.send(1, reader);
        }

        assertTrue(reader.anyMessageContains("/mylist entry 1"), "" + reader.getMessages());

        String handle = handleOf(linkOfTheIndicator(reader));
        reader.clearMessages();

        //Turning the page runs /ecpage, so the scope of THIS thread names ecpage - and the line must
        //still name the command that produced the page.
        harness.dispatch(ecpage, reader, handle + " 2");

        assertTrue(reader.anyMessageContains("/mylist entry 2"),
                "the second page named the wrong command: " + reader.getMessages());
    }

    @Test
    void everythingAtOnceDrawsNoArrowToAPageTheReaderHasAlreadyRead() {
        TestFPlayerSender everything = new TestFPlayerSender("Steve");
        TestFPlayerSender onePage = new TestFPlayerSender("Alex");

        PageViewer<String> viewer = pageOf(null, Arrays.asList("a", "b", "c"))
                .navigation(PageNavigation.command("/top %page%"))
                .build();

        viewer.send(new PageVisualization(1, 1, true), everything);
        viewer.send(1, onePage);

        assertEquals(Arrays.asList("a", "b", "c"), everything.getMessages(),
                "everything at once is the entries and nothing else: a bar under them would offer a "
                        + "page the reader has just read");
        assertTrue(onePage.anyMessageContains("Page ["),
                "while a single page still offers the way to the others: " + onePage.getMessages());
    }

    @Test
    void theCommandStrategyRunsTheLineTheCallerWroteWithThePageNumberInIt() {
        TestFPlayerSender reader = new TestFPlayerSender("Steve");

        pageOf(null, Arrays.asList("a", "b", "c"))
                .navigation(PageNavigation.command("/finaljobs top %page%"))
                .build()
                .send(reader);

        assertEquals("/finaljobs top 1", linkOfTheIndicator(reader));
        assertEquals(0, PageSessionManager.openSessions(),
                "re-running the caller's own command costs no memory");
    }

    /** {@code /ecpage <handle> <page>} -&gt; the handle. */
    private static String handleOf(String link) {
        String[] parts = link.split(" ");
        assertEquals(3, parts.length, "unexpected navigation link: " + link);
        return parts[1];
    }
}
