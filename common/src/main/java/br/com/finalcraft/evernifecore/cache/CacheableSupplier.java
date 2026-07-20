package br.com.finalcraft.evernifecore.cache;

import lombok.Data;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Caches the result of a {@link Supplier} for a fixed interval, refreshing lazily on the next read
 * after it expires.
 *
 * <p>Thread-safe: {@code getValue()} and {@code refreshAndGetValue()} are synchronized and the
 * cached fields are {@code volatile}, so concurrent readers see a consistent value and the wrapped
 * {@code supplier.get()} runs at most once per interval no matter how many threads race on an
 * expired cache.</p>
 */
@Data
public class CacheableSupplier<O> {

    protected Supplier<O> supplier;
    protected long cacheInterval = 0;

    protected transient volatile long lastExecuted = 0;
    protected transient volatile O value = null;

    public CacheableSupplier(Supplier<O> supplier, long cacheInterval) {
        this.supplier = supplier;
        this.cacheInterval = cacheInterval;
    }

    public synchronized O getValue(){
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis > getEndTime()){
            value = supplier.get();
            lastExecuted = currentTimeMillis;
        }
        return value;
    }

    public long getEndTime(){
        return lastExecuted + cacheInterval;
    }

    public synchronized O refreshAndGetValue(){
        lastExecuted = 0;
        return getValue();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Step Builder
    // -----------------------------------------------------------------------------------------------------------------

    public static <O> IStep1BCacheableSupplier<O> of(Supplier<O> supplier){
        return new CacheableSupplierStepBuilder(supplier);
    }

    public static interface IStep1BCacheableSupplier<O>{
        public CacheableSupplier<O> withInterval(long intervalMillis);
        public CacheableSupplier<O> withInterval(TimeUnit timeUnit, long value);
    }

    public static class CacheableSupplierStepBuilder<O> implements IStep1BCacheableSupplier<O>{
        private final Supplier<O> supplier;
        private long cacheInterval = 0;

        public CacheableSupplierStepBuilder(Supplier<O> supplier) {
            this.supplier = supplier;
        }

        @Override
        public CacheableSupplier<O> withInterval(long intervalMillis) {
            cacheInterval = intervalMillis;
            return new CacheableSupplier(
                    this.supplier,
                    this.cacheInterval
            );
        }

        @Override
        public CacheableSupplier<O> withInterval(TimeUnit timeUnit, long value) {
            this.cacheInterval = timeUnit.toMillis(value);
            return new CacheableSupplier(
                    this.supplier,
                    this.cacheInterval
            );
        }

    }

}
