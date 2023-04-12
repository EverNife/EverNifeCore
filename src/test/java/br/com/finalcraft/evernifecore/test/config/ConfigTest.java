package br.com.finalcraft.evernifecore.test.config;

import br.com.finalcraft.evernifecore.config.Config;
import org.junit.jupiter.api.Test;
import org.simpleyaml.configuration.file.YamlFile;
import org.simpleyaml.configuration.implementation.api.QuoteStyle;
import org.simpleyaml.configuration.implementation.snakeyaml.SnakeYamlImplementation;
import org.simpleyaml.configuration.implementation.snakeyaml.lib.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigTest {

    @Test
    public void breakYamlFile() throws IOException {

        if (true) return;

        // Create a YAML object
        // Create a DumperOptions object and set the defaultFlowStyle to FLOW
        org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
        options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.FLOW);
        options.setWidth(Integer.MAX_VALUE);
        Yaml yaml = new Yaml(options);

        // Dump the YAML data into a single line
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("name", "Jo\n\nhn");
        data.put("age", 30);
        data.put("city", "New York");

        String yamlStr = yaml.dump(data);

        System.out.println(yamlStr);

        Config newConfig = new Config("");
        newConfig.getConfiguration().loadFromString(yamlStr);
        System.out.println(newConfig.getInt("age"));

        if (true) return;

        YamlFile config = new YamlFile();
        SnakeYamlImplementation implementation = (SnakeYamlImplementation) config.options().configuration().getImplementation();
        implementation.getDumperOptions().setWidth(999999);
        implementation.getDumperOptions().setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);

        config.set("name","Abra");
        config.set("level",23);
        config.set("species", "Human");
        config.set("species", "Os");

        String serialized = config.dump();//.replace("\n", "|");

        System.out.println(serialized);

        YamlFile config2 = new YamlFile();
        config2.loadFromString(serialized.replace("|", "\n"));
        System.out.println(config2.get("name"));

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
