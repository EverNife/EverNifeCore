package br.com.finalcraft.evernifecore.minecraft.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.Test;
import java.util.List;
import net.md_5.bungee.api.chat.BaseComponent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;


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

    // ------------------------------------------------------------------
    //  click events, and how they compose with the rest
    // ------------------------------------------------------------------

    @Test
    public void toBaseComponentsPopulatesTheClickEvent() {
        Component button = Component.text("[EN_US]")
                .clickEvent(ClickEvent.runCommand("/ec dyn 123"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to change")));

        BaseComponent[] md5 = FCComponentUtil.toBaseComponents(button);

        // A real md_5 TextComponent, not adventure's opaque AdapterComponent (whose getters are null).
        assertEquals("net.md_5.bungee.api.chat.TextComponent", md5[0].getClass().getName());
        assertNotNull(md5[0].getClickEvent(), "click event dropped");
        assertNotNull(md5[0].getHoverEvent(), "hover event dropped");
        assertEquals("/ec dyn 123", md5[0].getClickEvent().getValue());
    }

    @Test
    public void clickSurvivesTheFullSplitAndSerializePath() {
        // Two clickable lines separated by a newline (the /fclocale list shape).
        Component message = Component.text("btn1")
                .clickEvent(ClickEvent.runCommand("/one"))
                .append(Component.newline())
                .append(Component.text("btn2").clickEvent(ClickEvent.suggestCommand("/two")));

        for (Component line : FCComponentUtil.splitNewlines(message)) {
            BaseComponent[] md5 = FCComponentUtil.toBaseComponents(line);
            assertTrue(anyHasClick(md5), "a line reached the wire without its click event");
        }
    }

    private static boolean anyHasClick(BaseComponent[] components) {
        for (BaseComponent c : components) {
            if (c.getClickEvent() != null) {
                return true;
            }
            if (c.getExtra() != null && anyHasClick(c.getExtra().toArray(new BaseComponent[0]))) {
                return true;
            }
        }
        return false;
    }
}
