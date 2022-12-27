package br.com.finalcraft.evernifecore.util.commons;

import lombok.Data;

@Data
public class MinMax<O> {

    private final O min;
    private final O max;

    private MinMax(O min, O max) {
        this.min = min;
        this.max = max;
    }

    public static <O> MinMax<O> of(O min, O max) {
        return new MinMax<>(min, max);
    }

}
