package br.com.finalcraft.evernifecore.test.config;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.fcconfiguration.annotation.FConfig;
import br.com.finalcraft.evernifecore.config.fcconfiguration.annotation.FConfigComplex;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.util.FCTimeUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FConfigTests {

    @Test
    public void testeFCConfigurations() throws IOException {

        if (true){
            return;
        }

        Config config = new Config(new File("teste.yml"));

        config.setValue("Teste", new Teste(UUID.randomUUID(), "John", 30, 100.0f));

        config.setValue("Testes", Arrays.asList(
                new Teste(UUID.randomUUID(), "John1", 10, 100.0f),
                new Teste(UUID.randomUUID(), "John2", 20, 200.0f),
                new Teste(UUID.randomUUID(), "John3", 30, 300.0f)
        ));

        config.setValue("DoubleTest",
                Arrays.asList(
                        new DoubleTeste(UUID.randomUUID(), new Teste(UUID.randomUUID(), "John1", 10, 100.0f), new Teste(UUID.randomUUID(), "John2", 10, 100.0f), null),
                        new DoubleTeste(
                                UUID.randomUUID(),
                                new Teste(UUID.randomUUID(), "John3", 10, 100.0f),
                                new Teste(UUID.randomUUID(), "John4", 10, 100.0f),
                                new LinkedHashSet<>(
                                        Arrays.asList(
                                                new DoubleTeste(UUID.randomUUID(), new Teste(UUID.randomUUID(), "John5", 1, 1), new Teste(UUID.randomUUID(), "John6", 1, 1), null),
                                                new DoubleTeste(UUID.randomUUID(), new Teste(UUID.randomUUID(), "John5", 2, 2), new Teste(UUID.randomUUID(), "John6", 2, 2), null),
                                                new DoubleTeste(UUID.randomUUID(), new Teste(UUID.randomUUID(), "John5", 3, 3), new Teste(UUID.randomUUID(), "John6", 3, 3), null)
                                        )
                                )
                        )
                ));

        config.setValue("ComplexTest", new ComplexTeste());

        config.save();

        config = new Config(new File("teste.yml"));
        config.getLoadableList("Testes", Teste.class).forEach(System.out::println);
        config.getLoadableList("DoubleTest", DoubleTeste.class).forEach(System.out::println);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FConfig
    public static class Teste {
        UUID uuid;
        String name;
        int age;
        float money;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FConfig
    public static class DoubleTeste {
        @FConfig.Id
        UUID uuid;
        Teste a;
        Teste b;
        @FConfig(comment = "Inner Double Tests")
        LinkedHashSet<DoubleTeste> inners;
    }

    @Data
    @FConfig
    public static class ComplexTeste implements FConfigComplex {
        UUID uuid = UUID.randomUUID();
        @FConfig(comment = "lastTimeSaved")
        String lastTimeSaved;

        @Override
        public void onConfigSavePre(ConfigSection section) {
            //Do something before saving
            lastTimeSaved = FCTimeUtil.getFormatted(System.currentTimeMillis());
        }
    }


}
