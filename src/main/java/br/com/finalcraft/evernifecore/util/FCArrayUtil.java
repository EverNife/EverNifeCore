package br.com.finalcraft.evernifecore.util;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;

public class FCArrayUtil {

    public static <T> T[] merge(@NotNull T value, T... otherValues){
        T[] theArray = (T[]) Array.newInstance(value.getClass(), otherValues.length + 1);
        theArray[0] = value;
        for (int i = 1; i < theArray.length; i++) {
            theArray[i] = otherValues[i];
        }
        return theArray;
    }


}
