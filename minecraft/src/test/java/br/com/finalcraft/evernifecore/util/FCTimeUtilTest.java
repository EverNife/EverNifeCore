package br.com.finalcraft.evernifecore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FCTimeUtilTest {

    @Test
    void testFromMillis() {
        assertEquals("0s", FCTimeUtil.fromMillis(0));
        assertEquals("1s", FCTimeUtil.fromMillis(1000));
        assertEquals("1m", FCTimeUtil.fromMillis(60000));
        assertEquals("1h", FCTimeUtil.fromMillis(3600000));
        assertEquals("1d", FCTimeUtil.fromMillis(86400000));
        assertEquals("1d 1h 1m 1s", FCTimeUtil.fromMillis(90061000));
    }

    @Test
    void testToMillis() {
        assertEquals(1000L, FCTimeUtil.toMillis("1s"));
        assertEquals(60000L, FCTimeUtil.toMillis("1m"));
        assertEquals(3600000L, FCTimeUtil.toMillis("1h"));
        assertEquals(86400000L, FCTimeUtil.toMillis("1d"));
        assertEquals(90061000L, FCTimeUtil.toMillis("1d 1h 1m 1s"));
        assertEquals(3600000L, FCTimeUtil.toMillis("1:"));
        assertEquals(3660000L, FCTimeUtil.toMillis("1:01"));
        assertEquals(3661000L, FCTimeUtil.toMillis("1:01:01"));
        assertNull(FCTimeUtil.toMillis("invalid"));
    }

    @Test
    void testUniversalDateConverter() {
        assertNotNull(FCTimeUtil.universalDateConverter("2023/01/01"));
        assertNotNull(FCTimeUtil.universalDateConverter("2023-01-01"));
        assertNotNull(FCTimeUtil.universalDateConverter("01/01/2023"));
        assertNotNull(FCTimeUtil.universalDateConverter("01/01/2023 12:30"));
        assertNotNull(FCTimeUtil.universalDateConverter("01/01/2023 12:30:10"));
        assertNotNull(FCTimeUtil.universalDateConverter("2023-01-01T12:30"));
        assertNull(FCTimeUtil.universalDateConverter(null));
        assertNull(FCTimeUtil.universalDateConverter(""));
        assertNull(FCTimeUtil.universalDateConverter("invalid"));
    }
}