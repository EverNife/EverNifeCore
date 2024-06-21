package br.com.finalcraft.evernifecore.cache;

import lombok.Data;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Data
public class CacheableSupplier<O> {

    protected Supplier<O> supplier;
    protected long cacheInterval = 0;

    protected transient long lastExecuted = 0;
    protected transient O value = null;

    public CacheableSupplier(Supplier<O> supplier, long cacheInterval) {
        this.supplier = supplier;
        this.cacheInterval = cacheInterval;
    }

    public O getValue(){
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

    public O refreshAndGetValue(){
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
