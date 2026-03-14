package br.com.finalcraft.evernifecore.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class FCCollectionUtilTest {

    @Test
    public void testSpliter() {
        List<String> parts = Arrays.asList("a","b","c","d","e","f","g","h","i","j","k","l","m","n"); //14 elements

        List<List<String>> lists = FCCollectionsUtil.partitionEvenly(parts, 6);

        String formated = "";
        for (int i = 0; i < lists.size(); i++) {
            String line = "List " + i + ": " + Arrays.asList(lists.get(i).toArray());
            formated = formated + line + "\n";
        }

        System.out.println(formated + "\n\n");
        assert formated.contains("List 0: [a, b, c]\n" +
                "List 1: [d, e]\n" +
                "List 2: [f, g]\n" +
                "List 3: [h, i, j]\n" +
                "List 4: [k, l]\n" +
                "List 5: [m, n]"
        );

        parts = Arrays.asList("a","b"); //2 elements
        lists = FCCollectionsUtil.partitionEvenly(parts, 7);
        formated = "";
        for (int i = 0; i < lists.size(); i++) {
            String line = "List " + i + ": " + Arrays.asList(lists.get(i).toArray());
            formated = formated + line + "\n";
        }
        System.out.println(formated);
    }

}
