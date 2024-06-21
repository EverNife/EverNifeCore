package br.com.finalcraft.evernifecore.cache;

import br.com.finalcraft.evernifecore.util.FCTickUtil;
import lombok.Getter;

import java.util.function.Supplier;

@Getter
public class CacheableSupplierPerTick<O> {

    protected Supplier<O> supplier;
    protected long cacheInterval = 0;

    protected transient long lastExecuted = 0;
    protected transient O value = null;

    public CacheableSupplierPerTick(Supplier<O> supplier, long cacheInterval) {
        this.supplier = supplier;
        this.cacheInterval = cacheInterval;
    }

    public O getValue(){
        long currentTick = FCTickUtil.getTickCount();
        if (currentTick > getEndTime()){
            value = supplier.get();
            lastExecuted = currentTick;
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

    public static <O> IStep1BCacheableSupplierPerTick<O> of(Supplier<O> supplier){
        return new CacheableSupplierPerTickStepBuilderPerTick(supplier);
    }

    public static interface IStep1BCacheableSupplierPerTick<O>{
        public CacheableSupplierPerTick<O> withInterval(long intervalTicks);
    }

    public static class CacheableSupplierPerTickStepBuilderPerTick<O> implements IStep1BCacheableSupplierPerTick<O> {
        private final Supplier<O> supplier;
        private long cacheInterval = 0;

        public CacheableSupplierPerTickStepBuilderPerTick(Supplier<O> supplier) {
            this.supplier = supplier;
        }

        @Override
        public CacheableSupplierPerTick<O> withInterval(long intervalTicks) {
            cacheInterval = intervalTicks;
            return new CacheableSupplierPerTick(
                    this.supplier,
                    this.cacheInterval
            );
        }

    }

}
