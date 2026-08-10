package br.com.finalcraft.evernifecore.util.collection;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Insertion-ordered map whose entries self-expire after a fixed lifetime.
 *
 * <p>Expired entries are purged amortized on write (each {@link #put} sweeps the
 * eldest entries and stops at the first live one, which is O(1) amortized under a
 * uniform time-to-live) and lazily on read ({@link #get} and {@link #containsKey}
 * drop an entry that has already expired and answer as if it were absent). There
 * is no background thread.
 *
 * <p>An optional maximum size bounds memory even under a burst that stays within
 * the time-to-live window: once the cap is exceeded the eldest entry is evicted.
 *
 * <p>The clock is injectable so the expiry behaviour can be tested deterministically
 * without sleeping.
 *
 * <p>This map is not thread-safe. Candidate to move to a shared utility library.
 */
public class SelfExpiringMap<K, V> extends LinkedHashMap<K, V> {

    private static final int NO_MAX_SIZE = -1;

    private final long ttlMillis;
    private final int maxSize;
    private final LongSupplier nowSupplier;
    private final LinkedHashMap<K, Long> expireAtByKey = new LinkedHashMap<>();

    public SelfExpiringMap(long ttlMillis) {
        this(ttlMillis, NO_MAX_SIZE, System::currentTimeMillis);
    }

    public SelfExpiringMap(long ttlMillis, int maxSize) {
        this(ttlMillis, maxSize, System::currentTimeMillis);
    }

    public SelfExpiringMap(long ttlMillis, int maxSize, LongSupplier nowSupplier) {
        this.ttlMillis = ttlMillis;
        this.maxSize = maxSize;
        this.nowSupplier = nowSupplier;
    }

    @Override
    public V put(K key, V value) {
        purgeExpired();
        //re-inserted rather than overwritten in place: the sweep walks insertion order and stops at
        //the first live entry, so a key that kept its old position with a new deadline would shelter
        //every expired entry behind it for as long as it goes on being renewed
        V previous = super.remove(key);
        expireAtByKey.remove(key);
        super.put(key, value);
        expireAtByKey.put(key, nowSupplier.getAsLong() + ttlMillis);
        return previous;
    }

    /**
     * Drops what has already expired, so a count or an iteration that follows sees only live
     * entries. Writes sweep on their own; this is for a reader that has to be exact.
     */
    public void sweepExpired() {
        purgeExpired();
    }

    @Override
    public V get(Object key) {
        if (hasExpired(key)) {
            removeEntry(key);
            return null;
        }
        return super.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        if (hasExpired(key)) {
            removeEntry(key);
            return false;
        }
        return super.containsKey(key);
    }

    @Override
    public V remove(Object key) {
        expireAtByKey.remove(key);
        return super.remove(key);
    }

    @Override
    public void clear() {
        expireAtByKey.clear();
        super.clear();
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        if (maxSize != NO_MAX_SIZE && size() > maxSize) {
            expireAtByKey.remove(eldest.getKey());
            return true;
        }
        return false;
    }

    private boolean hasExpired(Object key) {
        Long expireAt = expireAtByKey.get(key);
        return expireAt != null && expireAt <= nowSupplier.getAsLong();
    }

    private void removeEntry(Object key) {
        expireAtByKey.remove(key);
        super.remove(key);
    }

    /**
     * Sweep the eldest entries while they are expired, stopping at the first live one.
     * Insertion order plus a uniform lifetime means every later entry is younger, so the
     * early stop keeps the cost amortized.
     */
    private void purgeExpired() {
        long now = nowSupplier.getAsLong();
        Iterator<Map.Entry<K, Long>> it = expireAtByKey.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, Long> entry = it.next();
            if (entry.getValue() <= now) {
                super.remove(entry.getKey());
                it.remove();
            } else {
                break;
            }
        }
    }
}
