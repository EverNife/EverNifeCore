package br.com.finalcraft.evernifecore.util.numberwrapper;

import br.com.finalcraft.evernifecore.util.FCMathUtil;

import javax.annotation.Nonnull;
import java.util.Objects;

public class NumberWrapper<N extends Number> implements Comparable<NumberWrapper> {

    protected N value;

    public static <N extends Number> NumberWrapper<N> of(N number){
        return new NumberWrapper<>(number);
    }

    protected NumberWrapper(N value) {
        this.value = value;
    }

    public N get() {
        return value;
    }

    public int intValue() {
        return value.intValue();
    }

    public long longValue() {
        return value.longValue();
    }

    public float floatValue() {
        return value.floatValue();
    }

    public double doubleValue(){
        return value.doubleValue();
    }

    public NumberWrapper<N> setValue(N value) {
        this.value = value;
        return this;
    }

    public NumberWrapper<N> boundLower(N min){
        if (this.value instanceof Integer)  { this.value = (N) Integer.valueOf(Math.max(this.value.intValue(), (Integer) min)); return this; }
        if (this.value instanceof Long)     { this.value = (N) Long.valueOf(Math.max(this.value.longValue(), (Long) min)); return this; }
        if (this.value instanceof Float)    { this.value = (N) Float.valueOf(Math.max(this.value.floatValue(), (Float) min)); return this; }
        if (this.value instanceof Double)   { this.value = (N) Double.valueOf(Math.max(this.value.doubleValue(), (Double) min)); return this; }
        if (this.value instanceof Byte)     { this.value = (N) Byte.valueOf((byte) Math.max(this.value.byteValue(), (Byte) min)); return this; }

        throw new UnsupportedOperationException("The Number [" + value.getClass() + "] is not supported by NumberWrapper!");
    }

    public NumberWrapper<N> boundUpper(N max){
        if (this.value instanceof Integer)  { this.value = (N) Integer.valueOf(Math.min(this.value.intValue(), (Integer) max)); return this; }
        if (this.value instanceof Long)     { this.value = (N) Long.valueOf(Math.min(this.value.longValue(), (Long) max)); return this; }
        if (this.value instanceof Float)    { this.value = (N) Float.valueOf(Math.min(this.value.floatValue(), (Float) max)); return this; }
        if (this.value instanceof Double)   { this.value = (N) Double.valueOf(Math.min(this.value.doubleValue(), (Double) max)); return this; }
        if (this.value instanceof Byte)     { this.value = (N) Byte.valueOf((byte) Math.min(this.value.byteValue(), (Byte) max)); return this; }

        throw new UnsupportedOperationException("The Number [" + value.getClass() + "] is not supported by NumberWrapper!");
    }

    public NumberWrapper<N> bound(N min, N max){
        if (this.value instanceof Integer)  { this.value = (N) Integer.valueOf(Math.max(Math.min(this.value.intValue(), (Integer) max), (Integer) min)); return this; }
        if (this.value instanceof Long)     { this.value = (N) Long.valueOf(Math.max(Math.min(this.value.longValue(), (Long) max), (Long) min)); return this; }
        if (this.value instanceof Float)    { this.value = (N) Float.valueOf(Math.max(Math.min(this.value.floatValue(), (Float) max), (Float) min)); return this; }
        if (this.value instanceof Double)   { this.value = (N) Double.valueOf(Math.max(Math.min(this.value.doubleValue(), (Double) max), (Double) min)); return this; }
        if (this.value instanceof Byte)     { this.value = (N) Byte.valueOf((byte) Math.max(Math.min(this.value.byteValue(), (Byte) max), (Byte) min)); return this; }

        throw new UnsupportedOperationException("The Number [" + value.getClass() + "] is not supported by NumberWrapper!");
    }

    public boolean isBounded(N min, N max){
        if (this.value instanceof Integer)  { return (Integer)this.value >= (Integer)min && (Integer)this.value <= (Integer)max;}
        if (this.value instanceof Long)  { return (Long)this.value >= (Long)min && (Long)this.value <= (Long)max;}
        if (this.value instanceof Float)  { return (Float)this.value >= (Float)min && (Float)this.value <= (Float)max;}
        if (this.value instanceof Double)  { return (Double)this.value >= (Double)min && (Double)this.value <= (Double)max;}
        if (this.value instanceof Byte)  { return (Integer)this.value >= (Integer)min && (Integer)this.value <= (Integer)max;}

        throw new UnsupportedOperationException("The Number [" + value.getClass() + "] is not supported by NumberWrapper!");
    }

    public boolean isBoundedUpper(N max){
        if (this.value instanceof Integer)  { return (Integer)this.value <= max.intValue();}
        if (this.value instanceof Long)  { return (Long)this.value <= max.longValue();}
        if (this.value instanceof Float)  { return (Float)this.value <= max.floatValue();}
        if (this.value instanceof Double)  { return (Double)this.value <= max.doubleValue();}
        if (this.value instanceof Byte)  { return (Integer)this.value <= max.intValue();}

        throw new UnsupportedOperationException("The Number [" + value.getClass() + "] is not supported by NumberWrapper!");
    }

    public boolean isBoundedLower(N min){
        if (this.value instanceof Integer)  { return (Integer)this.value >= min.intValue();}
        if (this.value instanceof Long)  { return (Long)this.value >=  min.longValue();}
        if (this.value instanceof Float)  { return (Float)this.value >= min.floatValue();}
        if (this.value instanceof Double)  { return (Double)this.value >= min.doubleValue();}
        if (this.value instanceof Byte)  { return (Integer)this.value >= min.intValue();}

        throw new UnsupportedOperationException("The Number [" + value.getClass() + "] is not supported by NumberWrapper!");
    }

    public NumberWrapper<N> increment(N value) {
        if (this.value instanceof Integer)  { this.value = (N) Integer.valueOf(this.value.intValue() + value.intValue()); return this; }
        if (this.value instanceof Long)     { this.value = (N) Long.valueOf(this.value.longValue() + value.longValue()); return this; }
        if (this.value instanceof Float)    { this.value = (N) Float.valueOf(this.value.floatValue() + value.floatValue()); return this; }
        if (this.value instanceof Double)   { this.value = (N) Double.valueOf(this.value.doubleValue() + value.doubleValue()); return this; }
        if (this.value instanceof Byte)     { this.value = (N) Byte.valueOf((byte) (this.value.byteValue() + value.byteValue())); return this; }

        throw new UnsupportedOperationException("The Number [" + value.getClass() + "] is not supported by NumberWrapper!");
    }

    public NumberWrapper<N> decrement(N value) {
        if (this.value instanceof Integer)  { this.value = (N) Integer.valueOf(this.value.intValue() - value.intValue()); return this; }
        if (this.value instanceof Long)     { this.value = (N) Long.valueOf(this.value.longValue() - value.longValue()); return this; }
        if (this.value instanceof Float)    { this.value = (N) Float.valueOf(this.value.floatValue() - value.floatValue()); return this; }
        if (this.value instanceof Double)   { this.value = (N) Double.valueOf(this.value.doubleValue() - value.doubleValue()); return this; }
        if (this.value instanceof Byte)     { this.value = (N) Byte.valueOf((byte) (this.value.byteValue() - value.byteValue())); return this; }

        throw new UnsupportedOperationException("The Number [" + value.getClass() + "] is not supported by NumberWrapper!");
    }

    public NumberWrapper<N> multiply(N value){
        if (this.value instanceof Integer)  { this.value = (N) Integer.valueOf(this.value.intValue() * value.intValue()); return this; }
        if (this.value instanceof Long)     { this.value = (N) Long.valueOf(this.value.longValue() * value.longValue()); return this; }
        if (this.value instanceof Float)    { this.value = (N) Float.valueOf(this.value.floatValue() * value.floatValue()); return this; }
        if (this.value instanceof Double)   { this.value = (N) Double.valueOf(this.value.doubleValue() * value.doubleValue()); return this; }
        if (this.value instanceof Byte)     { this.value = (N) Byte.valueOf((byte) (this.value.byteValue() * value.byteValue())); return this; }

        throw new UnsupportedOperationException("The Number [" + value.getClass() + "] is not supported by NumberWrapper!");
    }

    public NumberWrapper<N> divide(N value){
        if (this.value instanceof Integer)  { this.value = (N) Integer.valueOf(this.value.intValue() / value.intValue()); return this; }
        if (this.value instanceof Long)     { this.value = (N) Long.valueOf(this.value.longValue() / value.longValue()); return this; }
        if (this.value instanceof Float)    { this.value = (N) Float.valueOf(this.value.floatValue() / value.floatValue()); return this; }
        if (this.value instanceof Double)   { this.value = (N) Double.valueOf(this.value.doubleValue() / value.doubleValue()); return this; }
        if (this.value instanceof Byte)     { this.value = (N) Byte.valueOf((byte) (this.value.byteValue() / value.byteValue())); return this; }

        throw new UnsupportedOperationException("The Number [" + value.getClass() + "] is not supported by NumberWrapper!");
    }

    public NumberWrapper<N> normalize(){
        if (this.value instanceof Float) {
            this.value = (N) Float.valueOf((float) FCMathUtil.normalizeDouble(this.value.doubleValue()));
        }
        if (this.value instanceof Double) {
            this.value = (N) Double.valueOf(FCMathUtil.normalizeDouble(this.value.doubleValue()));
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        NumberWrapper that = (NumberWrapper) o;
        //We compare the inner value not the class, so, a Byte and a Double with the same numerical value are equal!
        return Objects.equals(value.doubleValue(), that.value.doubleValue());
    }

    public NumberWrapper clone(){
        return new NumberWrapper(this.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public int compareTo(@Nonnull NumberWrapper o) {
        return Double.compare(this.value.doubleValue(), o.value.doubleValue());
    }

    @Override
    public String toString() {
        return FCMathUtil.toString(value.doubleValue());
    }

}
