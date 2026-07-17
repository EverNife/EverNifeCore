package br.com.finalcraft.evernifecore.cooldown;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the cooldown value ({@link CooldownEntry}), the handle over it ({@link Cooldown}) and the
 * bucket that files entries by their persist flag ({@link CooldownBucket}).
 */
class CooldownTest {

    // ==================== the anchor ====================

    @Test
    void aCustomDurationReadReinterpretsTheAnchor() {
        long now = System.currentTimeMillis();
        // started 200s ago, nominally lasting 300s
        Cooldown cooldown = new GenericCooldown("vip",
                now - TimeUnit.SECONDS.toMillis(200), TimeUnit.SECONDS.toMillis(300), false);

        assertFalse(cooldown.isInCooldown(150), "a 150s reading of the same anchor is already free");
        assertTrue(cooldown.isInCooldown(), "the nominal 300s reading still waits");
    }

    // ==================== latest() ====================

    @Test
    void latestLetsANewerStopBeatAnOlderStart() {
        CooldownEntry started = new CooldownEntry(100L, 60_000L, 100L, true);
        CooldownEntry stopped = new CooldownEntry(0L, 60_000L, 200L, true);

        assertSame(stopped, CooldownEntry.latest(started, stopped));
        assertSame(stopped, CooldownEntry.latest(stopped, started), "the outcome cannot depend on the order");
        assertEquals(0L, CooldownEntry.latest(started, stopped).getTimeStart());
    }

    @Test
    void latestBreaksAnUpdatedAtTieByTheLaterExpiry() {
        CooldownEntry longer = new CooldownEntry(100L, 60_000L, 500L, true);
        CooldownEntry shorter = new CooldownEntry(100L, 30_000L, 500L, true);

        assertSame(longer, CooldownEntry.latest(longer, shorter));
        assertSame(longer, CooldownEntry.latest(shorter, longer));
    }

    @Test
    void latestBreaksAFullTieByThePersistFlag() {
        CooldownEntry persistent = new CooldownEntry(100L, 60_000L, 500L, true);
        CooldownEntry ephemeral = new CooldownEntry(100L, 60_000L, 500L, false);

        assertSame(persistent, CooldownEntry.latest(persistent, ephemeral));
        assertSame(persistent, CooldownEntry.latest(ephemeral, persistent));
    }

    // ==================== the Cooldowns.yml form ====================

    @Test
    void theConfigFormCarriesTheMutationClock() {
        Cooldown cooldown = new GenericCooldown("mycd", new CooldownEntry(1000L, 5000L, 7777L, true));

        Map<String, Object> map = cooldown.toConfigMap();
        assertEquals(7777L, map.get("updatedAt"));

        Cooldown reread = Cooldown.fromConfigMap(map);
        assertEquals("mycd", reread.getIdentifier());
        assertEquals(1000L, reread.getStart());
        assertEquals(5000L, reread.getDuration());
        assertEquals(7777L, reread.getEntry().getUpdatedAt());
        assertTrue(reread.isPersistent(), "a stored cooldown is always persistent");
    }

    @Test
    void aStoredEntryWithoutAMutationClockFallsBackToItsStart() {
        Map<String, Object> written = new LinkedHashMap<>();
        written.put("identifier", "old");
        written.put("timeStart", 4242L);
        written.put("timeDuration", 5000L);

        Cooldown restored = Cooldown.fromConfigMap(written);

        assertEquals(4242L, restored.getEntry().getUpdatedAt());
        assertEquals(4242L, restored.getStart());
    }

    // ==================== the route hook ====================

    @Test
    void aCooldownThatIsNeverPersistentNeverReachesItsRoute() {
        RecordingCooldown cooldown = new RecordingCooldown("throwaway");

        cooldown.setDuration(1200);
        cooldown.startWith(60);
        cooldown.stop();

        assertEquals(0, cooldown.routeCalls, "a memory-only cooldown has no row to keep in agreement");
    }

    @Test
    void everyMutationOfAPersistentCooldownReachesItsRoute() {
        RecordingCooldown cooldown = new RecordingCooldown("kept");

        cooldown.setPersist(true);
        assertEquals(1, cooldown.routeCalls);

        cooldown.startWith(60);
        assertEquals(2, cooldown.routeCalls, "startWith is one mutation, so one call");

        cooldown.setDuration(TimeUnit.SECONDS.toMillis(30));
        assertEquals(3, cooldown.routeCalls);
    }

    @Test
    void stoppingStillReachesTheRouteOfACooldownThatJustLostItsPersistFlag() {
        RecordingCooldown cooldown = new RecordingCooldown("kept");
        cooldown.setPersist(true).startWith(60);
        int callsBeforeStop = cooldown.routeCalls;

        cooldown.stop();

        assertEquals(callsBeforeStop + 1, cooldown.routeCalls, "the stored row still has to be dropped");
        assertFalse(cooldown.isPersistent());
        assertEquals(0L, cooldown.getStart());
    }

    @Test
    void everyMutationStampsTheMutationClock() {
        RecordingCooldown cooldown = new RecordingCooldown("kept");
        assertEquals(0L, cooldown.getEntry().getUpdatedAt());

        cooldown.startWith(60);

        assertTrue(cooldown.getEntry().getUpdatedAt() > 0);
    }

    // ==================== the bucket ====================

    @Test
    void theBucketFilesAnEntryByItsPersistFlag() {
        TestBucket bucket = new TestBucket();

        CooldownEntry entry = bucket.resolveCooldown("cd");
        assertTrue(bucket.getPersistedCooldowns().isEmpty(), "resolving alone must not grow the row");
        assertTrue(bucket.getTransientCooldowns().isEmpty());

        bucket.fileCooldown("cd", entry);
        assertSame(entry, bucket.getTransientCooldowns().get("cd"));
        assertTrue(bucket.getPersistedCooldowns().isEmpty(), "persist == false never reaches storage");

        entry.setPersist(true);
        bucket.fileCooldown("cd", entry);
        assertSame(entry, bucket.getPersistedCooldowns().get("cd"));
        assertTrue(bucket.getTransientCooldowns().isEmpty(), "an entry lives in exactly one map");
        assertSame(entry, bucket.findCooldown("cd"));

        bucket.dropCooldown("cd");
        assertNull(bucket.findCooldown("cd"));
    }

    @Test
    void aHandleOverABucketFilesItsEntryOnceItIsPersistent() {
        TestBucket bucket = new TestBucket();

        new BucketCooldown("vip", bucket).setPersist(true).startWith(60);

        CooldownEntry stored = bucket.getPersistedCooldowns().get("vip");
        assertNotNull(stored, "a persistent cooldown must reach the row");
        assertTrue(stored.getUpdatedAt() > 0);

        assertTrue(new BucketCooldown("vip", bucket).isInCooldown(),
                "a later handle over the same bucket reads the state the first one left");
    }

    // ==================== fixtures ====================

    /** A route that only counts how often it was told to keep storage in agreement. */
    private static class RecordingCooldown extends Cooldown {

        private int routeCalls = 0;

        RecordingCooldown(String identifier) {
            super(identifier);
        }

        @Override
        protected void onMutated() {
            routeCalls++;
        }
    }

    /** The shape a stored route takes: a handle over an entry of a bucket, filed on every mutation. */
    private static class BucketCooldown extends Cooldown {

        private final CooldownBucket bucket;

        BucketCooldown(String identifier, CooldownBucket bucket) {
            super(identifier, bucket.resolveCooldown(identifier));
            this.bucket = bucket;
        }

        @Override
        protected void onMutated() {
            bucket.fileCooldown(getIdentifier(), getEntry());
        }
    }

    private static class TestBucket implements CooldownBucket {

        private final Map<String, CooldownEntry> persisted = new LinkedHashMap<>();
        private final Map<String, CooldownEntry> ephemeral = new LinkedHashMap<>();

        @Override
        public Map<String, CooldownEntry> getPersistedCooldowns() {
            return persisted;
        }

        @Override
        public Map<String, CooldownEntry> getTransientCooldowns() {
            return ephemeral;
        }
    }
}
