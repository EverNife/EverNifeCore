package br.com.finalcraft.evernifecore.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FCCollectionsUtil {

    private static <T> List<T> reversed(List<T> list){
        Collections.reverse(list);
        return list;
    }

    /**
     * Splits a list of elements into a specified number of sublists, evenly distributing the elements.
     *
     * @param elements the list of elements to be split
     * @param parts    the number of sublists to split the list into
     * @return a list of sublists, each containing an equal distribution of elements
     *
     * @example:
     *    // Input: ["a", "b", "c", "d", "e", "f", "g", "h", "i", "j"], 4
     *    // Output: [["a", "b", "c"], ["d", "e"], ["f", "g", "h"], ["i", "j"]]
     *
     *    // Input: ["a", "b"], 7
     *    // Output: [["a"], [], [], ["b"], [], [], []]
     *
     *    // Input: ["a", "b"], 8
     *    // Output: [["a"], [], [], [], ["b"], [], [], []]
     */
    public static <T> List<List<T>> partitionEvenly(List<T> elements, int parts){
        List<List<T>> result = new ArrayList<>(parts);

        if (parts == 1){
            result.add(new ArrayList<>(elements));
            return result;
        }

        int chunkSize = (int) Math.floor(elements.size() / (double) parts);
        int leftOver = (int) Math.floor(elements.size() % (double) parts);

        int gap = parts / leftOver;
        int gapCount = gap;
        int gapNiddle = 0;

        for (int i = 0; i < parts; i++) {
            List<T> subList = new ArrayList<>();

            int start = (i * chunkSize) + gapNiddle;
            int end = start + chunkSize;

            if (chunkSize > 0){ //Evenly split these Chunks into even parts
                subList.addAll(elements.subList(start, end));
            }

            if (leftOver > 0){ //We still have some leftOver
                if (gapCount == gap){ //If we are into a gap position we add one more element
                    gapCount = 0;
                    leftOver--;
                    gapNiddle++; //We move the gapNiddle to the right to prevent element duplication
                    subList.add(elements.get(end));
                }
                gapCount++;
            }

            result.add(subList);
        }

        return result;
    }

}
