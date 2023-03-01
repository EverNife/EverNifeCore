package br.com.finalcraft.evernifecore.test.config;

import org.junit.jupiter.api.Test;
import org.simpleyaml.configuration.file.YamlFile;
import org.simpleyaml.configuration.implementation.api.QuoteStyle;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigTest {

    @Test
    public void breakYamlFile() throws IOException {

        if (true) return;

        File testFile = new File("test-file.yml");
        YamlFile yamlFile = new YamlFile(testFile);

        yamlFile.options().quoteStyleDefaults().setQuoteStyle(List.class, QuoteStyle.DOUBLE);
        yamlFile.options().quoteStyleDefaults().setQuoteStyle(String.class, QuoteStyle.DOUBLE);

        String lineWithMultipleNewLines = "Hola Hermano?" +
                "\nQue passa?" +
                "\nTa tudo bem?" +
                "\nYo" +
                "\nSoy" +
                "\nEl" +
                "\nGran" +
                "\nAmigo" +
                "\naaaaaa bbbbb cccccc aaaaaa bbbbb cccccc aaaaaa bbbbb cccccc aaaaaa bbbbb cccccc aaaaaa bbbbb cccccc aaaaaa bbbbb cccccc";

        yamlFile.set("text", lineWithMultipleNewLines);

        List list = Arrays.stream(lineWithMultipleNewLines.split("\n")).collect(Collectors.toList());
        list.add("teste\n\n\n1asdasd");
        yamlFile.set("text2", list);

        HashMap hashMap = new HashMap();
        hashMap.put("abra", "cadabra");
        hashMap.put("fechate", "cesamo");
        yamlFile.set("text3", hashMap);

        yamlFile.save();

        yamlFile = new YamlFile(testFile);
        yamlFile.loadWithComments();

        System.out.println(yamlFile.getString("text"));
    }


}
