package br.com.finalcraft.evernifecore.pageviewer;

import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.pageviewer.nav.PVExtraMessages;
import br.com.finalcraft.evernifecore.pageviewer.nav.PageNavigation;
import br.com.finalcraft.evernifecore.pageviewer.theme.ClassicPageTheme;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageTheme;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule above a page: a width the server states because the client cannot be asked for one, and
 * a colour that has to survive the reset closing a generated run.
 */
@ECoreTest
class PageThemeTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("PageTheme", tempDir);
        FCLocaleManager.loadLocale(harness.ecPluginData, ClassicPageTheme.class, PVExtraMessages.class);
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private static String ruleSentBy(PageTheme theme) {
        TestCommandSender console = new TestCommandSender("CONSOLE");

        PageViewer.of(String.class)
                .source(() -> Collections.singletonList("alpha"))
                .unlimitedEntries()
                .theme(theme)
                .navigation(PageNavigation.none())
                .setFormatLine("line")
                .build()
                .send(console);

        assertEquals(2, console.getMessages().size(), "expected a rule and one entry: " + console.getMessages());
        return console.getMessages().get(0);
    }

    @Test
    void theClassicRuleIsFiftyThreeDashesWide() {
        String rule = ruleSentBy(PageTheme.classic());

        assertEquals(53, rule.chars().filter(character -> character == '-').count(),
                "the rule that fills a 320px chat line is 53 dashes wide: " + rule);
    }

    @Test
    void theClassicRuleReachesTheReaderGreenAndStruckThrough() {
        String rule = ruleSentBy(PageTheme.classic());
        String beforeTheDashes = rule.substring(0, rule.indexOf('-'));

        assertTrue(beforeTheDashes.contains("§a"), "the rule lost its colour: " + rule);
        assertTrue(beforeTheDashes.contains("§m"), "the rule lost its strikethrough: " + rule);
        assertFalse(beforeTheDashes.contains("§r"),
                "a reset before the dashes cancels both, which is how the rule used to render plain: " + rule);
    }

    // A platform that cannot measure its chat surface hands every layout helper its input back, so
    // the opt-in rule degrades to the unit it repeats instead of dividing by a zero width.
    @Test
    void theAutoFitRuleIsHandedBackUntouchedOnAPlatformThatCannotMeasureText() {
        String rule = ruleSentBy(PageTheme.autoFit());

        assertEquals(1, rule.chars().filter(character -> character == '-').count(),
                "an unmeasured platform cannot fill anything, so the unit comes back as it went in: " + rule);
    }
}
