package br.com.finalcraft.evernifecore.minecraft.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.md_5.bungee.api.chat.BaseComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Guards the 1.7.10 regression: adventure defaults to the 1.21.5 snake_case click JSON, which old
// clients ignore. toBaseComponents() must yield REAL md_5 components whose click event is populated,
// so the server re-serialises them in a form every client (down to 1.7.10) understands.
public class FCComponentUtilClickTest {

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
