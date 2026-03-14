package br.com.finalcraft.evernifecore.dependencies;

import net.byteflux.libby.Library;

public class ECoreDependencies {

    public static void initialize(DependencyManager dependencyManager){
        dependencyManager.loadLibrary(
                Library.builder()
                        .groupId("com.github.Carleslc.Simple-YAML")
                        .artifactId("Simple-Yaml")
                        .version("1.8.4")
                        .relocate("org.yaml","br.com.finalcraft.libs.yaml")
                        .build()
        ); //SnakeYAML + Simple-YAML
    }
}
