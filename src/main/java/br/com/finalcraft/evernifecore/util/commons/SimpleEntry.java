package br.com.finalcraft.evernifecore.util.commons;

import java.util.Map;
import java.util.Objects;

public class SimpleEntry<K,V> implements Map.Entry<K,V>{

    private final K key;
    private final V value;

    private SimpleEntry(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public static <K,V> SimpleEntry<K,V> of(K key, V value) {
        return new SimpleEntry<>(key, value);
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public V setValue(V value) {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (o instanceof Map.Entry) {
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            return Objects.equals(key, e.getKey()) &&
                    Objects.equals(value, e.getValue());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
