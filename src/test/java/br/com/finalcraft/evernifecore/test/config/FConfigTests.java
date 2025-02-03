package br.com.finalcraft.evernifecore.test.config;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.fcconfiguration.annotation.FConfig;
import br.com.finalcraft.evernifecore.config.fcconfiguration.annotation.IFConfigComplex;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.util.FCTimeUtil;
import lombok.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class FConfigTests {

    private static Config config;

//    @BeforeAll
//    public static void setUp() {
//        config = new Config(new File("teste.yml"));
//    }
//
//    @AfterAll
//    public static void tearDown() {
//        config.getTheFile().delete();
//    }


    @Test
    public void testeFCConfigurations() throws IOException {

        if (true){
            return;
        }

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

//        config.setValue("ComplexTest", new ComplexTeste());

        config.setValue("ComplexTestThatExtendsSomethingElse", new ComplexTesteThatExtendsSomethingElse());

        config.save();

        config = new Config(new File("teste.yml"));
        config.getLoadableList("Testes", Teste.class).forEach(System.out::println);
        config.getLoadableList("DoubleTest", DoubleTeste.class).forEach(System.out::println);
//        Optional.of(config.getLoadable("ComplexTest", ComplexTeste.class)).ifPresent(System.out::println);
        Optional.of(config.getLoadable("ComplexTestThatExtendsSomethingElse", ComplexTesteThatExtendsSomethingElse.class)).ifPresent(System.out::println);
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
        @FConfig.Comment("Inner Double Tests")
        LinkedHashSet<DoubleTeste> inners;
    }

    @Data
    @NoArgsConstructor
    public static class ComplexTeste implements IFConfigComplex {
        UUID uuid = UUID.randomUUID();
        @FConfig.Comment("lastTimeSaved")
        String lastTimeSaved;

        @Override
        public void onConfigSavePre(ConfigSection section) {
            //Do something before saving
            lastTimeSaved = FCTimeUtil.getFormatted(System.currentTimeMillis());
        }
    }

    @Data
    @FConfig(enforceSuperClassSerialization = FConfig.SuperClassSerialization.FORCED)
    @NoArgsConstructor
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper=false)
    public static class ComplexTesteThatExtendsSomethingElse extends ComplexTeste {
        long daysOfToday;

        @Override
        public void onConfigSavePre(ConfigSection section) {
            super.onConfigSavePre(section);
            //Do something before saving
            daysOfToday = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
        }
    }


}
