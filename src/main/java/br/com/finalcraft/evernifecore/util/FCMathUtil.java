package br.com.finalcraft.evernifecore.util;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FCMathUtil {

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
            return String.valueOf(FCBukkitUtil.normalizeDouble(value));
        }else {
            if (value < Long.MAX_VALUE && value > -Long.MAX_VALUE){
                return String.valueOf((long) value);
            }else {
                return decimalFormat.format(value);
            }
        }
    }

}
