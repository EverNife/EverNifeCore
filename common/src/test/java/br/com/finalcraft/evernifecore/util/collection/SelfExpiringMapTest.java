package br.com.finalcraft.evernifecore.util.collection;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic tests driven by a virtual clock: no sleeping, expiry is triggered by advancing
 * the injected time source.
 */
class SelfExpiringMapTest {

    @Test
    void entryExpiresOnGetOnceTheClockPassesTheTtl() {
        AtomicLong clock = new AtomicLong(0);
        SelfExpiringMap<String, String> map = new SelfExpiringMap<>(1000, -1, clock::get);

        map.put("a", "1");
        assertEquals("1", map.get("a"));
        assertTrue(map.containsKey("a"));

        clock.set(1000); // reach the expiry instant (expireAt <= now)
        assertNull(map.get("a"));
        assertFalse(map.containsKey("a"));
    }

    @Test
    void newPutSweepsOlderExpiredEntriesWithoutAnyRead() {
        AtomicLong clock = new AtomicLong(0);
        SelfExpiringMap<String, String> map = new SelfExpiringMap<>(1000, -1, clock::get);

        map.put("a", "1");
        map.put("b", "2");
        assertEquals(2, map.size());

        clock.set(1500); // both entries are now expired
        map.put("c", "3"); // inserting must purge the eldest expired entries

        assertEquals(1, map.size(), "the expired eldest entries should have been swept on write");
        assertTrue(map.containsKey("c"));
    }

    @Test
    void unexpiredEntryStays() {
        AtomicLong clock = new AtomicLong(0);
        SelfExpiringMap<String, String> map = new SelfExpiringMap<>(1000, -1, clock::get);

        map.put("a", "1");
        clock.set(999); // still within the lifetime
        assertEquals("1", map.get("a"));
        assertTrue(map.containsKey("a"));
    }

    @Test
    void maxSizeCapEvictsTheEldest() {
        AtomicLong clock = new AtomicLong(0);
        SelfExpiringMap<String, String> map = new SelfExpiringMap<>(1_000_000, 2, clock::get);

        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3"); // exceeds the cap of 2 -> eldest ("a") evicted

        assertEquals(2, map.size());
        assertFalse(map.containsKey("a"));
        assertTrue(map.containsKey("b"));
        assertTrue(map.containsKey("c"));
    }
}
