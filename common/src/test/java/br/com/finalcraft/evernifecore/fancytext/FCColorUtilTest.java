package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.util.FCColorUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class FCColorUtilTest {

    // ColorEnum has no toString() override, so getLastColors() used to return the enum name
    // (GRAY, RESET) instead of the code chars. Those names then leaked into the rendered text once
    // FancyFormatter started threading the trailing colour into the next segment.
    @Test
    public void getLastColorsReturnsCodeCharsNotEnumNames() {
        assertEquals("§7", FCColorUtil.getLastColors("hello §7world"));
        assertEquals("§r", FCColorUtil.getLastColors("§a§m-§r"));
        // Format codes trailing the last colour are preserved, in order.
        assertEquals("§a§l", FCColorUtil.getLastColors("§a§lbold green"));

        String last = FCColorUtil.getLastColors("§7x");
        assertFalse(last.contains("GRAY"), "leaked enum name: " + last);
        assertFalse(last.contains("RESET"), "leaked enum name: " + last);
    }
}
