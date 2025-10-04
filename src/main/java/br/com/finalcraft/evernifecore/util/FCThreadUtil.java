package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.util.commons.MinMax;

public class FCThreadUtil {

    public static MinMax<Integer> getMinMaxThreadCountBoundedToSystemCoreCount(int max) {
        return getMinMaxThreadCountBoundedToSystemCoreCount(1, max);
    }

    public static MinMax<Integer> getMinMaxThreadCountBoundedToSystemCoreCount(int min, int max) {
        int coreCount = Runtime.getRuntime().availableProcessors();
        min = Math.max(min, 1); // Ensure min is at least 1
        max = Math.max(max, min); // Ensure max is at least min
        max = Math.min(max, coreCount); // Ensure max does not exceed core count
        min = Math.min(min, max); // Ensure min does not exceed max
        return MinMax.of(min, max);
    }

}
