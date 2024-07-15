package br.com.finalcraft.evernifecore.test.config;

import br.com.finalcraft.evernifecore.config.Config;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigReloadingTest {

    private Yaml yaml = new Yaml();

    private long timings(Runnable runnable){
        long start = System.currentTimeMillis();
        runnable.run();
        long end = System.currentTimeMillis();
        return end - start;
    }

    @Test
    public void severalConvertToYaml(){
        /**
         * The idea of this test was to check the efficiency of storing the content of a
         * YamlFile on a String and reload it.
         *
         * Tested with a Config with 3000 lines, the avarage time to ''reload'' is 10 milliseconds.
         * With 300 lines it take around 1 to 2 milliseconds.
         *
         * Its good enough :/
         * This test was created while developing the {@link br.com.finalcraft.evernifecore.config.yaml.caching.SmartCachedYamlFileHolder}
         */

        if (true) return;

        AtomicReference<String> stringContentReference = new AtomicReference<>();

        long creationTime = timings(() -> {
            Config config = new Config("");
            Random random = new Random(1234567890);

            //This will generate a Yalm file with 300 lines
            for (int i = 0; i < 100; i++) {
                //Create a big config file
                config.setValue(random.nextInt(10000) + "." + random.nextInt(10000) + "." + i, i);
            }
            System.out.println(config.toString());
            stringContentReference.set(config.toString());
        });

        System.out.println(String.format("Took %s millis to generate the custom Config!", creationTime));

        String stringContent = stringContentReference.get();

        List<Long> timings = new ArrayList<>();

        int ATTEMPT_AMOUNT = 200;
        int BOUND = 50;
        for (int i = 0; i < ATTEMPT_AMOUNT + BOUND; i++) {
            long time = timings(() -> convertFromYaml(stringContent));
            if (i > BOUND){
                timings.add(time);
            }
        }

        for (Long timing : timings) {
            System.out.println("Time Consumed: " + timing);
        }

        System.out.println(String.format("Avarange: (%sx Times): %s",  ATTEMPT_AMOUNT, timings.stream().mapToLong(Long::longValue).average().getAsDouble()));
    }

    @SneakyThrows
    public void convertFromYaml(String yamlString)  {
        Config config2 = new Config("");
        config2.getConfiguration().loadFromString(yamlString);
    }

//    @SneakyThrows
//    public void convertFromJson(String jsonString)  {
//        Map<String,Object> map = yaml.load(jsonString);
//        JsonElement jsonObject = FCJsonUtil.getGson().toJsonTree(map);
//        String stringJson = jsonObject.toString();
//
//        Config config = new Config("");
//        config.getConfiguration().loadFromString(stringJson);
//    }


}
