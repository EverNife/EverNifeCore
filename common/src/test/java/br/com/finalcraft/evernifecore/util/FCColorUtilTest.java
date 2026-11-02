package br.com.finalcraft.evernifecore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FCColorUtilTest {

    @Test
    void colorfyTranslatesOnlyAnAmpersandThatStartsACode() {
        assertEquals("§aTom & Jerry §l50%& up", FCColorUtil.colorfy("&aTom & Jerry &l50%& up"));
        assertEquals("§x§f§f§0§0§0§0red", FCColorUtil.colorfy("&#ff0000red"));
        assertEquals("§aalready", FCColorUtil.colorfy("§aalready"));
        assertNull(FCColorUtil.colorfy((String) null));
    }

}
