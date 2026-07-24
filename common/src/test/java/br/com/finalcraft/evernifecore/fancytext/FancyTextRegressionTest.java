package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.util.FCColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
