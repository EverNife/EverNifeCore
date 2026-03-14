package br.com.finalcraft.evernifecore._misc.spliting;

import br.com.finalcraft.evernifecore.util.FCMathUtil;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

@Log
public class TextSplittingTests {

    private static final Pattern PIPE_PATTERN = Pattern.compile("\\|");

    /**
     * Comparation between different splits.
     *
     * From my tests, for small strings like blockPoss, tokenization can be,
     * on avarage, 31% faster than normal String::split.
     *
     * Patern split sucks at all, being like 80% slower than tokenization.
     * @throws InterruptedException
     */
    @Test
    @SneakyThrows
    public void comparaStringSplit_PatternSplit_TokenSplit() {

        if (true) {
            return; // Disabled test to avoid execution during normal test runs
        }

        String inputPattern = "%s|%s|%s";

        int ITERATIONS = 100;

        System.out.println(String.format("\nPreparing %s iterations with completetly random numbers for the pattern...\n", ITERATIONS));

        List<String> inputs = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            int x = FCMathUtil.getRandom().nextInt(1_000_000);
            int y = FCMathUtil.getRandom().nextInt(1_000_000);
            int z = FCMathUtil.getRandom().nextInt(1_000_000);
            inputs.add(String.format(inputPattern, x, y, z));
        }

        for (String input : inputs) {
            //hotload the VM with some random stuff
            input.length();
            inputs.toString();
        }

        // Benchmark PIPE_PATTERN.split()
        long startTime = System.nanoTime();
        for (String input : inputs) {
            String[] result = PIPE_PATTERN.split(input);
        }
        long endTime = System.nanoTime();
        long patternSplitDuration = endTime - startTime;
        System.out.println("Pattern.split() duration: " + patternSplitDuration + " ns (value ignored)");

        // Benchmark String.split()
        startTime = System.nanoTime();
        for (String input : inputs) {
            String[] result = input.split("\\|");
        }
        endTime = System.nanoTime();
        long splitDuration = endTime - startTime;
        System.out.println("String.split() duration: " + splitDuration + " ns");

        // Benchmark StringTokenizer
        startTime = System.nanoTime();
        for (String input : inputs) {
            List<String> result = new ArrayList<>();
            StringTokenizer tokenizer = new StringTokenizer(input, "|");
            while (tokenizer.hasMoreTokens()) {
                result.add(tokenizer.nextToken());
            }
        }
        endTime = System.nanoTime();
        long tokenizerDuration = endTime - startTime;
        System.out.println("StringTokenizer duration: " + tokenizerDuration + " ns\n");

        if (splitDuration < tokenizerDuration) {
            long nanoDifference = (tokenizerDuration - splitDuration);
            double percentage = (1d - ((double)splitDuration / (double)tokenizerDuration)) * 100;
            System.out.println(String.format("Split was Faster in %s ns (%s percent faster)", nanoDifference, FCMathUtil.toString(percentage)));
        }else {
            long nanoDifference = (splitDuration - tokenizerDuration);
            double percentage = (1d - ((double)tokenizerDuration / (double)splitDuration)) * 100;
            System.out.println(String.format("Tokenization was Faster in %s ns (%s percent faster)", nanoDifference, FCMathUtil.toString(percentage)));
        }
    }

}
