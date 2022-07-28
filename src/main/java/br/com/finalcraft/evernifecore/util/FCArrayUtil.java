package br.com.finalcraft.evernifecore.util;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;

public class FCArrayUtil {

    /**
     * Create a new array of the same type as the first argument, and copy the first argument and the rest of the arguments
     * into the new array.
     *
     * @param value The value to be added to the beginning of the array.
     * @return An array of the same type as the first argument.
     */
    public static <T> T[] merge(@NotNull T value, T... otherValues){
        T[] theArray = (T[]) Array.newInstance(value.getClass(), otherValues.length + 1);
        theArray[0] = value;
        for (int i = 1; i < theArray.length; i++) {
            theArray[i] = otherValues[i];
        }
        return theArray;
    }

    /**
     * It takes an array of type T and another array of type T and returns a new array of type T with both data merged
     *
     * @param value The array to be merged with otherValues.
     * @return An array of type T
     */
    public static <T> T[] mergeArray(@NotNull T[] value, T... otherValues){
        T[] theArray = (T[]) Array.newInstance(value.getClass(), value.length + otherValues.length);
        int index = 0;

        for (int i = 0; i < value.length; i++) {
            theArray[index] = value[i];
            index++;
        }

        for (int j = 0; j < otherValues.length; j++) {
            theArray[index] = otherValues[j];
            index++;
        }

        return theArray;
    }


}
