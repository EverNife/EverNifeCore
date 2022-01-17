package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class FCMathUtil {

    private static Random RANDOM = new Random();

    public static Random getRandom() {
        return RANDOM;
    }

    public static Double getMedian(List<Double> doubleList){
        if (doubleList.isEmpty()) throw new IllegalArgumentException("DoubleList may not be empty");

        List<Double> doubleListClone = new ArrayList<>(doubleList); //Clone the list to prevent un-intentional ordering
        Collections.sort(doubleListClone);
        double median;
        if (doubleListClone.size() % 2 == 0) {
            double middle = doubleListClone.get(doubleListClone.size() / 2);
            double beforeMiddle = doubleListClone.get(doubleListClone.size() / 2 - 1);
            median = (int) ((middle + beforeMiddle) / 2.0);
        }else{
            median = doubleListClone.get(doubleListClone.size() / 2);
        }
        return median;
    }

    public static DecimalFormat decimalFormat = new DecimalFormat("0");
    public static String toString(double value){
        if (value % 1 != 0){
            return String.valueOf(normalizeDouble(value));
        }else {
            if (value < Long.MAX_VALUE && value > -Long.MAX_VALUE){
                return String.valueOf((long) value);
            }else {
                return decimalFormat.format(value);
            }
        }
    }

    public static <N extends Number> NumberWrapper<N> wrapper(N number){
        return NumberWrapper.of(number);
    }

    public static double normalizeDouble(double value) {
        return normalizeDouble(value, 2);
    }

    public static double normalizeDouble(double value, int zeros) {
        if (zeros < 0) throw new IllegalArgumentException("'zeros' can not be negative, passed one was: " + zeros);
        double realZeros;
        switch (zeros) {
            case 0:                realZeros = 1;                   break;
            case 1:                realZeros = 10;                  break;
            case 2:                realZeros = 100;                 break;
            case 3:                realZeros = 1000;                break;
            case 4:                realZeros = 10000;               break;
            default:               realZeros = Math.pow(10,zeros);
        }
        return ((double) Math.round(value * realZeros) / realZeros);
    }
}
