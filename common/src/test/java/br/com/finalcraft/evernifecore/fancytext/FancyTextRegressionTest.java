package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.util.FCColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anti-regression pins for behaviour that already renders correctly today. None of these may go
 * red while the FancyText model/codec/vocabulary is rewritten - a failure here means a rendering
 * contract broke, not that a known bug was fixed.
 */
public class FancyTextRegressionTest {

    @Test
    void trailingColorBleedsIntoTheNextSegmentWithNoColorOfItsOwn() {
        // Legacy chat lets a colour bleed into the following text; FancyFormatter.toComponent()
        // carries each segment's trailing colour into the next one as its starting colour.
        FancyFormatter formatter = new FancyFormatter()
                .append("§aHello ")
                .append("World");

        String legacy = formatter.toLegacyString();

        String beforeWorld = legacy.substring(0, legacy.indexOf("World"));
        assertEquals("§a", FCColorUtil.getLastColors(beforeWorld),
                "green must still be active where 'World' starts: " + legacy);
    }

    @Test
    void hexColorSurvivesRenderingToAComponentAndBack() {
        String source = "&#ff0000Red text";
        Component component = FancyText.of(source).toComponent();

        assertEquals(FCColorUtil.colorfy(source), FCColorUtil.componentToString(component),
                "the &#RRGGBB hex form must round-trip through toComponent() unchanged");
    }

    @Test
    void hoverTextIsAttachedToTheRenderedComponent() {
        Component component = FancyText.of("Click me", "A helpful tooltip").toComponent();

        HoverEvent<?> hoverEvent = component.hoverEvent();
        assertNotNull(hoverEvent, "hover must be attached to the rendered component");
        assertEquals("A helpful tooltip",
                FCColorUtil.componentToString((Component) hoverEvent.value()));
    }

    @Test
    void everyClickActionTypeRendersItsOwnClickEvent() {
        Component runCommand = FancyText.of("run", null, "/say hi", ClickActionType.RUN_COMMAND).toComponent();
        assertEquals(ClickEvent.Action.RUN_COMMAND, runCommand.clickEvent().action());
        assertEquals("/say hi", runCommand.clickEvent().value());

        Component openUrl = FancyText.of("link", null, "https://example.com", ClickActionType.OPEN_URL).toComponent();
        assertEquals(ClickEvent.Action.OPEN_URL, openUrl.clickEvent().action());
        assertEquals("https://example.com", openUrl.clickEvent().value());

        Component suggest = FancyText.of("suggest", null, "/say ", ClickActionType.SUGGEST_COMMAND).toComponent();
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, suggest.clickEvent().action());
        assertEquals("/say ", suggest.clickEvent().value());

        Component none = FancyText.of("plain").toComponent();
        assertNull(none.clickEvent(), "a FancyText with no click action must render no ClickEvent");
    }

    @Test
    void getLastColorsKeepsAFormatCodeAfterTheColorThatIntroducedIt() {
        // '§l' (bold) is a FORMAT code, not a colour: the scan must not stop on it, but must still
        // carry it alongside the colour that is actually active.
        assertEquals("§a§l", FCColorUtil.getLastColors("§a§lHello"));
    }

    // ------------------------------------------------------------------
    //  absorbed from the minecraft module: these never needed it
    // ------------------------------------------------------------------

    @Test
    public void formatterDoesNotLeakColorNamesIntoText() {
        // Mirrors /fclocale list: a segment ending in a colour, then a plain button segment.
        FancyFormatter formatter = FancyFormatter.of("§d ♦ §bENCTemplate §7")
                .append(FancyText.of("[EN_US]§7"));

        String legacy = formatter.toLegacyString();

        assertFalse(legacy.contains("GRAY"), "enum name leaked into text: " + legacy);
        assertFalse(legacy.contains("RESET"), "enum name leaked into text: " + legacy);
        assertTrue(legacy.contains("[EN_US]"), "button text missing: " + legacy);
    }

    @Test
    public void formatterPropagatesTrailingColorIntoNextSegment() {
        // First segment ends green; the second has no colour of its own and must inherit it,
        // the way legacy chat bleeds a colour into the following text.
        FancyFormatter formatter = FancyFormatter.of("§aHello ")
                .append(FancyText.of("World"));

        String legacy = formatter.toLegacyString();

        assertTrue(legacy.contains("World"), "text missing: " + legacy);
        // The colour still active where "World" starts must be green - if propagation failed the
        // serializer would have reset it (e.g. "§aHello §fWorld").
        String beforeWorld = legacy.substring(0, legacy.indexOf("World"));
        assertEquals("§a", FCColorUtil.getLastColors(beforeWorld), "green not carried into 2nd segment: " + legacy);
    }
}
