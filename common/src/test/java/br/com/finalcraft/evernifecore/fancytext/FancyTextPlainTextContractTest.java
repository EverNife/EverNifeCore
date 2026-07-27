package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.TestCommandSender;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * C20 - obtaining a message's final text WITHOUT sending it: {@code toLegacyString(...)} (with
 * {@code §} colours and {@code ${key}} resolved) and {@code toPlainText(...)} (colours stripped).
 */
public class FancyTextPlainTextContractTest {

    @BeforeAll
    static void installTestPlatform() {
        TestPlatformFixture.ensureInstalled();
    }

    @Test
    void toLegacyStringEqualsSerializingTheComponent() {
        FancySegment segment = new FancySegment("§aHello §bworld");
        assertEquals(FCColorUtil.componentToString(segment.toComponent()), segment.toLegacyString(),
                "toLegacyString() must equal componentToString(toComponent())");
    }

    @Test
    void toLegacyStringWithContextResolvesPlaceholdersLikeSendWould() {
        FancySegment segment = new FancySegment("§aHello ${name}");
        segment.addPlaceholder("name", "World");

        TestCommandSender sender = new TestCommandSender("preview");
        segment.send(sender);
        String delivered = sender.getMessages().get(0);

        assertEquals(delivered, segment.toLegacyString(RenderContext.of(sender)),
                "toLegacyString(context) must equal what send() actually delivers");
        assertEquals("§aHello World", segment.toLegacyString(RenderContext.empty()),
                "the ${name} placeholder must be resolved in the preview");
    }

    @Test
    void toPlainTextStripsTheColourCodes() {
        FancySegment segment = new FancySegment("§aHello §b§lworld");
        String plain = segment.toPlainText();
        assertEquals("Hello world", plain, "toPlainText() must strip every colour/format code");
        assertFalse(plain.contains("§"), "no section sign may survive toPlainText()");
    }

    @Test
    void toPlainTextWithContextStripsColourAndResolvesPlaceholder() {
        FancySegment segment = new FancySegment("§aHi ${name}");
        segment.addPlaceholder("name", "Bob");

        assertEquals("Hi Bob", segment.toPlainText(RenderContext.empty()),
                "toPlainText(context) must both strip colour and resolve the placeholder");
    }
}
