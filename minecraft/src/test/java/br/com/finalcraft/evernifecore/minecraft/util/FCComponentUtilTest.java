package br.com.finalcraft.evernifecore.minecraft.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FCComponentUtilTest {

    // A '\n' inside a single component's content is what colorfyComponent() produces; the old client
    // shows it as a glyph, so the send path must break it into separate lines.
    @Test
    public void splitsNewlinesEmbeddedInTextContent() {
        Component message = Component.text("line1\nline2\nline3");

        List<Component> lines = FCComponentUtil.splitNewlines(message);

        assertEquals(3, lines.size());
        assertEquals("line1", flatten(lines.get(0)));
        assertEquals("line2", flatten(lines.get(1)));
        assertEquals("line3", flatten(lines.get(2)));
    }

    // Each emitted line must still carry the hover/click set on an ancestor, otherwise clickable
    // buttons split across lines would go dead - which is the whole point of the split.
    @Test
    public void carriesAncestorHoverAndClickOntoEveryLine() {
        Component message = Component.text("A\nB")
                .clickEvent(ClickEvent.runCommand("/do"))
                .hoverEvent(HoverEvent.showText(Component.text("tip")));

        List<Component> lines = FCComponentUtil.splitNewlines(message);

        assertEquals(2, lines.size());
        for (Component line : lines) {
            assertTrue(hasClick(line), "click lost on line: " + flatten(line));
            assertTrue(hasHover(line), "hover lost on line: " + flatten(line));
        }
    }

    @Test
    public void keepsSingleLineIntact() {
        List<Component> lines = FCComponentUtil.splitNewlines(Component.text("no breaks here"));

        assertEquals(1, lines.size());
        assertEquals("no breaks here", flatten(lines.get(0)));
    }

    private static String flatten(Component component) {
        StringBuilder sb = new StringBuilder();
        if (component instanceof TextComponent) {
            sb.append(((TextComponent) component).content());
        }
        for (Component child : component.children()) {
            sb.append(flatten(child));
        }
        return sb.toString();
    }

    private static boolean hasClick(Component component) {
        if (component.clickEvent() != null) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasClick(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasHover(Component component) {
        if (component.hoverEvent() != null) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasHover(child)) {
                return true;
            }
        }
        return false;
    }
}
