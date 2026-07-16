package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.util.FCColorUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Lives in the minecraft module because toComponent() pulls in FCPlatformType -> commons-lang3,
// which only the minecraft test runtime provides.
public class FancyFormatterColorTest {

    @Test
    public void formatterDoesNotLeakColorNamesIntoText() {
        // Mirrors /fclocale list: a segment ending in a colour, then a plain button segment.
        FancyFormatter formatter = FancyFormatter.of("§d ♦ §bENCTemplate §7")
                .append(FancyText.of("[EN_US]§7"));

        String legacy = FCColorUtil.componentToString(formatter.toComponent());

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

        String legacy = FCColorUtil.componentToString(formatter.toComponent());

        assertTrue(legacy.contains("World"), "text missing: " + legacy);
        // The colour still active where "World" starts must be green - if propagation failed the
        // serializer would have reset it (e.g. "§aHello §fWorld").
        String beforeWorld = legacy.substring(0, legacy.indexOf("World"));
        assertEquals("§a", FCColorUtil.getLastColors(beforeWorld), "green not carried into 2nd segment: " + legacy);
    }
}
