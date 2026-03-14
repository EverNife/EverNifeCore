package br.com.finalcraft.evernifecore.config.yaml.caching;

import org.simpleyaml.configuration.file.YamlFile;

public class SimpleYamlFileHolder implements IHasYamlFile {

    private final YamlFile yamlFile;

    public SimpleYamlFileHolder(YamlFile yamlFile) {
        this.yamlFile = yamlFile;
    }

    @Override
    public YamlFile getYamlFile() {
        return yamlFile;
    }

}
